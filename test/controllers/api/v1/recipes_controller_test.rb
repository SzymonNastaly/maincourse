require "test_helper"

class Api::V1::RecipesControllerTest < ActionDispatch::IntegrationTest
  setup do
    @user = users(:one)
    @other_user = users(:two)
    @cookbook = cookbooks(:one_personal)
    @other_cookbook = cookbooks(:two_personal)
    _token_record, @raw_token = ApiToken.generate_for(@user)
    @auth_headers = { "Authorization" => "Bearer #{@raw_token}" }
  end

  test "index returns user's recipes" do
    get api_v1_recipes_url, headers: @auth_headers, as: :json

    assert_response :success
    json = response.parsed_body
    assert_kind_of Array, json
    # User one has 2 recipes (one and three)
    assert_equal 2, json.length
    recipe_names = json.map { |r| r["name"] }
    assert_includes recipe_names, "Pasta Carbonara"
    assert_includes recipe_names, "Caesar Salad"
  end

  test "index returns recipes ordered by updated_at desc" do
    get api_v1_recipes_url, headers: @auth_headers, as: :json

    assert_response :success
    json = response.parsed_body
    timestamps = json.map { |r| r["updated_at"] }
    assert_equal timestamps.sort.reverse, timestamps
  end

  test "index with favorites filter returns only favorites" do
    get api_v1_recipes_url, params: { favorites: "true" }, headers: @auth_headers, as: :json

    assert_response :success
    json = response.parsed_body
    assert_equal 1, json.length
    assert_equal "Caesar Salad", json.first["name"]
    assert json.first["favorite"]
  end

  test "index returns expected fields" do
    get api_v1_recipes_url, headers: @auth_headers, as: :json

    assert_response :success
    json = response.parsed_body
    recipe = json.first
    assert recipe.key?("id")
    assert recipe.key?("name")
    assert recipe.key?("prep_time")
    assert recipe.key?("cook_time")
    assert recipe.key?("favorite")
    assert recipe.key?("cover_image_url")
    assert recipe.key?("cover_images")
    assert recipe.key?("updated_at")
  end

  test "index requires authentication" do
    get api_v1_recipes_url, as: :json

    assert_response :unauthorized
  end

  test "index returns 401 for expired token" do
    expired_headers = { "Authorization" => "Bearer test_token_expired" }

    get api_v1_recipes_url, headers: expired_headers, as: :json

    assert_response :unauthorized
  end

  test "index returns 401 for revoked token" do
    revoked_headers = { "Authorization" => "Bearer test_token_revoked" }

    get api_v1_recipes_url, headers: revoked_headers, as: :json

    assert_response :unauthorized
  end

  test "index does not return other user's recipes" do
    get api_v1_recipes_url, headers: @auth_headers, as: :json

    assert_response :success
    json = response.parsed_body
    recipe_names = json.map { |r| r["name"] }
    assert_not_includes recipe_names, "Chicken Curry"
  end

  test "batch returns recipe details with cursor paging" do
    recipe1 = recipes(:one)
    recipe2 = recipes(:three)

    recipe1.update!(updated_at: 2.days.ago)
    recipe2.update!(updated_at: 1.day.ago)

    get batch_api_v1_recipes_url, params: { limit: 1 }, headers: @auth_headers, as: :json

    assert_response :success
    json = response.parsed_body
    assert_equal 1, json["recipes"].length
    assert json["next_cursor"].present?
    assert_equal recipe1.id, json["recipes"].first["id"]

    get batch_api_v1_recipes_url,
      params: { limit: 1, cursor: json["next_cursor"] },
      headers: @auth_headers,
      as: :json

    assert_response :success
    json2 = response.parsed_body
    assert_equal [ recipe2.id ], json2["recipes"].map { |r| r["id"] }
  end

  test "batch returns 422 for invalid cursor" do
    get batch_api_v1_recipes_url, params: { cursor: "invalid" }, headers: @auth_headers, as: :json

    assert_response :unprocessable_entity
    json = response.parsed_body
    assert_equal "Invalid cursor", json["error"]
  end

  test "batch requires authentication" do
    get batch_api_v1_recipes_url, as: :json

    assert_response :unauthorized
  end

  test "show returns recipe details" do
    recipe = recipes(:one)

    get api_v1_recipe_url(recipe), headers: @auth_headers, as: :json

    assert_response :success
    json = response.parsed_body
    assert_equal recipe.id, json["id"]
    assert_equal recipe.name, json["name"]
    assert_equal recipe.prep_time, json["prep_time"]
    assert_equal recipe.cook_time, json["cook_time"]
    assert_equal recipe.servings, json["servings"]
    assert_equal recipe.ingredients.map(&:raw), json["ingredients"]
    assert_equal recipe.instructions, json["instructions"]
    assert json.key?("tags")
    assert json.key?("cover_images")
    assert json.key?("created_at")
    assert json.key?("updated_at")
  end

  test "show exposes optional ingredient enrichment metadata" do
    recipe = recipes(:one)
    ingredient = recipe.ingredients.first
    ingredient.update!(
      canonical_name: "olive oil",
      canonical_unit: "tablespoon",
      category: "oils_spices_condiments",
      enrichment_version: Llm::IngredientInstructions::VERSION
    )

    get api_v1_recipe_url(recipe), headers: @auth_headers, as: :json

    assert_response :success
    payload = response.parsed_body["structured_ingredients"].find { |entry| entry["id"] == ingredient.id }
    assert_equal "olive oil", payload["canonical_name"]
    assert_equal "tablespoon", payload["canonical_unit"]
    assert_equal "oils_spices_condiments", payload["category"]
    assert_equal Llm::IngredientInstructions::VERSION, payload["enrichment_version"]
  end

  test "show requires authentication" do
    recipe = recipes(:one)

    get api_v1_recipe_url(recipe), as: :json

    assert_response :unauthorized
  end

  test "show returns 404 for other user's recipe" do
    other_recipe = recipes(:two)

    get api_v1_recipe_url(other_recipe), headers: @auth_headers, as: :json

    assert_response :not_found
    json = response.parsed_body
    assert_equal "Recipe not found", json["error"]
  end

  test "show returns 404 for nonexistent recipe" do
    get api_v1_recipe_url(id: 999999), headers: @auth_headers, as: :json

    assert_response :not_found
  end

  test "import creates recipe and enqueues job" do
    assert_enqueued_with(job: RecipeImportJob) do
      post import_api_v1_recipes_url,
        params: { url: "https://example.com/recipe" },
        headers: @auth_headers,
        as: :json
    end

    assert_response :accepted
    json = response.parsed_body
    assert json["id"].present?
    assert_equal "pending", json["import_status"]

    recipe = Recipe.find(json["id"])
    assert_equal "Importing...", recipe.name
    assert_equal "https://example.com/recipe", recipe.source_url
    assert_equal @user.id, recipe.user_id
  end

  test "import returns error for blank URL" do
    post import_api_v1_recipes_url,
      params: { url: "" },
      headers: @auth_headers,
      as: :json

    assert_response :unprocessable_entity
    json = response.parsed_body
    assert_equal "URL is required", json["error"]
  end

  test "import returns error for invalid URL" do
    post import_api_v1_recipes_url,
      params: { url: "not-a-url" },
      headers: @auth_headers,
      as: :json

    assert_response :unprocessable_entity
    json = response.parsed_body
    assert json["error"].present?
  end

  test "import returns error for localhost URL" do
    post import_api_v1_recipes_url,
      params: { url: "http://localhost/recipe" },
      headers: @auth_headers,
      as: :json

    assert_response :unprocessable_entity
  end

  test "import requires authentication" do
    post import_api_v1_recipes_url,
      params: { url: "https://example.com/recipe" },
      as: :json

    assert_response :unauthorized
  end

  test "import_with_content enqueues job with meta tags for cover fallback" do
    payload = {
      url: "https://example.com/recipe",
      json_ld: [ "{\"@type\":\"Recipe\"}" ],
      html: "<body><article>Recipe</article></body>",
      meta_tags: { "og:image" => "https://example.com/og-cover.jpg", "og:title" => "Example Recipe" },
      cover_image_candidates: [ "https://example.com/dom-cover.jpg", "https://example.com/dom-cover-2.jpg" ]
    }

    assert_enqueued_jobs 1, only: RecipeContentImportJob do
      post import_with_content_api_v1_recipes_url,
        params: payload,
        headers: @auth_headers,
        as: :json
    end

    assert_response :accepted

    job = enqueued_jobs.find { |entry| entry[:job] == RecipeContentImportJob }
    assert_not_nil job

    args = job[:args]
    assert_equal @user.id, args[0]
    assert_equal "https://example.com/recipe", args[2]
    assert_equal [ "{\"@type\":\"Recipe\"}" ], args[3]
    assert_equal "<body><article>Recipe</article></body>", args[4]
    assert_equal "https://example.com/og-cover.jpg", args[5]["og:image"]
    assert_equal "Example Recipe", args[5]["og:title"]
    assert_equal [ "https://example.com/dom-cover.jpg", "https://example.com/dom-cover-2.jpg" ], args[6]
  end

  test "import_with_content returns error for blank URL" do
    post import_with_content_api_v1_recipes_url,
      params: { url: "", json_ld: [], html: "<body></body>" },
      headers: @auth_headers,
      as: :json

    assert_response :unprocessable_entity
    json = response.parsed_body
    assert_equal "URL is required", json["error"]
  end

  test "import_with_content returns 413 when payload exceeds 2MB" do
    validation_result = Struct.new(:success?, :error).new(true, nil)
    validator = Struct.new(:result) do
      def validate
        result
      end
    end.new(validation_result)

    large_html = "a" * (2.megabytes + 1)

    RecipeImporters::UrlValidator.stub(:new, ->(*) { validator }) do
      assert_no_enqueued_jobs only: RecipeContentImportJob do
        post import_with_content_api_v1_recipes_url,
          params: { url: "https://example.com/recipe", json_ld: [], html: large_html },
          headers: @auth_headers,
          as: :json
      end
    end

    assert_response :payload_too_large
  end

  # extract_from_text tests

  test "extract_from_text creates recipe and enqueues job" do
    text = "Chocolate Cake\n\nIngredients:\n- 2 cups flour"

    assert_enqueued_with(job: RecipeTextExtractJob) do
      post extract_from_text_api_v1_recipes_url,
        params: { text: text },
        headers: @auth_headers,
        as: :json
    end

    assert_response :accepted
    json = response.parsed_body
    assert json["id"].present?
    assert_equal "pending", json["import_status"]

    recipe = Recipe.find(json["id"])
    assert_equal "Importing...", recipe.name
    assert_nil recipe.source_url
    assert_equal @user.id, recipe.user_id
  end

  test "extract_from_text returns error for blank text" do
    post extract_from_text_api_v1_recipes_url,
      params: { text: "" },
      headers: @auth_headers,
      as: :json

    assert_response :unprocessable_entity
    json = response.parsed_body
    assert_equal "Text is required", json["error"]
  end

  test "extract_from_text returns error for text too long" do
    long_text = "a" * 50_001

    post extract_from_text_api_v1_recipes_url,
      params: { text: long_text },
      headers: @auth_headers,
      as: :json

    assert_response :unprocessable_entity
    json = response.parsed_body
    assert_equal "Text too long (max 50,000 chars)", json["error"]
  end

  test "extract_from_text requires authentication" do
    post extract_from_text_api_v1_recipes_url,
      params: { text: "Some recipe text" },
      as: :json

    assert_response :unauthorized
  end

  # extract_from_image tests

  test "extract_from_image creates recipe and enqueues job" do
    image = fixture_file_upload("test/fixtures/files/test_image.png", "image/png")

    assert_enqueued_with(job: RecipeImageExtractJob) do
      post extract_from_image_api_v1_recipes_url,
        params: { image: image },
        headers: @auth_headers
    end

    assert_response :accepted
    json = response.parsed_body
    assert json["id"].present?
    assert_equal "pending", json["import_status"]

    recipe = Recipe.find(json["id"])
    assert_equal "Importing...", recipe.name
    assert_equal @user.id, recipe.user_id
    assert recipe.import_image.attached?
    assert_not recipe.cover_image.attached?
  end

  test "extract_from_image returns error for blank image" do
    post extract_from_image_api_v1_recipes_url,
      params: { image: nil },
      headers: @auth_headers

    assert_response :unprocessable_entity
    json = response.parsed_body
    assert_equal "Image is required", json["error"]
  end

  test "extract_from_image returns error for non-image upload" do
    file = fixture_file_upload("test/fixtures/files/test.txt", "text/plain")

    post extract_from_image_api_v1_recipes_url,
      params: { image: file },
      headers: @auth_headers

    assert_response :unprocessable_entity
    json = response.parsed_body
    assert_equal "Image must be an image", json["error"]
  end

  test "extract_from_image requires authentication" do
    image = fixture_file_upload("test/fixtures/files/test_image.png", "image/png")

    post extract_from_image_api_v1_recipes_url,
      params: { image: image }

    assert_response :unauthorized
  end

  # Failed recipe handling tests

  test "index tracks first fetch of failed recipes" do
    recipe = @cookbook.recipes.create!(user: @user,
      name: "Failed Import",
      import_status: :failed,
      error_message: "Import from example.com failed."
    )
    assert_nil recipe.failed_recipe_fetched_at

    get api_v1_recipes_url, headers: @auth_headers, as: :json

    recipe.reload
    assert_not_nil recipe.failed_recipe_fetched_at
    assert_in_delta Time.current, recipe.failed_recipe_fetched_at, 2.seconds
  end

  test "index deletes failed recipes after 1 minute" do
    recipe = @cookbook.recipes.create!(user: @user,
      name: "Failed Import",
      import_status: :failed,
      error_message: "Import from example.com failed.",
      failed_recipe_fetched_at: 2.minutes.ago
    )

    assert_difference "@user.recipes.count", -1 do
      get api_v1_recipes_url, headers: @auth_headers, as: :json
    end

    assert_raises(ActiveRecord::RecordNotFound) { recipe.reload }
  end

  test "index does not delete recently fetched failed recipes" do
    recipe = @cookbook.recipes.create!(user: @user,
      name: "Failed Import",
      import_status: :failed,
      error_message: "Import from example.com failed.",
      failed_recipe_fetched_at: 30.seconds.ago
    )

    assert_no_difference "@user.recipes.count" do
      get api_v1_recipes_url, headers: @auth_headers, as: :json
    end

    assert_nothing_raised { recipe.reload }
  end

  test "index includes error_message in recipe list JSON" do
    @cookbook.recipes.create!(user: @user,
      name: "Failed Import",
      import_status: :failed,
      error_message: "Import from test.com failed."
    )

    get api_v1_recipes_url, headers: @auth_headers, as: :json

    assert_response :success
    json = response.parsed_body
    failed_recipe = json.find { |r| r["name"] == "Failed Import" }

    assert_not_nil failed_recipe
    assert_equal "Import from test.com failed.", failed_recipe["error_message"]
    assert_equal "failed", failed_recipe["import_status"]
  end

  # MARK: - Destroy Tests

  test "destroy returns 204 on successful deletion" do
    recipe = @cookbook.recipes.create!(user: @user, name: "To Delete")

    delete api_v1_recipe_url(recipe), headers: @auth_headers, as: :json

    assert_response :no_content
    assert_nil Recipe.find_by(id: recipe.id)
  end

  test "destroy returns 422 when recipe is still planned" do
    recipe = recipes(:one)

    delete api_v1_recipe_url(recipe), headers: @auth_headers, as: :json

    assert_response :unprocessable_entity
    assert_equal "Could not delete recipe", response.parsed_body["error"]
    assert Recipe.exists?(recipe.id)
  end

  test "destroy returns 404 for non-existent recipe" do
    delete api_v1_recipe_url(id: 999999), headers: @auth_headers, as: :json

    assert_response :not_found
    json = response.parsed_body
    assert_equal "Recipe not found", json["error"]
  end

  test "destroy returns 404 when trying to delete another user's recipe" do
    other_recipe = @other_cookbook.recipes.create!(user: @other_user, name: "Other User's Recipe")

    delete api_v1_recipe_url(other_recipe), headers: @auth_headers, as: :json

    assert_response :not_found
    assert Recipe.exists?(other_recipe.id)
  end

  test "destroy requires authentication" do
    recipe = @cookbook.recipes.create!(user: @user, name: "Protected Recipe")

    delete api_v1_recipe_url(recipe), as: :json

    assert_response :unauthorized
    assert Recipe.exists?(recipe.id)
  end

  # MARK: - Update Tests

  test "update changes recipe attributes" do
    recipe = @cookbook.recipes.create!(user: @user, name: "Old Name", notes: "Old notes")

    patch api_v1_recipe_url(recipe),
      params: { name: "New Name", notes: "New notes" },
      headers: @auth_headers,
      as: :json

    assert_response :success
    json = response.parsed_body
    assert_equal "New Name", json["name"]
    assert_equal "New notes", json["notes"]
    assert_equal "New Name", recipe.reload.name
  end

  test "update moves recipe to another cookbook the user belongs to" do
    shared = Cookbook.create!(name: "Shared", personal: false)
    CookbookMembership.create!(cookbook: shared, user: @user, role: :owner)
    recipe = @cookbook.recipes.create!(user: @user, name: "Moveable")

    patch api_v1_recipe_url(recipe),
      params: { cookbook_id: shared.id },
      headers: @auth_headers,
      as: :json

    assert_response :success
    assert_equal shared.id, recipe.reload.cookbook_id
  end

  test "update returns 422 when moving to a cookbook the user does not belong to" do
    other_cookbook = Cookbook.create!(name: "Not Mine", personal: false)
    recipe = @cookbook.recipes.create!(user: @user, name: "Stuck")

    patch api_v1_recipe_url(recipe),
      params: { cookbook_id: other_cookbook.id },
      headers: @auth_headers,
      as: :json

    assert_response :unprocessable_entity
    json = response.parsed_body
    assert_equal "Target cookbook not found or not accessible", json["error"]
    assert_equal @cookbook.id, recipe.reload.cookbook_id
  end

  test "update returns 404 for non-existent recipe" do
    patch api_v1_recipe_url(id: 999999),
      params: { name: "Nope" },
      headers: @auth_headers,
      as: :json

    assert_response :not_found
  end

  test "update returns 404 for another user's recipe" do
    other_recipe = @other_cookbook.recipes.create!(user: @other_user, name: "Theirs")

    patch api_v1_recipe_url(other_recipe),
      params: { name: "Mine now" },
      headers: @auth_headers,
      as: :json

    assert_response :not_found
    assert_equal "Theirs", other_recipe.reload.name
  end

  test "update changes ingredients" do
    recipe = @cookbook.recipes.create!(user: @user, name: "Test")
    recipe.replace_ingredients_from_strings([ "old ingredient" ])

    patch api_v1_recipe_url(recipe),
      params: { ingredients: [ "flour", "sugar", "butter" ] },
      headers: @auth_headers,
      as: :json

    assert_response :success
    json = response.parsed_body
    assert_equal [ "flour", "sugar", "butter" ], json["ingredients"]
    assert_equal [ "flour", "sugar", "butter" ], recipe.reload.ingredients.map(&:raw)
  end

  test "update changes instructions" do
    recipe = @cookbook.recipes.create!(user: @user, name: "Test", instructions: [ "old step" ])

    patch api_v1_recipe_url(recipe),
      params: { instructions: [ "step 1", "step 2" ] },
      headers: @auth_headers,
      as: :json

    assert_response :success
    json = response.parsed_body
    assert_equal [ "step 1", "step 2" ], json["instructions"]
    assert_equal [ "step 1", "step 2" ], recipe.reload.instructions
  end

  test "update strips blank entries from ingredients and instructions" do
    recipe = @cookbook.recipes.create!(user: @user, name: "Test")

    patch api_v1_recipe_url(recipe),
      params: { ingredients: [ "flour", "", "sugar" ], instructions: [ "step 1", "" ] },
      headers: @auth_headers,
      as: :json

    assert_response :success
    json = response.parsed_body
    assert_equal [ "flour", "sugar" ], json["ingredients"]
    assert_equal [ "step 1" ], json["instructions"]
  end

  test "update attaches cover image" do
    recipe = @cookbook.recipes.create!(user: @user, name: "Test")
    image = fixture_file_upload("test/fixtures/files/test_image.png", "image/png")

    patch api_v1_recipe_url(recipe),
      params: { cover_image: image },
      headers: @auth_headers

    assert_response :success
    assert recipe.reload.cover_image.attached?
    json = response.parsed_body
    assert json["cover_image_url"].present?
    assert_equal %w[card hero thumb], json.fetch("cover_images").keys.sort
    assert json.dig("cover_images", "card").present?
    assert json.dig("cover_images", "hero").present?
    assert json.dig("cover_images", "thumb").present?
  end

  test "update replaces existing cover image" do
    recipe = @cookbook.recipes.create!(user: @user, name: "Test")
    recipe.cover_image.attach(
      io: File.open(Rails.root.join("test/fixtures/files/test_image.png")),
      filename: "old.png",
      content_type: "image/png"
    )
    assert recipe.cover_image.attached?
    old_blob_id = recipe.cover_image.blob.id

    new_image = fixture_file_upload("test/fixtures/files/test_image.png", "image/png")

    patch api_v1_recipe_url(recipe),
      params: { cover_image: new_image },
      headers: @auth_headers

    assert_response :success
    assert recipe.reload.cover_image.attached?
    assert_not_equal old_blob_id, recipe.cover_image.blob.id
  end

  test "update can change multiple fields at once" do
    recipe = @cookbook.recipes.create!(user: @user, name: "Old", instructions: [ "b" ], notes: "old")
    recipe.replace_ingredients_from_strings([ "a" ])

    patch api_v1_recipe_url(recipe),
      params: { name: "New", ingredients: [ "x", "y" ], instructions: [ "z" ], notes: "new", prep_time: 10, cook_time: 20, servings: 4 },
      headers: @auth_headers,
      as: :json

    assert_response :success
    json = response.parsed_body
    assert_equal "New", json["name"]
    assert_equal [ "x", "y" ], json["ingredients"]
    assert_equal [ "z" ], json["instructions"]
    assert_equal "new", json["notes"]
    assert_equal 10, json["prep_time"]
    assert_equal 20, json["cook_time"]
    assert_equal 4, json["servings"]
  end

  test "update by collaborator in shared cookbook" do
    shared = Cookbook.create!(name: "Shared", personal: false)
    CookbookMembership.create!(cookbook: shared, user: @other_user, role: :owner)
    CookbookMembership.create!(cookbook: shared, user: @user, role: :collaborator)
    recipe = shared.recipes.create!(name: "Original", user: @other_user)

    patch api_v1_recipe_url(recipe),
      params: { name: "Edited by collaborator", ingredients: [ "new ingredient" ] },
      headers: @auth_headers.merge("X-Cookbook-Id" => shared.id.to_s),
      as: :json

    assert_response :success
    json = response.parsed_body
    assert_equal "Edited by collaborator", json["name"]
    assert_equal [ "new ingredient" ], json["ingredients"]
  end

  test "update requires authentication" do
    recipe = @cookbook.recipes.create!(user: @user, name: "Protected")

    patch api_v1_recipe_url(recipe),
      params: { name: "Hacked" },
      as: :json

    assert_response :unauthorized
    assert_equal "Protected", recipe.reload.name
  end

  # ===================
  # SHARED COOKBOOK OPERATIONS
  # ===================

  test "import creates recipe in shared cookbook via X-Cookbook-Id" do
    shared = Cookbook.create!(name: "Shared", personal: false)
    CookbookMembership.create!(cookbook: shared, user: @user, role: :owner)

    assert_enqueued_with(job: RecipeImportJob) do
      post import_api_v1_recipes_url,
        params: { url: "https://example.com/shared-recipe" },
        headers: @auth_headers.merge("X-Cookbook-Id" => shared.id.to_s),
        as: :json
    end

    assert_response :accepted
    recipe = Recipe.find(response.parsed_body["id"])
    assert_equal shared.id, recipe.cookbook_id
  end

  test "destroy recipe in shared cookbook via X-Cookbook-Id" do
    shared = Cookbook.create!(name: "Shared", personal: false)
    CookbookMembership.create!(cookbook: shared, user: @user, role: :owner)
    recipe = shared.recipes.create!(name: "Shared Recipe", user: @user)

    delete api_v1_recipe_url(recipe),
      headers: @auth_headers.merge("X-Cookbook-Id" => shared.id.to_s),
      as: :json

    assert_response :no_content
    assert_nil Recipe.find_by(id: recipe.id)
  end

  test "favorite recipe in shared cookbook via X-Cookbook-Id" do
    shared = Cookbook.create!(name: "Shared", personal: false)
    CookbookMembership.create!(cookbook: shared, user: @user, role: :owner)
    recipe = shared.recipes.create!(name: "Shared Recipe", user: @user, favorite: false)

    put api_v1_recipe_favorite_url(recipe),
      headers: @auth_headers.merge("X-Cookbook-Id" => shared.id.to_s),
      as: :json

    assert_response :success
    assert recipe.reload.favorite
  end

  test "cannot access shared cookbook recipe without X-Cookbook-Id header" do
    shared = Cookbook.create!(name: "Shared", personal: false)
    CookbookMembership.create!(cookbook: shared, user: @user, role: :owner)
    recipe = shared.recipes.create!(name: "Shared Only", user: @user)

    # Without X-Cookbook-Id, defaults to personal cookbook — recipe not found there
    delete api_v1_recipe_url(recipe), headers: @auth_headers, as: :json

    assert_response :not_found
  end

  test "collaborator can access shared cookbook recipes" do
    shared = Cookbook.create!(name: "Shared", personal: false)
    CookbookMembership.create!(cookbook: shared, user: @other_user, role: :owner)
    CookbookMembership.create!(cookbook: shared, user: @user, role: :collaborator)
    shared.recipes.create!(name: "Owner Recipe", user: @other_user)

    get api_v1_recipes_url,
      headers: @auth_headers.merge("X-Cookbook-Id" => shared.id.to_s),
      as: :json

    assert_response :success
    assert_equal 1, response.parsed_body.length
    assert_equal "Owner Recipe", response.parsed_body.first["name"]
  end
end
