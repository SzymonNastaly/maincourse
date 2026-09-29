class AddCategoryToShoppingListItems < ActiveRecord::Migration[8.1]
  def change
    add_column :shopping_list_items, :category, :string
    add_column :shopping_list_items, :canonical_name, :string
    add_column :shopping_list_items, :enrichment_version, :integer

    # ShoppingList::CategoryLookup matches item names against recipe ingredients.
    add_index :ingredients, "lower(name)", name: "index_ingredients_on_lower_name"
  end
end
