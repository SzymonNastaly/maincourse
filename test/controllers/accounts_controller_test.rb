require "test_helper"

class AccountsControllerTest < ActionDispatch::IntegrationTest
  setup do
    @user = users(:one)
    sign_in_as @user
  end

  test "requires authentication" do
    sign_out

    get account_path
    assert_redirected_to new_session_path

    delete account_path, params: { confirmation: "DELETE" }
    assert_redirected_to new_session_path
  end

  test "shows the email and what deletion removes" do
    get account_path

    assert_response :success
    assert_match @user.email_address, response.body
    assert_select "[data-testid=delete-confirmation]"
  end

  test "deletion needs the confirmation phrase" do
    assert_no_difference "User.count" do
      delete account_path, params: { confirmation: "delete" }
    end

    assert_equal "Type DELETE to confirm.", flash[:alert]
    assert_redirected_to account_path
  end

  test "deletion with the phrase removes the account and signs out" do
    assert_difference "User.count", -1 do
      delete account_path, params: { confirmation: "DELETE" }
    end

    assert_redirected_to new_session_path

    get recipes_path
    assert_redirected_to new_session_path
  end

  test "deleting an owner hands the shared cookbook to the remaining member" do
    shared = create_shared_cookbook_for(@user)
    join_cookbook(shared, users(:two))

    delete account_path, params: { confirmation: "DELETE" }

    assert Cookbook.exists?(shared.id)
    assert_equal users(:two), shared.reload.owner
  end
end
