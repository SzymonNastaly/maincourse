module CookbooksHelper
  # "Shared · 2 people" / "Personal · 42 recipes" — the rail card and the
  # switcher rows both use this.
  def cookbook_subtitle(cookbook)
    return "" if cookbook.blank?

    if cookbook.personal?
      t("web.personal_recipes", count: cookbook.recipes.count)
    else
      t("web.shared_people", count: cookbook.cookbook_memberships.count)
    end
  end

  def cookbook_icon(cookbook)
    cookbook&.personal? ? "user" : "users"
  end

  def membership_role_label(membership)
    membership.owner? ? t("web.owner") : t("web.member")
  end
end
