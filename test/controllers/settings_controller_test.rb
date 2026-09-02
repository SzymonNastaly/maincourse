require "test_helper"

class SettingsControllerTest < ActionDispatch::IntegrationTest
  setup do
    @user = users(:one)
    sign_in_as @user
  end

  test "requires authentication" do
    sign_out
    get edit_settings_path
    assert_redirected_to new_session_path
  end

  test "shows the signed in email and the free plan" do
    get edit_settings_path

    assert_response :success
    assert_match @user.email_address, response.body
    assert_match "Free Plan", response.body
    assert_select "[data-testid=upgrade-link]"
  end

  test "shows Pro without an upgrade link" do
    @user.update!(pro: true)

    get edit_settings_path

    assert_match "MainCourse Pro", response.body
    assert_match "iOS app", response.body
    assert_select "[data-testid=upgrade-link]", 0
  end

  test "updates the display name" do
    patch settings_path, params: { user: { name: "Szymon" } }

    assert_equal "Szymon", @user.reload.name
    assert_redirected_to edit_settings_path
  end

  test "toggles recipe reminders" do
    assert @user.lifecycle_notifications_enabled

    patch settings_path, params: { user: { lifecycle_notifications_enabled: "0" } }
    assert_not @user.reload.lifecycle_notifications_enabled

    patch settings_path, params: { user: { lifecycle_notifications_enabled: "1" } }
    assert @user.reload.lifecycle_notifications_enabled
  end

  test "cannot change anything else through settings" do
    patch settings_path, params: { user: { name: "Szymon", pro: "true", admin: "true" } }

    assert_not @user.reload.pro
    assert_not @user.admin
  end
end
