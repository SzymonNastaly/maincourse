require "test_helper"
require Rails.root.join("lib/screenshots/seed")

class ScreenshotsSeedTest < ActiveSupport::TestCase
  test "reset restores showcase state without duplicating recipes or touching another account" do
    existing_user = users(:one)
    existing_recipe_ids = existing_user.recipes.pluck(:id)
    first = Screenshots::Seed.call
    cookbook = Cookbook.find(first.fetch(:cookbook_id))
    recipe = Recipe.find(first.fetch(:recipes).fetch("tomato-orzo"))
    blob_id = recipe.cover_image.blob.id
    assert recipe.cover_image.download.start_with?("\x89PNG".b)
    recipe.update!(notes: "Changed during a screenshot run")
    cookbook.shopping_list_items.first.update!(name: "Changed item", checked_at: Time.current)

    second = Screenshots::Seed.call

    assert_equal first, second
    assert_equal 6, cookbook.recipes.count
    assert_equal blob_id, recipe.reload.cover_image.blob.id
    assert_includes recipe.notes, "one-pan lunch"
    assert_equal 8, cookbook.shopping_list_items.count
    assert_equal 0, cookbook.shopping_list_items.checked.count
    assert_equal second.fetch(:recipes).values, cookbook.recipes.order(updated_at: :desc).pluck(:id)
    assert_equal existing_recipe_ids.sort, existing_user.recipes.pluck(:id).sort
    assert_equal [ "Breakfast", "Lunch", "Dinner", "Dessert", "Salads" ].sort,
      cookbook.recipes.flat_map { |r| r.tags.pluck(:name) }.uniq.intersection(%w[Breakfast Lunch Dinner Dessert Salads]).sort
  end

  test "refuses to seed production or ordinary development databases" do
    %w[production development].each do |environment|
      Rails.stub(:env, ActiveSupport::StringInquirer.new(environment)) do
        assert_raises(RuntimeError) { Screenshots::Seed.call }
      end
    end
  end
end
