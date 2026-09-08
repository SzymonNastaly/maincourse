require "test_helper"
require "rack/test"

module OmniAuth
  module Strategies
    class MainCourseAppleTest < ActiveSupport::TestCase
      CLIENT_ID = "test-apple-services-id"

      setup do
        @provider_key = OpenSSL::PKey::RSA.generate(2048)
        @provider_jwk = JWT::JWK.new(@provider_key.public_key, kid: "apple-test-key")
        @client_key = OpenSSL::PKey::EC.generate("prime256v1")
        @auths = []
        @previous_test_mode = OmniAuth.config.test_mode
        @previous_request_validation_phase = OmniAuth.config.request_validation_phase
        @previous_on_failure = OmniAuth.config.on_failure
        OmniAuth.config.test_mode = false
        OmniAuth.config.request_validation_phase = ->(_env) { }
        OmniAuth.config.on_failure = method(:failure_response)
      end

      teardown do
        OmniAuth.config.test_mode = @previous_test_mode
        OmniAuth.config.request_validation_phase = @previous_request_validation_phase
        OmniAuth.config.on_failure = @previous_on_failure
      end

      test "Android callback requires a valid nonce when nonce_supported is absent or false" do
        [ nil, false ].each do |nonce_supported|
          browser, authorization = start_authorization(android: true)
          assert_equal "no-store", browser.last_response.headers["Cache-Control"]
          assert_equal "no-referrer", browser.last_response.headers["Referrer-Policy"]
          claims = valid_claims("nonce" => authorization.fetch("nonce"))
          claims["nonce_supported"] = nonce_supported unless nonce_supported.nil?

          callback(browser, authorization:, claims:)

          assert_predicate browser.last_response, :ok?, "nonce_supported=#{nonce_supported.inspect}"
          assert_equal "apple-user", @auths.last.uid
          assert_oauth_correlation_consumed(browser)
        end
      end

      test "Android callback rejects a missing nonce and consumes state and nonce" do
        browser, authorization = start_authorization(android: true)

        callback(browser, authorization:, claims: valid_claims.except("nonce"))

        assert_equal 401, browser.last_response.status
        assert_empty @auths
        assert_oauth_correlation_consumed(browser)
      end

      test "Android callback rejects invalid state before reaching the application and consumes correlation" do
        browser, authorization = start_authorization(android: true)

        callback(browser, authorization: authorization.merge("state" => "wrong-state"), claims: valid_claims)

        assert_equal 401, browser.last_response.status
        assert_empty @auths
        assert_oauth_correlation_consumed(browser)
        assert_not_requested :post, "https://appleid.apple.com/auth/token"
      end

      test "Android callback retains Apple signature issuer audience and time checks" do
        invalid_fixtures = [
          [ valid_claims, OpenSSL::PKey::RSA.generate(2048) ],
          [ valid_claims("iss" => "https://attacker.example"), @provider_key ],
          [ valid_claims("aud" => "attacker-client"), @provider_key ],
          [ valid_claims("iat" => 5.minutes.from_now.to_i), @provider_key ],
          [ valid_claims("exp" => 5.minutes.ago.to_i), @provider_key ]
        ]

        invalid_fixtures.each do |claims, signing_key|
          browser, authorization = start_authorization(android: true)
          callback(
            browser,
            authorization:,
            claims: claims.merge("nonce" => authorization.fetch("nonce")),
            signing_key:
          )

          assert_equal 401, browser.last_response.status
          assert_oauth_correlation_consumed(browser)
        end
        assert_empty @auths
      end

      test "ordinary non Android Apple callback keeps upstream optional nonce behavior" do
        browser, authorization = start_authorization(android: false)

        callback(browser, authorization:, claims: valid_claims.except("nonce", "nonce_supported"))

        assert_predicate browser.last_response, :ok?
        assert_equal "apple-user", @auths.sole.uid
      end

      private
        def start_authorization(android:)
          browser = Rack::Test::Session.new(Rack::MockSession.new(strategy_app))
          query = android ? "?android_transaction=captured-android-handle" : ""
          browser.post("/auth/apple#{query}")
          assert_equal 302, browser.last_response.status

          [ browser, Rack::Utils.parse_query(URI.parse(browser.last_response.location).query) ]
        end

        def callback(browser, authorization:, claims:, signing_key: @provider_key)
          stub_provider
          token = JWT.encode(claims, signing_key, "RS256", kid: @provider_jwk.kid)
          stub_request(:post, "https://appleid.apple.com/auth/token").to_return(
            status: 200,
            body: {
              access_token: "synthetic-access-token",
              token_type: "Bearer",
              expires_in: 3600,
              refresh_token: "synthetic-refresh-token",
              id_token: token
            }.to_json,
            headers: { "Content-Type" => "application/json" }
          )
          browser.post("/auth/apple/callback", {
            state: authorization.fetch("state"),
            code: "synthetic-authorization-code",
            id_token: token
          })
        end

        def strategy_app
          terminal = lambda do |env|
            @auths << env["omniauth.auth"] if env["omniauth.auth"]
            session = env.fetch("rack.session")
            body = {
              state: session["omniauth.state"],
              nonce: session["omniauth.nonce"]
            }.to_json
            [ 200, { "Content-Type" => "application/json" }, [ body ] ]
          end
          strategy = MainCourseApple.new(
            terminal,
            CLIENT_ID,
            "",
            team_id: "TESTTEAMID",
            key_id: "TESTKEYID",
            pem: @client_key.to_pem,
            authorized_client_ids: [ CLIENT_ID ]
          )
          Rack::Session::Cookie.new(
            strategy,
            key: "main_course_apple_strategy_test",
            secret: "a" * 64
          )
        end

        def valid_claims(overrides = {})
          {
            "iss" => "https://appleid.apple.com",
            "aud" => CLIENT_ID,
            "sub" => "apple-user",
            "email" => "apple-user@example.com",
            "email_verified" => "true",
            "nonce" => "set-from-authorization",
            "iat" => Time.current.to_i,
            "exp" => 5.minutes.from_now.to_i
          }.merge(overrides)
        end

        def stub_provider
          stub_request(:get, "https://appleid.apple.com/auth/keys").to_return(
            status: 200,
            body: { keys: [ @provider_jwk.export ] }.to_json,
            headers: { "Content-Type" => "application/json" }
          )
        end

        def failure_response(env)
          session = env.fetch("rack.session")
          body = {
            error: env["omniauth.error.type"],
            state: session["omniauth.state"],
            nonce: session["omniauth.nonce"]
          }.to_json
          [ 401, { "Content-Type" => "application/json" }, [ body ] ]
        end

        def assert_oauth_correlation_consumed(browser)
          browser.get("/inspect")
          correlation = JSON.parse(browser.last_response.body)
          assert_nil correlation.fetch("state")
          assert_nil correlation.fetch("nonce")
        end
    end
  end
end
