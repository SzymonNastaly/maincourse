module Api
  module V1
    class RecipeSavesController < BaseController
      skip_before_action :set_current_cookbook!

      rescue_from Recipes::Save::InvalidRequest, with: :render_unprocessable_entity
      rescue_from Recipes::Save::Conflict, with: :render_conflict
      rescue_from Recipes::Save::Unavailable, with: :render_gone

      def create
        cookbook = current_user.cookbooks.find_by(id: params[:cookbook_id])
        return render_api_error "forbidden", error: "Forbidden", status: :forbidden unless cookbook

        recipe = Recipes::Save.call(
          user: current_user,
          cookbook: cookbook,
          source: params[:source],
          request_id: params[:request_id]
        )

        render json: { recipe_id: recipe.id, cookbook_id: recipe.cookbook_id }
      end

      private

      def render_unprocessable_entity(error)
        render_api_error "invalid_recipe_save", error: error.message, status: :unprocessable_entity
      end

      def render_conflict(error)
        render_api_error "recipe_save_conflict", error: error.message, status: :conflict
      end

      def render_gone(error)
        render_api_error "recipe_save_gone", error: error.message, status: :gone
      end
    end
  end
end
