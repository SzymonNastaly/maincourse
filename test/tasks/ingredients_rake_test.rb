require "test_helper"
require "rake"

Rake::Task.define_task(:environment) unless Rake::Task.task_defined?(:environment)
load Rails.root.join("lib/tasks/ingredients.rake")

class IngredientsRakeTest < ActiveSupport::TestCase
  setup do
    Rake::Task["ingredients:enqueue_enrichment"].reenable
    Ingredient.delete_all
  end

  test "queues only recipes with ingredients needing enrichment" do
    stale_recipe = recipes(:one)
    current_recipe = recipes(:two)
    stale_recipe.ingredients.create!(position: 0, raw: "2 EL Olivenöl", amount: 2, unit: "EL")
    current_recipe.ingredients.create!(
      position: 0,
      raw: "salt",
      canonical_name: "salt",
      category: "oils_spices_condiments",
      enrichment_version: Llm::IngredientInstructions::VERSION
    )

    assert_enqueued_jobs 1, only: ParseRecipeIngredientsJob do
      Rake::Task["ingredients:enqueue_enrichment"].invoke
    end

    job = enqueued_jobs.find { |entry| entry[:job] == ParseRecipeIngredientsJob }
    assert_equal [ stale_recipe.id ], job[:args]
  end
end
