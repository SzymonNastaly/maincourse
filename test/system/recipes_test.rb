require "application_system_test_case"

class RecipesTest < ApplicationSystemTestCase
  setup do
    @user = users(:one)
    @recipe = recipes(:one)
    sign_in_through_the_form(@user)
  end

  test "signing in lands on the recipe grid" do
    assert_selector "h1", text: "All Recipes"
    assert_selector "[data-testid=recipe-card]", minimum: 2
  end

  test "the add-a-recipe dialog opens and imports a link" do
    wait_for_stimulus("[data-testid=add-recipe]")
    find("[data-testid=add-recipe]").click

    assert_selector "#add-recipe[open]"
    fill_in "url", with: "https://smittenkitchen.com/2026/miso-butter-roast-chicken"
    click_button "Import recipe"

    assert_text "Importing your recipe"
    assert_selector "[data-testid=importing-overlay]"
  end

  test "the servings stepper rescales the ingredient quantities" do
    @recipe.ingredients.first.update!(amount: 200, unit: "g", name: "spaghetti")

    visit recipe_path(@recipe)
    assert_text "200 g"

    # 4 servings -> 6.
    wait_for_stimulus("[aria-label='More servings']")
    2.times { find("[aria-label='More servings']").click }

    assert_text "300 g"
    assert_text "servings (×1.5)"
  end

  test "the add-to-list dialog counts only the ticked ingredients" do
    visit recipe_path(@recipe)
    wait_for_stimulus("[data-testid=add-all-to-list]")
    find("[data-testid=add-all-to-list]").click

    assert_selector "#add-to-list[open]"
    assert_button "Add 4"

    # Ticking only moves the count once list-review is listening for the change.
    wait_for_stimulus("#add-to-list [data-controller~=list-review]")

    all("#add-to-list input[type=checkbox]").first.click
    assert_button "Add 3"

    find("[data-testid=confirm-add-to-list]").click

    assert_text "Added 3 items"
    assert_selector "[data-testid=shopping-item]", minimum: 3
  end

  test "ticking a shopping list item moves it to Already got" do
    visit shopping_list_items_path
    assert_selector "[data-testid=shopping-item]", count: 3

    within all("[data-testid=shopping-item]").first do
      find("button", match: :first).click
    end

    # Section labels are uppercased in CSS, so match on the item's new home.
    assert_selector "details [data-testid=shopping-item]", count: 2
  end
end
