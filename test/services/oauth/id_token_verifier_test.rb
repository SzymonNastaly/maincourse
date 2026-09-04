require "test_helper"

class Oauth::IdTokenVerifierTest < ActiveSupport::TestCase
  setup do
    @key = OpenSSL::PKey::RSA.generate(2048)
    @jwk = JWT::JWK.new(@key.public_key, kid: "provider-key")
    Rails.cache.delete("oauth/google/jwks")
    Rails.cache.delete("oauth/apple/jwks")
  end

  test "verifies a Google identity token" do
    stub_jwks("https://www.googleapis.com/oauth2/v3/certs")

    result = Oauth::IdTokenVerifier.verify!(
      provider: "google",
      token: identity_token,
      audiences: [ "google-client-id" ],
      nonce: "raw-nonce"
    )

    assert_equal "provider-user-id", result[:uid]
    assert_equal "person@example.com", result[:email]
    assert result[:email_verified]
    assert_equal "Test Person", result[:name]
  end

  test "accepts Apple's string email verification claim" do
    stub_jwks("https://appleid.apple.com/auth/keys")
    token = identity_token(
      "iss" => "https://appleid.apple.com",
      "aud" => "app.hauptgang.ios",
      "email_verified" => "true",
      "nonce" => "hashed-nonce"
    )

    result = Oauth::IdTokenVerifier.verify!(
      provider: "apple",
      token:,
      audiences: [ "app.hauptgang.ios" ],
      nonce: "hashed-nonce"
    )

    assert_equal "provider-user-id", result[:uid]
  end

  test "rejects a token for another audience" do
    stub_jwks("https://www.googleapis.com/oauth2/v3/certs")

    assert_raises Oauth::Error do
      Oauth::IdTokenVerifier.verify!(
        provider: "google",
        token: identity_token,
        audiences: [ "another-client-id" ],
        nonce: "raw-nonce"
      )
    end
  end

  test "rejects a mismatched nonce" do
    stub_jwks("https://www.googleapis.com/oauth2/v3/certs")

    assert_raises Oauth::Error do
      Oauth::IdTokenVerifier.verify!(
        provider: "google",
        token: identity_token,
        audiences: [ "google-client-id" ],
        nonce: "another-nonce"
      )
    end
  end

  test "rejects an expired token" do
    request = stub_jwks("https://www.googleapis.com/oauth2/v3/certs")

    assert_raises Oauth::Error do
      Oauth::IdTokenVerifier.verify!(
        provider: "google",
        token: identity_token("exp" => 5.minutes.ago.to_i),
        audiences: [ "google-client-id" ],
        nonce: "raw-nonce"
      )
    end

    assert_requested request, times: 1
  end

  private
    def identity_token(overrides = {})
      payload = {
        "iss" => "https://accounts.google.com",
        "aud" => "google-client-id",
        "sub" => "provider-user-id",
        "email" => "person@example.com",
        "email_verified" => true,
        "name" => "Test Person",
        "nonce" => "raw-nonce",
        "iat" => Time.current.to_i,
        "exp" => 5.minutes.from_now.to_i
      }.merge(overrides)

      JWT.encode(payload, @key, "RS256", kid: @jwk.kid)
    end

    def stub_jwks(url)
      stub_request(:get, url).to_return(
        status: 200,
        body: { keys: [ @jwk.export ] }.to_json,
        headers: { "Content-Type" => "application/json" }
      )
    end
end
