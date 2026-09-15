class RecipeTextExtractJob < ApplicationJob
  queue_as :default

  def perform(user_id, recipe_id, text)
    user = User.find_by(id: user_id)
    return unless user

    recipe = user.recipes.find_by(id: recipe_id)
    return unless recipe
    return if recipe.completed?

    result = RecipeLlmService.new(text, prompt_type: :raw_text).extract

    if result.success?
      recipe.apply_extracted_attributes!(result.recipe_attributes.merge(import_status: :completed))
      ParseRecipeIngredientsJob.perform_later(recipe.id) if recipe.ingredients.any?(&:needs_enrichment?)
    else
      recipe.update!(
        import_status: :failed,
        error_message: "Import failed."
      )
      Rails.logger.error "[RecipeTextExtractJob] Extraction failed for recipe #{recipe_id}: #{result.error}"
    end
  rescue => error
    recipe&.update(
      import_status: :failed,
      error_message: "Import failed."
    )
    Rails.logger.error "[RecipeTextExtractJob] Unexpected error for recipe #{recipe_id}: #{error.class} - #{error.message}"
    raise
  end
end
