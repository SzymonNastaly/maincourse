module Notifications
  # Saved and forgotten. The user's own saves are proven intent, which makes them a
  # better suggestion than anything the app could pick on its own.
  class ResurfaceCampaign
    NAME = "resurface".freeze
    MIN_AGE = 14.days
    RESUGGEST_AFTER = 30.days

    # Recipes saved before view tracking shipped have no view history, so they all look
    # "never opened". Without this floor the campaign would fire across a user's entire
    # existing library. Set to the deploy date of the iOS release that sends view pings.
    VIEW_TRACKING_SINCE = Time.utc(2026, 8, 29)

    def self.eligible_for(user)
      recipe = candidates(user).first
      return nil if recipe.nil?

      Candidate.new(
        campaign: NAME,
        recipe: recipe,
        cookbook: recipe.cookbook,
        title: "Hauptgang",
        body: %(You saved "#{recipe.name}" a while back. Cook it this week?)
      )
    end

    def self.candidates(user)
      user.recipes
          .completed
          .where(created_at: VIEW_TRACKING_SINCE..MIN_AGE.ago)
          .where(cookbook_id: user.cookbooks.select(:id))
          .where.missing(:shopping_list_items)
          .where.not(id: excluded_recipe_ids(user))
          .order(created_at: :asc)
    end

    def self.excluded_recipe_ids(user)
      RecipeEngagement
        .where(user_id: user.id)
        .where(
          "last_viewed_at IS NOT NULL OR added_to_list_at IS NOT NULL OR last_suggested_at > ?",
          RESUGGEST_AFTER.ago
        )
        .select(:recipe_id)
    end
  end
end
