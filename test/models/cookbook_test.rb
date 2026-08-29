require "test_helper"

class CookbookTest < ActiveSupport::TestCase
  test "requires a name" do
    cookbook = Cookbook.new(name: nil)

    assert_not cookbook.valid?
    assert_includes cookbook.errors[:name], "can't be blank"
  end

  test "valid with name" do
    cookbook = Cookbook.new(name: "Family Recipes")

    assert cookbook.valid?
  end

  test "personal scope returns personal cookbooks" do
    personal = Cookbook.personal

    assert_includes personal, cookbooks(:one_personal)
    assert personal.all?(&:personal?)
  end

  test "shared scope returns non-personal cookbooks" do
    shared_cookbook = Cookbook.create!(name: "Shared", personal: false)
    CookbookMembership.create!(cookbook: shared_cookbook, user: users(:one), role: :owner)

    shared = Cookbook.shared

    assert_includes shared, shared_cookbook
    assert shared.none?(&:personal?)
  end

  test "owner returns the owner user" do
    cookbook = cookbooks(:one_personal)

    assert_equal users(:one), cookbook.owner
  end

  test "owner? returns true for owner" do
    cookbook = cookbooks(:one_personal)

    assert cookbook.owner?(users(:one))
  end

  test "owner? returns false for non-owner" do
    cookbook = cookbooks(:one_personal)

    assert_not cookbook.owner?(users(:two))
  end

  test "destroying cookbook cascades to recipes" do
    cookbook = cookbooks(:one_personal)
    recipe_ids = cookbook.recipes.pluck(:id)
    assert_not_empty recipe_ids

    cookbook.destroy!

    recipe_ids.each do |id|
      assert_nil Recipe.find_by(id: id)
    end
  end

  test "destroying cookbook with planned recipes succeeds" do
    cookbook = cookbooks(:one_personal)
    recipe = cookbook.recipes.first
    meal_plan = cookbook.meal_plans.create!(date: 1.year.from_now.to_date)
    meal_plan.entries.create!(recipe: recipe, proposed_by_user: users(:one))

    assert_nothing_raised do
      cookbook.destroy!
    end

    assert_not MealPlan.exists?(meal_plan.id)
    assert_not MealPlanEntry.exists?(meal_plan_id: meal_plan.id)
    assert_not Recipe.exists?(recipe.id)
  end

  test "destroying cookbook cascades to shopping list items" do
    cookbook = cookbooks(:one_personal)
    item_ids = cookbook.shopping_list_items.pluck(:id)
    assert_not_empty item_ids

    cookbook.destroy!

    item_ids.each do |id|
      assert_nil ShoppingListItem.find_by(id: id)
    end
  end

  test "destroying cookbook cascades to memberships" do
    cookbook = cookbooks(:one_personal)
    membership_ids = cookbook.cookbook_memberships.pluck(:id)
    assert_not_empty membership_ids

    cookbook.destroy!

    membership_ids.each do |id|
      assert_nil CookbookMembership.find_by(id: id)
    end
  end

  test "destroying cookbook with notification deliveries nullifies the delivery's cookbook_id" do
    cookbook = cookbooks(:one_personal)
    delivery = NotificationDelivery.create!(
      user: users(:one), campaign: "resurface", cookbook: cookbook, sent_at: 1.hour.ago
    )

    cookbook.destroy!

    # Cookbook should be destroyed
    assert_nil Cookbook.find_by(id: cookbook.id)

    # Delivery should still exist but with null cookbook_id
    assert_equal delivery.id, NotificationDelivery.find(delivery.id).id
    assert_nil NotificationDelivery.find(delivery.id).cookbook_id
  end
end
