require "test_helper"

class Api::V1::RegistrationsControllerTest < ActionDispatch::IntegrationTest
  test "create returns token with valid params" do
    assert_difference "User.count", 1 do
      post api_v1_registration_url, params: {
        email: "new-user@example.com",
        password: "password",
        password_confirmation: "password"
      }, as: :json
    end

    assert_response :created
    json = response.parsed_body
    assert_not_nil json["token"]
    assert_not_nil json["expires_at"]
    assert_not_nil json["user"]["id"]
    assert_equal "new-user@example.com", json["user"]["email"]
  end

  test "create with device_name stores device name" do
    post api_v1_registration_url, params: {
      email: "device-user@example.com",
      password: "password",
      password_confirmation: "password",
      device_name: "iPhone 15"
    }, as: :json

    assert_response :created
    user = User.find_by!(email_address: "device-user@example.com")
    token = user.api_tokens.last
    assert_equal "iPhone 15", token.name
  end

  test "create returns 422 with invalid confirmation" do
    post api_v1_registration_url, params: {
      email: "mismatch@example.com",
      password: "password",
      password_confirmation: "not-the-same"
    }, as: :json

    assert_response :unprocessable_entity
    json = response.parsed_body
    assert_includes json["errors"], "Password confirmation doesn't match Password"
  end

  test "create returns 422 with duplicate email" do
    post api_v1_registration_url, params: {
      email: users(:one).email_address,
      password: "password",
      password_confirmation: "password"
    }, as: :json

    assert_response :unprocessable_entity
    json = response.parsed_body
    assert_includes json["errors"], "Email address has already been taken"
  end

  test "signup returns the lifecycle notification preference" do
    post api_v1_registration_url, params: {
      name: "New", email: "new-pref@example.com", password: "password123", password_confirmation: "password123"
    }, as: :json

    assert_response :created
    assert_equal true, response.parsed_body.dig("user", "lifecycle_notifications_enabled")
  end
end
