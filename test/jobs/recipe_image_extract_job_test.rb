require "test_helper"

class RecipeImageExtractJobTest < ActiveSupport::TestCase
  include ActionDispatch::TestProcess

  setup do
    @user = users(:one)
    @recipe = recipes(:one)
    @recipe.update!(import_status: :pending, name: "Importing...")
    @recipe.import_image.attach(
      fixture_file_upload("test/fixtures/files/test_image.png", "image/png")
    )
  end

  test "updates recipe with extracted data on success" do
    stub_llm_response(
      name: "Image Recipe",
      ingredients: [ "1 cup flour" ],
      instructions: [ "Mix and bake" ],
      prep_time: 10,
      cook_time: 20,
      servings: 4,
      notes: "From photo"
    )

    RecipeImageExtractJob.perform_now(@user.id, @recipe.id)

    @recipe.reload
    assert_equal :completed, @recipe.import_status.to_sym
    assert_equal "Image Recipe", @recipe.name
    assert_equal [ "1 cup flour" ], @recipe.ingredients.map(&:raw)
    assert_equal [ "Mix and bake" ], @recipe.instructions
    assert_equal 10, @recipe.prep_time
    assert_equal 20, @recipe.cook_time
    assert_equal 4, @recipe.servings
    assert_equal "From photo", @recipe.notes
  end

  test "marks a photo with no recipe text as not a recipe" do
    stub_llm_no_recipe_found

    RecipeImageExtractJob.perform_now(@user.id, @recipe.id)

    @recipe.reload
    assert_equal :failed, @recipe.import_status.to_sym
    assert_equal RecipeImageExtractJob::NO_RECIPE_IN_PHOTO_MESSAGE, @recipe.error_message
  end

  test "marks recipe as failed when LLM times out" do
    stub_request(:post, LlmStubHelper::OPENROUTER_ENDPOINT)
      .to_raise(Faraday::TimeoutError.new("execution expired"))

    RecipeImageExtractJob.perform_now(@user.id, @recipe.id)

    @recipe.reload
    assert_equal :failed, @recipe.import_status.to_sym
    assert_equal "Import failed.", @recipe.error_message
  end

  test "marks recipe as failed when import image is missing" do
    @recipe.import_image.purge

    RecipeImageExtractJob.perform_now(@user.id, @recipe.id)

    @recipe.reload
    assert_equal :failed, @recipe.import_status.to_sym
    assert_equal "Import failed.", @recipe.error_message
  end

  test "does nothing when recipe no longer exists" do
    deleted_id = @recipe.id
    @recipe.meal_plan_entries.destroy_all
    @recipe.destroy!

    assert_nothing_raised do
      RecipeImageExtractJob.perform_now(@user.id, deleted_id)
    end
  end

  test "does nothing when user no longer exists" do
    deleted_user_id = @user.id
    @user.destroy

    assert_nothing_raised do
      RecipeImageExtractJob.perform_now(deleted_user_id, @recipe.id)
    end
  end

  test "does nothing when recipe is already completed" do
    @recipe.update!(import_status: :completed, name: "Already Done")

    RecipeImageExtractJob.perform_now(@user.id, @recipe.id)

    @recipe.reload
    assert_equal "Already Done", @recipe.name
  end
end
