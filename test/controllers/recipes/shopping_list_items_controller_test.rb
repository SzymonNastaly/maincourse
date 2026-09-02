require "test_helper"

module Recipes
  class ShoppingListItemsControllerTest < ActionDispatch::IntegrationTest
    setup do
      @user = users(:one)
      @recipe = recipes(:one)
      sign_in_as @user
    end

    test "adds only the ingredients that were left ticked" do
      assert_difference "ShoppingListItem.count", 2 do
        post recipe_shopping_list_items_path(@recipe), params: {
          items: [
            { name: "Spaghetti", details: "200 g" },
            { name: "Pancetta", details: "" }
          ]
        }
      end

      items = ShoppingListItem.order(:created_at).last(2)
      assert_equal [ "Spaghetti", "Pancetta" ], items.map(&:name)
      assert_equal "200 g", items.first.details
      assert_nil items.last.details
      assert items.all? { |item| item.source_recipe == @recipe }
      assert_redirected_to shopping_list_items_path
    end

    test "skips blank names" do
      assert_difference "ShoppingListItem.count", 1 do
        post recipe_shopping_list_items_path(@recipe), params: {
          items: [ { name: "Eggs", details: "3" }, { name: "  ", details: "" } ]
        }
      end
    end

    test "refuses when nothing was selected" do
      assert_no_difference "ShoppingListItem.count" do
        post recipe_shopping_list_items_path(@recipe), params: { items: [] }
      end

      assert_equal "Nothing selected.", flash[:alert]
    end

    test "cannot add from another user's recipe" do
      assert_no_difference "ShoppingListItem.count" do
        post recipe_shopping_list_items_path(recipes(:two)), params: {
          items: [ { name: "Chicken", details: "1" } ]
        }
      end

      assert_redirected_to recipes_path
    end
  end
end
