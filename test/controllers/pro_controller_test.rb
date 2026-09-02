require "test_helper"

# Payments are iOS-only; the web page explains Pro and points at the App Store.
class ProControllerTest < ActionDispatch::IntegrationTest
  setup do
    @user = users(:one)
    sign_in_as @user
  end

  test "requires authentication" do
    sign_out
    get pro_path
    assert_redirected_to new_session_path
  end

  test "a free user is sent to the App Store, with no prices on the page" do
    get pro_path

    assert_response :success
    assert_select "[data-testid=app-store-link][href=?]", ProController::APP_STORE_URL
    assert_match "Upgrade in the iOS app", response.body
    assert_no_match(/CHF|\$\d|per month/, response.body)
  end

  test "a free user sees how many imports are left" do
    cookbooks(:one_personal).recipes.create!(name: "Imported", user: @user, import_status: :completed)

    get pro_path

    assert_match @user.remaining_imports.to_i.to_s, response.body
  end

  test "a Pro user is told to manage the subscription on iOS" do
    @user.update!(pro: true)

    get pro_path

    assert_match "You're on Pro", response.body
    assert_match "iOS app", response.body
    assert_select "[data-testid=app-store-link]", 0
  end
end
