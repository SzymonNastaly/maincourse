module Notifications
  # Saved a couple of days ago and never used since. The recipe is still fresh in the
  # user's mind, which makes this the highest-intent nudge available.
  class ImportFollowUpCampaign
    NAME = "import_follow_up".freeze
    MIN_AGE = 2.days
    MAX_AGE = 7.days
    # A view within this window of the import is the import flow itself, not the user
    # coming back to the recipe later.
    IMPORT_VIEW_GRACE = "+1 hour".freeze

    def self.eligible_for(user)
      recipe = candidates(user).first
      return nil if recipe.nil?

      Candidate.new(
        campaign: NAME,
        recipe: recipe,
        cookbook: recipe.cookbook,
        title: "Hauptgang",
        body: %(You saved "#{recipe.name}" a couple of days ago — add the ingredients to your shopping list?)
      )
    end

    def self.candidates(user)
      user.recipes
          .completed
          .where(created_at: MAX_AGE.ago..MIN_AGE.ago)
          .where.missing(:shopping_list_items)
          .where.not(id: suggested_recipe_ids(user))
          .where.not(id: revisited_recipe_ids(user))
          .where.not(id: acted_on_recipe_ids(user))
          .order(created_at: :desc)
    end

    def self.suggested_recipe_ids(user)
      RecipeEngagement.where(user_id: user.id).where.not(last_suggested_at: nil).select(:recipe_id)
    end

    # Shopping list rows are not durable: ShoppingListItem.cleanup_stale_checked_for
    # destroys checked items after an hour, so `where.missing(:shopping_list_items)`
    # alone cannot tell "never added" from "added, checked off, and cleaned up". The
    # engagement row survives that cleanup and is the durable signal.
    def self.acted_on_recipe_ids(user)
      RecipeEngagement.where(user_id: user.id)
                      .where("added_to_list_at IS NOT NULL OR cooked_at IS NOT NULL")
                      .select(:recipe_id)
    end

    # SQLite-specific date arithmetic; this app has no other adapter.
    def self.revisited_recipe_ids(user)
      RecipeEngagement.where(user_id: user.id)
                      .joins(:recipe)
                      .where("recipe_engagements.last_viewed_at > datetime(recipes.created_at, ?)", IMPORT_VIEW_GRACE)
                      .select(:recipe_id)
    end
  end
end
