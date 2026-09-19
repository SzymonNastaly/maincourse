require "test_helper"

class Api::V1::RecipeSavesControllerTest < ActionDispatch::IntegrationTest
  setup do
    @user = users(:one)
    @other_user = users(:two)
    @cookbook = cookbooks(:one_personal)
    @other_cookbook = cookbooks(:two_personal)
    _record, token = ApiToken.generate_for(@user)
    @headers = { "Authorization" => "Bearer #{token}" }
  end

  test "requires authentication" do
    post api_v1_cookbook_recipe_saves_url(@cookbook), params: save_params, as: :json

    assert_response :unauthorized
    assert_equal 0, RecipeSave.count
  end

  test "copies the exact trusted sample contents and photograph without enqueueing extraction" do
    sample = JSON.parse(Rails.root.join("config/starter_recipes/tomato-orzo-v1.json").read)

    extraction_jobs = [
      RecipeImportJob,
      RecipeContentImportJob,
      RecipeTextExtractJob,
      RecipeImageExtractJob,
      ParseRecipeIngredientsJob
    ]
    assert_no_enqueued_jobs only: extraction_jobs do
      post_save(@cookbook)
    end

    assert_response :success
    recipe = Recipe.find(response.parsed_body.fetch("recipe_id"))
    assert_equal @user, recipe.user
    assert_equal @cookbook, recipe.cookbook
    assert_equal sample.fetch("name"), recipe.name
    assert_equal "An original MainCourse example recipe.", recipe.notes
    assert_equal sample.fetch("prep_time"), recipe.prep_time
    assert_equal sample.fetch("cook_time"), recipe.cook_time
    assert_equal sample.fetch("servings"), recipe.servings
    assert_equal sample.fetch("instructions"), recipe.instructions
    assert_equal sample.fetch("key"), recipe.starter_recipe_key
    assert_nil recipe.source_url
    assert_equal sample.fetch("ingredients").map { |entry| entry.fetch("raw") }, recipe.ingredients.map(&:raw)
    assert_equal sample.fetch("ingredients").map { |entry| entry["amount"]&.to_d }, recipe.ingredients.map(&:amount)
    assert_equal sample.fetch("ingredients").map { |entry| entry["unit"] }, recipe.ingredients.map(&:unit)
    assert_equal sample.fetch("ingredients").map { |entry| entry.fetch("name") }, recipe.ingredients.map(&:name)
    assert_equal sample.fetch("ingredients").map { |entry| entry["note"] }, recipe.ingredients.map(&:note)
    assert recipe.cover_image.attached?
    assert_equal sample.fetch("image_name"), recipe.cover_image.filename.to_s
    assert_equal "image/jpeg", recipe.cover_image.content_type
    receipt = RecipeSave.find_by!(saved_recipe: recipe)
    assert_equal @cookbook.id, receipt.original_destination_cookbook_id
    assert_equal "sample", receipt.source_type
    assert_equal sample.fetch("key"), receipt.source_key
  end

  test "returns the saved recipe through the normal detail endpoint" do
    post_save(@cookbook)
    recipe_id = response.parsed_body.fetch("recipe_id")

    get api_v1_recipe_url(recipe_id), headers: @headers, as: :json

    assert_response :success
    assert_equal "tomato-orzo-v1", response.parsed_body.fetch("starter_recipe_key")
    assert_equal "One-pan tomato & chickpea orzo", response.parsed_body.fetch("name")
    assert_equal "An original MainCourse example recipe.", response.parsed_body.fetch("notes")
    assert_nil response.parsed_body["source_url"]
    olive_oil = response.parsed_body.fetch("structured_ingredients").first
    assert_equal "1.0", olive_oil.fetch("amount").to_s
    assert response.parsed_body.dig("cover_images", "hero").present?
  end

  test "owner can save into an explicitly selected destination" do
    post_save(@cookbook)

    assert_response :success
    assert_equal @cookbook.id, response.parsed_body.fetch("cookbook_id")
  end

  test "collaborator can save into an explicitly selected destination" do
    shared = Cookbook.create!(name: "Shared", personal: false)
    CookbookMembership.create!(cookbook: shared, user: @other_user, role: :owner)
    CookbookMembership.create!(cookbook: shared, user: @user, role: :collaborator)

    post_save(shared)

    assert_response :success
    recipe = Recipe.find(response.parsed_body.fetch("recipe_id"))
    assert_equal shared, recipe.cookbook
    assert_equal @user, recipe.user
  end

  test "rejects a destination the user cannot access" do
    post_save(@other_cookbook)

    assert_response :forbidden
    assert_equal 0, RecipeSave.count
  end

  test "uses the authorized path destination independently of the ambient cookbook header" do
    post_save(@cookbook, headers: @headers.merge("X-Cookbook-Id" => @other_cookbook.id.to_s))

    assert_response :success
    assert_equal @cookbook.id, response.parsed_body.fetch("cookbook_id")
    assert_equal @cookbook, Recipe.find(response.parsed_body.fetch("recipe_id")).cookbook
  end

  test "rejects an unknown source type" do
    post_save(@cookbook, source: { type: "recipe", key: "tomato-orzo-v1" })

    assert_response :unprocessable_entity
    assert_equal 0, RecipeSave.count
  end

  test "rejects an unknown sample key" do
    post_save(@cookbook, source: { type: "sample", key: "unknown" })

    assert_response :unprocessable_entity
    assert_equal 0, RecipeSave.count
  end

  test "rejects an invalid request UUID" do
    post_save(@cookbook, request_id: "not-a-uuid")

    assert_response :unprocessable_entity
    assert_equal 0, RecipeSave.count
  end

  test "rejects an array source body" do
    post_save(@cookbook, source: [ { type: "sample", key: "tomato-orzo-v1" } ])

    assert_response :unprocessable_entity
    assert_equal 0, RecipeSave.count
  end

  test "rejects a scalar source body" do
    post_save(@cookbook, source: "sample")

    assert_response :unprocessable_entity
    assert_equal 0, RecipeSave.count
  end

  test "rejects non-string source fields" do
    post_save(@cookbook, source: { type: 1, key: "tomato-orzo-v1" })
    assert_response :unprocessable_entity
    assert_equal "Source type and key must be strings", response.parsed_body.fetch("error")

    post_save(@cookbook, source: { type: "sample", key: 1 })
    assert_response :unprocessable_entity
    assert_equal "Source type and key must be strings", response.parsed_body.fetch("error")
    assert_equal 0, RecipeSave.count
  end

  test "upload failure rolls back the operation and the same request can retry" do
    request_id = SecureRandom.uuid
    counts_before = [ Recipe.count, RecipeSave.count, ActiveStorage::Blob.count ]
    failed_upload = ->(*, **) { raise IOError, "storage unavailable" }

    assert_raises(IOError) do
      ActiveStorage::Blob.service.stub(:upload, failed_upload) do
        post_save(@cookbook, request_id: request_id)
      end
    end

    assert_equal counts_before, [ Recipe.count, RecipeSave.count, ActiveStorage::Blob.count ]

    post_save(@cookbook, request_id: request_id)

    assert_response :success
    recipe = Recipe.find(response.parsed_body.fetch("recipe_id"))
    assert recipe.cover_image.attached?
    assert recipe.cover_image.blob.service.exist?(recipe.cover_image.key)
  end

  test "retry returns the same recipe without consuming import allowance" do
    before = @user.monthly_import_count
    ids = []
    request_id = SecureRandom.uuid

    assert_difference("Recipe.count", 1) do
      2.times do
        post_save(@cookbook, request_id: request_id)
        assert_response :success
        ids << response.parsed_body.fetch("recipe_id")
      end
    end

    assert_equal 1, ids.uniq.length
    assert_equal before, @user.reload.monthly_import_count
    assert_equal 1, RecipeSave.where(user: @user, request_id: request_id).count
  end

  test "rejects request UUID reuse with a different destination" do
    shared = Cookbook.create!(name: "Shared", personal: false)
    CookbookMembership.create!(cookbook: shared, user: @user, role: :owner)
    request_id = SecureRandom.uuid
    post_save(@cookbook, request_id: request_id)

    assert_no_difference("Recipe.count") do
      post_save(shared, request_id: request_id)
    end

    assert_response :conflict
  end

  test "rejects request UUID reuse with a different source reference" do
    request_id = SecureRandom.uuid
    post_save(@cookbook, request_id: request_id)

    assert_no_difference("Recipe.count") do
      post_save(@cookbook, request_id: request_id, source: { type: "sample", key: "another-sample" })
    end

    assert_response :conflict
  end

  test "replay after deletion returns gone and preserves a tombstoned receipt" do
    request_id = SecureRandom.uuid
    post_save(@cookbook, request_id: request_id)
    recipe = Recipe.find(response.parsed_body.fetch("recipe_id"))
    receipt = RecipeSave.find_by!(user: @user, request_id: request_id)
    recipe.destroy!

    assert_nil receipt.reload.saved_recipe_id
    assert_no_difference("Recipe.count") do
      post_save(@cookbook, request_id: request_id)
    end
    assert_response :gone
  end

  test "a new request UUID deliberately recreates a deleted sample" do
    post_save(@cookbook)
    old_recipe_id = response.parsed_body.fetch("recipe_id")
    Recipe.find(old_recipe_id).destroy!

    assert_difference("Recipe.count", 1) do
      post_save(@cookbook, request_id: SecureRandom.uuid)
    end

    assert_response :success
    assert_not_equal old_recipe_id, response.parsed_body.fetch("recipe_id")
  end

  test "replay returns the recipe's current accessible cookbook after a move" do
    shared = Cookbook.create!(name: "Shared", personal: false)
    CookbookMembership.create!(cookbook: shared, user: @user, role: :owner)
    request_id = SecureRandom.uuid
    post_save(@cookbook, request_id: request_id)
    recipe = Recipe.find(response.parsed_body.fetch("recipe_id"))
    recipe.update!(cookbook: shared)

    post_save(@cookbook, request_id: request_id)

    assert_response :success
    assert_equal recipe.id, response.parsed_body.fetch("recipe_id")
    assert_equal shared.id, response.parsed_body.fetch("cookbook_id")
  end

  test "replay returns gone when the moved recipe is no longer accessible" do
    request_id = SecureRandom.uuid
    post_save(@cookbook, request_id: request_id)
    recipe = Recipe.find(response.parsed_body.fetch("recipe_id"))
    recipe.update_column(:cookbook_id, @other_cookbook.id)

    assert_no_difference("Recipe.count") do
      post_save(@cookbook, request_id: request_id)
    end

    assert_response :gone
  end

  private

  def save_params(source: { type: "sample", key: "tomato-orzo-v1" }, request_id: SecureRandom.uuid)
    { source: source, request_id: request_id }
  end

  def post_save(cookbook, source: { type: "sample", key: "tomato-orzo-v1" }, request_id: SecureRandom.uuid, headers: @headers)
    post api_v1_cookbook_recipe_saves_url(cookbook),
      params: save_params(source: source, request_id: request_id),
      headers: headers,
      as: :json
  end
end
