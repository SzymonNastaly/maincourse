module Api
  module V1
    class MealPlanSelectionsController < BaseController
      def update
        date = Date.parse(params[:date])
        meal_plan = current_cookbook.meal_plans.find_by!(date: date)
        entry = meal_plan.entries.find(params[:entry_id])

        if meal_plan.selected?
          if meal_plan.selected_entry_id == entry.id
            return render json: MealPlanSerializer.new(meal_plan, current_user: current_user).as_json
          else
            return render_api_error "meal_plan_selection_conflict", error: "A different recipe has already been selected for this date", status: :conflict
          end
        end

        meal_plan.update!(
          selected_entry: entry,
          selected_by_user: current_user,
          selected_at: Time.current
        )

        render json: MealPlanSerializer.new(meal_plan.reload, current_user: current_user).as_json
      rescue Date::Error
        render_api_error "invalid_request", error: "Invalid date format", status: :bad_request
      rescue ActiveRecord::RecordNotFound
        render_api_error "not_found", error: "Meal plan or entry not found", status: :not_found
      end

      def destroy
        date = Date.parse(params[:date])
        meal_plan = current_cookbook.meal_plans.find_by!(date: date)

        unless meal_plan.selected?
          return render json: MealPlanSerializer.new(meal_plan, current_user: current_user).as_json
        end

        meal_plan.update!(
          selected_entry: nil,
          selected_by_user: nil,
          selected_at: nil
        )

        render json: MealPlanSerializer.new(meal_plan.reload, current_user: current_user).as_json
      rescue Date::Error
        render_api_error "invalid_request", error: "Invalid date format", status: :bad_request
      rescue ActiveRecord::RecordNotFound
        render_api_error "not_found", error: "Meal plan not found", status: :not_found
      end
    end
  end
end
