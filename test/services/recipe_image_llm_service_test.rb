require "test_helper"

class RecipeImageLlmServiceTest < ActiveSupport::TestCase
  setup do
    @image_path = Rails.root.join("test/fixtures/files/test_image.png").to_s
  end

  test "extracts recipe from image" do
    stub_llm_response(
      name: "Photo Recipe",
      ingredients: [ "2 eggs" ],
      instructions: [ "Cook" ],
      prep_time: 5,
      cook_time: 10,
      servings: 2,
      notes: "Looks good"
    )

    result = RecipeImageLlmService.new(@image_path).extract

    assert result.success?
    assert_equal "Photo Recipe", result.recipe_attributes[:name]
    assert_equal [ "2 eggs" ], result.recipe_attributes[:ingredients].map { |i| i[:raw] }
    assert_equal [ "Cook" ], result.recipe_attributes[:instructions]
    assert_equal 5, result.recipe_attributes[:prep_time]
    assert_equal 10, result.recipe_attributes[:cook_time]
    assert_equal 2, result.recipe_attributes[:servings]
    assert_equal "Looks good", result.recipe_attributes[:notes]
  end

  test "routes the configured model exclusively through EU Vertex" do
    stub_llm_response(name: "Photo Recipe", ingredients: [], instructions: [])

    RecipeImageLlmService.new(@image_path).extract

    assert_requested(:post, LlmStubHelper::OPENROUTER_ENDPOINT) do |req|
      body = JSON.parse(req.body)
      body["model"] == "google/gemini-3.5-flash-lite" &&
        body.dig("provider", "only") == [ "google-vertex/eu" ]
    end
  end

  test "uses the shared ingredient enrichment instructions" do
    stub_llm_response(name: "Photo Recipe", ingredients: [], instructions: [])

    RecipeImageLlmService.new(@image_path).extract

    assert_requested(:post, LlmStubHelper::OPENROUTER_ENDPOINT) do |req|
      content = JSON.parse(req.body).dig("messages", -1, "content")
      content.any? { |part| part["type"] == "text" && part["text"].include?(Llm::IngredientInstructions.prompt) }
    end
  end

  test "returns failure when image path is blank" do
    result = RecipeImageLlmService.new("").extract

    assert_not result.success?
    assert_equal :extraction_failed, result.error_code
    assert_match(/no image provided/i, result.error)
  end

  test "returns failure when LLM returns empty name" do
    stub_llm_response(name: "", ingredients: [], instructions: [])

    result = RecipeImageLlmService.new(@image_path).extract

    assert_not result.success?
    assert_equal :not_a_recipe, result.error_code
    assert_equal "Could not identify recipe name", result.error
  end

  test "handles LLM API timeout" do
    stub_request(:post, LlmStubHelper::OPENROUTER_ENDPOINT)
      .to_raise(Faraday::TimeoutError.new("execution expired"))

    result = RecipeImageLlmService.new(@image_path).extract

    assert_not result.success?
    assert_equal :llm_timeout, result.error_code
    assert_match(/timed out/i, result.error)
  end

  test "handles RubyLLM API error" do
    stub_request(:post, LlmStubHelper::OPENROUTER_ENDPOINT)
      .to_return(status: 401, body: { error: { message: "Invalid API key" } }.to_json)

    result = RecipeImageLlmService.new(@image_path).extract

    assert_not result.success?
    assert_equal :llm_error, result.error_code
    assert_match(/api error/i, result.error)
  end
end
