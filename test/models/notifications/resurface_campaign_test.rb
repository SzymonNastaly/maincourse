require "test_helper"

class Notifications::ResurfaceCampaignTest < ActiveSupport::TestCase
  setup do
    @user = users(:one)
    @cookbook = cookbooks(:one_personal)
    # Push the fixture recipes below every VIEW_TRACKING_SINCE used in this file so they
    # can never be candidates.
    Recipe.where(cookbook: @cookbook).update_all(created_at: 3.years.ago)
  end

  def forgotten_recipe(created_at: 30.days.ago, name: "Goulash")
    recipe = @cookbook.recipes.create!(name: name, user: @user, import_status: :completed)
    recipe.update_column(:created_at, created_at)
    recipe
  end

  def with_tracking_since(time)
    original = Notifications::ResurfaceCampaign::VIEW_TRACKING_SINCE
    Notifications::ResurfaceCampaign.send(:remove_const, :VIEW_TRACKING_SINCE)
    Notifications::ResurfaceCampaign.const_set(:VIEW_TRACKING_SINCE, time)
    yield
  ensure
    Notifications::ResurfaceCampaign.send(:remove_const, :VIEW_TRACKING_SINCE)
    Notifications::ResurfaceCampaign.const_set(:VIEW_TRACKING_SINCE, original)
  end

  test "suggests an old recipe that was never opened" do
    with_tracking_since(1.year.ago) do
      recipe = forgotten_recipe

      candidate = Notifications::ResurfaceCampaign.eligible_for(@user)

      assert_not_nil candidate
      assert_equal recipe, candidate.recipe
      assert_equal "resurface", candidate.campaign
      assert_includes candidate.body, "Goulash"
    end
  end

  test "ignores a recipe saved before view tracking started" do
    # The window 20.days.ago..14.days.ago is non-empty, so a nil result here proves the
    # floor excluded the recipe rather than the age range doing it by accident.
    with_tracking_since(20.days.ago) do
      forgotten_recipe(created_at: 30.days.ago)

      assert_nil Notifications::ResurfaceCampaign.eligible_for(@user)
    end
  end

  test "ignores a recipe that is not old enough" do
    with_tracking_since(1.year.ago) do
      forgotten_recipe(created_at: 3.days.ago)

      assert_nil Notifications::ResurfaceCampaign.eligible_for(@user)
    end
  end

  test "ignores a recipe that was opened" do
    with_tracking_since(1.year.ago) do
      recipe = forgotten_recipe
      RecipeEngagement.record_view!(user: @user, recipe: recipe, viewed_at: Time.current)

      assert_nil Notifications::ResurfaceCampaign.eligible_for(@user)
    end
  end

  test "ignores a recipe that reached the shopping list" do
    with_tracking_since(1.year.ago) do
      recipe = forgotten_recipe
      RecipeEngagement.mark_added_to_list!(recipe: recipe)

      assert_nil Notifications::ResurfaceCampaign.eligible_for(@user)
    end
  end

  test "ignores a recipe suggested within the last 30 days" do
    with_tracking_since(1.year.ago) do
      recipe = forgotten_recipe
      RecipeEngagement.mark_suggested!(user: @user, recipe: recipe, at: 5.days.ago)

      assert_nil Notifications::ResurfaceCampaign.eligible_for(@user)
    end
  end

  test "suggests again once the resuggest window has passed" do
    with_tracking_since(1.year.ago) do
      recipe = forgotten_recipe
      RecipeEngagement.mark_suggested!(user: @user, recipe: recipe, at: 60.days.ago)

      assert_equal recipe, Notifications::ResurfaceCampaign.eligible_for(@user).recipe
    end
  end

  test "rotates through the library oldest first" do
    with_tracking_since(1.year.ago) do
      oldest = forgotten_recipe(created_at: 90.days.ago, name: "Oldest")
      forgotten_recipe(created_at: 20.days.ago, name: "Newer")

      assert_equal oldest, Notifications::ResurfaceCampaign.eligible_for(@user).recipe
    end
  end
end
