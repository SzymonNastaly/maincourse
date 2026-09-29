require "test_helper"

class ShoppingList::CategoryLookupTest < ActiveSupport::TestCase
  setup do
    Ingredient.delete_all
    @version = Llm::IngredientInstructions::VERSION
  end

  test "returns the most common enriched match, case-insensitively" do
    2.times { |i| enriched("Milch", "milk", "dairy_eggs", position: i) }
    enriched("milch", "milk", "beverages", position: 2)

    match = ShoppingList::CategoryLookup.call("MILCH")
    assert_equal "dairy_eggs", match.category
    assert_equal "milk", match.canonical_name
  end

  test "ignores unenriched and stale rows" do
    recipes(:one).ingredients.create!(position: 0, raw: "Milch", name: "Milch", category: "dairy_eggs", canonical_name: "milk", enrichment_version: @version - 1)
    recipes(:one).ingredients.create!(position: 1, raw: "Milch", name: "Milch")

    assert_nil ShoppingList::CategoryLookup.call("Milch")
    assert_nil ShoppingList::CategoryLookup.call(" ")
  end

  private

  def enriched(name, canonical_name, category, position:)
    recipes(:one).ingredients.create!(position:, raw: name, name:, canonical_name:, category:, enrichment_version: @version)
  end
end
