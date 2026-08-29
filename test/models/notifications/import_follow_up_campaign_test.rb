require "test_helper"

class Notifications::ImportFollowUpCampaignTest < ActiveSupport::TestCase
  setup do
    @user = users(:one)
    @cookbook = cookbooks(:one_personal)
    Recipe.where(cookbook: @cookbook).update_all(created_at: 1.year.ago)
  end

  def saved_recipe(created_at: 3.days.ago, name: "Ramen")
    recipe = @cookbook.recipes.create!(name: name, user: @user, import_status: :completed)
    recipe.update_column(:created_at, created_at)
    recipe
  end

  test "suggests a recipe saved a few days ago and never used" do
    recipe = saved_recipe

    candidate = Notifications::ImportFollowUpCampaign.eligible_for(@user)

    assert_not_nil candidate
    assert_equal recipe, candidate.recipe
    assert_equal "import_follow_up", candidate.campaign
    assert_includes candidate.body, "Ramen"
  end

  test "ignores a recipe saved too recently" do
    saved_recipe(created_at: 6.hours.ago)

    assert_nil Notifications::ImportFollowUpCampaign.eligible_for(@user)
  end

  test "ignores a recipe saved too long ago" do
    saved_recipe(created_at: 20.days.ago)

    assert_nil Notifications::ImportFollowUpCampaign.eligible_for(@user)
  end

  test "ignores a recipe whose ingredients reached the shopping list" do
    recipe = saved_recipe
    @cookbook.shopping_list_items.create!(client_id: "x1", name: "Noodles", source_recipe_id: recipe.id)

    assert_nil Notifications::ImportFollowUpCampaign.eligible_for(@user)
  end

  test "ignores a recipe the user came back to after importing" do
    recipe = saved_recipe
    RecipeEngagement.record_view!(user: @user, recipe: recipe, viewed_at: recipe.created_at + 2.days)

    assert_nil Notifications::ImportFollowUpCampaign.eligible_for(@user)
  end

  test "still suggests a recipe only viewed during the import itself" do
    recipe = saved_recipe
    RecipeEngagement.record_view!(user: @user, recipe: recipe, viewed_at: recipe.created_at + 1.minute)

    assert_not_nil Notifications::ImportFollowUpCampaign.eligible_for(@user)
  end

  test "ignores a recipe already suggested once" do
    recipe = saved_recipe
    RecipeEngagement.mark_suggested!(user: @user, recipe: recipe)

    assert_nil Notifications::ImportFollowUpCampaign.eligible_for(@user)
  end

  test "ignores a recipe already cooked even though its shopping list items were cleaned up" do
    recipe = saved_recipe
    RecipeEngagement.mark_cooked!(recipe: recipe)

    assert_nil Notifications::ImportFollowUpCampaign.eligible_for(@user)
  end

  test "ignores a recipe already added to the list even though the items were cleaned up" do
    recipe = saved_recipe
    RecipeEngagement.mark_added_to_list!(recipe: recipe)

    assert_nil Notifications::ImportFollowUpCampaign.eligible_for(@user)
  end

  test "ignores a recipe whose import failed" do
    recipe = saved_recipe
    recipe.update!(import_status: :failed)

    assert_nil Notifications::ImportFollowUpCampaign.eligible_for(@user)
  end

  test "prefers the most recently saved eligible recipe" do
    saved_recipe(created_at: 6.days.ago, name: "Older")
    newer = saved_recipe(created_at: 2.days.ago, name: "Newer")

    assert_equal newer, Notifications::ImportFollowUpCampaign.eligible_for(@user).recipe
  end
end
