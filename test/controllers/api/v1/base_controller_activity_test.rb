require "test_helper"

class Api::V1::BaseControllerActivityTest < ActionDispatch::IntegrationTest
  setup do
    @user = users(:one)
    _token_record, @raw_token = ApiToken.generate_for(@user)
    @auth_headers = { "Authorization" => "Bearer #{@raw_token}" }
  end

  test "an authenticated request records last_active_at" do
    @user.update_column(:last_active_at, nil)

    get api_v1_shopping_list_items_url, headers: @auth_headers, as: :json

    assert_response :success
    assert_not_nil @user.reload.last_active_at
  end

  test "an unauthenticated request records nothing" do
    @user.update_column(:last_active_at, nil)

    get api_v1_shopping_list_items_url, as: :json

    assert_response :unauthorized
    assert_nil @user.reload.last_active_at
  end
end
