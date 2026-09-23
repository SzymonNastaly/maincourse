require "test_helper"

module RecipeImporters
  class LlmExtractorTest < ActiveSupport::TestCase
    # ===================
    # SUCCESSFUL EXTRACTION
    # ===================

    test "extracts recipe from LLM response" do
      html = build_html_with_content("Chocolate Cake Recipe. Ingredients: 2 cups flour, 1 cup sugar. Instructions: Mix and bake.")
      stub_llm_response(
        name: "Chocolate Cake",
        ingredients: [ "2 cups flour", "1 cup sugar" ],
        instructions: [ "Mix ingredients", "Bake at 350°F for 30 minutes" ],
        prep_time: 15,
        cook_time: 30,
        servings: 8,
        notes: "A delicious family recipe"
      )

      result = LlmExtractor.new(html, "https://example.com/recipe").extract

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

    test "handles minimal recipe data (name only required fields)" do
      html = build_html_with_content("Simple Recipe")
      stub_llm_response(
        name: "Simple Dish",
        ingredients: [ "1 ingredient" ],
        instructions: [ "Do something" ]
      )

      result = LlmExtractor.new(html, "https://example.com/simple").extract

      assert result.success?
      assert_equal "Simple Dish", result.recipe_attributes[:name]
      assert_nil result.recipe_attributes[:prep_time]
      assert_nil result.recipe_attributes[:cook_time]
      assert_nil result.recipe_attributes[:servings]
      assert_nil result.recipe_attributes[:notes]
    end

    # ===================
    # ERROR HANDLING
    # ===================

    test "returns failure when LLM returns empty name" do
      html = build_html_with_content("Some random content")
      stub_llm_response(name: "", ingredients: [], instructions: [])

      result = LlmExtractor.new(html, "https://example.com/page").extract

      assert_not result.success?
      assert_equal :not_a_recipe, result.error_code
      assert_equal "Could not identify recipe name", result.error
    end

    test "returns failure when LLM returns nil content" do
      html = build_html_with_content("Some content")
      stub_openrouter_api(response_body: { "choices" => [ { "message" => { "content" => nil } } ] })

      result = LlmExtractor.new(html, "https://example.com/page").extract

      assert_not result.success?
      assert_equal :extraction_failed, result.error_code
    end

    test "handles LLM API timeout" do
      html = build_html_with_content("Recipe content")
      stub_request(:post, LlmStubHelper::OPENROUTER_ENDPOINT)
        .to_raise(Faraday::TimeoutError.new("execution expired"))

      result = LlmExtractor.new(html, "https://example.com/recipe").extract

      assert_not result.success?
      assert_equal :llm_timeout, result.error_code
      assert_match(/timed out/i, result.error)
    end

    test "handles LLM connection failure" do
      html = build_html_with_content("Recipe content")
      stub_request(:post, LlmStubHelper::OPENROUTER_ENDPOINT)
        .to_raise(Faraday::ConnectionFailed.new("connection refused"))

      result = LlmExtractor.new(html, "https://example.com/recipe").extract

      assert_not result.success?
      assert_equal :llm_timeout, result.error_code
    end

    test "handles RubyLLM API error" do
      html = build_html_with_content("Recipe content")
      stub_request(:post, LlmStubHelper::OPENROUTER_ENDPOINT)
        .to_return(status: 401, body: { error: { message: "Invalid API key" } }.to_json)

      result = LlmExtractor.new(html, "https://example.com/recipe").extract

      assert_not result.success?
      assert_equal :llm_error, result.error_code
      assert_match(/api error/i, result.error)
    end

    test "handles unexpected errors gracefully" do
      html = build_html_with_content("Recipe content")
      stub_request(:post, LlmStubHelper::OPENROUTER_ENDPOINT)
        .to_return(status: 200, body: "not valid json at all")

      result = LlmExtractor.new(html, "https://example.com/recipe").extract

      assert_not result.success?
      assert_equal :extraction_failed, result.error_code
    end

    # ===================
    # HTML CLEANING
    # ===================

    test "removes script tags from HTML" do
      html = <<~HTML
        <html>
        <body>
          <script>alert('evil');</script>
          <h1>Recipe Title</h1>
          <script type="text/javascript">var x = 1;</script>
        </body>
        </html>
      HTML

      stub_llm_response(name: "Recipe Title", ingredients: [], instructions: [])

      LlmExtractor.new(html, "https://example.com/recipe").extract

      # Verify the LLM was called with cleaned text (no script content)
      assert_requested(:post, LlmStubHelper::OPENROUTER_ENDPOINT) do |req|
        body = JSON.parse(req.body)
        prompt = body["messages"].last["content"]
        !prompt.include?("alert") && !prompt.include?("var x")
      end
    end

    test "removes style tags from HTML" do
      html = <<~HTML
        <html>
        <head><style>body { color: red; }</style></head>
        <body><h1>Recipe</h1></body>
        </html>
      HTML

      stub_llm_response(name: "Recipe", ingredients: [], instructions: [])

      LlmExtractor.new(html, "https://example.com/recipe").extract

      assert_requested(:post, LlmStubHelper::OPENROUTER_ENDPOINT) do |req|
        body = JSON.parse(req.body)
        prompt = body["messages"].last["content"]
        !prompt.include?("color: red")
      end
    end

    test "removes navigation and footer elements" do
      html = <<~HTML
        <html>
        <body>
          <nav><a href="/">Home</a><a href="/about">About</a></nav>
          <header>Site Header</header>
          <main><h1>Great Recipe</h1></main>
          <footer>Copyright 2024</footer>
          <aside>Advertisement</aside>
        </body>
        </html>
      HTML

      stub_llm_response(name: "Great Recipe", ingredients: [], instructions: [])

      LlmExtractor.new(html, "https://example.com/recipe").extract

      assert_requested(:post, LlmStubHelper::OPENROUTER_ENDPOINT) do |req|
        body = JSON.parse(req.body)
        prompt = body["messages"].last["content"]
        prompt.include?("Great Recipe") &&
          !prompt.include?("About") &&
          !prompt.include?("Copyright") &&
          !prompt.include?("Advertisement")
      end
    end

    test "normalizes whitespace in extracted text" do
      html = <<~HTML
        <html>
        <body>
          <h1>Recipe    Title</h1>
          <p>Lots     of
          whitespace   here</p>
        </body>
        </html>
      HTML

      stub_llm_response(name: "Recipe Title", ingredients: [], instructions: [])

      LlmExtractor.new(html, "https://example.com/recipe").extract

      assert_requested(:post, LlmStubHelper::OPENROUTER_ENDPOINT) do |req|
        body = JSON.parse(req.body)
        prompt = body["messages"].last["content"]
        # Original HTML had "Recipe    Title" (4 spaces) - should be normalized to single space
        # Check that multiple consecutive spaces within text are collapsed
        prompt.include?("Recipe Title") && !prompt.include?("Recipe    Title")
      end
    end

    # ===================
    # TEXT LENGTH LIMITS
    # ===================

    test "truncates text to maximum length" do
      # Create HTML with content exceeding MAX_TEXT_LENGTH
      long_content = "Recipe content. " * 2000  # ~32,000 chars
      html = "<html><body>#{long_content}</body></html>"

      stub_llm_response(name: "Long Recipe", ingredients: [], instructions: [])

      LlmExtractor.new(html, "https://example.com/recipe").extract

      assert_requested(:post, LlmStubHelper::OPENROUTER_ENDPOINT) do |req|
        body = JSON.parse(req.body)
        prompt = body["messages"].last["content"]
        # The webpage content portion should be truncated
        # Total prompt is larger due to instructions, but webpage content <= 15,000
        prompt.length < 20_000  # Some buffer for prompt template
      end
    end

    test "returns failure when HTML has no extractable content" do
      html = "<html><head><script>only script</script></head><body></body></html>"

      result = LlmExtractor.new(html, "https://example.com/empty").extract

      assert_not result.success?
      assert_equal :extraction_failed, result.error_code
      assert_match(/could not extract text/i, result.error)
    end

    # ===================
    # PARTIAL DATA HANDLING
    # ===================

    test "handles empty ingredients array" do
      html = build_html_with_content("Recipe with no ingredients listed")
      stub_llm_response(name: "No Ingredients Recipe", ingredients: [], instructions: [ "Just do it" ])

      result = LlmExtractor.new(html, "https://example.com/recipe").extract

      assert result.success?
      assert_equal [], result.recipe_attributes[:ingredients]
    end

    test "handles empty instructions array" do
      html = build_html_with_content("Recipe with no instructions")
      stub_llm_response(name: "No Instructions Recipe", ingredients: [ "1 cup flour" ], instructions: [])

      result = LlmExtractor.new(html, "https://example.com/recipe").extract

      assert result.success?
      assert_equal [], result.recipe_attributes[:instructions]
    end

    test "normalizes array elements by stripping whitespace" do
      html = build_html_with_content("Recipe")
      stub_llm_response(
        name: "Whitespace Recipe",
        ingredients: [ "  flour  ", "\nsugar\n", "  " ],
        instructions: [ "  Step 1  ", "" ]
      )

      result = LlmExtractor.new(html, "https://example.com/recipe").extract

      assert result.success?
      assert_equal [ "flour", "sugar" ], result.recipe_attributes[:ingredients].map { |i| i[:raw] }
      assert_equal [ "Step 1" ], result.recipe_attributes[:instructions]
    end

    test "handles non-array ingredients gracefully" do
      html = build_html_with_content("Recipe")
      stub_llm_response(
        name: "Bad Format Recipe",
        ingredients: "not an array",
        instructions: [ "Step 1" ]
      )

      result = LlmExtractor.new(html, "https://example.com/recipe").extract

      assert result.success?
      assert_equal [], result.recipe_attributes[:ingredients]
    end

    test "strips whitespace from notes" do
      html = build_html_with_content("Recipe")
      stub_llm_response(
        name: "Notes Recipe",
        ingredients: [],
        instructions: [],
        notes: "  Some notes with whitespace  "
      )

      result = LlmExtractor.new(html, "https://example.com/recipe").extract

      assert result.success?
      assert_equal "Some notes with whitespace", result.recipe_attributes[:notes]
    end

    test "returns nil for blank notes" do
      html = build_html_with_content("Recipe")
      stub_llm_response(
        name: "Empty Notes Recipe",
        ingredients: [],
        instructions: [],
        notes: "   "
      )

      result = LlmExtractor.new(html, "https://example.com/recipe").extract

      assert result.success?
      assert_nil result.recipe_attributes[:notes]
    end

    private

    def build_html_with_content(text)
      "<html><body><div>#{text}</div></body></html>"
    end
  end
end
