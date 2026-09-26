module NavigationHelper
  # The rail's destinations, in order. `count` renders muted; `badge` renders as
  # a green pill (used for the number of things still to buy).
  def nav_items
    return [] if current_cookbook.blank?

    [
      { key: :recipes, label: t("web.recipes"), icon: "utensils", path: recipes_path,
        count: current_cookbook.recipes.where.not(import_status: :failed).count },
      { key: :search, label: t("web.search"), icon: "search", path: search_path },
      { key: :shopping, label: t("web.shopping_list"), icon: "shopping-cart", path: shopping_list_items_path,
        badge: current_cookbook.shopping_list_items.unchecked.count },
      { key: :cookbooks, label: t("web.cookbooks"), icon: "book-open", path: cookbooks_path }
    ]
  end

  def nav_active?(key)
    current_nav_key == key
  end

  def current_nav_key
    case controller_name
    when "recipes" then :recipes
    when "searches" then :search
    when "shopping_list_items" then :shopping
    when "cookbooks", "invitations" then :cookbooks
    when "settings", "accounts", "pro" then :settings
    end
  end

  def rail_collections
    Tag.for_cookbook(current_cookbook)
  end
end
