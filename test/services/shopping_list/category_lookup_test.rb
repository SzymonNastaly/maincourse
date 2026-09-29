require "test_helper"

class ShoppingList::CategoryLookupTest < ActiveSupport::TestCase
  setup do
    Ingredient.delete_all
    ShoppingListItem.delete_all
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

  test "learns from confirmed shopping list items" do
    confirmed_item("Spülmittel", "dish soap", "household")
    item("Spülmittel", category: "pantry", enrichment_version: nil)

    match = ShoppingList::CategoryLookup.call("Spülmittel")
    assert_equal "household", match.category
    assert_equal "dish soap", match.canonical_name
  end

  test "adds up recipe ingredients and shopping list items" do
    enriched("Chili", "chili", "produce", position: 0)
    2.times { confirmed_item("chili", "chili powder", "oils_spices_condiments") }

    assert_equal "oils_spices_condiments", ShoppingList::CategoryLookup.call("Chili").category
  end

  private

  def confirmed_item(name, canonical_name, category)
    item(name, canonical_name:, category:, enrichment_version: @version)
  end

  # Writes the columns directly: saving would recategorize the item.
  def item(name, **columns)
    ShoppingListItem.create!(cookbook: cookbooks(:one_personal), user: users(:one), client_id: SecureRandom.uuid, name:)
      .tap { |item| item.update_columns(**columns) }
  end


  def enriched(name, canonical_name, category, position:)
    recipes(:one).ingredients.create!(position:, raw: name, name:, canonical_name:, category:, enrichment_version: @version)
  end
end
