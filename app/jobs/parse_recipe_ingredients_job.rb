class ParseRecipeIngredientsJob < ApplicationJob
  queue_as :default

  def perform(recipe_id)
    recipe = Recipe.find_by(id: recipe_id)
    return unless recipe

    rows = recipe.ingredients.select(&:needs_enrichment?)
    return if rows.empty?

    parsed = IngredientParser.call(rows.map(&:raw))
    parsed_by_raw = parsed.index_by { |h| h[:raw] }

    Ingredient.transaction do
      rows.each do |ingredient|
        hit = parsed_by_raw[ingredient.raw]
        next unless hit

        ingredient.update!(
          name: hit[:name].presence || ingredient.raw,
          amount: hit[:amount],
          amount_max: hit[:amount_max],
          unit: hit[:unit],
          note: hit[:note],
          canonical_name: hit[:canonical_name],
          canonical_unit: hit[:canonical_unit],
          category: hit[:category],
          enrichment_version: hit[:enrichment_version]
        )
      end
    end
  end
end
