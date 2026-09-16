require "test_helper"

class ShoppingList::PolicyTest < ActiveSupport::TestCase
  test "excludes only explicit common staple identities" do
    staples = [
      "water", "tap water",
      "salt", "table salt", "sea salt", "kosher salt",
      "black pepper", "ground black pepper"
    ]

    staples.each do |name|
      assert_equal false, ShoppingList::Policy.default_included?(name), name
    end

    [ nil, "", "pepper", "red pepper", "sparkling water", "salt substitute", "olive oil" ].each do |name|
      assert_equal true, ShoppingList::Policy.default_included?(name), name.inspect
    end
  end
end
