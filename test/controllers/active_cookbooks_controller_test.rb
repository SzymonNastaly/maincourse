require "test_helper"

class ActiveCookbooksControllerTest < ActionDispatch::IntegrationTest
  setup do
    @user = users(:one)
    @personal = cookbooks(:one_personal)
    sign_in_as @user
  end

  test "requires authentication" do
    sign_out
    patch active_cookbook_path(cookbook_id: @personal.id)
    assert_redirected_to new_session_path
  end

  test "defaults to the shared cookbook when there is one" do
    shared = create_shared_cookbook_for(@user)

    get recipes_path

    assert_select "[data-testid=cookbook-switcher-trigger]", text: /#{shared.name}/
  end

  test "defaults to the personal cookbook otherwise" do
    get recipes_path

    assert_select "[data-testid=cookbook-switcher-trigger]", text: /#{@personal.name}/
  end

  test "switching remembers the choice" do
    shared = create_shared_cookbook_for(@user)

    patch active_cookbook_path(cookbook_id: @personal.id)

    assert_equal @personal.id, session[:cookbook_id]
    assert_redirected_to recipes_path

    get recipes_path
    assert_select "[data-testid=cookbook-switcher-trigger]", text: /#{@personal.name}/
    assert_no_match shared.name, css_select("[data-testid=cookbook-switcher-trigger]").to_s
  end

  test "switching refuses a cookbook the user is not a member of" do
    patch active_cookbook_path(cookbook_id: cookbooks(:two_personal).id)

    assert_nil session[:cookbook_id]
    assert_equal "That cookbook is not available.", flash[:alert]
  end

  test "switching from a recipe page returns to the list, not a dead link" do
    shared = create_shared_cookbook_for(@user)

    patch active_cookbook_path(cookbook_id: shared.id),
          headers: { "HTTP_REFERER" => recipe_url(recipes(:one)) }

    assert_redirected_to recipes_path
  end

  test "switching from the shopping list stays on the shopping list" do
    shared = create_shared_cookbook_for(@user)

    patch active_cookbook_path(cookbook_id: shared.id),
          headers: { "HTTP_REFERER" => shopping_list_items_url }

    assert_redirected_to shopping_list_items_path
  end
end
