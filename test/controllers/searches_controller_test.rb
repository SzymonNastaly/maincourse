require "test_helper"

class SearchesControllerTest < ActionDispatch::IntegrationTest
  setup do
    @user = users(:one)
    sign_in_as @user
  end

  test "requires authentication" do
    sign_out
    get search_path
    assert_redirected_to new_session_path
  end

  test "an empty query prompts rather than listing everything" do
    get search_path

    assert_response :success
    assert_match "Search your recipes", response.body
    assert_select "[data-testid=recipe-card]", 0
  end

  test "matches on the recipe name, case-insensitively" do
    get search_path(q: "carbonara")

    assert_match recipes(:one).name, response.body
    assert_no_match recipes(:three).name, response.body
  end

  test "matches on an ingredient" do
    get search_path(q: "croutons")

    assert_match recipes(:three).name, response.body
    assert_no_match recipes(:one).name, response.body
  end

  test "matches on a method step" do
    get search_path(q: "Drizzle dressing")

    assert_match recipes(:three).name, response.body
  end

  test "searches across every cookbook the user belongs to" do
    shared = create_shared_cookbook_for(@user)
    shared_recipe = shared.recipes.create!(name: "Shared Labneh Flatbread", user: @user)

    get search_path(q: "labneh")

    assert_match shared_recipe.name, response.body
  end

  test "never returns another user's recipes" do
    get search_path(q: "curry")

    assert_no_match recipes(:two).name, response.body
    assert_match "No results", response.body
  end

  test "excludes failed imports" do
    cookbooks(:one_personal).recipes.create!(
      name: "Broken Carbonara", user: @user, import_status: :failed
    )

    get search_path(q: "Broken Carbonara")

    assert_match "No results", response.body
  end

  test "escapes SQL wildcards in the query" do
    get search_path(q: "%")

    assert_response :success
    assert_match "No results", response.body
  end
end
