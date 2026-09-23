class RecipeImageExtractJob < ApplicationJob
  queue_as :default

  NO_RECIPE_IN_PHOTO_MESSAGE = "No recipe found in that photo."

  def perform(user_id, recipe_id)
    user = User.find_by(id: user_id)
    return unless user

    recipe = user.recipes.find_by(id: recipe_id)
    return unless recipe
    return if recipe.completed?

    unless recipe.import_image.attached?
      recipe.update!(import_status: :failed, error_message: "Import failed.")
      Rails.logger.error "[RecipeImageExtractJob] Missing import image for recipe #{recipe_id}"
      return
    end

    result = recipe.import_image.blob.open do |file|
      RecipeImageLlmService.new(file.path).extract
    end

    if result.success?
      recipe.apply_extracted_attributes!(result.recipe_attributes.merge(import_status: :completed))
      ParseRecipeIngredientsJob.perform_later(recipe.id) if recipe.ingredients.any?(&:needs_enrichment?)
    elsif not_a_recipe?(result)
      recipe.update!(import_status: :failed, error_message: NO_RECIPE_IN_PHOTO_MESSAGE)
      Rails.logger.info "[RecipeImageExtractJob] No recipe text in photo for recipe #{recipe_id}"
    else
      recipe.update!(
        import_status: :failed,
        error_message: "Import failed."
      )
      Rails.logger.error "[RecipeImageExtractJob] Extraction failed for recipe #{recipe_id}: #{result.error}"
    end
  rescue => error
    recipe&.update(
      import_status: :failed,
      error_message: "Import failed."
    )
    Rails.logger.error "[RecipeImageExtractJob] Unexpected error for recipe #{recipe_id}: #{error.class} - #{error.message}"
    raise
  end

  private

  def not_a_recipe?(result)
    result.error_code == :not_a_recipe
  end
end
