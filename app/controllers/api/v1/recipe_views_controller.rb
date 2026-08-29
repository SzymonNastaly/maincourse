module Api
  module V1
    # Records that the user opened a recipe. Views are batched by the client and
    # flushed on the next sync, so this endpoint is not cookbook-scoped.
    #
    # Deliberately not idempotent: a retried batch may double-count view_count.
    # last_viewed_at is idempotent under max and is the field campaigns read, so a
    # dedupe key is not worth the complexity.
    class RecipeViewsController < BaseController
      skip_before_action :set_current_cookbook!

      def create
        views = params[:views]
        return render json: { error: "views is required" }, status: :unprocessable_entity unless views.is_a?(Array)

        # Cap the batch: each entry costs two queries, and the payload is client-supplied.
        views.first(500).each { |view| record(view) }
        head :no_content
      end

      private

      def record(view)
        return unless view.is_a?(ActionController::Parameters) || view.is_a?(Hash)

        recipe_id = view[:recipe_id]
        return if recipe_id.blank?

        recipe = accessible_recipes.find_by(id: recipe_id)
        return if recipe.nil?

        RecipeEngagement.record_view!(user: current_user, recipe: recipe, viewed_at: parse_time(view[:viewed_at]))
      end

      def accessible_recipes
        Recipe.where(cookbook_id: current_user.cookbooks.select(:id))
      end

      def parse_time(value)
        return Time.current if value.blank?

        Time.iso8601(value.to_s)
      rescue ArgumentError
        Time.current
      end
    end
  end
end
