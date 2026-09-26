module Api
  module V1
    class MealPlanEntriesController < BaseController
      def create
        date = Date.parse(params[:date])
        recipe = current_cookbook.recipes.find(params[:recipe_id])

        meal_plan = current_cookbook.meal_plans.find_or_create_by!(date: date)

        if meal_plan.selected?
          return render_api_error "meal_plan_finalized", error: "Meal plan for this date has already been finalized", status: :unprocessable_entity
        end

        entry = meal_plan.entries.find_or_initialize_by(recipe: recipe)
        if entry.new_record?
          entry.proposed_by_user = current_user
          entry.save!
          PendingNotification.record_event!(
            cookbook: current_cookbook,
            actor: current_user,
            category: :meal_plan_activity,
            event: { kind: "entry_added", recipe_name: recipe.name, date: date.iso8601 }
          )
        end

        meal_plan.reload
        render json: MealPlanSerializer.new(meal_plan, current_user: current_user).as_json,
               status: (entry.previously_new_record? ? :created : :ok)
      rescue Date::Error
        render_api_error "invalid_request", error: "Invalid date format", status: :bad_request
      rescue ActiveRecord::RecordNotFound
        render_api_error "not_found", error: "Recipe not found", status: :not_found
      rescue ActiveRecord::RecordInvalid => e
        render_api_error "invalid_request", error: e.message, status: :unprocessable_entity
      end

      def destroy
        entry = MealPlanEntry.find(params[:id])
        meal_plan = entry.meal_plan

        unless meal_plan.cookbook_id == current_cookbook.id
          return render_api_error "not_found", error: "Not found", status: :not_found
        end

        if meal_plan.selected?
          return render_api_error "meal_plan_finalized", error: "Meal plan for this date has already been finalized", status: :unprocessable_entity
        end

        entry.destroy!
        head :no_content
      rescue ActiveRecord::RecordNotFound
        render_api_error "not_found", error: "Entry not found", status: :not_found
      end
    end
  end
end
