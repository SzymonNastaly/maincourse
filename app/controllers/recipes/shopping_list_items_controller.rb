module Recipes
  # "Add all to shopping list" from a recipe. The review dialog on the recipe
  # page posts the ingredients the user left ticked, already scaled to the
  # servings they are cooking for.
  class ShoppingListItemsController < ApplicationController
    before_action :require_cookbook

    def create
      recipe = current_cookbook.recipes.find(params[:recipe_id])
      entries = Array(params[:items]).filter_map { |item| normalize(item) }

      if entries.empty?
        return redirect_to recipe, alert: "Nothing selected."
      end

      ShoppingListItem.transaction do
        entries.each do |entry|
          current_cookbook.shopping_list_items.create!(
            name: entry[:name],
            details: entry[:details],
            source_recipe: recipe,
            user: Current.user,
            client_id: SecureRandom.uuid
          )
        end
      end

      redirect_to shopping_list_items_path,
                  notice: "Added #{helpers.pluralize(entries.size, 'item')} from #{recipe.name}."
    rescue ActiveRecord::RecordNotFound
      redirect_to recipes_path, alert: "That recipe is not in this cookbook."
    end

    private

    def normalize(item)
      return nil unless item.respond_to?(:permit)

      permitted = item.permit(:name, :details)
      name = permitted[:name].to_s.strip
      return nil if name.blank?

      { name: name, details: permitted[:details].to_s.strip.presence }
    end
  end
end
