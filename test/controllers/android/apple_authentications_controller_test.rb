require "test_helper"

module Android
  class AppleAuthenticationsControllerTest < ActionDispatch::IntegrationTest
  test "sign in landing is inert, private, and contains no credentials" do
    transaction, handle = start_transaction

    https!
    assert_no_difference [ "User.count", "Identity.count", "Session.count", "ApiToken.count" ] do
      get "/android/apple/sign_in", params: { transaction_id: handle }
    end

    assert_response :success
    assert_equal "no-store", response.headers["Cache-Control"]
    assert_equal "strict-origin", response.headers["Referrer-Policy"]
    assert_select "h1", "Continue with Apple"
    assert_select "form[action='/auth/apple?android_transaction=#{handle}'][method='post'][data-turbo='false']"
    assert_select "form[action='/android/apple/cancel'][method='post'][data-turbo='false'] input[name='transaction_id'][value='#{handle}']"
    assert_select "input[name='id_token']", count: 0
    assert_select "input[name='authorization_code']", count: 0
    assert_select "input[name='exchange_code']", count: 0
    assert_nil transaction.reload.user
  end

  test "an HTTP landing explains that real Apple sign in needs HTTPS" do
    _transaction, handle = start_transaction

    get "/android/apple/sign_in", params: { transaction_id: handle }

    assert_response :success
    assert_equal "strict-origin", response.headers["Referrer-Policy"]
    assert_select "h1", "HTTPS setup required"
    assert_select "form[action^='/auth/apple']", count: 0
    assert_select "form[action='/android/apple/cancel'][method='post'][data-turbo='false'] input[name='transaction_id'][value='#{handle}']"
    assert_select "p", text: /registered HTTPS MainCourse address/
  end

  test "an unknown handle has no redirect fallback" do
    get "/android/apple/sign_in", params: { transaction_id: "unknown-handle" }

    assert_response :bad_request
    assert_nil response.location
    assert_select "h1", "Apple sign-in link unavailable"
  end

  test "a known expired handle returns only a fixed error to its stored URI" do
    transaction, handle = start_transaction
    transaction.update_column(:expires_at, 1.minute.ago)

    https!
    get "/android/apple/sign_in", params: { transaction_id: handle, return_uri: "https://attacker.example" }

    assert_redirected_to "https://app.getmaincourse.com/android/auth/apple?transaction_id=#{handle}&error=transaction_expired"
  end

  test "cancel returns a fixed error to the allowlisted URI" do
    _transaction, handle = start_transaction

    post "/android/apple/cancel", params: { transaction_id: handle, error: "attacker-value" }

    assert_redirected_to "https://app.getmaincourse.com/android/auth/apple?transaction_id=#{handle}&error=cancelled"
    assert_equal "no-store", response.headers["Cache-Control"]
    assert_equal "no-referrer", response.headers["Referrer-Policy"]
  end

  test "browser cancel accepts its valid CSRF token and own origin" do
    _transaction, handle = start_transaction

    with_forgery_protection do
      get "/android/apple/sign_in", params: { transaction_id: handle }
      token = css_select("form[action='/android/apple/cancel'] input[name='authenticity_token']").sole["value"]

      post "/android/apple/cancel",
        params: { transaction_id: handle, authenticity_token: token },
        headers: { "HTTP_ORIGIN" => "http://www.example.com" }

      assert_redirected_to "https://app.getmaincourse.com/android/auth/apple?transaction_id=#{handle}&error=cancelled"
    end
  end

  test "browser cancel rejects a null origin even with a valid CSRF token" do
    _transaction, handle = start_transaction

    with_forgery_protection do
      get "/android/apple/sign_in", params: { transaction_id: handle }
      token = css_select("form[action='/android/apple/cancel'] input[name='authenticity_token']").sole["value"]

      post "/android/apple/cancel",
        params: { transaction_id: handle, authenticity_token: token },
        headers: { "HTTP_ORIGIN" => "null" }

      assert_response :unprocessable_content
    end
  end

  test "HTTPS Apple provider form accepts its token and own origin" do
    _transaction, handle = start_transaction

    with_forgery_protection_and_real_omniauth do
      https!
      get "/android/apple/sign_in", params: { transaction_id: handle }
      action = "/auth/apple?android_transaction=#{handle}"
      token = css_select("form[action='#{action}'] input[name='authenticity_token']").sole["value"]

      post action,
        params: { authenticity_token: token },
        headers: { "HTTP_ORIGIN" => "https://www.example.com" }

      assert_response :redirect
      assert_equal "appleid.apple.com", URI.parse(response.location).host
      assert_equal "/auth/authorize", URI.parse(response.location).path
      assert_equal "no-store", response.headers["Cache-Control"]
      assert_equal "no-referrer", response.headers["Referrer-Policy"]
    end
  end

  test "invalid Apple request origins and tokens return a fixed app failure" do
    [
      [ "null", :valid ],
      [ "https://attacker.example", :valid ],
      [ "https://www.example.com", :missing ]
    ].each do |origin, token_kind|
      reset!
      _transaction, handle = start_transaction

      with_forgery_protection_and_real_omniauth do
        https!
        get "/android/apple/sign_in", params: { transaction_id: handle }
        token = css_select("form[action^='/auth/apple'] input[name='authenticity_token']").sole["value"]

        post "/auth/apple?android_transaction=#{handle}",
          params: { authenticity_token: token_kind == :valid ? token : nil },
          headers: { "HTTP_ORIGIN" => origin }

        assert_redirected_to "https://app.getmaincourse.com/android/auth/apple?transaction_id=#{handle}&error=authentication_failed"
        assert_equal "no-store", response.headers["Cache-Control"]
        assert_equal "no-referrer", response.headers["Referrer-Policy"]

        post "/auth/apple/callback", params: { error: "access_denied" }
        assert_equal "/auth/failure", URI.parse(response.location).path
      end
    end
  end

  test "invalid Apple request without a handle keeps the generic failure route" do
    with_forgery_protection_and_real_omniauth do
      https!
      post "/auth/apple", headers: { "HTTP_ORIGIN" => "https://www.example.com" }

      assert_redirected_to "/auth/failure?message=authentication_failed&strategy=apple"
      refute_match %r{\A(?:com\.getmaincourse|https://app\.getmaincourse\.com/android/auth/apple)}, response.location
      assert_equal "no-store", response.headers["Cache-Control"]
      assert_equal "no-referrer", response.headers["Referrer-Policy"]
    end
  end

  test "Apple callback parameters cannot supply a missing request-phase handle" do
    _transaction, handle = start_transaction

    with_forgery_protection_and_real_omniauth do
      https!
      post "/auth/apple/callback", params: {
        android_transaction: handle,
        error: "access_denied"
      }

      assert_response :redirect
      assert_equal "/auth/failure", URI.parse(response.location).path
      refute_match %r{\A(?:com\.getmaincourse|https://app\.getmaincourse\.com/android/auth/apple)}, response.location
      assert_equal "no-store", response.headers["Cache-Control"]
      assert_equal "no-referrer", response.headers["Referrer-Policy"]
    end
  end

  test "browser cancel still rejects a missing CSRF token" do
    _transaction, handle = start_transaction

    with_forgery_protection do
      post "/android/apple/cancel",
        params: { transaction_id: handle },
        headers: { "HTTP_ORIGIN" => "null" }

      assert_response :unprocessable_content
    end
  end

  test "cancel with an unknown handle renders locally without a redirect" do
    post "/android/apple/cancel", params: { transaction_id: "unknown" }

    assert_response :bad_request
    assert_nil response.location
  end

  test "release App Link fallback is static and does not echo its query" do
    get "/android/auth/apple", params: {
      transaction_id: "secret-transaction",
      exchange_code: "secret-code",
      error: "provider-payload"
    }

    assert_response :success
    assert_equal "no-store", response.headers["Cache-Control"]
    assert_equal "no-referrer", response.headers["Referrer-Policy"]
    assert_select "h1", "Return to MainCourse"
    assert_not_includes response.body, "secret-transaction"
    assert_not_includes response.body, "secret-code"
    assert_not_includes response.body, "provider-payload"
  end

    private
      def start_transaction
        AppleAuthTransaction.start!(code_challenge: "A" * 43, callback: "release")
      end

      def with_forgery_protection
        previous = ActionController::Base.allow_forgery_protection
        ActionController::Base.allow_forgery_protection = true
        yield
      ensure
        ActionController::Base.allow_forgery_protection = previous
      end

      def with_forgery_protection_and_real_omniauth
        previous_test_mode = OmniAuth.config.test_mode
        OmniAuth.config.test_mode = false
        with_forgery_protection { yield }
      ensure
        OmniAuth.config.test_mode = previous_test_mode
      end
  end
end
