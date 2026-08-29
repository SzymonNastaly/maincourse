require "test_helper"

class RecipeTest < ActiveSupport::TestCase
  # ===================
  # ASSOCIATION TESTS
  # ===================

  test "belongs to a user" do
    recipe = recipes(:one)

    # The recipe should have a user
    assert_not_nil recipe.user
    assert_equal users(:one), recipe.user
  end

  test "belongs to a cookbook" do
    recipe = recipes(:one)

    assert_not_nil recipe.cookbook
    assert_equal cookbooks(:one_personal), recipe.cookbook
  end

  test "can access recipes through user" do
    user = users(:one)

    # User should have recipes (the inverse of belongs_to)
    assert_includes user.recipes, recipes(:one)
    assert_includes user.recipes, recipes(:three)
    assert_not_includes user.recipes, recipes(:two)  # belongs to user :two
  end

  # ===================
  # VALIDATION TESTS
  # ===================

  test "requires a name" do
    recipe = Recipe.new(
      cookbook: cookbooks(:one_personal),
      user: users(:one),
      name: nil  # Missing required field
    )

    assert_not recipe.valid?
    assert_includes recipe.errors[:name], "can't be blank"
  end

  test "user is optional" do
    recipe = Recipe.new(
      name: "Test Recipe",
      cookbook: cookbooks(:one_personal),
      user: nil
    )

    assert recipe.valid?
  end

  test "requires a cookbook" do
    recipe = Recipe.new(
      name: "Test Recipe",
      user: users(:one),
      cookbook: nil
    )

    assert_not recipe.valid?
    assert_includes recipe.errors[:cookbook], "must exist"
  end

  test "valid with minimal attributes" do
    recipe = Recipe.new(
      name: "Simple Recipe",
      cookbook: cookbooks(:one_personal),
      user: users(:one)
    )

    assert recipe.valid?
  end

  # ===================
  # SCOPE TESTS
  # ===================

  test "favorited scope returns only favorites" do
    user_one_recipes = users(:one).recipes

    # User one has: :one (not favorite) and :three (favorite)
    favorites = user_one_recipes.favorited

    assert_includes favorites, recipes(:three)
    assert_not_includes favorites, recipes(:one)
  end

  # ===================
  # CALLBACK TESTS
  # ===================

  test "ensures instructions is an array" do
    recipe = Recipe.new(
      name: "Test",
      cookbook: cookbooks(:one_personal),
      user: users(:one),
      instructions: nil
    )

    recipe.valid?  # Triggers before_validation callback

    assert_equal [], recipe.instructions
  end

  # ===================
  # COVER IMAGE TESTS
  # ===================

  test "can attach a cover image" do
    recipe = recipes(:one)

    recipe.cover_image.attach(
      io: File.open(Rails.root.join("test/fixtures/files/test_image.png")),
      filename: "test_image.png",
      content_type: "image/png"
    )

    assert recipe.cover_image.attached?
  end

  test "cover image is purged when recipe is destroyed" do
    recipe = recipes(:one)
    recipe.cover_image.attach(
      io: File.open(Rails.root.join("test/fixtures/files/test_image.png")),
      filename: "test_image.png",
      content_type: "image/png"
    )
    recipe.save!

    blob_id = recipe.cover_image.blob.id

    # purge_later enqueues a job, so we need to perform it
    recipe.meal_plan_entries.destroy_all
    perform_enqueued_jobs do
      recipe.destroy!
    end

    assert_nil ActiveStorage::Blob.find_by(id: blob_id)
  end

  test "cover image has semantic variants" do
    recipe = recipes(:one)
    recipe.cover_image.attach(
      io: File.open(Rails.root.join("test/fixtures/files/test_image.png")),
      filename: "test_image.png",
      content_type: "image/png"
    )

    assert recipe.cover_image.variant(:thumb).present?
    assert recipe.cover_image.variant(:card).present?
    assert recipe.cover_image.variant(:hero).present?
  end

  test "cover image keeps legacy variant aliases" do
    recipe = recipes(:one)
    recipe.cover_image.attach(
      io: File.open(Rails.root.join("test/fixtures/files/test_image.png")),
      filename: "test_image.png",
      content_type: "image/png"
    )

    assert recipe.cover_image.variant(:thumbnail).present?
    assert recipe.cover_image.variant(:display).present?
  end

  test "cover image urls returns semantic variants" do
    recipe = recipes(:one)
    recipe.cover_image.attach(
      io: File.open(Rails.root.join("test/fixtures/files/test_image.png")),
      filename: "test_image.png",
      content_type: "image/png"
    )

    assert_equal %i[thumb card hero], recipe.cover_image_urls.keys
    assert recipe.cover_image_urls[:thumb].present?
    assert recipe.cover_image_urls[:card].present?
    assert recipe.cover_image_urls[:hero].present?
  end

  # ===================
  # DEPENDENT DESTROY TEST
  # ===================

  test "recipes are nullified when user is deleted" do
    user = users(:one)
    recipe_ids = user.recipes.pluck(:id)

    assert_not_empty recipe_ids, "User should have recipes for this test"

    # When we delete the user, recipes are nullified (user_id set to nil), not deleted
    # Recipes now belong to cookbooks; user is just "created_by"
    user.destroy

    # Recipes still exist but with null user_id
    # Note: user.destroy triggers destroy_owned_cookbooks! which cascades to recipes
    recipe_ids.each do |id|
      assert_nil Recipe.find_by(id: id), "Recipe #{id} should be deleted via cookbook cascade"
    end
  end

  test "destroying a recipe with notification deliveries nullifies the delivery's recipe_id" do
    recipe = recipes(:one)
    # Clear any meal plan entries that would restrict deletion
    recipe.meal_plan_entries.destroy_all
    delivery = NotificationDelivery.create!(
      user: users(:one), campaign: "resurface", recipe: recipe, sent_at: 1.hour.ago
    )

    recipe.destroy

    # Recipe should be destroyed
    assert_nil Recipe.find_by(id: recipe.id)

    # Delivery should still exist but with null recipe_id
    assert_equal delivery.id, NotificationDelivery.find(delivery.id).id
    assert_nil NotificationDelivery.find(delivery.id).recipe_id
  end

  test "destroying a recipe deletes its recipe engagements" do
    recipe = recipes(:one)
    recipe.meal_plan_entries.destroy_all
    engagement = RecipeEngagement.create!(user: users(:one), recipe: recipe)

    recipe.destroy

    assert_nil Recipe.find_by(id: recipe.id)
    assert_nil RecipeEngagement.find_by(id: engagement.id)
  end
end
