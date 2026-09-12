require "test_helper"

class Api::V1::DeviceTokensControllerTest < ActionDispatch::IntegrationTest
  setup do
    @user = users(:one)
    _record, @raw_token = ApiToken.generate_for(@user)
    @auth_headers = { "Authorization" => "Bearer #{@raw_token}" }
  end

  test "create registers a token" do
    post api_v1_device_tokens_url,
         params: { token: "abcdef", environment: "sandbox" },
         headers: @auth_headers, as: :json

    assert_response :created
    assert_equal 1, @user.device_tokens.count
    assert_equal "sandbox", @user.device_tokens.first.environment
    assert_equal "apns", @user.device_tokens.first.provider
  end

  test "create registers an FCM token" do
    post api_v1_device_tokens_url,
         params: { token: "fcm-token", provider: "fcm", time_zone: "Europe/Berlin" },
         headers: @auth_headers, as: :json

    assert_response :created
    registration = @user.device_tokens.find_by!(token: "fcm-token")
    assert_equal "fcm", registration.provider
    assert_equal "fcm", response.parsed_body["provider"]
  end

  test "create rejects invalid provider" do
    post api_v1_device_tokens_url,
         params: { token: "abc", provider: "webpush" },
         headers: @auth_headers, as: :json

    assert_response :unprocessable_entity
  end

  test "create rejects invalid environment" do
    post api_v1_device_tokens_url,
         params: { token: "abc", environment: "weird" },
         headers: @auth_headers, as: :json

    assert_response :unprocessable_entity
  end

  test "create rejects blank token" do
    post api_v1_device_tokens_url,
         params: { token: "", environment: "production" },
         headers: @auth_headers, as: :json

    assert_response :unprocessable_entity
  end

  test "create is idempotent (re-registering same token updates it)" do
    DeviceToken.register!(user: @user, token: "shared-token", environment: "production")

    post api_v1_device_tokens_url,
         params: { token: "shared-token", environment: "sandbox" },
         headers: @auth_headers, as: :json

    assert_response :created
    assert_equal 1, DeviceToken.where(token: "shared-token").count
    assert_equal "sandbox", DeviceToken.find_by(token: "shared-token").environment
  end

  test "destroy removes the user's token" do
    DeviceToken.register!(user: @user, token: "to-delete", environment: "production")

    delete api_v1_device_token_url(token: "to-delete"), headers: @auth_headers, as: :json

    assert_response :no_content
    assert_nil DeviceToken.find_by(token: "to-delete")
  end

  test "destroy can target one provider when opaque token values match" do
    DeviceToken.register!(user: @user, token: "same", provider: "apns", environment: "production")
    DeviceToken.register!(user: @user, token: "same", provider: "fcm", environment: "production")

    delete api_v1_device_token_url(token: "same"),
           params: { provider: "fcm" }, headers: @auth_headers, as: :json

    assert_response :no_content
    assert DeviceToken.exists?(token: "same", provider: "apns")
    assert_not DeviceToken.exists?(token: "same", provider: "fcm")
  end

  test "create requires auth" do
    post api_v1_device_tokens_url, params: { token: "x" }, as: :json
    assert_response :unauthorized
  end

  test "create stores a valid time zone on the user" do
    post api_v1_device_tokens_url,
         params: { token: "tz-token-1", environment: "sandbox", time_zone: "Europe/Zurich" },
         headers: @auth_headers, as: :json

    assert_response :created
    assert_equal "Europe/Zurich", @user.reload.time_zone
  end

  test "create ignores an unknown time zone" do
    @user.update_column(:time_zone, "Europe/Zurich")

    post api_v1_device_tokens_url,
         params: { token: "tz-token-2", environment: "sandbox", time_zone: "Mars/Olympus" },
         headers: @auth_headers, as: :json

    assert_response :created
    assert_equal "Europe/Zurich", @user.reload.time_zone
  end

  test "create without a time zone leaves the existing value" do
    @user.update_column(:time_zone, "Europe/Zurich")

    post api_v1_device_tokens_url,
         params: { token: "tz-token-3", environment: "sandbox" },
         headers: @auth_headers, as: :json

    assert_response :created
    assert_equal "Europe/Zurich", @user.reload.time_zone
  end
end
