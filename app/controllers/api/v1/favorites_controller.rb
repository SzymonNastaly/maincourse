module Api
  module V1
    class FavoritesController < BaseController
      before_action :set_recipe

      # PUT /api/v1/recipes/:recipe_id/favorite
      # Idempotent: safe to call multiple times
      def update
        @recipe.update!(favorite: true)
        render json: { id: @recipe.id, favorite: true }
      end

      # DELETE /api/v1/recipes/:recipe_id/favorite
      # Idempotent: safe to call multiple times
      def destroy
        @recipe.update!(favorite: false)
        render json: { id: @recipe.id, favorite: false }
      end

      private

      def set_recipe
        @recipe = current_cookbook.recipes.find(params[:recipe_id])
      rescue ActiveRecord::RecordNotFound
        render_api_error "not_found", error: "Recipe not found", status: :not_found
      end
    end
  end
end
