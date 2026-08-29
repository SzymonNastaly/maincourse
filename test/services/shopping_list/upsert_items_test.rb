require "test_helper"

class ShoppingList::UpsertItemsTest < ActiveSupport::TestCase
  setup do
    @user = users(:one)
    @cookbook = cookbooks(:one_personal)
  end

  test "returns error when items are empty" do
    result = ShoppingList::UpsertItems.new(user: @user, cookbook: @cookbook, items: []).call

    assert_not result.success?
    assert_equal [ { client_id: nil, error: "No items provided" } ], result.errors
    assert_equal [], result.items
  end

  test "creates items with valid attributes" do
    recipe = recipes(:one)

    result = ShoppingList::UpsertItems.new(
      user: @user,
      cookbook: @cookbook,
      items: [ { client_id: "client-1", name: "Milk", source_recipe_id: recipe.id } ]
    ).call

    assert result.success?
    assert_equal 1, result.items.size
    assert_equal "Milk", result.items.first.name
    assert_equal "client-1", result.items.first.client_id
    assert_equal recipe.id, result.items.first.source_recipe_id
  end

  test "returns error when client_id or name is missing" do
    result = ShoppingList::UpsertItems.new(
      user: @user,
      cookbook: @cookbook,
      items: [ { client_id: "", name: "" } ]
    ).call

    assert_not result.success?
    assert_equal 1, result.errors.size
    assert_equal "client_id and name are required", result.errors.first[:error]
  end

  test "returns error when source_recipe_id does not belong to cookbook" do
    other_recipe = recipes(:two)

    result = ShoppingList::UpsertItems.new(
      user: @user,
      cookbook: @cookbook,
      items: [ { client_id: "client-2", name: "Bread", source_recipe_id: other_recipe.id } ]
    ).call

    assert_not result.success?
    assert_equal "Recipe not found", result.errors.first[:error]
  end

  test "creates item without source_recipe_id" do
    result = ShoppingList::UpsertItems.new(
      user: @user,
      cookbook: @cookbook,
      items: [ { client_id: "no-recipe", name: "Butter" } ]
    ).call

    assert result.success?
    assert_nil result.items.first.source_recipe_id
  end

  test "rolls back all items when any item is invalid" do
    assert_no_difference "ShoppingListItem.count" do
      result = ShoppingList::UpsertItems.new(
        user: @user,
        cookbook: @cookbook,
        items: [
          { client_id: "valid-1", name: "Flour" },
          { client_id: "", name: "" },
          { client_id: "valid-2", name: "Sugar" }
        ]
      ).call

      assert_not result.success?
      assert_equal [], result.items
      assert_equal 1, result.errors.size
    end
  end

  test "upserts by client_id and updates checked_at" do
    existing = ShoppingListItem.create!(
      cookbook: @cookbook,
      user: @user,
      client_id: "client-3",
      name: "Old Name",
      checked_at: nil
    )

    checked_time = Time.current
    result = ShoppingList::UpsertItems.new(
      user: @user,
      cookbook: @cookbook,
      items: [ { client_id: "client-3", name: "New Name", checked_at: checked_time } ]
    ).call

    assert result.success?
    assert_equal 1, result.items.size
    assert_equal existing.id, result.items.first.id
    assert_equal "New Name", result.items.first.name
    assert_equal checked_time.to_i, result.items.first.checked_at.to_i
  end

  test "retries on concurrent duplicate client_id" do
    existing = shopping_list_items(:unchecked_milk)

    raised = false

    service = ShoppingList::UpsertItems.new(
      user: @user,
      cookbook: @cookbook,
      items: [ { client_id: existing.client_id, name: "Updated Milk" } ]
    )

    original_save = ShoppingListItem.instance_method(:save)

    ShoppingListItem.define_method(:save) do |*args, **kwargs|
      if !raised && client_id == existing.client_id
        raised = true
        raise ActiveRecord::RecordNotUnique, "duplicate"
      end
      original_save.bind(self).call(*args, **kwargs)
    end

    result = service.call

    assert result.success?
    assert_equal "Updated Milk", result.items.first.name
    assert_equal existing.id, result.items.first.id
  ensure
    ShoppingListItem.define_method(:save, original_save)
  end

  test "returns error when recipe is deleted between validation and save" do
    recipe = @cookbook.recipes.create!(name: "Temp Recipe", user: @user)

    original_save = ShoppingListItem.instance_method(:save)
    raised = false

    ShoppingListItem.define_method(:save) do |*args, **kwargs|
      if !raised && source_recipe_id.present?
        raised = true
        raise ActiveRecord::InvalidForeignKey, "FK violation"
      end
      original_save.bind(self).call(*args, **kwargs)
    end

    result = ShoppingList::UpsertItems.new(
      user: @user,
      cookbook: @cookbook,
      items: [ { client_id: "fk-race", name: "Ingredient", source_recipe_id: recipe.id } ]
    ).call

    assert_not result.success?
    assert_equal "Recipe not found", result.errors.first[:error]
  ensure
    ShoppingListItem.define_method(:save, original_save)
  end

  test "creates an item with details" do
    result = ShoppingList::UpsertItems.new(
      user: @user,
      cookbook: @cookbook,
      items: [ { client_id: "details-1", name: "Tomato", details: "200g, halved" } ]
    ).call

    assert result.success?
    assert_equal "200g, halved", result.items.first.details
  end

  test "upsert replaces details with new value" do
    ShoppingListItem.create!(
      cookbook: @cookbook,
      user: @user,
      client_id: "details-replace",
      name: "Tomato",
      details: "old"
    )

    result = ShoppingList::UpsertItems.new(
      user: @user,
      cookbook: @cookbook,
      items: [ { client_id: "details-replace", name: "Tomato", details: "new" } ]
    ).call

    assert result.success?
    assert_equal "new", result.items.first.details
  end

  test "upsert clears details when client sends nil" do
    ShoppingListItem.create!(
      cookbook: @cookbook,
      user: @user,
      client_id: "details-clear",
      name: "Tomato",
      details: "200g"
    )

    result = ShoppingList::UpsertItems.new(
      user: @user,
      cookbook: @cookbook,
      items: [ { client_id: "details-clear", name: "Tomato", details: nil } ]
    ).call

    assert result.success?
    assert_nil result.items.first.details
  end

  test "blank details normalizes to nil" do
    result = ShoppingList::UpsertItems.new(
      user: @user,
      cookbook: @cookbook,
      items: [ { client_id: "details-blank", name: "Tomato", details: "   " } ]
    ).call

    assert result.success?
    assert_nil result.items.first.details
  end

  test "unchecks item by upserting with blank checked_at" do
    existing = ShoppingListItem.create!(
      cookbook: @cookbook,
      user: @user,
      client_id: "client-uncheck",
      name: "Checked Item",
      checked_at: Time.current
    )

    result = ShoppingList::UpsertItems.new(
      user: @user,
      cookbook: @cookbook,
      items: [ { client_id: "client-uncheck", name: "Checked Item", checked_at: "" } ]
    ).call

    assert result.success?
    assert_nil result.items.first.checked_at
    assert_equal existing.id, result.items.first.id
  end

  test "adding an item from a recipe records added_to_list_at" do
    recipe = recipes(:one)

    ShoppingList::UpsertItems.new(
      user: @user,
      cookbook: @cookbook,
      items: [ { client_id: "eng-1", name: "Pancetta", source_recipe_id: recipe.id } ]
    ).call

    engagement = RecipeEngagement.find_by(user_id: @user.id, recipe_id: recipe.id)
    assert_not_nil engagement
    assert_not_nil engagement.added_to_list_at
  end

  test "adding an item without a source recipe records nothing" do
    assert_no_difference "RecipeEngagement.count" do
      ShoppingList::UpsertItems.new(
        user: @user,
        cookbook: @cookbook,
        items: [ { client_id: "eng-2", name: "Salt" } ]
      ).call
    end
  end

  test "item upsert succeeds even if engagement recording fails" do
    recipe = recipes(:one)

    RecipeEngagement.stub :mark_added_to_list!, proc { raise StandardError, "Engagement failed" } do
      result = ShoppingList::UpsertItems.new(
        user: @user,
        cookbook: @cookbook,
        items: [ { client_id: "resilient-1", name: "Pancetta", source_recipe_id: recipe.id } ]
      ).call

      assert result.success?
      assert_equal 1, result.items.size
      assert_equal "Pancetta", result.items.first.name
      assert_equal recipe.id, result.items.first.source_recipe_id
    end
  end
end
