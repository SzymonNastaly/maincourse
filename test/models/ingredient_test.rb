require "test_helper"

class IngredientTest < ActiveSupport::TestCase
  setup do
    @recipe = recipes(:one)
  end

  test "is invalid without raw" do
    ingredient = Ingredient.new(recipe: @recipe, name: "flour")
    assert_not ingredient.valid?
    assert_includes ingredient.errors[:raw], "can't be blank"
  end

  test "is valid without name (parser fills it in later)" do
    ingredient = Ingredient.new(recipe: @recipe, raw: "1 cup flour")
    assert ingredient.valid?
  end

  test "is invalid without recipe" do
    ingredient = Ingredient.new(raw: "salt", name: "salt")
    assert_not ingredient.valid?
  end

  test "parsed? is true when amount present" do
    ingredient = Ingredient.new(raw: "1 cup flour", name: "flour", amount: 1)
    assert ingredient.parsed?
  end

  test "parsed? is true when unit present" do
    ingredient = Ingredient.new(raw: "a pinch of salt", name: "salt", unit: "pinch")
    assert ingredient.parsed?
  end

  test "parsed? is false when neither amount nor unit set" do
    ingredient = Ingredient.new(raw: "salt", name: "salt")
    assert_not ingredient.parsed?
  end

  test "needs enrichment until processed with the current contract version" do
    ingredient = Ingredient.new(
      raw: "Salz nach Geschmack",
      name: "Salz",
      canonical_name: "salt",
      category: "oils_spices_condiments"
    )

    assert ingredient.needs_enrichment?

    ingredient.enrichment_version = Llm::IngredientInstructions::VERSION

    assert_not ingredient.needs_enrichment?
  end

  test "quantity fields do not imply enrichment completion" do
    ingredient = Ingredient.new(raw: "2 EL Olivenöl", name: "Olivenöl", amount: 2, unit: "EL")

    assert ingredient.parsed?
    assert ingredient.needs_enrichment?
  end

  test "current version without required canonical metadata still needs enrichment" do
    ingredient = Ingredient.new(
      raw: "salt",
      category: "oils_spices_condiments",
      enrichment_version: Llm::IngredientInstructions::VERSION
    )

    assert ingredient.needs_enrichment?
  end

  test "validates normalized category and unit vocabularies" do
    ingredient = Ingredient.new(
      recipe: @recipe,
      raw: "2 splashes oil",
      canonical_name: "oil",
      canonical_unit: "splash",
      category: "somewhere"
    )

    assert_not ingredient.valid?
    assert_includes ingredient.errors[:canonical_unit], "is not included in the list"
    assert_includes ingredient.errors[:category], "is not included in the list"
  end
end
