require "test_helper"

class RecipeLlmServiceTest < ActiveSupport::TestCase
  # Successful extraction

  test "extracts recipe from text with webpage prompt type" do
    text = "Chocolate Cake Recipe. Ingredients: 2 cups flour, 1 cup sugar. Instructions: Mix and bake."
    stub_llm_response(
      name: "Chocolate Cake",
      ingredients: [ "2 cups flour", "1 cup sugar" ],
      instructions: [ "Mix ingredients", "Bake at 350°F for 30 minutes" ],
      prep_time: 15,
      cook_time: 30,
      servings: 8,
      notes: "A delicious family recipe"
    )

    result = RecipeLlmService.new(text, prompt_type: :webpage, source_url: "https://example.com/recipe").extract

    assert result.success?
    assert_equal "Chocolate Cake", result.recipe_attributes[:name]
    assert_equal [ "2 cups flour", "1 cup sugar" ], result.recipe_attributes[:ingredients].map { |i| i[:raw] }
    assert_equal [ "Mix ingredients", "Bake at 350°F for 30 minutes" ], result.recipe_attributes[:instructions]
    assert_equal 15, result.recipe_attributes[:prep_time]
    assert_equal 30, result.recipe_attributes[:cook_time]
    assert_equal 8, result.recipe_attributes[:servings]
    assert_equal "A delicious family recipe", result.recipe_attributes[:notes]
    assert_equal "https://example.com/recipe", result.recipe_attributes[:source_url]
  end

  test "extracts recipe from raw text with raw_text prompt type" do
    text = <<~TEXT
      Grandma's Apple Pie

      Ingredients:
      - 6 apples, sliced
      - 1 cup sugar
      - 2 pie crusts

      Instructions:
      1. Preheat oven to 375°F
      2. Mix apples with sugar
      3. Fill pie crust and cover
      4. Bake for 45 minutes
    TEXT

    stub_llm_response(
      name: "Grandma's Apple Pie",
      ingredients: [ "6 apples, sliced", "1 cup sugar", "2 pie crusts" ],
      instructions: [ "Preheat oven to 375°F", "Mix apples with sugar", "Fill pie crust and cover", "Bake for 45 minutes" ]
    )

    result = RecipeLlmService.new(text, prompt_type: :raw_text).extract

    assert result.success?
    assert_equal "Grandma's Apple Pie", result.recipe_attributes[:name]
    assert_nil result.recipe_attributes[:source_url]
  end

  test "uses webpage prompt type by default" do
    stub_llm_response(name: "Test Recipe", ingredients: [], instructions: [])

    RecipeLlmService.new("Some text").extract

    assert_requested(:post, LlmStubHelper::OPENROUTER_ENDPOINT) do |req|
      body = JSON.parse(req.body)
      prompt = body["messages"].last["content"]
      prompt.include?("webpage content")
    end
  end

  test "routes the configured model exclusively through EU Vertex" do
    stub_llm_response(name: "Test Recipe", ingredients: [], instructions: [])

    RecipeLlmService.new("Some text").extract

    assert_requested(:post, LlmStubHelper::OPENROUTER_ENDPOINT) do |req|
      body = JSON.parse(req.body)
      body["model"] == "google/gemini-3.5-flash-lite" &&
        body.dig("provider", "only") == [ "google-vertex/eu" ]
    end
  end

  test "uses raw_text prompt when specified" do
    stub_llm_response(name: "Test Recipe", ingredients: [], instructions: [])

    RecipeLlmService.new("Some text", prompt_type: :raw_text).extract

    assert_requested(:post, LlmStubHelper::OPENROUTER_ENDPOINT) do |req|
      body = JSON.parse(req.body)
      prompt = body["messages"].last["content"]
      prompt.include?("Recipe text:")
    end
  end

  test "uses the shared ingredient enrichment instructions" do
    stub_llm_response(name: "Test Recipe", ingredients: [], instructions: [])

    RecipeLlmService.new("Some text").extract

    assert_requested(:post, LlmStubHelper::OPENROUTER_ENDPOINT) do |req|
      JSON.parse(req.body).dig("messages", -1, "content").include?(Llm::IngredientInstructions.prompt)
    end
  end

  test "returns versioned canonical ingredient metadata" do
    stub_llm_response(
      name: "Pasta",
      ingredients: [ {
        raw: "2 EL Olivenöl",
        name: "Olivenöl",
        amount: 2,
        unit: "el",
        canonical_name: "olive oil",
        canonical_unit: "tablespoon",
        category: "oils_spices_condiments"
      } ],
      instructions: [ "Cook" ]
    )

    ingredient = RecipeLlmService.new("Pasta recipe").extract.recipe_attributes[:ingredients].sole

    assert_equal "olive oil", ingredient[:canonical_name]
    assert_equal "tablespoon", ingredient[:canonical_unit]
    assert_equal "oils_spices_condiments", ingredient[:category]
    assert_equal Llm::IngredientInstructions::VERSION, ingredient[:enrichment_version]
  end

  test "handles minimal recipe data" do
    stub_llm_response(
      name: "Simple Dish",
      ingredients: [ "1 ingredient" ],
      instructions: [ "Do something" ]
    )

    result = RecipeLlmService.new("Simple Recipe", prompt_type: :webpage).extract

    assert result.success?
    assert_equal "Simple Dish", result.recipe_attributes[:name]
    assert_nil result.recipe_attributes[:prep_time]
    assert_nil result.recipe_attributes[:cook_time]
    assert_nil result.recipe_attributes[:servings]
    assert_nil result.recipe_attributes[:notes]
  end

  test "does not include source_url when not provided" do
    stub_llm_response(name: "Test Recipe", ingredients: [], instructions: [])

    result = RecipeLlmService.new("Some text").extract

    assert result.success?
    assert_not result.recipe_attributes.key?(:source_url)
  end

  # Argument validation

  test "raises ArgumentError for invalid prompt_type" do
    assert_raises(ArgumentError) do
      RecipeLlmService.new("text", prompt_type: :invalid)
    end
  end

  # Error handling

  test "returns failure when LLM returns empty name" do
    stub_llm_response(name: "", ingredients: [], instructions: [])

    result = RecipeLlmService.new("Some random content").extract

    assert_not result.success?
    assert_equal :extraction_failed, result.error_code
    assert_match(/could not identify recipe name/i, result.error)
  end

  test "returns failure when text is blank" do
    result = RecipeLlmService.new("").extract

    assert_not result.success?
    assert_equal :extraction_failed, result.error_code
    assert_match(/no text content/i, result.error)
  end

  test "returns failure when text is nil" do
    result = RecipeLlmService.new(nil).extract

    assert_not result.success?
    assert_equal :extraction_failed, result.error_code
    assert_match(/no text content/i, result.error)
  end

  test "returns failure when LLM returns nil content" do
    stub_openrouter_api(response_body: { "choices" => [ { "message" => { "content" => nil } } ] })

    result = RecipeLlmService.new("Some content").extract

    assert_not result.success?
    assert_equal :extraction_failed, result.error_code
  end

  test "handles LLM API timeout" do
    stub_request(:post, LlmStubHelper::OPENROUTER_ENDPOINT)
      .to_raise(Faraday::TimeoutError.new("execution expired"))

    result = RecipeLlmService.new("Recipe content").extract

    assert_not result.success?
    assert_equal :llm_timeout, result.error_code
    assert_match(/timed out/i, result.error)
  end

  test "handles LLM connection failure" do
    stub_request(:post, LlmStubHelper::OPENROUTER_ENDPOINT)
      .to_raise(Faraday::ConnectionFailed.new("connection refused"))

    result = RecipeLlmService.new("Recipe content").extract

    assert_not result.success?
    assert_equal :llm_timeout, result.error_code
  end

  test "handles RubyLLM API error" do
    stub_request(:post, LlmStubHelper::OPENROUTER_ENDPOINT)
      .to_return(status: 401, body: { error: { message: "Invalid API key" } }.to_json)

    result = RecipeLlmService.new("Recipe content").extract

    assert_not result.success?
    assert_equal :llm_error, result.error_code
    assert_match(/api error/i, result.error)
  end

  test "handles unexpected errors gracefully" do
    stub_request(:post, LlmStubHelper::OPENROUTER_ENDPOINT)
      .to_return(status: 200, body: "not valid json at all")

    result = RecipeLlmService.new("Recipe content").extract

    assert_not result.success?
    assert_equal :extraction_failed, result.error_code
  end

  # Text length limits

  test "truncates text to maximum length" do
    long_content = "Recipe content. " * 2000  # ~32,000 chars

    stub_llm_response(name: "Long Recipe", ingredients: [], instructions: [])

    RecipeLlmService.new(long_content).extract

    assert_requested(:post, LlmStubHelper::OPENROUTER_ENDPOINT) do |req|
      body = JSON.parse(req.body)
      prompt = body["messages"].last["content"]
      prompt.length < 20_000  # Some buffer for prompt template
    end
  end

  # Partial data handling

  test "handles empty ingredients array" do
    stub_llm_response(name: "No Ingredients Recipe", ingredients: [], instructions: [ "Just do it" ])

    result = RecipeLlmService.new("Recipe with no ingredients listed").extract

    assert result.success?
    assert_equal [], result.recipe_attributes[:ingredients]
  end

  test "handles empty instructions array" do
    stub_llm_response(name: "No Instructions Recipe", ingredients: [ "1 cup flour" ], instructions: [])

    result = RecipeLlmService.new("Recipe with no instructions").extract

    assert result.success?
    assert_equal [], result.recipe_attributes[:instructions]
  end

  test "normalizes array elements by stripping whitespace" do
    stub_llm_response(
      name: "Whitespace Recipe",
      ingredients: [ "  flour  ", "\nsugar\n", "  " ],
      instructions: [ "  Step 1  ", "" ]
    )

    result = RecipeLlmService.new("Recipe").extract

    assert result.success?
    assert_equal [ "flour", "sugar" ], result.recipe_attributes[:ingredients].map { |i| i[:raw] }
    assert_equal [ "Step 1" ], result.recipe_attributes[:instructions]
  end

  test "handles non-array ingredients gracefully" do
    stub_llm_response(
      name: "Bad Format Recipe",
      ingredients: "not an array",
      instructions: [ "Step 1" ]
    )

    result = RecipeLlmService.new("Recipe").extract

    assert result.success?
    assert_equal [], result.recipe_attributes[:ingredients]
  end

  test "strips whitespace from notes" do
    stub_llm_response(
      name: "Notes Recipe",
      ingredients: [],
      instructions: [],
      notes: "  Some notes with whitespace  "
    )

    result = RecipeLlmService.new("Recipe").extract

    assert result.success?
    assert_equal "Some notes with whitespace", result.recipe_attributes[:notes]
  end

  test "returns nil for blank notes" do
    stub_llm_response(
      name: "Empty Notes Recipe",
      ingredients: [],
      instructions: [],
      notes: "   "
    )

    result = RecipeLlmService.new("Recipe").extract

    assert result.success?
    assert_nil result.recipe_attributes[:notes]
  end
end
