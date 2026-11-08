# Search spans every cookbook the user belongs to — unlike the rest of the web
# UI, which is scoped to the active cookbook. That matches the mockup's
# "3 results across Our Kitchen and My Recipes".
class SearchesController < ApplicationController
  RESULT_LIMIT = 60

  def show
    @query = params[:q].to_s.strip
    @cookbooks = available_cookbooks
    @results = @query.present? ? search : Recipe.none
  end

  private

  def search
    searchable = Recipe.where(cookbook_id: @cookbooks.map(&:id)).where.not(import_status: :failed)

    searchable.where(id: matching_ids(searchable, SearchFold.call(@query)))
              .with_attached_cover_image
              .includes(:cookbook)
              .order(updated_at: :desc)
              .limit(RESULT_LIMIT)
  end

  # Matches in Ruby because SearchFold's case and accent folding has no SQL form
  # shared by SQLite and Postgres, and one user's cookbooks are small enough to scan.
  def matching_ids(recipes, term)
    ingredients = Ingredient.where(recipe_id: recipes.select(:id)).pluck(:recipe_id, :raw).group_by(&:first)

    recipes.pluck(:id, :name, :instructions).filter_map do |id, name, instructions|
      texts = [ name, *Array(instructions), *ingredients.fetch(id, []).map(&:last) ]
      id if texts.any? { |text| SearchFold.call(text.to_s).include?(term) }
    end
  end
end
