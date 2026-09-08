require "test_helper"

class OmniauthCallbacksControllerTest < ActionDispatch::IntegrationTest
  setup do
    @previous_test_mode = OmniAuth.config.test_mode
    @previous_google_auth = OmniAuth.config.mock_auth[:google_oauth2]
    @previous_apple_auth = OmniAuth.config.mock_auth[:apple]
    OmniAuth.config.test_mode = true
  end

  teardown do
    OmniAuth.config.test_mode = @previous_test_mode
    restore_mock(:google_oauth2, @previous_google_auth)
    restore_mock(:apple, @previous_apple_auth)
  end

  test "requires password sign-in before linking Google to a password account" do
    user = users(:one)
    OmniAuth.config.mock_auth[:google_oauth2] = auth_hash(
      provider: "google_oauth2",
      uid: "google-web-user",
      email: user.email_address,
      name: "Web User"
    )

    assert_no_difference [ "User.count", "Identity.count", "Session.count" ] do
      get auth_google_oauth2_callback_path
    end

    assert_redirected_to new_session_path
    assert_match "Sign in with your password", flash[:alert]
    assert user.reload.authenticate("password")
  end

  test "captures confirmed Apple account creation intent through the test-mode OmniAuth request phase" do
    OmniAuth.config.mock_auth[:apple] = auth_hash(
      provider: "apple",
      uid: "apple-web-user",
      email: "apple-web@example.com",
      name: "Apple Web User",
      refresh_token: "rejected-apple-web-refresh-token"
    )
    apple_client = FakeAppleClient.new

    assert_no_difference [ "User.count", "Identity.count", "Session.count" ] do
      Oauth::Configuration.stub(:apple_services_id, "app.hauptgang.web") do
        Oauth::AppleClient.stub(:new, apple_client) do
          post auth_apple_callback_path
        end
      end
    end

    assert_redirected_to confirm_apple_account_creation_path

    OmniAuth.config.mock_auth[:apple] = auth_hash(
      provider: "apple",
      uid: "apple-web-user",
      email: "apple-web@example.com",
      name: "Apple Web User",
      refresh_token: "fresh-apple-web-refresh-token"
    )

    assert_difference [ "User.count", "Identity.count", "Session.count" ], 1 do
      Oauth::Configuration.stub(:apple_services_id, "app.hauptgang.web") do
        post "/auth/apple?allow_account_creation=true"
        assert_redirected_to auth_apple_callback_url
        assert_nil URI.parse(response.location).query

        post auth_apple_callback_path
      end
    end

    assert_redirected_to root_url
    identity = Identity.find_by!(provider: "apple", uid: "apple-web-user")
    assert_equal "fresh-apple-web-refresh-token", identity.apple_refresh_token_for("app.hauptgang.web")
    assert_equal [
      { refresh_token: "rejected-apple-web-refresh-token", client_id: "app.hauptgang.web" }
    ], apple_client.revocations
  end

  test "an unknown Apple callback redirects to confirmation and revokes its unused token" do
    OmniAuth.config.mock_auth[:apple] = auth_hash(
      provider: "apple",
      uid: "unconfirmed-web-user",
      email: "unconfirmed-web@example.com",
      refresh_token: "unused-unconfirmed-token"
    )
    apple_client = FakeAppleClient.new

    assert_no_difference [ "User.count", "Identity.count", "Session.count" ] do
      Oauth::Configuration.stub(:apple_services_id, "app.hauptgang.web") do
        Oauth::AppleClient.stub(:new, apple_client) do
          post auth_apple_callback_path
        end
      end
    end

    assert_redirected_to confirm_apple_account_creation_path
    assert_equal [
      { refresh_token: "unused-unconfirmed-token", client_id: "app.hauptgang.web" }
    ], apple_client.revocations
  end

  test "does not trust account creation intent added only to the Apple callback" do
    OmniAuth.config.mock_auth[:apple] = auth_hash(
      provider: "apple",
      uid: "tampered-web-user",
      email: "tampered-web@example.com",
      refresh_token: "tampered-refresh-token"
    )
    apple_client = FakeAppleClient.new

    assert_no_difference [ "User.count", "Identity.count", "Session.count" ] do
      Oauth::Configuration.stub(:apple_services_id, "app.hauptgang.web") do
        Oauth::AppleClient.stub(:new, apple_client) do
          post auth_apple_callback_path, params: { allow_account_creation: "true" }
        end
      end
    end

    assert_redirected_to confirm_apple_account_creation_path
  end

  test "consumes confirmed account creation intent after one callback" do
    OmniAuth.config.mock_auth[:apple] = auth_hash(
      provider: "apple",
      uid: "one-time-confirmed-user",
      email: "one-time-confirmed@example.com",
      refresh_token: "confirmed-refresh-token"
    )
    apple_client = FakeAppleClient.new

    Oauth::AppleClient.stub(:new, apple_client) do
      Oauth::Configuration.stub(:apple_services_id, "app.hauptgang.web") do
        post "/auth/apple?allow_account_creation=true"
        post auth_apple_callback_path
      end
    end

    OmniAuth.config.mock_auth[:apple] = auth_hash(
      provider: "apple",
      uid: "replayed-intent-user",
      email: "replayed-intent@example.com",
      refresh_token: "replayed-refresh-token"
    )

    assert_no_difference [ "User.count", "Identity.count", "Session.count" ] do
      Oauth::Configuration.stub(:apple_services_id, "app.hauptgang.web") do
        Oauth::AppleClient.stub(:new, apple_client) do
          post auth_apple_callback_path
        end
      end
    end

    assert_redirected_to confirm_apple_account_creation_path
  end

  test "does not accept a nonliteral request-phase confirmation value" do
    OmniAuth.config.mock_auth[:apple] = auth_hash(
      provider: "apple",
      uid: "invalid-web-confirmation",
      email: "invalid-web-confirmation@example.com",
      refresh_token: "invalid-confirmation-token"
    )
    apple_client = FakeAppleClient.new

    assert_no_difference [ "User.count", "Identity.count", "Session.count" ] do
      Oauth::Configuration.stub(:apple_services_id, "app.hauptgang.web") do
        Oauth::AppleClient.stub(:new, apple_client) do
          post "/auth/apple?allow_account_creation=1"
          post auth_apple_callback_path
        end
      end
    end

    assert_redirected_to confirm_apple_account_creation_path
  end

  test "signs in a known Apple identity without account creation confirmation" do
    user = users(:one)
    Identity.create!(provider: "apple", uid: "known-web-apple", email: "old-apple@example.com", user:)
    OmniAuth.config.mock_auth[:apple] = auth_hash(
      provider: "apple",
      uid: "known-web-apple",
      email: "changed-apple@example.com",
      refresh_token: "known-apple-refresh-token"
    )

    assert_no_difference [ "User.count", "Identity.count" ] do
      assert_difference "Session.count", 1 do
        Oauth::Configuration.stub(:apple_services_id, "app.hauptgang.web") do
          post auth_apple_callback_path
        end
      end
    end

    assert_redirected_to root_url
    assert_equal "changed-apple@example.com", user.identities.apple.find_by!(uid: "known-web-apple").email
  end

  test "keeps unknown Google account creation unchanged" do
    OmniAuth.config.mock_auth[:google_oauth2] = auth_hash(
      provider: "google_oauth2",
      uid: "new-google-web-user",
      email: "new-google-web@example.com"
    )

    assert_difference [ "User.count", "Identity.count", "Session.count" ], 1 do
      get auth_google_oauth2_callback_path
    end

    assert_redirected_to root_url
  end

  test "confirmation page starts a fresh Apple request without retaining credentials" do
    assert_no_difference [ "User.count", "Identity.count", "Session.count" ] do
      Rails.application.config.x.oauth.stub(:apple_enabled, true) do
        get confirm_apple_account_creation_path
      end
    end

    assert_response :success
    assert_select "h1", "Create a separate account?"
    assert_select "a[href='#{new_session_path}']", "Use existing account"
    assert_select "form[action='/auth/apple?allow_account_creation=true'][method='post'][data-turbo='false']"
    assert_select "input[name='id_token']", count: 0
    assert_select "input[name='authorization_code']", count: 0
    assert_select "input[name='refresh_token']", count: 0
  end

  test "revokes an Apple token when a password account must be linked manually" do
    user = users(:one)
    OmniAuth.config.mock_auth[:apple] = auth_hash(
      provider: "apple",
      uid: "apple-link-user",
      email: user.email_address,
      refresh_token: "unused-web-refresh-token"
    )
    apple_client = FakeAppleClient.new

    Oauth::Configuration.stub(:apple_services_id, "app.hauptgang.web") do
      Oauth::AppleClient.stub(:new, apple_client) do
        post auth_apple_callback_path
      end
    end

    assert_redirected_to new_session_path
    assert_equal [
      { refresh_token: "unused-web-refresh-token", client_id: "app.hauptgang.web" }
    ], apple_client.revocations
  end

  test "rejects a provider identity without a verified email" do
    OmniAuth.config.mock_auth[:google_oauth2] = auth_hash(
      provider: "google_oauth2",
      uid: "unverified-web-user",
      email: "unverified@example.com",
      email_verified: false
    )

    assert_no_difference [ "User.count", "Identity.count", "Session.count" ] do
      get auth_google_oauth2_callback_path
    end

    assert_redirected_to new_session_path
    assert_match "couldn't sign you in", flash[:alert]
  end

  test "failure redirects to sign in with a generic error" do
    get auth_failure_path

    assert_redirected_to new_session_path
    assert_match "couldn't sign you in", flash[:alert]
  end

  test "Android Apple handoff authorizes a known identity from the captured request handle only" do
    user = users(:one)
    Identity.create!(provider: "apple", uid: "known-android-apple", email: "old-android@example.com", user:)
    transaction, handle = start_android_transaction
    _other_transaction, other_handle = start_android_transaction
    OmniAuth.config.mock_auth[:apple] = auth_hash(
      provider: "apple",
      uid: "known-android-apple",
      email: "changed-android@example.com",
      refresh_token: "android-refresh-token"
    )

    assert_no_difference [ "User.count", "Identity.count", "Session.count", "ApiToken.count" ] do
      Oauth::Configuration.stub(:apple_services_id, "app.hauptgang.web") do
        post "/auth/apple?android_transaction=#{handle}"
        post auth_apple_callback_path, params: {
          android_transaction: other_handle,
          transaction_id: other_handle,
          allow_account_creation: "true"
        }
      end
    end

    redirect = URI.parse(response.location)
    assert_equal "https://app.getmaincourse.com/android/auth/apple", "#{redirect.scheme}://#{redirect.host}#{redirect.path}"
    assert_equal [ "exchange_code", "transaction_id" ], Rack::Utils.parse_query(redirect.query).keys.sort
    assert_equal handle, Rack::Utils.parse_query(redirect.query)["transaction_id"]
    assert_equal user, transaction.reload.user
    assert_nil AppleAuthTransaction.find_by_handle(other_handle).user
    assert_equal "changed-android@example.com", user.identities.apple.find_by!(uid: "known-android-apple").email
  end

  test "an unknown captured Android handle revokes its unused Apple token exactly once" do
    OmniAuth.config.mock_auth[:apple] = auth_hash(
      provider: "apple",
      uid: "missing-android-transaction",
      email: "missing-android-transaction@example.com",
      name: "Missing Android Transaction",
      refresh_token: "missing-transaction-refresh-token"
    )
    apple_client = FakeAppleClient.new

    assert_no_difference [ "User.count", "Identity.count", "Session.count", "ApiToken.count" ] do
      Oauth::Configuration.stub(:apple_services_id, "app.hauptgang.web") do
        Oauth::AppleClient.stub(:new, apple_client) do
          post "/auth/apple?android_transaction=garbage-captured-handle"
          post auth_apple_callback_path
        end
      end
    end

    assert_response :bad_request
    assert_nil response.location
    assert_equal [
      { refresh_token: "missing-transaction-refresh-token", client_id: "app.hauptgang.web" }
    ], apple_client.revocations
  end

  test "local Android debug handoff redirects only to its stored custom scheme" do
    user = users(:one)
    Identity.create!(provider: "apple", uid: "debug-android-apple", email: "debug-android@example.com", user:)
    _transaction, handle = start_android_transaction(callback: "debug")
    OmniAuth.config.mock_auth[:apple] = auth_hash(
      provider: "apple",
      uid: "debug-android-apple",
      email: "debug-android@example.com",
      refresh_token: "debug-android-token"
    )

    Oauth::Configuration.stub(:apple_services_id, "app.hauptgang.web") do
      post "/auth/apple?android_transaction=#{handle}"
      post auth_apple_callback_path
    end

    redirect = URI.parse(response.location)
    assert_equal "com.getmaincourse.app.debug", redirect.scheme
    assert_equal "/oauth/apple", redirect.path
    assert_equal [ "exchange_code", "transaction_id" ], Rack::Utils.parse_query(redirect.query).keys.sort
  end

  test "a forged first Android creation flag marks confirmation and revokes the unused token" do
    transaction, handle = start_android_transaction
    OmniAuth.config.mock_auth[:apple] = auth_hash(
      provider: "apple",
      uid: "new-forged-android-apple",
      email: "new-forged-android@example.com",
      refresh_token: "first-unused-refresh-token"
    )
    apple_client = FakeAppleClient.new

    assert_no_difference [ "User.count", "Identity.count", "Session.count", "ApiToken.count" ] do
      Oauth::Configuration.stub(:apple_services_id, "app.hauptgang.web") do
        Oauth::AppleClient.stub(:new, apple_client) do
          post "/auth/apple?android_transaction=#{handle}&allow_account_creation=true"
          post auth_apple_callback_path
        end
      end
    end

    assert_redirected_to "/android/apple/confirm_account_creation?transaction_id=#{handle}"
    assert transaction.reload.confirmation_required?
    assert_equal [
      { refresh_token: "first-unused-refresh-token", client_id: "app.hauptgang.web" }
    ], apple_client.revocations
  end

  test "a repeated unknown Android callback re-renders confirmation instead of failing" do
    transaction, handle = start_android_transaction
    transaction.with_lock { transaction.mark_confirmation_required! }
    OmniAuth.config.mock_auth[:apple] = auth_hash(
      provider: "apple",
      uid: "repeat-unknown-android-apple",
      email: "repeat-unknown-android@example.com",
      refresh_token: "repeat-unused-refresh-token"
    )
    apple_client = FakeAppleClient.new

    assert_no_difference [ "User.count", "Identity.count", "Session.count", "ApiToken.count" ] do
      Oauth::Configuration.stub(:apple_services_id, "app.hauptgang.web") do
        Oauth::AppleClient.stub(:new, apple_client) do
          post "/auth/apple?android_transaction=#{handle}"
          post auth_apple_callback_path
        end
      end
    end

    assert_redirected_to "/android/apple/confirm_account_creation?transaction_id=#{handle}"
    assert transaction.reload.confirmation_required?
    assert_equal [
      { refresh_token: "repeat-unused-refresh-token", client_id: "app.hauptgang.web" }
    ], apple_client.revocations
  end

  test "confirmed Android creation requires a fresh captured request and creates no browser session" do
    transaction, handle = start_android_transaction
    transaction.with_lock { transaction.mark_confirmation_required! }
    OmniAuth.config.mock_auth[:apple] = auth_hash(
      provider: "apple",
      uid: "confirmed-android-apple",
      email: "confirmed-android@example.com",
      name: "Confirmed Android",
      refresh_token: "fresh-android-refresh-token"
    )

    assert_difference [ "User.count", "Identity.count" ], 1 do
      assert_no_difference [ "Session.count", "ApiToken.count" ] do
        Oauth::Configuration.stub(:apple_services_id, "app.hauptgang.web") do
          post "/auth/apple?android_transaction=#{handle}&allow_account_creation=true"
          post auth_apple_callback_path
        end
      end
    end

    query = Rack::Utils.parse_query(URI.parse(response.location).query)
    assert_equal [ "exchange_code", "transaction_id" ], query.keys.sort
    assert_equal handle, query["transaction_id"]
    assert_equal "confirmed-android-apple", transaction.reload.user.identities.apple.sole.uid
  end

  test "an expired Android callback is rejected before Identity side effects and revokes the token" do
    user = users(:one)
    identity = Identity.create!(provider: "apple", uid: "expired-android-apple", email: "old-expired@example.com", user:)
    transaction, handle = start_android_transaction
    transaction.update_column(:expires_at, 1.minute.ago)
    OmniAuth.config.mock_auth[:apple] = auth_hash(
      provider: "apple",
      uid: "expired-android-apple",
      email: "must-not-persist@example.com",
      refresh_token: "expired-unused-token"
    )
    apple_client = FakeAppleClient.new

    Oauth::Configuration.stub(:apple_services_id, "app.hauptgang.web") do
      Oauth::AppleClient.stub(:new, apple_client) do
        post "/auth/apple?android_transaction=#{handle}"
        post auth_apple_callback_path
      end
    end

    assert_redirected_to "https://app.getmaincourse.com/android/auth/apple?transaction_id=#{handle}&error=transaction_expired"
    assert_equal "old-expired@example.com", identity.reload.email
    assert_nil transaction.reload.authorized_at
    assert_equal [
      { refresh_token: "expired-unused-token", client_id: "app.hauptgang.web" }
    ], apple_client.revocations
  end

  test "a second Android callback cannot mutate Identity or mint another exchange code" do
    user = users(:one)
    identity = Identity.create!(provider: "apple", uid: "once-android-apple", email: "first@example.com", user:)
    transaction, handle = start_android_transaction
    OmniAuth.config.mock_auth[:apple] = auth_hash(
      provider: "apple", uid: "once-android-apple", email: "accepted@example.com", refresh_token: "accepted-token"
    )

    Oauth::Configuration.stub(:apple_services_id, "app.hauptgang.web") do
      post "/auth/apple?android_transaction=#{handle}"
      post auth_apple_callback_path
    end
    first_exchange_digest = transaction.reload.exchange_digest

    OmniAuth.config.mock_auth[:apple] = auth_hash(
      provider: "apple", uid: "once-android-apple", email: "rejected@example.com", refresh_token: "rejected-token"
    )
    apple_client = FakeAppleClient.new
    Oauth::Configuration.stub(:apple_services_id, "app.hauptgang.web") do
      Oauth::AppleClient.stub(:new, apple_client) do
        post "/auth/apple?android_transaction=#{handle}"
        post auth_apple_callback_path
      end
    end

    assert_redirected_to "https://app.getmaincourse.com/android/auth/apple?transaction_id=#{handle}&error=transaction_unavailable"
    assert_equal "accepted@example.com", identity.reload.email
    assert_equal first_exchange_digest, transaction.reload.exchange_digest
    assert_equal [ { refresh_token: "rejected-token", client_id: "app.hauptgang.web" } ], apple_client.revocations
  end

  test "a consumed Android transaction is rejected before Identity side effects" do
    user = users(:one)
    identity = Identity.create!(provider: "apple", uid: "consumed-android-apple", email: "consumed-old@example.com", user:)
    transaction, handle = start_android_transaction
    transaction.update_column(:consumed_at, Time.current)
    OmniAuth.config.mock_auth[:apple] = auth_hash(
      provider: "apple",
      uid: "consumed-android-apple",
      email: "must-not-update-consumed@example.com",
      refresh_token: "consumed-unused-token"
    )
    apple_client = FakeAppleClient.new

    Oauth::Configuration.stub(:apple_services_id, "app.hauptgang.web") do
      Oauth::AppleClient.stub(:new, apple_client) do
        post "/auth/apple?android_transaction=#{handle}"
        post auth_apple_callback_path
      end
    end

    assert_redirected_to "https://app.getmaincourse.com/android/auth/apple?transaction_id=#{handle}&error=transaction_unavailable"
    assert_equal "consumed-old@example.com", identity.reload.email
    assert_nil transaction.reload.authorized_at
    assert_equal [ { refresh_token: "consumed-unused-token", client_id: "app.hauptgang.web" } ], apple_client.revocations
  end

  test "an Android OmniAuth failure uses the captured handle and a fixed error" do
    transaction, handle = start_android_transaction
    _other_transaction, other_handle = start_android_transaction
    OmniAuth.config.mock_auth[:apple] = :invalid_credentials

    assert_no_difference [ "User.count", "Identity.count", "Session.count", "ApiToken.count" ] do
      post "/auth/apple?android_transaction=#{handle}"
      post auth_apple_callback_path, params: { android_transaction: other_handle, error: "provider-secret" }
    end

    assert_redirected_to "https://app.getmaincourse.com/android/auth/apple?transaction_id=#{handle}&error=authentication_failed"
    assert_equal "no-store", response.headers["Cache-Control"]
    assert_equal "no-referrer", response.headers["Referrer-Policy"]
    assert_nil transaction.reload.authorized_at
    assert_nil transaction.user
  end

  test "an Android provider cancellation returns only the fixed cancelled error" do
    _transaction, handle = start_android_transaction
    OmniAuth.config.mock_auth[:apple] = :access_denied

    post "/auth/apple?android_transaction=#{handle}"
    post auth_apple_callback_path, params: { error: "attacker-error" }

    assert_redirected_to "https://app.getmaincourse.com/android/auth/apple?transaction_id=#{handle}&error=cancelled"
  end

  test "Apple's user cancelled authorize failure returns the fixed Android cancelled error" do
    _transaction, handle = start_android_transaction
    OmniAuth.config.mock_auth[:apple] = :user_cancelled_authorize

    post "/auth/apple?android_transaction=#{handle}"
    post auth_apple_callback_path, params: { error: "provider-error-must-not-echo" }

    assert_redirected_to "https://app.getmaincourse.com/android/auth/apple?transaction_id=#{handle}&error=cancelled"
    assert_not_includes response.location, "provider-error-must-not-echo"
  end

  test "an Android failure without a known captured handle has no app redirect fallback" do
    OmniAuth.config.mock_auth[:apple] = :invalid_credentials

    post "/auth/apple?android_transaction=missing-handle"
    post auth_apple_callback_path, params: { transaction_id: "callback-handle" }

    assert_redirected_to "/auth/failure?message=invalid_credentials&strategy=apple"
  end

  test "a Google request CSRF failure cannot turn a pending Apple web callback into an Android handoff" do
    user = users(:one)
    identity = Identity.create!(provider: "apple", uid: "apple-web-after-google-csrf", email: "old@example.com", user:)
    transaction, handle = start_android_transaction
    OmniAuth.config.mock_auth[:apple] = auth_hash(
      provider: "apple",
      uid: "apple-web-after-google-csrf",
      email: "updated@example.com",
      refresh_token: "web-after-google-csrf-token"
    )
    previous_forgery_protection = ActionController::Base.allow_forgery_protection

    assert_no_difference [ "User.count", "Identity.count", "ApiToken.count" ] do
      assert_difference "Session.count", 1 do
        ActionController::Base.allow_forgery_protection = true
        OmniAuth.config.test_mode = false
        https!
        Rails.application.config.x.oauth.stub(:apple_enabled, true) { get new_session_path }
        authenticity_token = css_select("form[action='/auth/apple'] input[name='authenticity_token']").sole["value"]

        post "/auth/apple",
          params: { authenticity_token: },
          headers: { "HTTP_ORIGIN" => "https://www.example.com" }
        assert_equal "appleid.apple.com", URI.parse(response.location).host
        apple_state = request.session["omniauth.state"]
        apple_nonce = request.session["omniauth.nonce"]

        post "/auth/google_oauth2?android_transaction=#{handle}",
          headers: { "HTTP_ORIGIN" => "https://attacker.example" }
        google_failure_location = response.location
        params_after_google_failure = request.session["omniauth.params"]
        state_after_google_failure = request.session["omniauth.state"]
        nonce_after_google_failure = request.session["omniauth.nonce"]

        OmniAuth.config.test_mode = true
        Oauth::Configuration.stub(:apple_services_id, "app.hauptgang.web") do
          post auth_apple_callback_path, params: { android_transaction: handle }
        end

        assert_redirected_to root_url
        assert_equal "/auth/failure?message=ActionController%3A%3AInvalidAuthenticityToken&strategy=google_oauth2",
          google_failure_location
        assert_nil params_after_google_failure
        assert_equal apple_state, state_after_google_failure
        assert_equal apple_nonce, nonce_after_google_failure
      end
    end

    assert_equal "updated@example.com", identity.reload.email
    assert_nil transaction.reload.user
    assert_nil transaction.exchange_digest
  ensure
    ActionController::Base.allow_forgery_protection = previous_forgery_protection
  end

  test "Android confirmation page retains only the handle in fresh POST actions" do
    transaction, handle = start_android_transaction
    transaction.with_lock { transaction.mark_confirmation_required! }

    https!
    get "/android/apple/confirm_account_creation", params: {
      transaction_id: handle,
      id_token: "must-not-echo",
      authorization_code: "must-not-echo"
    }

    assert_response :success
    assert_equal "strict-origin", response.headers["Referrer-Policy"]
    assert_select "h1", "Create a new MainCourse account?"
    assert_select "form[action='/auth/apple?android_transaction=#{handle}&allow_account_creation=true'][method='post'][data-turbo='false']"
    assert_select "form[action='/android/apple/cancel'][method='post'][data-turbo='false'] input[name='transaction_id'][value='#{handle}']"
    assert_not_includes response.body, "must-not-echo"
  end

  private
    class FakeAppleClient
      attr_reader :revocations

      def initialize
        @revocations = []
      end

      def revoke!(refresh_token:, client_id:)
        revocations << { refresh_token:, client_id: }
      end
    end

    def auth_hash(provider:, uid:, email:, name: nil, email_verified: true, refresh_token: nil)
      OmniAuth::AuthHash.new(
        provider:,
        uid:,
        info: { email:, name:, email_verified: },
        credentials: { token: "provider-token", refresh_token: }
      )
    end

    def restore_mock(provider, value)
      if value
        OmniAuth.config.mock_auth[provider] = value
      else
        OmniAuth.config.mock_auth.delete(provider)
      end
    end

    def start_android_transaction(callback: "release")
      AppleAuthTransaction.start!(code_challenge: "A" * 43, callback:)
    end
end
