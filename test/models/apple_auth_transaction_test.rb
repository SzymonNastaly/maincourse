require "test_helper"

class AppleAuthTransactionTest < ActiveSupport::TestCase
  RFC_7636_VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
  RFC_7636_CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"

  test "start creates a five-minute transaction and returns a 32-byte opaque handle" do
    freeze_time do
      transaction, handle = AppleAuthTransaction.start!(
        code_challenge: RFC_7636_CHALLENGE,
        callback: "release"
      )

      assert_equal 43, handle.length
      assert_match(/\A[A-Za-z0-9_-]+\z/, handle)
      assert_equal Digest::SHA256.hexdigest(handle), transaction.handle_digest
      assert_equal "https://app.getmaincourse.com/android/auth/apple", transaction.return_uri
      assert_equal 5.minutes.from_now, transaction.expires_at
      refute_includes transaction.attributes.values, handle
      refute_includes AppleAuthTransaction.column_names, "code_verifier"
      refute_includes AppleAuthTransaction.column_names, "exchange_code"
    end
  end

  test "start accepts only a 43-character URL-safe unpadded S256 challenge" do
    invalid_challenges = [ nil, 123, "a" * 42, "a" * 44, "a" * 42 + "=", "a" * 42 + "." ]

    invalid_challenges.each do |challenge|
      assert_raises(AppleAuthTransaction::InvalidStartError) do
        AppleAuthTransaction.start!(code_challenge: challenge, callback: "release")
      end
    end
  end

  test "start accepts only server-selected callback names" do
    [ nil, 1, "", "https://attacker.example/callback", "DEBUG" ].each do |callback|
      assert_raises(AppleAuthTransaction::InvalidStartError) do
        AppleAuthTransaction.start!(code_challenge: RFC_7636_CHALLENGE, callback: callback)
      end
    end
  end

  test "debug callback is allowed only in local environments" do
    transaction, = AppleAuthTransaction.start!(code_challenge: RFC_7636_CHALLENGE, callback: "debug")
    assert_equal "com.getmaincourse.app.debug:/oauth/apple", transaction.return_uri

    Rails.env.stub(:local?, false) do
      assert_raises(AppleAuthTransaction::InvalidStartError) do
        AppleAuthTransaction.start!(code_challenge: RFC_7636_CHALLENGE, callback: "debug")
      end
    end
  end

  test "find_by_handle performs digest lookup" do
    transaction, handle = AppleAuthTransaction.start!(code_challenge: RFC_7636_CHALLENGE, callback: "release")

    assert_equal transaction, AppleAuthTransaction.find_by_handle(handle)
    assert_nil AppleAuthTransaction.find_by_handle("not-the-handle")
    assert_nil AppleAuthTransaction.find_by_handle(nil)
  end

  test "start deletes transactions that expired more than one day ago" do
    old = create_transaction(expires_at: 1.day.ago - 1.second)
    recent = create_transaction(expires_at: 1.day.ago + 1.second)

    AppleAuthTransaction.start!(code_challenge: RFC_7636_CHALLENGE, callback: "release")

    refute AppleAuthTransaction.exists?(old.id)
    assert AppleAuthTransaction.exists?(recent.id)
  end

  test "authorize stores only an exchange digest and limits exchange to one minute" do
    freeze_time do
      transaction = create_transaction(expires_at: 5.minutes.from_now)

      exchange_code = transaction.authorize!(user: users(:one))

      assert_equal 43, exchange_code.length
      assert_equal Digest::SHA256.hexdigest(exchange_code), transaction.exchange_digest
      assert_equal Time.current, transaction.authorized_at
      assert_equal 1.minute.from_now, transaction.exchange_expires_at
      refute_includes transaction.attributes.values, exchange_code
    end
  end

  test "exchange expiry never extends the transaction lifetime" do
    freeze_time do
      transaction = create_transaction(expires_at: 30.seconds.from_now)
      transaction.authorize!(user: users(:one))

      assert_equal 30.seconds.from_now, transaction.exchange_expires_at
    end
  end

  test "confirmation required state preserves an authorizable transaction" do
    transaction = create_transaction

    transaction.mark_confirmation_required!

    assert_predicate transaction.confirmation_required_at, :present?
    assert_predicate transaction, :confirmation_required?
    assert_same transaction, transaction.authorizable!
    assert_raises(AppleAuthTransaction::AuthorizationStateError) { transaction.mark_confirmation_required! }
  end

  test "authorize rejects expired or already-authorized state" do
    expired = create_transaction(expires_at: 1.second.ago)
    assert_raises(AppleAuthTransaction::AuthorizationStateError) { expired.authorize!(user: users(:one)) }

    transaction = create_transaction
    transaction.authorize!(user: users(:one))
    assert_raises(AppleAuthTransaction::AuthorizationStateError) { transaction.authorize!(user: users(:one)) }
  end

  test "exchange validates the RFC 7636 S256 vector and returns a normal session payload" do
    transaction = create_transaction(code_challenge: RFC_7636_CHALLENGE)
    exchange_code = transaction.authorize!(user: users(:one))

    payload = transaction.exchange!(
      exchange_code: exchange_code,
      code_verifier: RFC_7636_VERIFIER,
      device_name: "Pixel 10",
      onboarding_device_id: nil
    )

    token = ApiToken.find_by_raw_token(payload[:token])
    assert_equal users(:one), token.user
    assert_equal "Pixel 10", token.name
    assert_equal token.expires_at, payload[:expires_at]
    assert_equal users(:one).id, payload.dig(:user, :id)
    assert_equal users(:one).email_address, payload.dig(:user, :email)
    assert_predicate transaction.reload.consumed_at, :present?
  end

  test "exchange links onboarding in the same successful operation" do
    onboarding = OnboardingResponse.create!(device_id: "apple-onboarding", answers: { "goal" => "plan" })
    transaction = create_transaction(code_challenge: RFC_7636_CHALLENGE)
    exchange_code = transaction.authorize!(user: users(:one))

    transaction.exchange!(
      exchange_code: exchange_code,
      code_verifier: RFC_7636_VERIFIER,
      device_name: nil,
      onboarding_device_id: onboarding.device_id
    )

    assert_equal users(:one), onboarding.reload.user
  end

  test "wrong code and wrong verifier are generic failures that do not consume or mint tokens" do
    transaction = create_transaction(code_challenge: RFC_7636_CHALLENGE)
    exchange_code = transaction.authorize!(user: users(:one))

    assert_no_difference("ApiToken.count") do
      assert_raises(AppleAuthTransaction::ExchangeError) do
        transaction.exchange!(exchange_code: "x" * 43, code_verifier: RFC_7636_VERIFIER,
          device_name: nil, onboarding_device_id: nil)
      end
    end
    assert_nil transaction.reload.consumed_at

    assert_no_difference("ApiToken.count") do
      assert_raises(AppleAuthTransaction::ExchangeError) do
        transaction.exchange!(exchange_code: exchange_code, code_verifier: "A" * 43,
          device_name: nil, onboarding_device_id: nil)
      end
    end
    assert_nil transaction.reload.consumed_at
  end

  test "exchange rejects malformed verifier values" do
    transaction = create_transaction(code_challenge: RFC_7636_CHALLENGE)
    exchange_code = transaction.authorize!(user: users(:one))
    invalid_verifiers = [ nil, 123, "a" * 42, "a" * 129, "a" * 42 + "/", "é" * 43 ]

    invalid_verifiers.each do |verifier|
      assert_raises(AppleAuthTransaction::ExchangeError) do
        transaction.exchange!(exchange_code: exchange_code, code_verifier: verifier,
          device_name: nil, onboarding_device_id: nil)
      end
    end
    assert_nil transaction.reload.consumed_at
  end

  test "exchange rejects expired authorization and replay without another token" do
    transaction = create_transaction(code_challenge: RFC_7636_CHALLENGE)
    exchange_code = transaction.authorize!(user: users(:one))

    travel 61.seconds do
      assert_no_difference("ApiToken.count") do
        assert_raises(AppleAuthTransaction::ExchangeError) do
          transaction.exchange!(exchange_code: exchange_code, code_verifier: RFC_7636_VERIFIER,
            device_name: nil, onboarding_device_id: nil)
        end
      end
    end

    fresh = create_transaction(code_challenge: RFC_7636_CHALLENGE)
    fresh_code = fresh.authorize!(user: users(:one))
    fresh.exchange!(exchange_code: fresh_code, code_verifier: RFC_7636_VERIFIER,
      device_name: nil, onboarding_device_id: nil)

    assert_no_difference("ApiToken.count") do
      assert_raises(AppleAuthTransaction::ExchangeError) do
        fresh.exchange!(exchange_code: fresh_code, code_verifier: RFC_7636_VERIFIER,
          device_name: nil, onboarding_device_id: nil)
      end
    end
  end

  test "failed onboarding link rolls back token generation and consumption" do
    transaction = create_transaction(code_challenge: RFC_7636_CHALLENGE)
    exchange_code = transaction.authorize!(user: users(:one))

    assert_no_difference("ApiToken.count") do
      OnboardingResponse.stub(:link_to_user!, ->(**) { raise ActiveRecord::RecordInvalid }) do
        assert_raises(ActiveRecord::RecordInvalid) do
          transaction.exchange!(exchange_code: exchange_code, code_verifier: RFC_7636_VERIFIER,
            device_name: nil, onboarding_device_id: "broken-link")
        end
      end
    end
    assert_nil transaction.reload.consumed_at
  end

  test "user deletion cascades to authorized transactions" do
    user = User.create!(email_address: "apple-cascade@example.com", password: "password")
    transaction = create_transaction
    transaction.authorize!(user: user)

    user.destroy!

    refute AppleAuthTransaction.exists?(transaction.id)
  end

  private
    def create_transaction(code_challenge: RFC_7636_CHALLENGE, expires_at: 5.minutes.from_now)
      AppleAuthTransaction.create!(
        handle_digest: Digest::SHA256.hexdigest(SecureRandom.urlsafe_base64(32, padding: false)),
        code_challenge: code_challenge,
        return_uri: "https://app.getmaincourse.com/android/auth/apple",
        expires_at: expires_at
      )
    end
