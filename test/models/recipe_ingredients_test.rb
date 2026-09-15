require "test_helper"

class RecipeIngredientsTest < ActiveSupport::TestCase
  test "persists enrichment returned by recipe extraction" do
    recipe = recipes(:one)

    recipe.replace_ingredients_from_hashes([ {
      raw: "2 EL Olivenöl",
      name: "Olivenöl",
      amount: 2,
      unit: "el",
      canonical_name: "olive oil",
      canonical_unit: "tablespoon",
      category: "oils_spices_condiments",
      enrichment_version: Llm::IngredientInstructions::VERSION
    } ])

    ingredient = recipe.ingredients.sole
    assert_equal "olive oil", ingredient.canonical_name
    assert_equal "tablespoon", ingredient.canonical_unit
    assert_equal "oils_spices_condiments", ingredient.category
    assert_equal Llm::IngredientInstructions::VERSION, ingredient.enrichment_version
  end
end
