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
    assert_equal 14, cookbook.recipes.count
    assert_equal blob_id, recipe.reload.cover_image.blob.id
    assert_includes recipe.notes, "one-pan lunch"
    assert_equal 8, cookbook.shopping_list_items.count
    assert_equal 0, cookbook.shopping_list_items.checked.count
    assert_equal second.fetch(:recipes).values, cookbook.recipes.order(updated_at: :desc).pluck(:id)
    assert_equal existing_recipe_ids.sort, existing_user.recipes.pluck(:id).sort
    assert_equal [ "Breakfast", "Lunch", "Dinner", "Dessert", "Salads" ].sort,
      cookbook.recipes.flat_map { |r| r.tags.pluck(:name) }.uniq.intersection(%w[Breakfast Lunch Dinner Dessert Salads]).sort
  end

  test "seeds a translated showcase with the same recipes and shopping list" do
    english = Screenshots::Seed.call
    german = Screenshots::Seed.call(locale: "de-DE")
    cookbook = Cookbook.find(german.fetch(:cookbook_id))
    recipe = Recipe.find(german.fetch(:recipes).fetch("tomato-orzo"))

    assert_equal english.fetch(:recipes).keys, german.fetch(:recipes).keys
    assert_equal 14, cookbook.recipes.count
    assert_equal "Cremiger Orzo mit Tomaten und Basilikum", recipe.name
    assert recipe.cover_image.attached?
    assert_includes cookbook.recipes.flat_map { |r| r.tags.pluck(:name) }, "Frühstück"
    item = cookbook.shopping_list_items.find_by!(name: "Kirschtomaten")
    assert_equal [ "400 g", "produce", "cherry tomato" ], [ item.details, item.category, item.canonical_name ]
    assert_raises(ArgumentError) { Screenshots::Seed.call(locale: "fr-FR") }
  end

  test "translated fixtures keep the English quantities, order and shopping aisles" do
    english = Screenshots::Seed.fixture("en-US")
    comparable = ->(fixture) do
      {
        recipes: fixture.fetch("recipes").map do |recipe|
          recipe.slice("slug", "prep_time", "cook_time", "servings", "favorite").merge(
            "tags" => recipe.fetch("tags").size,
            "instructions" => recipe.fetch("instructions").size,
            "amounts" => recipe.fetch("ingredients").map { |ingredient| ingredient["amount"] }
          )
        end,
        shopping: fixture.fetch("shopping_list").map { |item| item.slice("category", "canonical_name") }
      }
    end

    (Screenshots::Seed::LOCALES - [ "en-US" ]).each do |locale|
      assert_equal comparable.(english), comparable.(Screenshots::Seed.fixture(locale)), locale
    end
  end

  test "refuses to seed production or ordinary development databases" do
    %w[production development].each do |environment|
      Rails.stub(:env, ActiveSupport::StringInquirer.new(environment)) do
        assert_raises(RuntimeError) { Screenshots::Seed.call }
      end
    end
  end
end
