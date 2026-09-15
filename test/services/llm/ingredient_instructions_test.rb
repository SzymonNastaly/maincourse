require "test_helper"

class Llm::IngredientInstructionsTest < ActiveSupport::TestCase
  test "defines stable multilingual normalization instructions" do
    prompt = Llm::IngredientInstructions.prompt

    assert_includes prompt, "canonical_name"
    assert_includes prompt, "lowercase English"
    assert_includes prompt, "canonical_unit"
    assert_includes prompt, "category"
    assert_includes prompt, "olive oil"
    assert_includes prompt, "Do not translate `name`"
  end

  test "normalizes known values and rejects unsupported units" do
    assert_equal "dairy_eggs", Llm::IngredientInstructions.normalize_category(" Dairy_Eggs ")
    assert_equal "other", Llm::IngredientInstructions.normalize_category("unknown aisle")
    assert_equal "tablespoon", Llm::IngredientInstructions.normalize_unit(" Tablespoon ")
    assert_nil Llm::IngredientInstructions.normalize_unit("splash")
    assert_nil Llm::IngredientInstructions.normalize_unit(nil)
  end
end
