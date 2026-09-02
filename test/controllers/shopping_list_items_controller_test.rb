require "test_helper"

class ShoppingListItemsControllerTest < ActionDispatch::IntegrationTest
  setup do
    @user = users(:one)
    @cookbook = cookbooks(:one_personal)
    sign_in_as @user
  end

  test "requires authentication" do
    sign_out
    get shopping_list_items_path
    assert_redirected_to new_session_path
  end

  test "index splits to buy from already got" do
    get shopping_list_items_path

    assert_response :success
    # Milk and Bread are unchecked; Eggs was ticked ten minutes ago.
    assert_match "Milk", response.body
    assert_match "Bread", response.body
    assert_match "Eggs", response.body
    assert_select "[data-testid=shopping-item]", 3
  end

  test "index drops checked items older than an hour" do
    stale = @cookbook.shopping_list_items.create!(
      name: "Old parsley", client_id: SecureRandom.uuid, checked_at: 2.hours.ago
    )

    get shopping_list_items_path

    assert_not ShoppingListItem.exists?(stale.id)
  end

  test "index only shows the active cookbook" do
    get shopping_list_items_path

    assert_no_match shopping_list_items(:other_user_item).name, response.body
  end

  test "create adds an item with a generated client id" do
    assert_difference "ShoppingListItem.count", 1 do
      post shopping_list_items_path, params: { shopping_list_item: { name: "  Flaky sea salt " } }
    end

    item = ShoppingListItem.order(:created_at).last
    assert_equal "Flaky sea salt", item.name
    assert item.client_id.present?
    assert_equal @user, item.user
    assert_redirected_to shopping_list_items_path
  end

  test "create ignores an empty name" do
    assert_no_difference "ShoppingListItem.count" do
      post shopping_list_items_path, params: { shopping_list_item: { name: "   " } }
    end

    assert_equal "Type something to add.", flash[:alert]
  end

  test "toggle ticks and unticks" do
    item = shopping_list_items(:unchecked_milk)

    patch toggle_shopping_list_item_path(item)
    assert item.reload.checked_at.present?

    patch toggle_shopping_list_item_path(item)
    assert_nil item.reload.checked_at
  end

  test "toggle cannot reach another cookbook's item" do
    other = cookbooks(:two_personal).shopping_list_items.create!(
      name: "Not yours", client_id: SecureRandom.uuid
    )

    patch toggle_shopping_list_item_path(other)

    assert_nil other.reload.checked_at
    assert_redirected_to shopping_list_items_path
  end

  test "destroy removes a single item" do
    assert_difference "ShoppingListItem.count", -1 do
      delete shopping_list_item_path(shopping_list_items(:unchecked_milk))
    end
  end

  test "destroy_all clears the whole list including ticked items" do
    @cookbook.shopping_list_items.create!(
      name: "Butter", client_id: SecureRandom.uuid, checked_at: 1.minute.ago
    )

    delete destroy_all_shopping_list_items_path

    assert_equal 0, @cookbook.shopping_list_items.count
    assert_redirected_to shopping_list_items_path
  end

  test "destroy_all leaves other cookbooks alone" do
    other = cookbooks(:two_personal).shopping_list_items.create!(
      name: "Theirs", client_id: SecureRandom.uuid
    )

    delete destroy_all_shopping_list_items_path

    assert ShoppingListItem.exists?(other.id)
  end
end
