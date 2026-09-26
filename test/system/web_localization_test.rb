require "application_system_test_case"

class WebLocalizationSystemTest < ApplicationSystemTestCase
  test "language picker survives sign in and translated scaling submits stable quantities" do
    ingredient = ingredients(:one_spaghetti)
    ingredient.update!(amount: 1.2, unit: "g")
    visit new_session_path
    select "Deutsch", from: "locale"
    click_button "Save"
    assert_selector "html[lang=de]"

    fill_in "email_address", with: users(:one).email_address
    fill_in "password", with: "password"
    find("[data-testid=submit]").click
    assert_selector "h1", text: "Alle Rezepte"
    visit recipe_path(recipes(:one))
    wait_for_stimulus("[data-controller~=portion-scaler]")
    assert_text "1,2 g"
    2.times { find("[aria-label='Mehr Portionen']").click }
    assert_text "1,8 g"
    assert_text "Portionen (×1,5)"

    find("[data-testid=add-all-to-list]").click
    assert_selector "#add-to-list[open]"
    assert_button "4 hinzufügen"
    find("[data-testid=confirm-add-to-list]").click
    assert_text "4 Einträge"
    assert_equal "1.8 g", ShoppingListItem.order(:created_at).where(name: ingredient.name).last.details

    with_mobile_viewport do
      visit edit_settings_path
      select "Polski", from: "locale"
      within "form[action='/locale']" do
        click_button "Speichern"
      end
      assert_selector "html[lang=pl]"
      assert_selector "h1", text: "Ustawienia"
      find("[data-testid=drawer-toggle]").click
      assert_selector "[data-testid=drawer]", visible: true
      assert_link "Lista zakupów"
      assert_equal false, page.evaluate_script("document.documentElement.scrollWidth > window.innerWidth")

      visit cookbooks_path
      wait_for_stimulus("[data-testid=create-shared-cookbook]")
      find("[data-testid=create-shared-cookbook]").click
      assert_selector "#create-cookbook[open]"
      assert_equal false, page.evaluate_script("document.querySelector('#create-cookbook').scrollWidth > document.querySelector('#create-cookbook').clientWidth")

      visit recipe_path(recipes(:one))
      wait_for_stimulus("[data-controller~=portion-scaler]")
      assert_selector "[data-portion-scaler-target=servings]", text: "4"
      assert_selector "[data-portion-scaler-target=label]", text: "porcje"
      find("[aria-label='Więcej porcji']").click
      assert_selector "[data-portion-scaler-target=servings]", text: "5"
      assert_selector "[data-portion-scaler-target=label]", text: "porcji"
      4.times { find("[aria-label='Mniej porcji']").click }
      assert_selector "[data-portion-scaler-target=servings]", text: "1"
      assert_selector "[data-portion-scaler-target=label]", text: "porcja"
    end
  end
end
