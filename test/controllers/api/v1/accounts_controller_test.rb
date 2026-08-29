require "test_helper"

class Api::V1::AccountsControllerTest < ActionDispatch::IntegrationTest
  setup do
    @user = users(:one)
    _token_record, @raw_token = ApiToken.generate_for(@user)
    @auth_headers = { "Authorization" => "Bearer #{@raw_token}" }
  end

  test "update changes the user's name" do
    patch api_v1_account_url,
      params: { user: { name: "New Name" } },
      headers: @auth_headers,
      as: :json

    assert_response :ok
    assert_equal "New Name", @user.reload.name
  end

  test "update toggles lifecycle notifications off" do
    patch api_v1_account_url,
          params: { user: { lifecycle_notifications_enabled: false } },
          headers: @auth_headers, as: :json

    assert_response :success
    assert_not @user.reload.lifecycle_notifications_enabled
    assert_equal false, response.parsed_body["user"]["lifecycle_notifications_enabled"]
  end

  test "update leaves the preference alone when not supplied" do
    @user.update_column(:lifecycle_notifications_enabled, false)

    patch api_v1_account_url, params: { user: { name: "Renamed" } },
          headers: @auth_headers, as: :json

    assert_response :success
    assert_not @user.reload.lifecycle_notifications_enabled
  end

  test "destroy deletes the account and cascades data" do
    user_id = @user.id
    personal_cookbook_id = @user.personal_cookbook.id

    delete api_v1_account_url, headers: @auth_headers, as: :json

    assert_response :no_content
    assert_nil User.find_by(id: user_id)
    assert_nil Cookbook.find_by(id: personal_cookbook_id)
  end

  test "destroy revokes the api token" do
    delete api_v1_account_url, headers: @auth_headers, as: :json

    assert_response :no_content

    get api_v1_recipes_url, headers: @auth_headers, as: :json
    assert_response :unauthorized
  end

  test "destroy requires authentication" do
    delete api_v1_account_url, as: :json
    assert_response :unauthorized
  end
end
