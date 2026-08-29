require "test_helper"

class Api::V1::SessionsControllerTest < ActionDispatch::IntegrationTest
  setup do
    @user = users(:one)
  end

  test "create returns token with valid credentials" do
    post api_v1_session_url, params: {
      email: @user.email_address,
      password: "password"
    }, as: :json

    assert_response :created
    json = response.parsed_body
    assert_not_nil json["token"]
    assert_not_nil json["expires_at"]
    assert_equal @user.id, json["user"]["id"]
    assert_equal @user.email_address, json["user"]["email"]
  end

  test "create with device_name stores device name" do
    post api_v1_session_url, params: {
      email: @user.email_address,
      password: "password",
      device_name: "iPhone 15"
    }, as: :json

    assert_response :created
    token = @user.api_tokens.last
    assert_equal "iPhone 15", token.name
  end

  test "create returns 401 with invalid password" do
    post api_v1_session_url, params: {
      email: @user.email_address,
      password: "wrong_password"
    }, as: :json

    assert_response :unauthorized
    json = response.parsed_body
    assert_equal "Invalid email or password", json["error"]
  end

  test "create returns 401 with invalid email" do
    post api_v1_session_url, params: {
      email: "nonexistent@example.com",
      password: "password"
    }, as: :json

    assert_response :unauthorized
  end

  test "destroy revokes the token" do
    _token_record, raw_token = ApiToken.generate_for(@user)

    delete api_v1_session_url,
      headers: { "Authorization" => "Bearer #{raw_token}" },
      as: :json

    assert_response :ok
    json = response.parsed_body
    assert_equal "Logged out successfully", json["message"]
  end

  test "destroy requires authentication" do
    delete api_v1_session_url, as: :json

    assert_response :unauthorized
  end

  test "token is invalid after logout" do
    _token_record, raw_token = ApiToken.generate_for(@user)

    delete api_v1_session_url,
      headers: { "Authorization" => "Bearer #{raw_token}" },
      as: :json

    assert_response :ok

    # Try to use the token again
    get api_v1_recipes_url,
      headers: { "Authorization" => "Bearer #{raw_token}" },
      as: :json

    assert_response :unauthorized
  end

  test "login returns the lifecycle notification preference" do
    users(:one).update!(lifecycle_notifications_enabled: false)

    post api_v1_session_url, params: { email: users(:one).email_address, password: "password" }, as: :json

    assert_response :created
    assert_equal false, response.parsed_body.dig("user", "lifecycle_notifications_enabled")
  end
end
