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

  test "the add-to-list dialog excludes an enriched staple until explicitly included" do
    salt = @recipe.ingredients.first
    salt.update!(name: "Salz", raw: "Salz nach Geschmack", canonical_name: "salt")

    visit recipe_path(@recipe)
    wait_for_stimulus("[data-testid=add-all-to-list]")
    find("[data-testid=add-all-to-list]").click

    assert_selector "#add-to-list[open]"
    assert_text "Common staples are excluded by default. Include anything you need."
    salt_checkbox = find("input[type=checkbox][aria-label='Add Salz']")
    assert_not salt_checkbox.checked?
    assert_button "Add 3"

    salt_checkbox.click
    assert_button "Add 4"
    find("[data-testid=confirm-add-to-list]").click

    assert_text "Added 4 items"
    assert @recipe.cookbook.shopping_list_items.exists?(name: "Salz")
  end

  test "the add-to-list dialog disables Add when every ingredient is a staple" do
    canonical_names = [ "water", "salt", "black pepper", "tap water" ]
    @recipe.ingredients.each_with_index do |ingredient, index|
      ingredient.update!(canonical_name: canonical_names.fetch(index))
    end

    visit recipe_path(@recipe)
    wait_for_stimulus("[data-testid=add-all-to-list]")
    find("[data-testid=add-all-to-list]").click

    assert_selector "#add-to-list[open] input[type=checkbox]:not(:checked)", count: 4
    assert_button "Add 0", disabled: true
  end

  test "adding recipe ingredients offers to replace a list older than 36 hours" do
    shopping_list_items(:unchecked_milk).update!(created_at: 37.hours.ago)
    existing_ids = @recipe.cookbook.shopping_list_items.ids

    visit recipe_path(@recipe)
    wait_for_stimulus("[data-testid=add-all-to-list]")
    find("[data-testid=add-all-to-list]").click
    wait_for_stimulus("#add-to-list [data-controller~=list-review]")

    find("[data-testid=confirm-add-to-list]").click

    assert_text "Start a fresh shopping list?"
    assert_button "Clear and add"
    assert_button "Keep and add"
    assert_equal existing_ids.sort, @recipe.cookbook.shopping_list_items.reload.ids.sort

    click_button "Clear and add"

    assert_text "Added 4 items"
    assert_equal 4, @recipe.cookbook.shopping_list_items.reload.count
    assert_empty @recipe.cookbook.shopping_list_items.where(id: existing_ids)
  end

  test "old list cutoff is evaluated when Add is pressed" do
    oldest = shopping_list_items(:unchecked_milk)
    oldest.update!(created_at: 35.hours.ago)

    visit recipe_path(@recipe)
    wait_for_stimulus("[data-testid=add-all-to-list]")
    find("[data-testid=add-all-to-list]").click
    wait_for_stimulus("#add-to-list [data-controller~=list-review]")

    add_time = oldest.created_at.to_f * 1000 + 36.hours.in_milliseconds + 1
    page.execute_script("Date.now = () => #{add_time}")
    find("[data-testid=confirm-add-to-list]").click

    assert_text "Start a fresh shopping list?"
  end

  test "old list prompt can be cancelled or kept" do
    oldest = shopping_list_items(:unchecked_milk)
    oldest.update!(created_at: 37.hours.ago)

    visit recipe_path(@recipe)
    wait_for_stimulus("[data-testid=add-all-to-list]")
    find("[data-testid=add-all-to-list]").click
    wait_for_stimulus("#add-to-list [data-controller~=list-review]")
    find("[data-testid=confirm-add-to-list]").click

    click_button "Cancel"
    assert_button "Add 4"
    click_button "Add 4"
    click_button "Keep and add"

    assert_text "Added 4 items"
    assert @recipe.cookbook.shopping_list_items.reload.exists?(id: oldest.id)
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
