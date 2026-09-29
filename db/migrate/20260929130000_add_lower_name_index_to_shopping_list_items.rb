class AddLowerNameIndexToShoppingListItems < ActiveRecord::Migration[8.1]
  def change
    # ShoppingList::CategoryLookup also matches against confirmed shopping items.
    add_index :shopping_list_items, "lower(name)", name: "index_shopping_list_items_on_lower_name"
  end
end
