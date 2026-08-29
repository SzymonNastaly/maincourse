module Notifications
  # A list that was filled and then abandoned. Shopping is the only naturally recurring
  # behaviour left in the app, so this is the nudge with the most real-world pull.
  class StaleShoppingListCampaign
    NAME = "stale_shopping_list".freeze
    MIN_ITEMS = 3
    STALENESS = 3.days

    def self.eligible_for(user)
      user.cookbooks.each do |cookbook|
        next unless stale?(cookbook)

        count = cookbook.shopping_list_items.unchecked.count
        return Candidate.new(
          campaign: NAME,
          recipe: nil,
          cookbook: cookbook,
          title: "Hauptgang",
          body: "#{count} #{'item'.pluralize(count)} are still on your shopping list."
        )
      end

      nil
    end

    def self.stale?(cookbook)
      unchecked = cookbook.shopping_list_items.unchecked
      return false if unchecked.count < MIN_ITEMS

      oldest = unchecked.minimum(:created_at)
      return false if oldest.nil? || oldest > STALENESS.ago

      # Deletions leave no row, so updated_at is the available proxy for "touched".
      !cookbook.shopping_list_items.where(updated_at: STALENESS.ago..).exists?
    end
  end
end