end

class AppleAuthTransactionConcurrencyTest < ActiveSupport::TestCase
  self.use_transactional_tests = false

  test "concurrent exchange calls on actual SQLite connections mint one token" do
    verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
    transaction, = AppleAuthTransaction.start!(
      code_challenge: "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
      callback: "release"
    )
    exchange_code = transaction.authorize!(user: users(:one))
    ready = Queue.new
    release = Queue.new

    results = 2.times.map do
      Thread.new do
        ActiveRecord::Base.connection_pool.with_connection do
          ready << true
          release.pop
          begin
            payload = AppleAuthTransaction.find(transaction.id).exchange!(
              exchange_code: exchange_code,
              code_verifier: verifier,
              device_name: "Apple concurrency test",
              onboarding_device_id: nil
            )
            [ :success, payload ]
          rescue AppleAuthTransaction::ExchangeError
            [ :rejected, nil ]
          end
        end
      end
    end
    2.times { ready.pop }
    2.times { release << true }
    outcomes = results.map(&:value)

    assert_equal 1, outcomes.count { |outcome, _| outcome == :success }
    assert_equal 1, outcomes.count { |outcome, _| outcome == :rejected }
    assert_equal 1, ApiToken.where(name: "Apple concurrency test").count
  ensure
    AppleAuthTransaction.where(id: transaction&.id).delete_all
    ApiToken.where(name: "Apple concurrency test").delete_all
  end
end
