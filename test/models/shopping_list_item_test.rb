require "test_helper"

class ShoppingListItemTest < ActiveSupport::TestCase
  test "valid with all attributes" do
    item = ShoppingListItem.new(
      cookbook: cookbooks(:one_personal),
      user: users(:one),
      client_id: "new-client-id",
      name: "Apples"
    )

    assert item.valid?
  end

  test "details is optional and round-trips" do
    item = ShoppingListItem.create!(
      cookbook: cookbooks(:one_personal),
      user: users(:one),
      client_id: "details-client-id",
      name: "Tomato",
      details: "200g, halved"
    )

    assert_equal "200g, halved", item.reload.details

    no_details = ShoppingListItem.create!(
      cookbook: cookbooks(:one_personal),
      user: users(:one),
      client_id: "no-details-client-id",
      name: "Salt"
    )

    assert_nil no_details.reload.details
  end

  test "requires name" do
    item = ShoppingListItem.new(
      cookbook: cookbooks(:one_personal),
      user: users(:one),
      client_id: "new-client-id",
      name: nil
    )

    assert_not item.valid?
    assert_includes item.errors[:name], "can't be blank"
  end

  test "requires client_id" do
    item = ShoppingListItem.new(
      cookbook: cookbooks(:one_personal),
      user: users(:one),
      client_id: nil,
      name: "Apples"
    )

    assert_not item.valid?
    assert_includes item.errors[:client_id], "can't be blank"
  end

  test "client_id must be unique per cookbook" do
    existing = shopping_list_items(:unchecked_milk)

    duplicate = ShoppingListItem.new(
      cookbook: existing.cookbook,
      user: existing.user,
      client_id: existing.client_id,
      name: "Duplicate"
    )

    assert_not duplicate.valid?
    assert_includes duplicate.errors[:client_id], "has already been taken"
  end

  test "same client_id allowed for different cookbooks" do
    item = ShoppingListItem.new(
      cookbook: cookbooks(:two_personal),
      user: users(:two),
      client_id: shopping_list_items(:unchecked_milk).client_id,
      name: "Milk"
    )

    assert item.valid?
  end

  test "belongs to user" do
    item = shopping_list_items(:unchecked_milk)

    assert_equal users(:one), item.user
  end

  test "belongs to cookbook" do
    item = shopping_list_items(:unchecked_milk)

    assert_equal cookbooks(:one_personal), item.cookbook
  end

  test "belongs to source_recipe optionally" do
    with_recipe = shopping_list_items(:unchecked_bread)
    without_recipe = shopping_list_items(:unchecked_milk)

    assert_equal recipes(:one), with_recipe.source_recipe
    assert_nil without_recipe.source_recipe
  end

  test "unchecked scope returns items without checked_at" do
    cookbook_items = cookbooks(:one_personal).shopping_list_items.unchecked

    assert_includes cookbook_items, shopping_list_items(:unchecked_milk)
    assert_includes cookbook_items, shopping_list_items(:unchecked_bread)
    assert_not_includes cookbook_items, shopping_list_items(:checked_eggs)
    assert_not_includes cookbook_items, shopping_list_items(:stale_checked_butter)
  end

  test "checked scope returns items with checked_at" do
    cookbook_items = cookbooks(:one_personal).shopping_list_items.checked

    assert_includes cookbook_items, shopping_list_items(:checked_eggs)
    assert_includes cookbook_items, shopping_list_items(:stale_checked_butter)
    assert_not_includes cookbook_items, shopping_list_items(:unchecked_milk)
  end

  test "stale_checked scope returns items checked more than 1 hour ago" do
    cookbook_items = cookbooks(:one_personal).shopping_list_items.stale_checked

    assert_includes cookbook_items, shopping_list_items(:stale_checked_butter)
    assert_not_includes cookbook_items, shopping_list_items(:checked_eggs)
    assert_not_includes cookbook_items, shopping_list_items(:unchecked_milk)
  end

  test "old_for_recipe_addition includes only items created more than 36 hours ago" do
    travel_to Time.zone.local(2026, 9, 15, 10) do
      cookbook = cookbooks(:one_personal)
      old = cookbook.shopping_list_items.create!(
        user: users(:one), client_id: "old-for-recipe", name: "Old", created_at: 36.hours.ago - 1.second
      )
      boundary = cookbook.shopping_list_items.create!(
        user: users(:one), client_id: "boundary-for-recipe", name: "Boundary", created_at: 36.hours.ago
      )

      matching = cookbook.shopping_list_items.old_for_recipe_addition

      assert_includes matching, old
      assert_not_includes matching, boundary
    end
  end

  test "cleanup_stale_checked_for destroys stale checked items" do
    cookbook = cookbooks(:one_personal)
    stale = shopping_list_items(:stale_checked_butter)
    recent = shopping_list_items(:checked_eggs)

    ShoppingListItem.cleanup_stale_checked_for(cookbook)

    assert_raises(ActiveRecord::RecordNotFound) { stale.reload }
    assert_nothing_raised { recent.reload }
  end

  test "cleanup_stale_checked_for does not affect other cookbooks" do
    other_item = shopping_list_items(:other_user_item)

    ShoppingListItem.cleanup_stale_checked_for(cookbooks(:one_personal))

    assert_nothing_raised { other_item.reload }
  end
end
