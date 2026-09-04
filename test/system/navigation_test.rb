require "application_system_test_case"

class NavigationTest < ApplicationSystemTestCase
  setup do
    @user = users(:one)
    sign_in_through_the_form(@user)
  end

  test "the desktop rail links to every destination" do
    assert_selector "[data-testid=rail]"

    click_link "Shopping List"
    assert_selector "h1", text: "Shopping List"

    click_link "Cookbooks"
    assert_selector "h1", text: "Cookbooks"

    click_link "Recipes"
    assert_selector "h1", text: "All Recipes"
  end

  test "the rail collapses behind the menu button on a narrow window" do
    with_mobile_viewport do
      visit recipes_path

      assert_no_selector "[data-testid=rail]", visible: true
      wait_for_stimulus("[data-testid=drawer-toggle]")
      find("[data-testid=drawer-toggle]").click

      assert_selector "[data-testid=drawer]", visible: true
      within "[data-testid=drawer]" do
        click_link "Shopping List"
      end

      assert_selector "h1", text: "Shopping List"
    end
  end

  test "the cookbook switcher changes the active cookbook" do
    shared = Cookbook.create!(name: "Our Kitchen", personal: false)
    CookbookMembership.create!(cookbook: shared, user: @user, role: :owner)

    visit recipes_path
    wait_for_stimulus("[data-testid=cookbook-switcher-trigger]")
    find("[data-testid=cookbook-switcher-trigger]").click

    assert_selector "#cookbook-switcher[open]"
    find("[data-testid=switch-cookbook-#{cookbooks(:one_personal).id}]").click

    assert_text "Now showing My Recipes"
  end

  test "cmd-K focuses the search field" do
    wait_for_stimulus("[data-controller~=search-shortcut]")
    find("body").send_keys([ :meta, "k" ])

    assert_equal "q", page.evaluate_script("document.activeElement.name")
  end
end
