require "test_helper"

class Api::V1::AppleAuthTransactionsControllerTest < ActionDispatch::IntegrationTest
  RFC_7636_VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
  RFC_7636_CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"

  test "start is anonymous and returns a same-origin browser URL" do
    with_apple_enabled do
      post api_v1_apple_auth_transaction_url,
        params: { code_challenge: RFC_7636_CHALLENGE, callback: "release" },
        headers: { "X-Cookbook-Id" => "not-a-cookbook" },
        as: :json
    end

    assert_response :created
    body = response.parsed_body
    assert_equal "http://www.example.com/android/apple/sign_in?transaction_id=#{body.fetch("transaction_id")}", body["browser_url"]
    assert_predicate Time.zone.parse(body.fetch("expires_at")), :future?
    assert_equal "no-store", response.headers["Cache-Control"]
    assert_equal "no-referrer", response.headers["Referrer-Policy"]
  end

  test "start returns service unavailable when Apple is not configured" do
    Rails.application.config.x.oauth.stub(:apple_enabled, false) do
      post api_v1_apple_auth_transaction_url,
        params: { code_challenge: RFC_7636_CHALLENGE, callback: "release" }, as: :json
    end

    assert_response :service_unavailable
    assert_equal({ "error" => "Apple sign-in is temporarily unavailable" }, response.parsed_body)
    assert_equal "no-store", response.headers["Cache-Control"]
  end

  test "start rejects invalid input without accepting a client redirect" do
    with_apple_enabled do
      post api_v1_apple_auth_transaction_url,
        params: {
          code_challenge: RFC_7636_CHALLENGE,
          callback: "https://attacker.example/callback"
        }, as: :json
    end

    assert_response :bad_request
    assert_equal({ "error" => "Invalid Apple authentication request" }, response.parsed_body)
    assert_equal 0, AppleAuthTransaction.count
  end

  test "exchange is anonymous and returns the normal session response once" do
    transaction, handle = AppleAuthTransaction.start!(code_challenge: RFC_7636_CHALLENGE, callback: "release")
    exchange_code = transaction.authorize!(user: users(:one))
    params = {
      transaction_id: handle,
      exchange_code: exchange_code,
      code_verifier: RFC_7636_VERIFIER,
      device_name: "Pixel 10"
    }

    assert_difference("ApiToken.count", 1) do
      post exchange_api_v1_apple_auth_transaction_url,
        params: params,
        headers: { "X-Cookbook-Id" => "not-a-cookbook" },
        as: :json
    end

    assert_response :created
    assert ApiToken.find_by_raw_token(response.parsed_body.fetch("token"))
    assert_equal users(:one).id, response.parsed_body.dig("user", "id")
    assert_equal "no-store", response.headers["Cache-Control"]

    assert_no_difference("ApiToken.count") do
      post exchange_api_v1_apple_auth_transaction_url, params: params, as: :json
    end
    assert_response :bad_request
    assert_equal({ "error" => "Could not authenticate with Apple" }, response.parsed_body)
  end

  test "wrong verifier returns the same generic response and remains exchangeable" do
    transaction, handle = AppleAuthTransaction.start!(code_challenge: RFC_7636_CHALLENGE, callback: "release")
    exchange_code = transaction.authorize!(user: users(:one))

    assert_no_difference("ApiToken.count") do
      post exchange_api_v1_apple_auth_transaction_url, params: {
        transaction_id: handle,
        exchange_code: exchange_code,
        code_verifier: "A" * 43
      }, as: :json
    end
    assert_response :bad_request
    assert_equal({ "error" => "Could not authenticate with Apple" }, response.parsed_body)
    assert_nil transaction.reload.consumed_at

    assert_difference("ApiToken.count", 1) do
      post exchange_api_v1_apple_auth_transaction_url, params: {
        transaction_id: handle,
        exchange_code: exchange_code,
        code_verifier: RFC_7636_VERIFIER
      }, as: :json
    end
    assert_response :created
  end

  test "unknown transaction and wrong exchange code have the same generic response" do
    transaction, handle = AppleAuthTransaction.start!(code_challenge: RFC_7636_CHALLENGE, callback: "release")
    transaction.authorize!(user: users(:one))

    post exchange_api_v1_apple_auth_transaction_url, params: {
      transaction_id: "unknown",
      exchange_code: "wrong",
      code_verifier: RFC_7636_VERIFIER
    }, as: :json
    unknown_response = response.parsed_body

    post exchange_api_v1_apple_auth_transaction_url, params: {
      transaction_id: handle,
      exchange_code: "wrong",
      code_verifier: RFC_7636_VERIFIER
    }, as: :json

    assert_response :bad_request
    assert_equal unknown_response, response.parsed_body
    assert_equal({ "error" => "Could not authenticate with Apple" }, response.parsed_body)
  end

  test "sensitive handoff parameters are filtered from logs" do
    filtered = ActiveSupport::ParameterFilter.new(Rails.application.config.filter_parameters).filter(
      "transaction_id" => "transaction",
      "android_transaction" => "browser-transaction",
      "exchange_code" => "exchange",
      "code_verifier" => "verifier"
    )

    assert_equal [ "[FILTERED]" ], filtered.values.uniq
  end

  private
    def with_apple_enabled(&block)
      Rails.application.config.x.oauth.stub(:apple_enabled, true, &block)
    end
end
