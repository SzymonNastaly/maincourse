module Api
  module V1
    class MealPlanVotesController < BaseController
      before_action :set_entry

      def create
        if @entry.meal_plan.selected?
          return render_api_error "meal_plan_finalized", error: "Meal plan has already been finalized", status: :unprocessable_entity
        end

        vote = @entry.votes.find_or_create_by!(user: current_user)
        if vote.previously_new_record?
          PendingNotification.record_event!(
            cookbook: current_cookbook,
            actor: current_user,
            category: :meal_plan_activity,
            event: { kind: "vote", recipe_name: @entry.recipe.name }
          )
        end

        render json: MealPlanSerializer.new(
          @entry.meal_plan.reload,
          current_user: current_user
        ).as_json
      rescue ActiveRecord::RecordInvalid => e
        render_api_error "invalid_request", error: e.message, status: :unprocessable_entity
      end

      def destroy
        if @entry.meal_plan.selected?
          return render_api_error "meal_plan_finalized", error: "Meal plan has already been finalized", status: :unprocessable_entity
        end

        vote = @entry.votes.find_by(user: current_user)
        vote&.destroy!

        render json: MealPlanSerializer.new(
          @entry.meal_plan.reload,
          current_user: current_user
        ).as_json
      end

      private

      def set_entry
        @entry = MealPlanEntry.find(params[:meal_plan_entry_id])
        meal_plan = @entry.meal_plan

        unless meal_plan.cookbook_id == current_cookbook.id
          render_api_error "not_found", error: "Not found", status: :not_found
        end
      rescue ActiveRecord::RecordNotFound
        render_api_error "not_found", error: "Entry not found", status: :not_found
      end
    end
  end
end
