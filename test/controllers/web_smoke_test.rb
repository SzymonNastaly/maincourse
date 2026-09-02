require "test_helper"

# Renders every signed-in web page once. Cheap insurance against a view that
# only blows up at request time.
class WebSmokeTest < ActionDispatch::IntegrationTest
  setup do
    @user = users(:one)
    sign_in_as @user
  end

  test "every top-level page renders" do
    [
      recipes_path,
      recipe_path(recipes(:one)),
      new_recipe_path,
      edit_recipe_path(recipes(:one)),
      search_path,
      search_path(q: "pasta"),
      shopping_list_items_path,
      cookbooks_path,
      edit_settings_path,
      account_path,
      pro_path
    ].each do |path|
      get path
      assert_response :success, "GET #{path} returned #{response.status}"
    end
  end

  test "signed out pages render" do
    sign_out

    [ new_session_path, new_registration_path, new_password_path ].each do |path|
      get path
      assert_response :success, "GET #{path} returned #{response.status}"
    end
  end
end
