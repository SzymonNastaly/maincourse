require "test_helper"

class Api::V1::ShoppingListItemsControllerTest < ActionDispatch::IntegrationTest
  setup do
    @user = users(:one)
    @other_user = users(:two)
    _token_record, @raw_token = ApiToken.generate_for(@user)
    @auth_headers = { "Authorization" => "Bearer #{@raw_token}" }
  end

  # ===================
  # INDEX
  # ===================

  test "index returns user's shopping list items" do
    get api_v1_shopping_list_items_url, headers: @auth_headers, as: :json

    assert_response :success
    json = response.parsed_body
    assert_kind_of Array, json

    names = json.map { |i| i["name"] }
    assert_includes names, "Milk"
    assert_includes names, "Bread"
    assert_not_includes names, "Rice"
  end

  test "index returns unchecked items before checked items" do
    get api_v1_shopping_list_items_url, headers: @auth_headers, as: :json

    assert_response :success
    json = response.parsed_body

    first_checked_index = json.index { |i| i["checked_at"].present? }
    return if first_checked_index.nil?

    unchecked_after = json[first_checked_index..].any? { |i| i["checked_at"].nil? }
    assert_not unchecked_after, "Unchecked items should appear before checked items"
  end

  test "index returns expected fields" do
    get api_v1_shopping_list_items_url, headers: @auth_headers, as: :json

    assert_response :success
    item = response.parsed_body.first
    assert item.key?("id")
    assert item.key?("client_id")
    assert item.key?("name")
    assert item.key?("details")
    assert item.key?("checked_at")
    assert item.key?("source_recipe_id")
    assert item.key?("created_at")
    assert item.key?("updated_at")
  end

  test "index does not return other user's items" do
    get api_v1_shopping_list_items_url, headers: @auth_headers, as: :json

    assert_response :success
    client_ids = response.parsed_body.map { |i| i["client_id"] }
    assert_not_includes client_ids, shopping_list_items(:other_user_item).client_id
  end

  test "index cleans up stale checked items" do
    stale = shopping_list_items(:stale_checked_butter)

    get api_v1_shopping_list_items_url, headers: @auth_headers, as: :json

    assert_response :success
    assert_raises(ActiveRecord::RecordNotFound) { stale.reload }
    ids = response.parsed_body.map { |i| i["id"] }
    assert_not_includes ids, stale.id
  end

  test "index requires authentication" do
    get api_v1_shopping_list_items_url, as: :json

    assert_response :unauthorized
  end

  # ===================
  # CREATE
  # ===================

  test "create with single item" do
    post api_v1_shopping_list_items_url,
      params: { item: { client_id: "new-1", name: "Cheese" } },
      headers: @auth_headers,
      as: :json

    assert_response :created
    json = response.parsed_body
    assert_equal 1, json.size
    assert_equal "Cheese", json.first["name"]
    assert_equal "new-1", json.first["client_id"]
  end

  test "create with bulk items" do
    post api_v1_shopping_list_items_url,
      params: { items: [
        { client_id: "bulk-1", name: "Flour" },
        { client_id: "bulk-2", name: "Sugar" }
      ] },
      headers: @auth_headers,
      as: :json

    assert_response :created
    json = response.parsed_body
    assert_equal 2, json.size
    names = json.map { |i| i["name"] }
    assert_includes names, "Flour"
    assert_includes names, "Sugar"
  end

  test "create with source_recipe_id" do
    recipe = recipes(:one)

    post api_v1_shopping_list_items_url,
      params: { item: { client_id: "recipe-item-1", name: "Spaghetti", source_recipe_id: recipe.id } },
      headers: @auth_headers,
      as: :json

    assert_response :created
    assert_equal recipe.id, response.parsed_body.first["source_recipe_id"]
  end

  test "create with details round-trips through API" do
    post api_v1_shopping_list_items_url,
      params: { item: { client_id: "details-api-1", name: "Tomato", details: "200g, halved" } },
      headers: @auth_headers,
      as: :json

    assert_response :created
    assert_equal "200g, halved", response.parsed_body.first["details"]
  end

  test "create returns 422 for invalid data" do
    post api_v1_shopping_list_items_url,
      params: { item: { client_id: "", name: "" } },
      headers: @auth_headers,
      as: :json

    assert_response :unprocessable_entity
    json = response.parsed_body
    assert json["error"].present?
  end

  test "create returns 422 for empty items" do
    post api_v1_shopping_list_items_url,
      params: { items: [] },
      headers: @auth_headers,
      as: :json

    assert_response :unprocessable_entity
  end

  test "create rolls back all items when any item is invalid" do
    assert_no_difference "ShoppingListItem.count" do
      post api_v1_shopping_list_items_url,
        params: { items: [
          { client_id: "rollback-1", name: "Flour" },
          { client_id: "", name: "" }
        ] },
        headers: @auth_headers,
        as: :json
    end

    assert_response :unprocessable_entity
  end

  test "create requires authentication" do
    post api_v1_shopping_list_items_url,
      params: { item: { client_id: "unauth-1", name: "Test" } },
      as: :json

    assert_response :unauthorized
  end

  # ===================
  # UPDATE
  # ===================

  test "update checks item via checked param" do
    item = shopping_list_items(:unchecked_milk)

    patch api_v1_shopping_list_item_url(item),
      params: { checked: true },
      headers: @auth_headers,
      as: :json

    assert_response :success
    assert_not_nil response.parsed_body["checked_at"]
  end

  test "update unchecks item via checked param" do
    item = shopping_list_items(:checked_eggs)

    patch api_v1_shopping_list_item_url(item),
      params: { checked: false },
      headers: @auth_headers,
      as: :json

    assert_response :success
    assert_nil response.parsed_body["checked_at"]
  end

  test "update sets checked_at directly" do
    item = shopping_list_items(:unchecked_milk)
    timestamp = Time.current.iso8601

    patch api_v1_shopping_list_item_url(item),
      params: { checked_at: timestamp },
      headers: @auth_headers,
      as: :json

    assert_response :success
    assert_not_nil response.parsed_body["checked_at"]
  end

  test "update clears checked_at with empty value" do
    item = shopping_list_items(:checked_eggs)

    patch api_v1_shopping_list_item_url(item),
      params: { checked_at: "" },
      headers: @auth_headers,
      as: :json

    assert_response :success
    assert_nil response.parsed_body["checked_at"]
  end

  test "update returns 422 for invalid checked_at format" do
    item = shopping_list_items(:unchecked_milk)

    patch api_v1_shopping_list_item_url(item),
      params: { checked_at: "not-a-timestamp" },
      headers: @auth_headers,
      as: :json

    assert_response :unprocessable_entity
    assert_equal "Invalid checked_at format", response.parsed_body["error"]
    item.reload
    assert_nil item.checked_at
  end

  test "update sets created_at when provided" do
    item = shopping_list_items(:unchecked_milk)
    new_created_at = Time.current.iso8601

    patch api_v1_shopping_list_item_url(item),
      params: { checked: false, created_at: new_created_at },
      headers: @auth_headers,
      as: :json

    assert_response :success
    assert_equal Time.iso8601(new_created_at).to_i, Time.iso8601(response.parsed_body["created_at"]).to_i
  end

  test "update ignores blank created_at" do
    item = shopping_list_items(:unchecked_milk)
    original_created_at = item.created_at

    patch api_v1_shopping_list_item_url(item),
      params: { checked: false, created_at: "" },
      headers: @auth_headers,
      as: :json

    assert_response :success
    item.reload
    assert_equal original_created_at.to_i, item.created_at.to_i
  end

  test "update returns 422 without checked or checked_at" do
    item = shopping_list_items(:unchecked_milk)

    patch api_v1_shopping_list_item_url(item),
      params: { name: "New Name" },
      headers: @auth_headers,
      as: :json

    assert_response :unprocessable_entity
    assert_equal "checked or checked_at is required", response.parsed_body["error"]
  end

  test "update returns 404 for other user's item" do
    other_item = shopping_list_items(:other_user_item)

    patch api_v1_shopping_list_item_url(other_item),
      params: { checked: true },
      headers: @auth_headers,
      as: :json

    assert_response :not_found
  end

  test "update returns 404 for nonexistent item" do
    patch api_v1_shopping_list_item_url(id: 999999),
      params: { checked: true },
      headers: @auth_headers,
      as: :json

    assert_response :not_found
  end

  test "update requires authentication" do
    item = shopping_list_items(:unchecked_milk)

    patch api_v1_shopping_list_item_url(item),
      params: { checked: true },
      as: :json

    assert_response :unauthorized
  end

  # ===================
  # DESTROY
  # ===================

  test "destroy returns 204 on success" do
    item = shopping_list_items(:unchecked_milk)

    delete api_v1_shopping_list_item_url(item), headers: @auth_headers, as: :json

    assert_response :no_content
    assert_nil ShoppingListItem.find_by(id: item.id)
  end

  test "destroy returns 404 for other user's item" do
    other_item = shopping_list_items(:other_user_item)

    delete api_v1_shopping_list_item_url(other_item), headers: @auth_headers, as: :json

    assert_response :not_found
    assert ShoppingListItem.exists?(other_item.id)
  end

  test "destroy returns 404 for nonexistent item" do
    delete api_v1_shopping_list_item_url(id: 999999), headers: @auth_headers, as: :json

    assert_response :not_found
  end

  test "destroy requires authentication" do
    item = shopping_list_items(:unchecked_milk)

    delete api_v1_shopping_list_item_url(item), as: :json

    assert_response :unauthorized
    assert ShoppingListItem.exists?(item.id)
  end

  # ===================
  # COOKBOOK SCOPING
  # ===================

  test "upsert items validates source_recipe_id against cookbook" do
    shared = Cookbook.create!(name: "Shared", personal: false)
    CookbookMembership.create!(cookbook: shared, user: @user, role: :owner)

    # Recipe in user's personal cookbook should not be linkable from shared cookbook
    personal_recipe = recipes(:one)

    post api_v1_shopping_list_items_url,
      params: { item: { client_id: "cross-1", name: "Cross Item", source_recipe_id: personal_recipe.id } },
      headers: @auth_headers.merge("X-Cookbook-Id" => shared.id.to_s),
      as: :json

    assert_response :unprocessable_entity
    assert_equal "Recipe not found", response.parsed_body["error"]
  end

  test "checking off an item from a recipe records cooked_at" do
    recipe = recipes(:one)
    item = cookbooks(:one_personal).shopping_list_items.create!(
      client_id: "cooked-1", name: "Pancetta", source_recipe_id: recipe.id, user: @user
    )

    patch api_v1_shopping_list_item_url(item),
          params: { checked: true }, headers: @auth_headers, as: :json

    assert_response :success
    engagement = RecipeEngagement.find_by(user_id: @user.id, recipe_id: recipe.id)
    assert_not_nil engagement
    assert_not_nil engagement.cooked_at
  end

  test "unchecking an item does not record cooked_at" do
    recipe = recipes(:one)
    item = cookbooks(:one_personal).shopping_list_items.create!(
      client_id: "cooked-2", name: "Pancetta", source_recipe_id: recipe.id,
      user: @user, checked_at: Time.current
    )

    patch api_v1_shopping_list_item_url(item),
          params: { checked: false }, headers: @auth_headers, as: :json

    assert_response :success
    assert_nil RecipeEngagement.find_by(user_id: @user.id, recipe_id: recipe.id)&.cooked_at
  end
end
