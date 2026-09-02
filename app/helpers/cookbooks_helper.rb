module CookbooksHelper
  # "Shared · 2 people" / "Personal · 42 recipes" — the rail card and the
  # switcher rows both use this.
  def cookbook_subtitle(cookbook)
    return "" if cookbook.blank?

    if cookbook.personal?
      "Personal · #{pluralize(cookbook.recipes.count, 'recipe')}"
    else
      "Shared · #{pluralize(cookbook.cookbook_memberships.count, 'person', plural: 'people')}"
    end
  end

  def cookbook_icon(cookbook)
    cookbook&.personal? ? "user" : "users"
  end

  def membership_role_label(membership)
    membership.owner? ? "Owner" : "Member"
  end
end
