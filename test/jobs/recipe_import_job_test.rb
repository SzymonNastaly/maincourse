require "test_helper"

class RecipeImportJobTest < ActiveSupport::TestCase
  setup do
    @user = users(:one)
    @recipe = recipes(:one)
    @recipe.update!(import_status: :pending, name: "Placeholder")
  end

  test "updates recipe with extracted data on success" do
    html = <<~HTML
      <html>
      <head>
        <script type="application/ld+json">
          {
            "@type": "Recipe",
            "name": "Imported Recipe",
            "recipeIngredient": ["2 cups flour", "1 egg"],
            "recipeInstructions": ["Mix ingredients", "Bake at 350F"],
            "prepTime": "PT15M",
            "cookTime": "PT30M",
            "recipeYield": "4 servings"
          }
        </script>
      </head>
      </html>
    HTML

    stub_request(:get, "https://example.com/recipe")
      .to_return(status: 200, body: html, headers: { "Content-Type" => "text/html" })

    RecipeImportJob.perform_now(@user.id, @recipe.id, "https://example.com/recipe")

    @recipe.reload
    assert_equal :completed, @recipe.import_status.to_sym
    assert_equal "Imported Recipe", @recipe.name
    assert_equal [ "2 cups flour", "1 egg" ], @recipe.ingredients.map(&:raw)
    assert_equal [ "Mix ingredients", "Bake at 350F" ], @recipe.instructions
    assert_equal 15, @recipe.prep_time
    assert_equal 30, @recipe.cook_time
    assert_equal 4, @recipe.servings
    assert_equal "https://example.com/recipe", @recipe.source_url
  end

  test "marks recipe as failed when import fails" do
    stub_request(:get, "https://example.com/recipe")
      .to_return(status: 404, headers: { "Content-Type" => "text/html" })

    RecipeImportJob.perform_now(@user.id, @recipe.id, "https://example.com/recipe")

    @recipe.reload
    assert_equal :failed, @recipe.import_status.to_sym
    assert_not_nil @recipe.error_message
    assert_equal "Import from example.com failed.", @recipe.error_message
  end

  test "attaches cover image from instagram import" do
    stub_request(:get, %r{\Ahttps://www\.instagram\.com/}).to_return(status: 302, headers: { "Location" => "https://www.instagram.com/accounts/login/" })
    url = "https://www.instagram.com/p/DRUf_pBiPdh/"
    previous_key = ENV["APIFY_API_KEY"]
    ENV["APIFY_API_KEY"] = "test-apify-key"

    response_body = [
      {
        "caption" => "Test Recipe\n\nIngredients:\n- 1 cup flour\n\nInstructions:\n- Mix",
        "displayUrl" => "https://example.com/cover.jpg"
      }
    ]

    stub_request(:post, "https://api.apify.com/v2/acts/apify~instagram-reel-scraper/run-sync-get-dataset-items")
      .with(
        query: { "token" => "test-apify-key" },
        headers: { "Content-Type" => "application/json" },
        body: { username: [ url ], resultsLimit: 1 }.to_json
      )
      .to_return(status: 200, body: response_body.to_json, headers: { "Content-Type" => "application/json" })

    stub_request(:get, "https://example.com/cover.jpg")
      .to_return(status: 200, body: "fake-image-bytes", headers: { "Content-Type" => "image/jpeg" })

    stub_llm_response(name: "Test Recipe", ingredients: [ "1 cup flour" ], instructions: [ "Mix" ])

    RecipeImportJob.perform_now(@user.id, @recipe.id, url)

    @recipe.reload
    assert_equal :completed, @recipe.import_status.to_sym
    assert_equal "Test Recipe", @recipe.name
    assert @recipe.cover_image.attached?
  ensure
    ENV["APIFY_API_KEY"] = previous_key
  end

  test "marks recipe as failed when no recipe data found" do
    html = "<html><body><h1>Just a regular page</h1></body></html>"

    stub_request(:get, "https://example.com/page")
      .to_return(status: 200, body: html, headers: { "Content-Type" => "text/html" })

    stub_llm_no_recipe_found

    RecipeImportJob.perform_now(@user.id, @recipe.id, "https://example.com/page")

    @recipe.reload
    assert_equal :failed, @recipe.import_status.to_sym
    assert_not_nil @recipe.error_message
    assert_equal "Import from example.com failed.", @recipe.error_message
  end

  test "skips cover image download when cover_image_url points to private host" do
    result = RecipeImporter::Result.new(
      success?: true,
      recipe_attributes: { name: "Imported Recipe", ingredients: [ "1 cup flour" ], instructions: [ "Mix" ] },
      cover_image_url: "http://127.0.0.1/cover.jpg",
      error: nil,
      error_code: nil
    )
    importer = Minitest::Mock.new
    importer.expect(:import, result)

    RecipeImporter.stub(:new, importer) do
      RecipeImportJob.perform_now(@user.id, @recipe.id, "https://example.com/recipe")
    end

    importer.verify
    assert_not_requested(:get, "http://127.0.0.1/cover.jpg")

    @recipe.reload
    assert_equal :completed, @recipe.import_status.to_sym
    assert_equal "Imported Recipe", @recipe.name
    assert_not @recipe.cover_image.attached?
  end

  test "does nothing when recipe no longer exists" do
    deleted_id = @recipe.id
    @recipe.meal_plan_entries.destroy_all
    @recipe.destroy!

    assert_nothing_raised do
      RecipeImportJob.perform_now(@user.id, deleted_id, "https://example.com/recipe")
    end
  end

  test "does nothing when user no longer exists" do
    deleted_user_id = @user.id
    @user.destroy

    assert_nothing_raised do
      RecipeImportJob.perform_now(deleted_user_id, @recipe.id, "https://example.com/recipe")
    end
  end

  test "marks recipe as failed on unexpected error and re-raises" do
    stub_request(:get, "https://example.com/recipe")
      .to_raise(StandardError.new("Unexpected"))

    assert_raises(StandardError) do
      RecipeImportJob.perform_now(@user.id, @recipe.id, "https://example.com/recipe")
    end

    @recipe.reload
    assert_equal :failed, @recipe.import_status.to_sym
    assert_not_nil @recipe.error_message
    assert_includes @recipe.error_message, "example.com"
  end
end
