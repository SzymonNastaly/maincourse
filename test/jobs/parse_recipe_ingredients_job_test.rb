require "test_helper"

class ParseRecipeIngredientsJobTest < ActiveSupport::TestCase
  setup do
    @recipe = recipes(:one)
    @recipe.ingredients.destroy_all
  end

  test "updates unparsed ingredients with structured data" do
    @recipe.ingredients.create!(position: 0, raw: "2 cups flour", name: "2 cups flour")
    @recipe.ingredients.create!(position: 1, raw: "1 tsp salt", name: "1 tsp salt")

    stub_ingredient_parse([
      {
        "raw" => "2 cups flour", "name" => "flour", "amount" => 2, "unit" => "cup",
        "canonical_name" => "flour", "canonical_unit" => "cup", "category" => "pantry"
      },
      {
        "raw" => "1 tsp salt", "name" => "salt", "amount" => 1, "unit" => "tsp",
        "canonical_name" => "salt", "canonical_unit" => "teaspoon", "category" => "oils_spices_condiments"
      }
    ])

    ParseRecipeIngredientsJob.perform_now(@recipe.id)

    rows = @recipe.reload.ingredients
    assert_equal "flour", rows[0].name
    assert_equal 2.0, rows[0].amount
    assert_equal "cup", rows[0].unit
    assert_equal "flour", rows[0].canonical_name
    assert_equal "cup", rows[0].canonical_unit
    assert_equal "pantry", rows[0].category
    assert_equal Llm::IngredientInstructions::VERSION, rows[0].enrichment_version
    assert_equal "salt", rows[1].name
  end

  test "enriches legacy structured ingredients" do
    ingredient = @recipe.ingredients.create!(position: 0, raw: "1 cup flour", name: "flour", amount: 1, unit: "cup")
    stub_ingredient_parse([
      {
        "raw" => "1 cup flour", "name" => "flour", "amount" => 1, "unit" => "cup",
        "canonical_name" => "flour", "canonical_unit" => "cup", "category" => "pantry"
      }
    ])

    ParseRecipeIngredientsJob.perform_now(@recipe.id)

    assert_equal Llm::IngredientInstructions::VERSION, ingredient.reload.enrichment_version
  end

  test "skips ingredients already enriched with the current version" do
    @recipe.ingredients.create!(
      position: 0,
      raw: "salt to taste",
      name: "salt",
      canonical_name: "salt",
      category: "oils_spices_condiments",
      enrichment_version: Llm::IngredientInstructions::VERSION
    )

    ParseRecipeIngredientsJob.perform_now(@recipe.id)

    assert_not_requested(:post, LlmStubHelper::OPENROUTER_ENDPOINT)
  end

  test "leaves a parser fallback eligible for retry" do
    ingredient = @recipe.ingredients.create!(position: 0, raw: "salt to taste")
    stub_request(:post, LlmStubHelper::OPENROUTER_ENDPOINT)
      .to_raise(Faraday::TimeoutError.new("timeout"))

    ParseRecipeIngredientsJob.perform_now(@recipe.id)

    assert ingredient.reload.needs_enrichment?
    assert_equal "salt to taste", ingredient.name
  end

  test "no-ops when recipe is missing" do
    assert_nothing_raised do
      ParseRecipeIngredientsJob.perform_now(0)
    end
    assert_not_requested(:post, LlmStubHelper::OPENROUTER_ENDPOINT)
  end

  test "no-ops when recipe has no ingredients" do
    assert_nothing_raised do
      ParseRecipeIngredientsJob.perform_now(@recipe.id)
    end
    assert_not_requested(:post, LlmStubHelper::OPENROUTER_ENDPOINT)
  end

  private

  def stub_ingredient_parse(parsed)
    content = { "ingredients" => parsed }
    stub_openrouter_api(response_body: build_openrouter_response(content))
  end
end
