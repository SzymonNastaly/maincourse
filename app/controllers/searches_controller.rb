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
    term = "%#{ActiveRecord::Base.sanitize_sql_like(@query.downcase)}%"

    Recipe.where(cookbook_id: @cookbooks.map(&:id))
          .where.not(import_status: :failed)
          .where(<<~SQL.squish, term: term)
            LOWER(recipes.name) LIKE :term
            OR LOWER(COALESCE(recipes.instructions, '')) LIKE :term
            OR EXISTS (
              SELECT 1 FROM ingredients
              WHERE ingredients.recipe_id = recipes.id
                AND LOWER(ingredients.raw) LIKE :term
            )
          SQL
          .with_attached_cover_image
          .includes(:cookbook)
          .order(updated_at: :desc)
          .limit(RESULT_LIMIT)
  end
end
