class ShoppingListItemsController < ApplicationController
  before_action :require_cookbook
  before_action :set_item, only: %i[toggle destroy]

  def index
    # Checked items expire after an hour, same as the API index.
    ShoppingListItem.cleanup_stale_checked_for(current_cookbook)

    items = current_cookbook.shopping_list_items.includes(:source_recipe)
    @to_buy = items.unchecked.order(created_at: :desc)
    @already_got = items.checked.order(checked_at: :desc)
  end

  def create
    name = params.dig(:shopping_list_item, :name).to_s.strip

    if name.blank?
      return redirect_to shopping_list_items_path, alert: "Type something to add."
    end

    current_cookbook.shopping_list_items.create!(
      name: name,
      user: Current.user,
      client_id: SecureRandom.uuid
    )

    redirect_to shopping_list_items_path
  end

  def toggle
    @item.update!(checked_at: @item.checked_at.nil? ? Time.current : nil)
    redirect_to shopping_list_items_path
  end

  def destroy
    @item.destroy!
    redirect_to shopping_list_items_path, status: :see_other
  end

  def destroy_all
    current_cookbook.shopping_list_items.destroy_all
    redirect_to shopping_list_items_path, notice: "Shopping list cleared.", status: :see_other
  end

  private

  def set_item
    @item = current_cookbook.shopping_list_items.find(params[:id])
  rescue ActiveRecord::RecordNotFound
    redirect_to shopping_list_items_path, alert: "That item is no longer on the list."
  end
end
