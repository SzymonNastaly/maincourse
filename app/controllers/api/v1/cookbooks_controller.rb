module Api
  module V1
    class CookbooksController < BaseController
      skip_before_action :set_current_cookbook!

      # GET /api/v1/cookbooks
      def index
        cookbooks = current_user.cookbooks.includes(cookbook_memberships: :user)
        recipe_counts = Recipe.where(cookbook_id: cookbooks.select(:id)).group(:cookbook_id).count

        render json: cookbooks.map { |cookbook| cookbook_json(cookbook, recipe_count: recipe_counts[cookbook.id] || 0) }
      end

      # POST /api/v1/cookbooks
      def create
        name = params[:name].to_s.strip
        if name.blank?
          return render_api_error "name_required", error: "Name is required", status: :unprocessable_entity
        end

        move_recipes = ActiveModel::Type::Boolean.new.cast(params[:move_personal_recipes])

        cookbook = current_user.with_lock do
          if current_user.shared_cookbook.present?
            return render_api_error "shared_cookbook_exists", error: "You already have a shared cookbook", status: :unprocessable_entity
          end

          ActiveRecord::Base.transaction do
            shared = Cookbook.create!(name: name, personal: false)
            CookbookMembership.create!(cookbook: shared, user: current_user, role: :owner)

            if move_recipes
              personal = current_user.personal_cookbook
              personal.recipes.update_all(cookbook_id: shared.id)
              personal.shopping_list_items.update_all(cookbook_id: shared.id)
            end

            shared
          end
        end

        render json: cookbook_json(cookbook.reload), status: :created
      end

      # DELETE /api/v1/cookbooks/:id
      def destroy
        cookbook = current_user.cookbooks.find_by(id: params[:id])
        if cookbook.nil?
          return render_api_error "not_found", error: "Cookbook not found", status: :not_found
        end

        if cookbook.personal?
          return render_api_error "personal_cookbook_required", error: "Cannot delete personal cookbook", status: :unprocessable_entity
        end

        unless cookbook.owner?(current_user)
          return render_api_error "owner_required", error: "Only the owner can delete this cookbook", status: :forbidden
        end

        cookbook.destroy!
        head :no_content
      end

      # POST /api/v1/cookbooks/:id/leave
      def leave
        cookbook = current_user.cookbooks.find_by(id: params[:id])
        if cookbook.nil?
          return render_api_error "not_found", error: "Cookbook not found", status: :not_found
        end

        if cookbook.personal?
          return render_api_error "personal_cookbook_required", error: "Cannot leave personal cookbook", status: :unprocessable_entity
        end

        if cookbook.owner?(current_user)
          return render_api_error "owner_cannot_leave", error: "Owner cannot leave. Delete the cookbook instead.", status: :unprocessable_entity
        end

        membership = cookbook.cookbook_memberships.find_by(user: current_user)
        membership.destroy!
        head :no_content
      end

      private

      def cookbook_json(cookbook, recipe_count: nil)
        {
          id: cookbook.id,
          name: cookbook.name,
          personal: cookbook.personal,
          recipe_count: recipe_count || cookbook.recipes.count,
          members: cookbook.cookbook_memberships.map do |membership|
            {
              id: membership.user.id,
              email: membership.user.email_address,
              role: membership.role
            }
          end
        }
      end
    end
  end
end
