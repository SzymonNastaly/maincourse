module Api
  module V1
    class MealPlansController < BaseController
      def index
        from = Date.parse(params[:from])
        to = Date.parse(params[:to])

        meal_plans = current_cookbook.meal_plans
          .where(date: from..to)
          .includes(entries: [ :recipe, { recipe: { cover_image_attachment: :blob } }, :proposed_by_user, :votes ])
          .order(:date)

        render json: meal_plans.map { |mp| MealPlanSerializer.new(mp, current_user: current_user).as_json }
      rescue Date::Error
        render_api_error "invalid_request", error: "Invalid date format. Use YYYY-MM-DD.", status: :bad_request
      end
    end
  end
end
