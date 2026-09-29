require "test_helper"

class Api::V1::CookbooksControllerTest < ActionDispatch::IntegrationTest
  setup do
    @user = users(:one)
    @other_user = users(:two)
    _token_record, @raw_token = ApiToken.generate_for(@user)
    @auth_headers = { "Authorization" => "Bearer #{@raw_token}" }
  end

  # ===================
  # INDEX
  # ===================

  test "index returns user's cookbooks" do
    get api_v1_cookbooks_url, headers: @auth_headers, as: :json

    assert_response :success
    json = response.parsed_body
    assert_kind_of Array, json
    assert_equal 1, json.length
    assert_equal "My Recipes", json.first["name"]
    assert_equal true, json.first["default_name"]
    assert json.first["personal"]
    assert json.first["members"].any?
  end

  test "index includes recipe count" do
    get api_v1_cookbooks_url, headers: @auth_headers, as: :json

    assert_response :success
    cookbook = response.parsed_body.first
    assert_equal @user.personal_cookbook.recipes.count, cookbook["recipe_count"]
  end

  test "index requires authentication" do
    get api_v1_cookbooks_url, as: :json

    assert_response :unauthorized
  end

  # ===================
  # CREATE
  # ===================

  test "create creates a shared cookbook" do
    post api_v1_cookbooks_url,
      params: { name: "Family Recipes" },
      headers: @auth_headers,
      as: :json

    assert_response :created
    json = response.parsed_body
    assert_equal "Family Recipes", json["name"]
    assert_equal false, json["default_name"]
    assert_not json["personal"]
    assert_equal 1, json["members"].length
    assert_equal "owner", json["members"].first["role"]
  end

  test "create returns 422 for blank name" do
    post api_v1_cookbooks_url,
      params: { name: "" },
      headers: @auth_headers,
      as: :json

    assert_response :unprocessable_entity
  end

  test "create returns 422 when user already has a shared cookbook" do
    # Create first shared cookbook
    Cookbook.create!(name: "First Shared", personal: false).tap do |c|
      CookbookMembership.create!(cookbook: c, user: @user, role: :owner)
    end

    post api_v1_cookbooks_url,
      params: { name: "Second Shared" },
      headers: @auth_headers,
      as: :json

    assert_response :unprocessable_entity
    assert_equal "You already have a shared cookbook", response.parsed_body["error"]
  end

  test "create with move_personal_recipes moves recipes to shared cookbook" do
    personal_recipe_count = @user.personal_cookbook.recipes.count
    assert personal_recipe_count > 0

    post api_v1_cookbooks_url,
      params: { name: "Family Recipes", move_personal_recipes: true },
      headers: @auth_headers,
      as: :json

    assert_response :created
    json = response.parsed_body
    assert_equal personal_recipe_count, json["recipe_count"]
    assert_equal 0, @user.personal_cookbook.recipes.count
  end

  test "create with move_personal_recipes moves shopping list items too" do
    personal_item_count = @user.personal_cookbook.shopping_list_items.count
    assert personal_item_count > 0

    post api_v1_cookbooks_url,
      params: { name: "Family Recipes", move_personal_recipes: true },
      headers: @auth_headers,
      as: :json

    assert_response :created
    assert_equal 0, @user.personal_cookbook.shopping_list_items.count
  end

  # ===================
  # DESTROY
  # ===================

  test "destroy deletes shared cookbook owned by user" do
    shared = Cookbook.create!(name: "Shared", personal: false)
    CookbookMembership.create!(cookbook: shared, user: @user, role: :owner)

    delete api_v1_cookbook_url(shared), headers: @auth_headers, as: :json

    assert_response :no_content
    assert_nil Cookbook.find_by(id: shared.id)
  end

  test "destroy cascades deletion to recipes in cookbook" do
    shared = Cookbook.create!(name: "Shared", personal: false)
    CookbookMembership.create!(cookbook: shared, user: @user, role: :owner)
    recipe = shared.recipes.create!(name: "Shared Recipe", user: @user)

    delete api_v1_cookbook_url(shared), headers: @auth_headers, as: :json

    assert_response :no_content
    assert_nil Recipe.find_by(id: recipe.id)
  end

  test "destroy returns 422 for personal cookbook" do
    delete api_v1_cookbook_url(@user.personal_cookbook), headers: @auth_headers, as: :json

    assert_response :unprocessable_entity
    assert_equal "Cannot delete personal cookbook", response.parsed_body["error"]
  end

  test "destroy returns 403 for non-owner" do
    shared = Cookbook.create!(name: "Shared", personal: false)
    CookbookMembership.create!(cookbook: shared, user: @other_user, role: :owner)
    CookbookMembership.create!(cookbook: shared, user: @user, role: :collaborator)

    delete api_v1_cookbook_url(shared), headers: @auth_headers, as: :json

    assert_response :forbidden
  end

  test "destroy returns 404 for cookbook user is not a member of" do
    other_cookbook = cookbooks(:two_personal)

    delete api_v1_cookbook_url(other_cookbook), headers: @auth_headers, as: :json

    assert_response :not_found
  end

  # ===================
  # LEAVE
  # ===================

  test "leave removes collaborator membership" do
    shared = Cookbook.create!(name: "Shared", personal: false)
    CookbookMembership.create!(cookbook: shared, user: @other_user, role: :owner)
    CookbookMembership.create!(cookbook: shared, user: @user, role: :collaborator)

    post leave_api_v1_cookbook_url(shared), headers: @auth_headers, as: :json

    assert_response :no_content
    assert_not CookbookMembership.exists?(user: @user, cookbook: shared)
    assert Cookbook.exists?(shared.id), "Cookbook should still exist"
  end

  test "leave returns 422 for owner" do
    shared = Cookbook.create!(name: "Shared", personal: false)
    CookbookMembership.create!(cookbook: shared, user: @user, role: :owner)

    post leave_api_v1_cookbook_url(shared), headers: @auth_headers, as: :json

    assert_response :unprocessable_entity
    assert_equal "Owner cannot leave. Delete the cookbook instead.", response.parsed_body["error"]
  end

  test "leave returns 422 for personal cookbook" do
    post leave_api_v1_cookbook_url(@user.personal_cookbook), headers: @auth_headers, as: :json

    assert_response :unprocessable_entity
  end

  # ===================
  # COOKBOOK SCOPING
  # ===================

  test "user cannot access another user's cookbook recipes via X-Cookbook-Id header" do
    other_cookbook = cookbooks(:two_personal)

    get api_v1_recipes_url,
      headers: @auth_headers.merge("X-Cookbook-Id" => other_cookbook.id.to_s),
      as: :json

    assert_response :forbidden
  end

  test "X-Cookbook-Id header scopes to correct cookbook" do
    shared = Cookbook.create!(name: "Shared", personal: false)
    CookbookMembership.create!(cookbook: shared, user: @user, role: :owner)
    shared.recipes.create!(name: "Shared Recipe", user: @user)

    get api_v1_recipes_url,
      headers: @auth_headers.merge("X-Cookbook-Id" => shared.id.to_s),
      as: :json

    assert_response :success
    json = response.parsed_body
    assert_equal 1, json.length
    assert_equal "Shared Recipe", json.first["name"]
  end

  test "index succeeds even with invalid X-Cookbook-Id header" do
    get api_v1_cookbooks_url,
      headers: @auth_headers.merge("X-Cookbook-Id" => "99999"),
      as: :json

    assert_response :success
  end

  test "defaults to personal cookbook when no X-Cookbook-Id header" do
    get api_v1_recipes_url, headers: @auth_headers, as: :json

    assert_response :success
    json = response.parsed_body
    names = json.map { |r| r["name"] }
    assert_includes names, "Pasta Carbonara"
  end
end
