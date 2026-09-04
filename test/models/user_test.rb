require "test_helper"

class UserTest < ActiveSupport::TestCase
  test "requires a password without an OAuth identity" do
    user = User.new(email_address: "passwordless@example.com")

    assert_not user.valid?
    assert user.errors.of_kind?(:password, :blank)
  end

  test "allows an OAuth identity instead of a password" do
    user = User.new(email_address: "oauth@example.com")
    user.identities.build(provider: "google", uid: "oauth-user", email: user.email_address)

    assert user.valid?
  end

  test "downcases and strips email_address" do
    user = User.new(email_address: " DOWNCASED@EXAMPLE.COM ")
    assert_equal("downcased@example.com", user.email_address)
  end

  test "creates personal cookbook on user creation" do
    user = User.create!(email_address: "new@example.com", password: "password")

    assert_not_nil user.personal_cookbook
    assert user.personal_cookbook.personal?
    assert_equal "My Recipes", user.personal_cookbook.name
    assert user.personal_cookbook.owner?(user)
  end

  test "personal_cookbook returns personal cookbook" do
    user = users(:one)

    assert_equal cookbooks(:one_personal), user.personal_cookbook
  end

  test "shared_cookbook returns nil when no shared cookbook" do
    user = users(:one)

    assert_nil user.shared_cookbook
  end

  test "shared_cookbook returns shared cookbook when exists" do
    user = users(:one)
    shared = Cookbook.create!(name: "Family", personal: false)
    CookbookMembership.create!(cookbook: shared, user: user, role: :owner)

    assert_equal shared, user.shared_cookbook
  end

  test "destroying user destroys owned cookbooks and their recipes" do
    user = users(:one)
    cookbook = user.personal_cookbook
    recipe_ids = cookbook.recipes.pluck(:id)
    assert_not_empty recipe_ids

    user.destroy!

    assert_nil Cookbook.find_by(id: cookbook.id)
    recipe_ids.each { |id| assert_nil Recipe.find_by(id: id) }
  end

  test "destroying user deletes their recipe engagements" do
    user = users(:two)
    recipe = recipes(:one)
    engagement = RecipeEngagement.create!(user: user, recipe: recipe)

    user.destroy!

    assert_nil RecipeEngagement.find_by(id: engagement.id)
  end

  test "destroying user as collaborator removes membership but keeps cookbook" do
    owner = users(:one)
    collaborator = users(:two)

    shared = Cookbook.create!(name: "Shared", personal: false)
    CookbookMembership.create!(cookbook: shared, user: owner, role: :owner)
    CookbookMembership.create!(cookbook: shared, user: collaborator, role: :collaborator)

    collaborator.destroy!

    assert Cookbook.exists?(shared.id), "Shared cookbook should still exist"
    assert_not CookbookMembership.exists?(user_id: collaborator.id, cookbook_id: shared.id)
  end

  test "destroying user who owns shared cookbook with no other members deletes it" do
    user = users(:one)
    shared = Cookbook.create!(name: "Shared", personal: false)
    CookbookMembership.create!(cookbook: shared, user: user, role: :owner)
    recipe = shared.recipes.create!(name: "Shared Recipe", user: user)

    user.destroy!

    assert_nil Cookbook.find_by(id: shared.id)
    assert_nil Recipe.find_by(id: recipe.id)
  end

  test "destroying user who owns shared cookbook with collaborators transfers ownership" do
    owner = users(:one)
    collaborator = users(:two)
    shared = Cookbook.create!(name: "Shared", personal: false)
    CookbookMembership.create!(cookbook: shared, user: owner, role: :owner)
    CookbookMembership.create!(cookbook: shared, user: collaborator, role: :collaborator)
    recipe = shared.recipes.create!(name: "Shared Recipe", user: owner)

    owner.destroy!

    assert Cookbook.exists?(shared.id), "Shared cookbook should survive"
    assert Recipe.exists?(recipe.id), "Shared recipe should survive"
    assert_equal collaborator, shared.reload.owner
  end

  test "touch_last_active! writes when never active" do
    user = users(:one)
    user.update_column(:last_active_at, nil)

    assert user.touch_last_active!
    assert_not_nil user.reload.last_active_at
  end

  test "touch_last_active! is throttled within the hour" do
    user = users(:one)
    recent = 10.minutes.ago
    user.update_column(:last_active_at, recent)

    assert_not user.touch_last_active!
    assert_in_delta recent.to_i, user.reload.last_active_at.to_i, 1
  end

  test "touch_last_active! writes again after the throttle window" do
    user = users(:one)
    user.update_column(:last_active_at, 2.hours.ago)

    assert user.touch_last_active!
    assert_operator user.reload.last_active_at, :>, 5.minutes.ago
  end
end
