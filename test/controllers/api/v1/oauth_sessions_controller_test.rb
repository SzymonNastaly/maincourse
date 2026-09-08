require "test_helper"

class Api::V1::OauthSessionsControllerTest < ActionDispatch::IntegrationTest
  setup do
    @key = OpenSSL::PKey::RSA.generate(2048)
    @jwk = JWT::JWK.new(@key.public_key, kid: "oauth-key")
    Rails.cache.delete("oauth/google/jwks")
    Rails.cache.delete("oauth/apple/jwks")
  end

  test "signs in with Google and returns an API token" do
    stub_jwks("https://www.googleapis.com/oauth2/v3/certs")

    assert_difference [ "User.count", "Identity.count", "ApiToken.count" ], 1 do
      Oauth::Configuration.stub(:google_client_ids, [ "google-client-id" ]) do
        post api_v1_oauth_session_url, params: {
          provider: "google",
          id_token: identity_token,
          nonce: "google-nonce",
          device_name: "Test iPhone"
        }, as: :json
      end
    end

    assert_response :created
    assert response.parsed_body["token"].present?
    assert_equal "oauth@example.com", response.parsed_body.dig("user", "email")
    assert_equal "Test iPhone", ApiToken.order(:created_at).last.name
  end

  test "requires password sign-in before linking an existing password account" do
    user = users(:one)
    stub_jwks("https://www.googleapis.com/oauth2/v3/certs")

    assert_no_difference "User.count" do
      assert_no_difference "Identity.count" do
        Oauth::Configuration.stub(:google_client_ids, [ "google-client-id" ]) do
          post api_v1_oauth_session_url, params: {
            provider: "google",
            id_token: identity_token("email" => user.email_address),
            nonce: "google-nonce"
          }, as: :json
        end
      end
    end

    assert_response :conflict
    assert_equal "account_link_required", response.parsed_body["error_code"]
    assert user.reload.authenticate("password")
  end

  test "signs in with Apple, exchanges the code, and stores its refresh token" do
    stub_jwks("https://appleid.apple.com/auth/keys")
    nonce = "apple-raw-nonce"
    token = identity_token(
      "iss" => "https://appleid.apple.com",
      "aud" => "app.hauptgang.ios",
      "nonce" => Digest::SHA256.hexdigest(nonce),
      "email_verified" => "true"
    )
    exchanged_token = identity_token(
      "iss" => "https://appleid.apple.com",
      "aud" => "app.hauptgang.ios",
      "nonce" => nil,
      "email_verified" => "true"
    )
    apple_client = FakeAppleClient.new({
      "id_token" => exchanged_token,
      "refresh_token" => "apple-refresh-token"
    })

    assert_difference [ "User.count", "Identity.count", "ApiToken.count" ], 1 do
      Oauth::Configuration.stub(:apple_bundle_id, "app.hauptgang.ios") do
        Oauth::AppleClient.stub(:new, apple_client) do
          post api_v1_oauth_session_url, params: {
            provider: "apple",
            id_token: token,
            authorization_code: "apple-code",
            nonce:,
            name: "Apple Person",
            allow_account_creation: true
          }, as: :json
        end
      end
    end

    assert_response :created
    identity = Identity.find_by!(provider: "apple", uid: "oauth-user")
    assert_equal "apple-refresh-token", identity.apple_refresh_token_for("app.hauptgang.ios")
    assert_equal({ code: "apple-code", client_id: "app.hauptgang.ios" }, apple_client.exchange_arguments)
  end

  test "requires confirmation before creating an Apple account for an updated client" do
    params, apple_client = apple_request(email: "unconfirmed@example.com", nonce: "unconfirmed-nonce")
    params[:allow_account_creation] = false

    assert_no_difference [ "User.count", "Identity.count", "ApiToken.count" ] do
      post_apple_oauth_session(params, apple_client)
    end

    assert_response :conflict
    assert_equal "apple_account_creation_confirmation_required", response.parsed_body["error_code"]
    assert_match "isn't linked", response.parsed_body["error"]
    assert_equal [
      { refresh_token: "apple-refresh-token", client_id: "app.hauptgang.ios" }
    ], apple_client.revocations
  end

  test "returns an actionable legacy error when Apple creation intent is omitted" do
    params, apple_client = apple_request(email: "legacy@example.com", nonce: "legacy-nonce")

    assert_no_difference [ "User.count", "Identity.count", "ApiToken.count" ] do
      post_apple_oauth_session(params, apple_client)
    end

    assert_response :unprocessable_entity
    assert_equal(
      "Update the app to create a new Apple account, or use the prior sign-in method to access an existing account.",
      response.parsed_body["error"]
    )
    assert_nil response.parsed_body["error_code"]
  end

  test "accepts only literal JSON true as Apple account creation confirmation" do
    [ "true", nil ].each_with_index do |allow_account_creation, index|
      params, apple_client = apple_request(
        uid: "strict-apple-user-#{index}",
        email: "strict-#{index}@example.com",
        nonce: "strict-nonce-#{index}"
      )
      params[:allow_account_creation] = allow_account_creation

      assert_no_difference [ "User.count", "Identity.count", "ApiToken.count" ] do
        post_apple_oauth_session(params, apple_client)
      end

      assert_response :conflict
      assert_equal "apple_account_creation_confirmation_required", response.parsed_body["error_code"]
    end
  end

  test "Apple confirmation does not bypass nonce verification" do
    params, apple_client = apple_request(email: "wrong-nonce@example.com", nonce: "expected-nonce")
    params[:nonce] = "different-nonce"
    params[:allow_account_creation] = true

    assert_no_difference [ "User.count", "Identity.count", "ApiToken.count" ] do
      post_apple_oauth_session(params, apple_client)
    end

    assert_response :unauthorized
    assert_nil apple_client.exchange_arguments
  end

  test "Apple confirmation does not bypass signature verification" do
    params, apple_client = apple_request(
      email: "invalid-signature@example.com",
      nonce: "signed-nonce",
      client_signing_key: OpenSSL::PKey::RSA.generate(2048)
    )
    params[:allow_account_creation] = true

    assert_no_difference [ "User.count", "Identity.count", "ApiToken.count" ] do
      post_apple_oauth_session(params, apple_client)
    end

    assert_response :unauthorized
    assert_nil apple_client.exchange_arguments
  end

  test "revokes an unused Apple token when a password account must be linked manually" do
    user = users(:one)
    stub_jwks("https://appleid.apple.com/auth/keys")
    nonce = "apple-link-nonce"
    token = identity_token(
      "iss" => "https://appleid.apple.com",
      "aud" => "app.hauptgang.ios",
      "nonce" => Digest::SHA256.hexdigest(nonce),
      "email" => user.email_address,
      "email_verified" => "true"
    )
    exchanged_token = identity_token(
      "iss" => "https://appleid.apple.com",
      "aud" => "app.hauptgang.ios",
      "nonce" => nil,
      "email" => user.email_address,
      "email_verified" => "true"
    )
    apple_client = FakeAppleClient.new({
      "id_token" => exchanged_token,
      "refresh_token" => "unused-refresh-token"
    })

    Oauth::Configuration.stub(:apple_bundle_id, "app.hauptgang.ios") do
      Oauth::AppleClient.stub(:new, apple_client) do
        post api_v1_oauth_session_url, params: {
          provider: "apple",
          id_token: token,
          authorization_code: "apple-code",
          nonce:
        }, as: :json
      end
    end

    assert_response :conflict
    assert_equal [
      { refresh_token: "unused-refresh-token", client_id: "app.hauptgang.ios" }
    ], apple_client.revocations
  end

  test "rejects an unsupported provider" do
    assert_no_difference [ "User.count", "ApiToken.count" ] do
      post api_v1_oauth_session_url, params: { provider: "unknown" }, as: :json
    end

    assert_response :unauthorized
    assert_equal "Could not authenticate with that provider", response.parsed_body["error"]
    assert_equal "oauth_failed", response.parsed_body["error_code"]
  end

  test "reports provider outages as temporarily unavailable" do
    error = Oauth::UnavailableError.new("Provider signing keys are unavailable")

    Rails.error.stub(:report, nil) do
      Oauth::Configuration.stub(:google_client_ids, -> { raise error }) do
        post api_v1_oauth_session_url, params: {
          provider: "google",
          id_token: "token",
          nonce: "nonce"
        }, as: :json
      end
    end

    assert_response :service_unavailable
    assert_equal "oauth_unavailable", response.parsed_body["error_code"]
  end

  private
    FakeAppleClient = Struct.new(:response, :exchange_arguments, :revocations) do
      def exchange_code!(code:, client_id:)
        self.exchange_arguments = { code:, client_id: }
        response
      end

      def revoke!(refresh_token:, client_id:)
        self.revocations ||= []
        revocations << { refresh_token:, client_id: }
      end
    end

    def identity_token(overrides = {}, signing_key = @key)
      payload = {
        "iss" => "https://accounts.google.com",
        "aud" => "google-client-id",
        "sub" => "oauth-user",
        "email" => "oauth@example.com",
        "email_verified" => true,
        "name" => "OAuth Person",
        "nonce" => "google-nonce",
        "iat" => Time.current.to_i,
        "exp" => 5.minutes.from_now.to_i
      }.merge(overrides)

      JWT.encode(payload, signing_key, "RS256", kid: @jwk.kid)
    end

    def apple_request(uid: "oauth-user", email:, nonce:, client_signing_key: @key)
      stub_jwks("https://appleid.apple.com/auth/keys")
      client_token = identity_token({
        "iss" => "https://appleid.apple.com",
        "aud" => "app.hauptgang.ios",
        "sub" => uid,
        "email" => email,
        "nonce" => Digest::SHA256.hexdigest(nonce),
        "email_verified" => "true"
      }, client_signing_key)
      server_token = identity_token(
        "iss" => "https://appleid.apple.com",
        "aud" => "app.hauptgang.ios",
        "sub" => uid,
        "email" => email,
        "nonce" => nil,
        "email_verified" => "true"
      )
      apple_client = FakeAppleClient.new({
        "id_token" => server_token,
        "refresh_token" => "apple-refresh-token"
      })
      params = {
        provider: "apple",
        id_token: client_token,
        authorization_code: "apple-code",
        nonce:
      }

      [ params, apple_client ]
    end

    def post_apple_oauth_session(params, apple_client)
      Oauth::Configuration.stub(:apple_bundle_id, "app.hauptgang.ios") do
        Oauth::AppleClient.stub(:new, apple_client) do
          post api_v1_oauth_session_url, params:, as: :json
        end
      end
    end

    def stub_jwks(url)
      stub_request(:get, url).to_return(
        status: 200,
        body: { keys: [ @jwk.export ] }.to_json,
        headers: { "Content-Type" => "application/json" }
      )
    end
end
