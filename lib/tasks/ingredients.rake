namespace :ingredients do
  desc "Enqueue enrichment for recipes containing ingredients on an old or incomplete contract"
  task enqueue_enrichment: :environment do
    scope = Ingredient.where(
      "enrichment_version IS NULL OR enrichment_version != ?",
      Llm::IngredientInstructions::VERSION
    )
    recipe_ids = scope.distinct.pluck(:recipe_id)

    recipe_ids.each { |recipe_id| ParseRecipeIngredientsJob.perform_later(recipe_id) }

    puts "Enqueued ingredient enrichment for #{recipe_ids.size} recipes."
  end
end
