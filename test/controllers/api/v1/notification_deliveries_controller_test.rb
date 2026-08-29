require "test_helper"

class Api::V1::NotificationDeliveriesControllerTest < ActionDispatch::IntegrationTest
  setup do
    @user = users(:one)
    _token_record, @raw_token = ApiToken.generate_for(@user)
    @auth_headers = { "Authorization" => "Bearer #{@raw_token}" }
    @delivery = NotificationDelivery.create!(
      user: @user, campaign: "resurface", recipe: recipes(:one), sent_at: 1.hour.ago
    )
  end

  test "opened marks the delivery as opened" do
    post opened_api_v1_notification_delivery_url(@delivery), headers: @auth_headers, as: :json

    assert_response :no_content
    assert_not_nil @delivery.reload.opened_at
  end

  test "opened records an action and the last one wins" do
    post opened_api_v1_notification_delivery_url(@delivery),
         params: { action_taken: "added_to_list" }, headers: @auth_headers, as: :json
    post opened_api_v1_notification_delivery_url(@delivery),
         params: { action_taken: "checked_off" }, headers: @auth_headers, as: :json

    assert_response :no_content
    assert_equal "checked_off", @delivery.reload.action_taken
  end

  test "opened keeps the first opened_at" do
    post opened_api_v1_notification_delivery_url(@delivery), headers: @auth_headers, as: :json
    first_opened = @delivery.reload.opened_at

    travel 1.hour do
      post opened_api_v1_notification_delivery_url(@delivery), headers: @auth_headers, as: :json
    end

    assert_in_delta first_opened.to_i, @delivery.reload.opened_at.to_i, 1
  end

  test "opened does not touch another user's delivery" do
    other = NotificationDelivery.create!(user: users(:two), campaign: "resurface", sent_at: 1.hour.ago)

    post opened_api_v1_notification_delivery_url(other), headers: @auth_headers, as: :json

    assert_response :not_found
    assert_nil other.reload.opened_at
  end
end
