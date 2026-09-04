require "test_helper"

class Oauth::AppleClientTest < ActiveSupport::TestCase
  setup do
    @key = OpenSSL::PKey::EC.generate("prime256v1")
    @client = Oauth::AppleClient.new(
      team_id: "APPLETEAM",
      key_id: "APPLEKEY",
      private_key: @key.to_pem
    )
  end

  test "exchanges an authorization code" do
    request = stub_request(:post, Oauth::AppleClient::TOKEN_URL)
      .with { |web_request| valid_apple_request?(web_request, token: "one-time-code") }
      .to_return(
        status: 200,
        body: { id_token: "identity-token", refresh_token: "refresh-token" }.to_json,
        headers: { "Content-Type" => "application/json" }
      )

    result = @client.exchange_code!(code: "one-time-code", client_id: "app.hauptgang.ios")

    assert_requested request
    assert_equal "identity-token", result["id_token"]
    assert_equal "refresh-token", result["refresh_token"]
  end

  test "revokes a refresh token" do
    request = stub_request(:post, Oauth::AppleClient::REVOKE_URL)
      .with { |web_request| valid_apple_request?(web_request, token: "refresh-token") }
      .to_return(status: 200, body: "")

    assert @client.revoke!(refresh_token: "refresh-token", client_id: "app.hauptgang.ios")
    assert_requested request
  end

  test "raises a safe error for an Apple error response" do
    stub_request(:post, Oauth::AppleClient::TOKEN_URL).to_return(
      status: 400,
      body: { error: "invalid_grant" }.to_json
    )

    error = assert_raises Oauth::Error do
      @client.exchange_code!(code: "expired-code", client_id: "app.hauptgang.ios")
    end

    assert_match "invalid_grant", error.message
  end

  test "reports rejected client credentials as provider unavailability" do
    stub_request(:post, Oauth::AppleClient::TOKEN_URL).to_return(
      status: 400,
      body: { error: "invalid_client" }.to_json
    )

    assert_raises Oauth::UnavailableError do
      @client.exchange_code!(code: "one-time-code", client_id: "app.hauptgang.ios")
    end
  end

  private
    def valid_apple_request?(request, token:)
      params = URI.decode_www_form(request.body).to_h
      payload, header = JWT.decode(params.fetch("client_secret"), @key.public_key, true, algorithms: [ "ES256" ])

      params["client_id"] == "app.hauptgang.ios" &&
        (params["code"] == token || params["token"] == token) &&
        payload["iss"] == "APPLETEAM" &&
        payload["sub"] == "app.hauptgang.ios" &&
        header["kid"] == "APPLEKEY"
    end
end
