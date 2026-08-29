require "test_helper"

class Notifications::StaleShoppingListCampaignTest < ActiveSupport::TestCase
  setup do
    @user = users(:one)
    @cookbook = cookbooks(:one_personal)
    @cookbook.shopping_list_items.delete_all
  end

  def stale_items(count: 3, age: 5.days)
    count.times.map do |i|
      item = @cookbook.shopping_list_items.create!(client_id: "stale-#{i}", name: "Item #{i}")
      item.update_columns(created_at: age.ago, updated_at: age.ago)
      item
    end
  end

  test "suggests when enough items have sat untouched" do
    stale_items

    candidate = Notifications::StaleShoppingListCampaign.eligible_for(@user)

    assert_not_nil candidate
    assert_equal "stale_shopping_list", candidate.campaign
    assert_nil candidate.recipe
    assert_equal @cookbook, candidate.cookbook
    assert_includes candidate.body, "3 items"
  end

  test "ignores a list with too few items" do
    stale_items(count: 2)

    assert_nil Notifications::StaleShoppingListCampaign.eligible_for(@user)
  end

  test "ignores a list that is not old enough" do
    stale_items(age: 1.day)

    assert_nil Notifications::StaleShoppingListCampaign.eligible_for(@user)
  end

  test "ignores a list touched recently" do
    stale_items
    @cookbook.shopping_list_items.create!(client_id: "fresh", name: "Fresh item")

    assert_nil Notifications::StaleShoppingListCampaign.eligible_for(@user)
  end

  test "ignores checked items when counting" do
    stale_items(count: 2)
    checked = @cookbook.shopping_list_items.create!(client_id: "done", name: "Done", checked_at: 4.days.ago)
    checked.update_columns(created_at: 5.days.ago, updated_at: 5.days.ago)

    assert_nil Notifications::StaleShoppingListCampaign.eligible_for(@user)
  end
end
