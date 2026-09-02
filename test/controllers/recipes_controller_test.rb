require "test_helper"

class RecipesControllerTest < ActionDispatch::IntegrationTest
  setup do
    @user = users(:one)
    @recipe = recipes(:one)
    @cookbook = cookbooks(:one_personal)
    sign_in_as @user
  end

  # --- Authentication ------------------------------------------------------

  test "every action requires authentication" do
    sign_out

    get recipes_path
    assert_redirected_to new_session_path

    get recipe_path(@recipe)
    assert_redirected_to new_session_path

    get new_recipe_path
    assert_redirected_to new_session_path

    post recipes_path, params: { recipe: { name: "Nope" } }
    assert_redirected_to new_session_path
  end

  # --- Index ---------------------------------------------------------------

  test "index lists the active cookbook's recipes" do
    get recipes_path

    assert_response :success
    assert_match recipes(:one).name, response.body
    assert_match recipes(:three).name, response.body
    assert_no_match recipes(:two).name, response.body
  end

  test "index scopes to the active cookbook, not just the personal one" do
    shared = create_shared_cookbook_for(@user)
    shared_recipe = shared.recipes.create!(name: "Shared Focaccia", user: @user)

    patch active_cookbook_path(cookbook_id: shared.id)
    get recipes_path

    assert_match shared_recipe.name, response.body
    assert_no_match recipes(:one).name, response.body
  end

  test "index filters by tag" do
    get recipes_path(tag: tags(:breakfast).slug)

    assert_response :success
    assert_match recipes(:one).name, response.body
    assert_no_match recipes(:three).name, response.body
  end

  test "index sorts by name when asked" do
    get recipes_path(sort: "name")

    assert_response :success
    assert_operator response.body.index(recipes(:three).name), :<,
                    response.body.index(recipes(:one).name)
  end

  test "index shows failed imports as banners, not cards" do
    failed = @cookbook.recipes.create!(
      name: "Importing…", user: @user, import_status: :failed,
      error_message: "The site blocked our reader."
    )

    get recipes_path

    assert_response :success
    assert_select "[data-testid=failed-import]", 1
    assert_match failed.error_message, response.body
  end

  test "index renders pending imports with a spinner" do
    @cookbook.recipes.create!(name: "Importing…", user: @user, import_status: :pending)

    get recipes_path

    assert_select "[data-testid=importing-overlay]", 1
  end

  test "index shows the recipe count in the rail" do
    get recipes_path

    assert_select "[data-testid=recipes-count]", text: @cookbook.recipes.count.to_s
  end

  # --- Show ----------------------------------------------------------------

  test "show renders ingredients and method" do
    get recipe_path(@recipe)

    assert_response :success
    assert_match @recipe.name, response.body
    assert_match "spaghetti", response.body
    assert_match "Boil pasta", response.body
    assert_select "[data-testid=servings-stepper]"
  end

  test "show cannot reach another user's recipe" do
    get recipe_path(recipes(:two))

    assert_redirected_to recipes_path
    assert_equal "That recipe is no longer available.", flash[:alert]
  end

  test "show follows a recipe into the user's other cookbook and switches" do
    shared = create_shared_cookbook_for(@user)
    shared_recipe = shared.recipes.create!(name: "Shared Focaccia", user: @user)

    # A shared cookbook is preferred by default, so start from the personal one.
    patch active_cookbook_path(cookbook_id: @cookbook.id)
    assert_equal @cookbook.id, session[:cookbook_id]

    get recipe_path(shared_recipe)

    assert_response :success
    assert_equal shared.id, session[:cookbook_id]
  end

  # --- Create / update / destroy -------------------------------------------

  test "create saves a recipe with ingredients, steps and tags" do
    assert_difference "Recipe.count", 1 do
      post recipes_path, params: {
        recipe: {
          name: "Miso Butter Roast Chicken",
          servings: 4,
          prep_time: 20,
          cook_time: 65,
          ingredients: [ "1 whole chicken", "60 g unsalted butter", "" ],
          instructions: [ "Heat the oven", "Roast", "  " ],
          tag_ids: [ "", tags(:dinner).id.to_s ]
        }
      }
    end

    recipe = Recipe.order(:created_at).last
    assert_redirected_to recipe_path(recipe)
    assert_equal @cookbook, recipe.cookbook
    assert_equal @user, recipe.user
    assert_equal [ "1 whole chicken", "60 g unsalted butter" ], recipe.ingredients.map(&:raw)
    assert_equal [ "Heat the oven", "Roast" ], recipe.instructions
    assert_equal [ tags(:dinner) ], recipe.tags
  end

  test "create re-renders on validation failure" do
    assert_no_difference "Recipe.count" do
      post recipes_path, params: { recipe: { name: "" } }
    end

    assert_response :unprocessable_entity
    assert_select "[data-testid=form-errors]"
  end

  test "update replaces ingredients and enqueues parsing" do
    assert_enqueued_with(job: ParseRecipeIngredientsJob) do
      patch recipe_path(@recipe), params: {
        recipe: { name: "Carbonara", ingredients: [ "200 g spaghetti" ] }
      }
    end

    assert_redirected_to recipe_path(@recipe)
    assert_equal [ "200 g spaghetti" ], @recipe.reload.ingredients.map(&:raw)
  end

  test "update does not purge the cover image when no new file is sent" do
    @recipe.cover_image.attach(
      io: File.open(Rails.root.join("app/assets/images/icon.png")),
      filename: "icon.png", content_type: "image/png"
    )

    patch recipe_path(@recipe), params: { recipe: { name: "Still Carbonara", cover_image: "" } }

    assert @recipe.reload.cover_image.attached?
  end

  test "destroy removes the recipe" do
    recipe = @cookbook.recipes.create!(name: "Throwaway", user: @user)

    assert_difference "Recipe.count", -1 do
      delete recipe_path(recipe)
    end

    assert_redirected_to recipes_path
  end

  # Recipes are blocked from deletion while a meal plan references them
  # (Recipe#meal_plan_entries is restrict_with_error). Meal plans have no UI on
  # web or iOS today, so surface the reason rather than failing silently.
  test "destroy explains itself when a meal plan still references the recipe" do
    assert_no_difference "Recipe.count" do
      delete recipe_path(@recipe)
    end

    assert_redirected_to recipe_path(@recipe)
    assert flash[:alert].present?
  end

  test "destroy cannot reach another user's recipe" do
    assert_no_difference "Recipe.count" do
      delete recipe_path(recipes(:two))
    end
  end

  # --- Move ----------------------------------------------------------------

  test "move relocates a recipe to the other cookbook" do
    shared = create_shared_cookbook_for(@user)

    patch move_recipe_path(@recipe, cookbook_id: shared.id)

    assert_redirected_to recipes_path
    assert_equal shared, @recipe.reload.cookbook
  end

  test "move rejects a cookbook the user is not in" do
    patch move_recipe_path(@recipe, cookbook_id: cookbooks(:two_personal).id)

    assert_equal @cookbook, @recipe.reload.cookbook
  end
end
