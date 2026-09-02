class CookbooksController < ApplicationController
  before_action :set_cookbook, only: %i[destroy leave]

  def index
    @personal = Current.user.personal_cookbook
    @shared = Current.user.shared_cookbook
    @memberships = @shared&.cookbook_memberships&.includes(:user)&.order(:created_at) || []
    @invitation = @shared&.cookbook_invitations&.active&.order(created_at: :desc)&.first
  end

  # Creating the one shared cookbook a user is allowed. Mirrors the API rules.
  def create
    name = params.dig(:cookbook, :name).to_s.strip
    move_recipes = ActiveModel::Type::Boolean.new.cast(params.dig(:cookbook, :move_personal_recipes))

    return redirect_to cookbooks_path, alert: "Give your cookbook a name." if name.blank?

    shared = Current.user.with_lock do
      next nil if Current.user.shared_cookbook.present?

      Cookbook.transaction do
        cookbook = Cookbook.create!(name: name, personal: false)
        CookbookMembership.create!(cookbook: cookbook, user: Current.user, role: :owner)

        if move_recipes
          personal = Current.user.personal_cookbook
          personal.recipes.update_all(cookbook_id: cookbook.id)
          personal.shopping_list_items.update_all(cookbook_id: cookbook.id)
        end

        cookbook
      end
    end

    if shared.nil?
      return redirect_to cookbooks_path, alert: "You already have a shared cookbook."
    end

    switch_cookbook(shared)
    redirect_to cookbooks_path, notice: "#{shared.name} is ready."
  end

  def destroy
    if @cookbook.personal?
      return redirect_to cookbooks_path, alert: "Your personal cookbook cannot be deleted."
    end

    unless @cookbook.owner?(Current.user)
      return redirect_to cookbooks_path, alert: "Only the owner can delete this cookbook."
    end

    @cookbook.destroy!
    reset_to_personal_cookbook
    redirect_to cookbooks_path, notice: "Cookbook deleted.", status: :see_other
  end

  def leave
    if @cookbook.personal?
      return redirect_to cookbooks_path, alert: "You cannot leave your personal cookbook."
    end

    if @cookbook.owner?(Current.user)
      return redirect_to cookbooks_path, alert: "Owners cannot leave. Delete the cookbook instead."
    end

    @cookbook.cookbook_memberships.find_by!(user: Current.user).destroy!
    reset_to_personal_cookbook
    redirect_to cookbooks_path, notice: "You left #{@cookbook.name}."
  end

  private

  def set_cookbook
    @cookbook = Current.user.cookbooks.find(params[:id])
  rescue ActiveRecord::RecordNotFound
    redirect_to cookbooks_path, alert: "Cookbook not found."
  end

  def reset_to_personal_cookbook
    @available_cookbooks = nil
    switch_cookbook(Current.user.personal_cookbook)
  end
end
