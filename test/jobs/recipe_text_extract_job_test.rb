require "test_helper"

class RecipeTextExtractJobTest < ActiveSupport::TestCase
  setup do
    @user = users(:one)
    @recipe = recipes(:one)
    @recipe.update!(import_status: :pending, name: "Importing...")
  end

  test "updates recipe with extracted data on success" do
    text = "Chocolate Cake\n\nIngredients:\n- 2 cups flour\n- 1 cup sugar\n\nInstructions:\n1. Mix dry ingredients\n2. Bake at 350F"

    stub_llm_response(
      name: "Chocolate Cake",
      ingredients: [ "2 cups flour", "1 cup sugar" ],
      instructions: [ "Mix dry ingredients", "Bake at 350F" ],
      prep_time: 15,
      cook_time: 30,
      servings: 8,
      notes: "A delicious family recipe"
    )

    RecipeTextExtractJob.perform_now(@user.id, @recipe.id, text)

    @recipe.reload
    assert_equal :completed, @recipe.import_status.to_sym
    assert_equal "Chocolate Cake", @recipe.name
    assert_equal [ "2 cups flour", "1 cup sugar" ], @recipe.ingredients.map(&:raw)
    assert_equal [ "Mix dry ingredients", "Bake at 350F" ], @recipe.instructions
    assert_equal 15, @recipe.prep_time
    assert_equal 30, @recipe.cook_time
    assert_equal 8, @recipe.servings
    assert_equal "A delicious family recipe", @recipe.notes
  end

  test "queues a timer parse for the extracted steps" do
    stub_llm_response(name: "Soup", ingredients: [ "water" ], instructions: [ "Simmer 20 minutes" ])

    assert_enqueued_with(job: ParseInstructionTimersJob, args: [ @recipe.id ]) do
      RecipeTextExtractJob.perform_now(@user.id, @recipe.id, "Soup: simmer water 20 minutes")
    end
  end

  test "does not set source_url since extraction is from raw text" do
    text = "Simple Recipe\n\nIngredients:\n- 1 item\n\nDo the thing."
    @recipe.update!(source_url: nil)

    stub_llm_response(
      name: "Simple Recipe",
      ingredients: [ "1 item" ],
      instructions: [ "Do the thing" ]
    )

    RecipeTextExtractJob.perform_now(@user.id, @recipe.id, text)

    @recipe.reload
    assert_equal :completed, @recipe.import_status.to_sym
    assert_nil @recipe.source_url
  end

  test "marks recipe as failed when extraction fails" do
    stub_llm_no_recipe_found

    RecipeTextExtractJob.perform_now(@user.id, @recipe.id, "random text with no recipe")

    @recipe.reload
    assert_equal :failed, @recipe.import_status.to_sym
    assert_equal "Import failed.", @recipe.error_message
  end

  test "marks recipe as failed when LLM times out" do
    stub_request(:post, LlmStubHelper::OPENROUTER_ENDPOINT)
      .to_raise(Faraday::TimeoutError.new("execution expired"))

    RecipeTextExtractJob.perform_now(@user.id, @recipe.id, "Some recipe text")

    @recipe.reload
    assert_equal :failed, @recipe.import_status.to_sym
    assert_equal "Import failed.", @recipe.error_message
  end

  test "does nothing when recipe no longer exists" do
    deleted_id = @recipe.id
    @recipe.meal_plan_entries.destroy_all
    @recipe.destroy!

    assert_nothing_raised do
      RecipeTextExtractJob.perform_now(@user.id, deleted_id, "Some text")
    end
  end

  test "does nothing when user no longer exists" do
    deleted_user_id = @user.id
    @user.destroy

    assert_nothing_raised do
      RecipeTextExtractJob.perform_now(deleted_user_id, @recipe.id, "Some text")
    end
  end

  test "does nothing when recipe is already completed" do
    @recipe.update!(import_status: :completed, name: "Already Done")

    RecipeTextExtractJob.perform_now(@user.id, @recipe.id, "New text")

    @recipe.reload
    assert_equal "Already Done", @recipe.name
  end

  test "marks recipe as failed on unexpected error and re-raises" do
    # Stub RecipeLlmService.new to return a mock that raises on extract
    mock_service = Minitest::Mock.new
    mock_service.expect(:extract, nil) { raise StandardError.new("Unexpected") }

    RecipeLlmService.stub(:new, mock_service) do
      assert_raises(StandardError) do
        RecipeTextExtractJob.perform_now(@user.id, @recipe.id, "Some text")
      end
    end

    @recipe.reload
    assert_equal :failed, @recipe.import_status.to_sym
    assert_equal "Import failed.", @recipe.error_message
  end
end
