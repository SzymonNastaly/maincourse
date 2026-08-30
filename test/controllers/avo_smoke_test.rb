require "test_helper"

class AvoSmokeTest < ActionDispatch::IntegrationTest
  RESOURCES = {
    "cookbooks" => -> { cookbooks(:one) },
    "cookbook_invitations" => nil,
    "meal_plans" => nil,
    "meal_plan_entries" => nil,
    "onboarding_responses" => nil,
    "recipes" => -> { recipes(:one) },
    "shopping_list_items" => nil,
    "tags" => nil,
    "users" => -> { users(:one) }
  }

  setup do
    @admin = users(:one)
    @admin.update!(admin: true)
    sign_in_as @admin
  end

  test "avo root loads" do
    get "/avo"
    follow_redirect! while response.redirect?
    assert_response :success
  end

  RESOURCES.each_key do |slug|
    test "#{slug} index loads" do
      get "/avo/resources/#{slug}"
      assert_response :success
    end
  end

  test "record show and edit pages load" do
    get "/avo/resources/recipes/#{recipes(:one).id}"
    assert_response :success

    get "/avo/resources/recipes/#{recipes(:one).id}/edit"
    assert_response :success

    get "/avo/resources/users/#{users(:one).id}"
    assert_response :success
  end

  test "resource search runs the ransack query" do
    get "/avo/avo_api/recipes/search", params: { q: recipes(:one).name }
    assert_response :success
  end

  test "new record form loads" do
    get "/avo/resources/tags/new"
    assert_response :success
  end

  test "create, update and destroy work" do
    assert_difference -> { Tag.count }, +1 do
      post "/avo/resources/tags", params: { tag: { name: "Smoke", slug: "smoke" } }
    end
    tag = Tag.order(:id).last
    assert_response :redirect

    patch "/avo/resources/tags/#{tag.id}", params: { tag: { name: "Smoke Renamed" } }
    assert_response :redirect
    assert_equal "Smoke Renamed", tag.reload.name

    assert_difference -> { Tag.count }, -1 do
      delete "/avo/resources/tags/#{tag.id}"
    end
  end

  test "non-admin is redirected away" do
    sign_out
    sign_in_as users(:two)

    get "/avo"
    assert_redirected_to "/"
  end
end
