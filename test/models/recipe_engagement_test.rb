require "test_helper"

class RecipeEngagementTest < ActiveSupport::TestCase
  setup do
    @user = users(:one)
    @recipe = recipes(:one)
  end

  test "record_view! creates a row and counts the view" do
    engagement = RecipeEngagement.record_view!(user: @user, recipe: @recipe, viewed_at: Time.current)

    assert_equal 1, engagement.view_count
    assert_not_nil engagement.last_viewed_at
  end

  test "record_view! keeps the latest view timestamp" do
    later = Time.current
    earlier = later - 1.day

    RecipeEngagement.record_view!(user: @user, recipe: @recipe, viewed_at: later)
    engagement = RecipeEngagement.record_view!(user: @user, recipe: @recipe, viewed_at: earlier)

    assert_in_delta later.to_i, engagement.last_viewed_at.to_i, 1
    assert_equal 2, engagement.view_count
  end

  test "mark_added_to_list! credits every member of the cookbook" do
    cookbook = Cookbook.create!(name: "Shared", personal: false)
    cookbook.cookbook_memberships.create!(user: @user, role: :owner)
    cookbook.cookbook_memberships.create!(user: users(:two), role: :collaborator)
    recipe = cookbook.recipes.create!(name: "Stew", user: @user)

    RecipeEngagement.mark_added_to_list!(recipe: recipe)

    assert_equal 2, RecipeEngagement.where(recipe_id: recipe.id).count
    assert RecipeEngagement.where(recipe_id: recipe.id).all? { |e| e.added_to_list_at.present? }
  end

  test "mark_added_to_list! keeps the first timestamp" do
    first = 3.days.ago
    RecipeEngagement.mark_added_to_list!(recipe: @recipe, at: first)
    RecipeEngagement.mark_added_to_list!(recipe: @recipe, at: Time.current)

    engagement = RecipeEngagement.find_by(user_id: @user.id, recipe_id: @recipe.id)
    assert_in_delta first.to_i, engagement.added_to_list_at.to_i, 1
  end

  test "mark_cooked! records the most recent cook for every member" do
    cookbook = Cookbook.create!(name: "Shared", personal: false)
    cookbook.cookbook_memberships.create!(user: @user, role: :owner)
    cookbook.cookbook_memberships.create!(user: users(:two), role: :collaborator)
    recipe = cookbook.recipes.create!(name: "Stew", user: @user)

    RecipeEngagement.mark_cooked!(recipe: recipe, at: 5.days.ago)
    latest = Time.current
    RecipeEngagement.mark_cooked!(recipe: recipe, at: latest)

    RecipeEngagement.where(recipe_id: recipe.id).each do |engagement|
      assert_in_delta latest.to_i, engagement.cooked_at.to_i, 1
    end
  end

  test "a member who joins later is not credited retroactively" do
    cookbook = Cookbook.create!(name: "Shared", personal: false)
    cookbook.cookbook_memberships.create!(user: @user, role: :owner)
    recipe = cookbook.recipes.create!(name: "Stew", user: @user)

    RecipeEngagement.mark_cooked!(recipe: recipe)
    cookbook.cookbook_memberships.create!(user: users(:two), role: :collaborator)

    assert_nil RecipeEngagement.find_by(user_id: users(:two).id, recipe_id: recipe.id)
  end

  test "mark_suggested! stamps and counts suggestions" do
    RecipeEngagement.mark_suggested!(user: @user, recipe: @recipe)
    engagement = RecipeEngagement.mark_suggested!(user: @user, recipe: @recipe)

    assert_equal 2, engagement.suggested_count
    assert_not_nil engagement.last_suggested_at
  end
end
