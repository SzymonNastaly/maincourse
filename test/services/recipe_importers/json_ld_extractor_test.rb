require "test_helper"

module RecipeImporters
  class JsonLdExtractorTest < ActiveSupport::TestCase
    # ===================
    # BASIC EXTRACTION
    # ===================

    test "extracts recipe from simple JSON-LD" do
      html = build_html_with_json_ld({
        "@type" => "Recipe",
        "name" => "Chocolate Chip Cookies",
        "recipeIngredient" => [ "2 cups flour", "1 cup sugar" ],
        "recipeInstructions" => [ "Mix ingredients", "Bake at 350°F" ],
        "prepTime" => "PT15M",
        "cookTime" => "PT30M",
        "recipeYield" => "24 cookies"
      })

      result = JsonLdExtractor.new(html, "https://example.com/cookies").extract

      assert result.success?
      assert_equal "Chocolate Chip Cookies", result.recipe_attributes[:name]
      assert_equal [ "2 cups flour", "1 cup sugar" ], result.recipe_attributes[:ingredients].map { |i| i[:raw] }
      assert_equal [ "Mix ingredients", "Bake at 350°F" ], result.recipe_attributes[:instructions]
      assert_equal 15, result.recipe_attributes[:prep_time]
      assert_equal 30, result.recipe_attributes[:cook_time]
      assert_equal 24, result.recipe_attributes[:servings]
      assert_equal "https://example.com/cookies", result.recipe_attributes[:source_url]
    end

    test "decodes HTML entities in user-facing text" do
      html = build_html_with_json_ld({
        "@type" => "Recipe",
        "name" => "Easy &amp; Cheesy Baked Spaghetti",
        "recipeIngredient" => [ "Kosher salt &amp; black pepper" ],
        "recipeInstructions" => [ { "@type" => "HowToStep", "text" => "Mix &amp; bake" } ],
        "description" => "Quick &amp; cozy"
      })

      result = JsonLdExtractor.new(html, "https://example.com/baked-spaghetti").extract

      assert result.success?
      assert_equal "Easy & Cheesy Baked Spaghetti", result.recipe_attributes[:name]
      assert_equal [ "Kosher salt & black pepper" ], result.recipe_attributes[:ingredients].map { |ingredient| ingredient[:raw] }
      assert_equal [ "Mix & bake" ], result.recipe_attributes[:instructions]
      assert_equal "Quick & cozy", result.recipe_attributes[:notes]
    end

    test "returns failure when no JSON-LD found" do
      html = "<html><body><h1>No recipe here</h1></body></html>"

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert_not result.success?
      assert_match(/no json-ld/i, result.error)
    end

    test "returns failure when JSON-LD exists but no Recipe type" do
      html = build_html_with_json_ld({
        "@type" => "Article",
        "name" => "Blog Post About Food"
      })

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert_not result.success?
    end

    test "returns failure when recipe has no name" do
      html = build_html_with_json_ld({
        "@type" => "Recipe",
        "recipeIngredient" => [ "Some ingredient" ]
      })

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert_not result.success?
    end

    # ===================
    # FULL SCHEMA.ORG URL @type
    # ===================

    test "handles https schema.org URL in @type" do
      html = build_html_with_json_ld({
        "@type" => "https://schema.org/Recipe",
        "name" => "HTTPS Schema Recipe"
      })

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert result.success?
      assert_equal "HTTPS Schema Recipe", result.recipe_attributes[:name]
    end

    test "handles http schema.org URL in @type" do
      html = build_html_with_json_ld({
        "@type" => "http://schema.org/Recipe",
        "name" => "HTTP Schema Recipe"
      })

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert result.success?
      assert_equal "HTTP Schema Recipe", result.recipe_attributes[:name]
    end

    test "handles schema.org URL in array @type" do
      html = build_html_with_json_ld({
        "@type" => [ "https://schema.org/Recipe", "https://schema.org/HowTo" ],
        "name" => "Array URL Type Recipe"
      })

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert result.success?
      assert_equal "Array URL Type Recipe", result.recipe_attributes[:name]
    end

    # ===================
    # mainEntity/mainEntityOfPage WRAPPERS
    # ===================

    test "finds recipe in mainEntity wrapper" do
      html = build_html_with_json_ld({
        "@type" => "WebPage",
        "name" => "Recipe Page",
        "mainEntity" => {
          "@type" => "Recipe",
          "name" => "Wrapped Recipe"
        }
      })

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert result.success?
      assert_equal "Wrapped Recipe", result.recipe_attributes[:name]
    end

    test "finds recipe in mainEntityOfPage wrapper" do
      html = build_html_with_json_ld({
        "@type" => "WebPage",
        "mainEntityOfPage" => {
          "@type" => "Recipe",
          "name" => "Main Entity Of Page Recipe"
        }
      })

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert result.success?
      assert_equal "Main Entity Of Page Recipe", result.recipe_attributes[:name]
    end

    test "finds recipe in nested mainEntity with schema.org URL type" do
      html = build_html_with_json_ld({
        "@type" => "WebPage",
        "mainEntity" => {
          "@type" => "https://schema.org/Recipe",
          "name" => "Nested URL Type Recipe"
        }
      })

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert result.success?
      assert_equal "Nested URL Type Recipe", result.recipe_attributes[:name]
    end

    # ===================
    # @graph HANDLING
    # ===================

    test "finds recipe inside @graph array" do
      html = build_html_with_json_ld({
        "@context" => "https://schema.org",
        "@graph" => [
          { "@type" => "WebPage", "name" => "Recipe Page" },
          { "@type" => "Recipe", "name" => "Lasagna" },
          { "@type" => "Organization", "name" => "Cooking Site" }
        ]
      })

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert result.success?
      assert_equal "Lasagna", result.recipe_attributes[:name]
    end

    # ===================
    # ARRAY @type HANDLING
    # ===================

    test "handles recipe with array @type" do
      html = build_html_with_json_ld({
        "@type" => [ "Recipe", "HowTo" ],
        "name" => "Multi-type Recipe"
      })

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert result.success?
      assert_equal "Multi-type Recipe", result.recipe_attributes[:name]
    end

    # ===================
    # MULTIPLE JSON-LD SCRIPTS
    # ===================

    test "finds recipe in second JSON-LD script" do
      html = <<~HTML
        <html>
        <head>
          <script type="application/ld+json">
            {"@type": "WebSite", "name": "Cooking Blog"}
          </script>
          <script type="application/ld+json">
            {"@type": "Recipe", "name": "Found It"}
          </script>
        </head>
        <body></body>
        </html>
      HTML

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert result.success?
      assert_equal "Found It", result.recipe_attributes[:name]
    end

    # ===================
    # DURATION PARSING
    # ===================

    test "parses hours only duration" do
      html = build_html_with_json_ld({
        "@type" => "Recipe",
        "name" => "Slow Cook",
        "cookTime" => "PT2H"
      })

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert_equal 120, result.recipe_attributes[:cook_time]
    end

    test "parses hours and minutes duration" do
      html = build_html_with_json_ld({
        "@type" => "Recipe",
        "name" => "Roast",
        "cookTime" => "PT1H30M"
      })

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert_equal 90, result.recipe_attributes[:cook_time]
    end

    test "handles missing duration gracefully" do
      html = build_html_with_json_ld({
        "@type" => "Recipe",
        "name" => "Quick Recipe"
      })

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert_nil result.recipe_attributes[:prep_time]
      assert_nil result.recipe_attributes[:cook_time]
    end

    test "handles invalid duration format" do
      html = build_html_with_json_ld({
        "@type" => "Recipe",
        "name" => "Bad Duration",
        "cookTime" => "30 minutes"
      })

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert_nil result.recipe_attributes[:cook_time]
    end

    test "parses duration with days" do
      html = build_html_with_json_ld({
        "@type" => "Recipe",
        "name" => "Day Long",
        "cookTime" => "P1D"
      })

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert_equal 1440, result.recipe_attributes[:cook_time]
    end

    test "parses duration with seconds only rounds to 1 minute" do
      html = build_html_with_json_ld({
        "@type" => "Recipe",
        "name" => "Quick Seconds",
        "prepTime" => "PT45S"
      })

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert_equal 1, result.recipe_attributes[:prep_time]
    end

    test "parses complex duration with days hours and minutes" do
      html = build_html_with_json_ld({
        "@type" => "Recipe",
        "name" => "Complex Duration",
        "cookTime" => "P1DT2H30M"
      })

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert_equal 1590, result.recipe_attributes[:cook_time]
    end

    test "parses duration with hours minutes and seconds" do
      html = build_html_with_json_ld({
        "@type" => "Recipe",
        "name" => "Full Duration",
        "cookTime" => "PT1H30M45S"
      })

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert_equal 91, result.recipe_attributes[:cook_time]
    end

    # ===================
    # INSTRUCTION FORMATS
    # ===================

    test "extracts instructions from HowToStep objects" do
      html = build_html_with_json_ld({
        "@type" => "Recipe",
        "name" => "Step Recipe",
        "recipeInstructions" => [
          { "@type" => "HowToStep", "text" => "First step" },
          { "@type" => "HowToStep", "text" => "Second step" }
        ]
      })

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert_equal [ "First step", "Second step" ], result.recipe_attributes[:instructions]
    end

    test "extracts instructions from HowToSection with steps" do
      html = build_html_with_json_ld({
        "@type" => "Recipe",
        "name" => "Sectioned Recipe",
        "recipeInstructions" => [
          {
            "@type" => "HowToSection",
            "name" => "Prepare",
            "itemListElement" => [
              { "@type" => "HowToStep", "text" => "Prep step 1" },
              { "@type" => "HowToStep", "text" => "Prep step 2" }
            ]
          }
        ]
      })

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert_equal [ "Prep step 1", "Prep step 2" ], result.recipe_attributes[:instructions]
    end

    test "handles mixed instruction formats" do
      html = build_html_with_json_ld({
        "@type" => "Recipe",
        "name" => "Mixed Instructions",
        "recipeInstructions" => [
          "Plain string instruction",
          { "@type" => "HowToStep", "text" => "Object instruction" }
        ]
      })

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert_equal [ "Plain string instruction", "Object instruction" ], result.recipe_attributes[:instructions]
    end

    test "extracts instructions from ItemList wrapper with string items" do
      html = build_html_with_json_ld({
        "@type" => "Recipe",
        "name" => "ItemList Recipe",
        "recipeInstructions" => {
          "@type" => "ItemList",
          "itemListElement" => [
            { "@type" => "ListItem", "position" => 1, "item" => "First step" },
            { "@type" => "ListItem", "position" => 2, "item" => "Second step" }
          ]
        }
      })

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert_equal [ "First step", "Second step" ], result.recipe_attributes[:instructions]
    end

    test "extracts instructions from ItemList with HowToStep items" do
      html = build_html_with_json_ld({
        "@type" => "Recipe",
        "name" => "ItemList HowToStep Recipe",
        "recipeInstructions" => {
          "@type" => "ItemList",
          "itemListElement" => [
            { "@type" => "ListItem", "position" => 1, "item" => { "@type" => "HowToStep", "text" => "Step one" } },
            { "@type" => "ListItem", "position" => 2, "item" => { "@type" => "HowToStep", "text" => "Step two" } }
          ]
        }
      })

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert_equal [ "Step one", "Step two" ], result.recipe_attributes[:instructions]
    end

    test "extracts instructions from ListItem with name fallback" do
      html = build_html_with_json_ld({
        "@type" => "Recipe",
        "name" => "ListItem Name Recipe",
        "recipeInstructions" => {
          "@type" => "ItemList",
          "itemListElement" => [
            { "@type" => "ListItem", "position" => 1, "name" => "Do this first" },
            { "@type" => "ListItem", "position" => 2, "name" => "Do this second" }
          ]
        }
      })

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert_equal [ "Do this first", "Do this second" ], result.recipe_attributes[:instructions]
    end

    # ===================
    # SERVINGS/YIELD
    # ===================

    test "extracts numeric servings" do
      html = build_html_with_json_ld({
        "@type" => "Recipe",
        "name" => "Serves 4",
        "recipeYield" => "4"
      })

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert_equal 4, result.recipe_attributes[:servings]
    end

    test "extracts servings from descriptive yield" do
      html = build_html_with_json_ld({
        "@type" => "Recipe",
        "name" => "Makes Many",
        "recipeYield" => "Makes about 24 cookies"
      })

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert_equal 24, result.recipe_attributes[:servings]
    end

    test "handles yield as array (takes first)" do
      html = build_html_with_json_ld({
        "@type" => "Recipe",
        "name" => "Array Yield",
        "recipeYield" => [ "6 servings", "6" ]
      })

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert_equal 6, result.recipe_attributes[:servings]
    end

    # ===================
    # INGREDIENTS
    # ===================

    test "strips whitespace from ingredients" do
      html = build_html_with_json_ld({
        "@type" => "Recipe",
        "name" => "Whitespace Recipe",
        "recipeIngredient" => [ "  2 cups flour  ", "\n1 cup sugar\n" ]
      })

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert_equal [ "2 cups flour", "1 cup sugar" ], result.recipe_attributes[:ingredients].map { |i| i[:raw] }
    end

    test "filters out blank ingredients" do
      html = build_html_with_json_ld({
        "@type" => "Recipe",
        "name" => "Blanks Recipe",
        "recipeIngredient" => [ "1 cup flour", "", "  ", "2 eggs" ]
      })

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert_equal [ "1 cup flour", "2 eggs" ], result.recipe_attributes[:ingredients].map { |i| i[:raw] }
    end

    test "handles ingredients key instead of recipeIngredient" do
      html = build_html_with_json_ld({
        "@type" => "Recipe",
        "name" => "Old Format",
        "ingredients" => [ "1 cup flour" ]
      })

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert_equal [ "1 cup flour" ], result.recipe_attributes[:ingredients].map { |i| i[:raw] }
    end

    # ===================
    # DESCRIPTION/NOTES
    # ===================

    test "extracts description as notes" do
      html = build_html_with_json_ld({
        "@type" => "Recipe",
        "name" => "Described Recipe",
        "description" => "This is a family favorite passed down for generations."
      })

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert_equal "This is a family favorite passed down for generations.", result.recipe_attributes[:notes]
    end

    # ===================
    # MALFORMED DATA
    # ===================

    test "handles invalid JSON gracefully" do
      html = <<~HTML
        <html>
        <head>
          <script type="application/ld+json">
            { invalid json here
          </script>
        </head>
        <body></body>
        </html>
      HTML

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert_not result.success?
    end

    test "extracts from direct JSON-LD strings and ignores invalid blocks" do
      extractor = JsonLdExtractor.new("", "https://example.com")
      json_ld_strings = [
        "</script><script>alert(1)</script>",
        "{ invalid json",
        JSON.generate({
          "@type" => "Recipe",
          "name" => "Direct JSON-LD Recipe",
          "recipeIngredient" => [ "1 cup flour" ],
          "recipeInstructions" => [ "Mix" ]
        })
      ]

      result = extractor.extract_from_json_ld_strings(json_ld_strings)

      assert result.success?
      assert_equal "Direct JSON-LD Recipe", result.recipe_attributes[:name]
      assert_equal [ "1 cup flour" ], result.recipe_attributes[:ingredients].map { |i| i[:raw] }
      assert_equal [ "Mix" ], result.recipe_attributes[:instructions]
    end

    test "handles single instruction wrapped in string" do
      html = build_html_with_json_ld({
        "@type" => "Recipe",
        "name" => "Single Step",
        "recipeInstructions" => "Just one instruction"
      })

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert_equal [ "Just one instruction" ], result.recipe_attributes[:instructions]
    end

    # ===================
    # IMAGE EXTRACTION
    # ===================

    test "extracts image URL from string" do
      html = build_html_with_json_ld({
        "@type" => "Recipe",
        "name" => "Cookies",
        "image" => "https://example.com/photo.jpg"
      })

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert result.success?
      assert_equal "https://example.com/photo.jpg", result.cover_image_url
    end

    test "extracts image URL from array of strings" do
      html = build_html_with_json_ld({
        "@type" => "Recipe",
        "name" => "Cookies",
        "image" => [ "https://example.com/photo1.jpg", "https://example.com/photo2.jpg" ]
      })

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert result.success?
      assert_equal "https://example.com/photo1.jpg", result.cover_image_url
    end

    test "extracts image URL from ImageObject hash" do
      html = build_html_with_json_ld({
        "@type" => "Recipe",
        "name" => "Cookies",
        "image" => { "@type" => "ImageObject", "url" => "https://example.com/photo.jpg" }
      })

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert result.success?
      assert_equal "https://example.com/photo.jpg", result.cover_image_url
    end

    test "extracts image URL from array of ImageObject hashes" do
      html = build_html_with_json_ld({
        "@type" => "Recipe",
        "name" => "Cookies",
        "image" => [ { "@type" => "ImageObject", "url" => "https://example.com/photo.jpg" } ]
      })

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert result.success?
      assert_equal "https://example.com/photo.jpg", result.cover_image_url
    end

    test "returns nil cover_image_url when no image in JSON-LD" do
      html = build_html_with_json_ld({
        "@type" => "Recipe",
        "name" => "Cookies"
      })

      result = JsonLdExtractor.new(html, "https://example.com").extract

      assert result.success?
      assert_nil result.cover_image_url
    end

    private

    def build_html_with_json_ld(data)
      json = JSON.generate(data)
      <<~HTML
        <html>
        <head>
          <script type="application/ld+json">
            #{json}
          </script>
        </head>
        <body></body>
        </html>
      HTML
    end
  end
end
