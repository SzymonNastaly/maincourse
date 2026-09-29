# Confirms shopping list item categories with the ingredient parser. Items are
# usable (and usually already provisionally categorized) before this runs.
class EnrichShoppingListItemsJob < ApplicationJob
  class IncompleteEnrichment < StandardError; end

  queue_as :default

  # The parser turns LLM outages into fallbacks, so spread the retries over
  # about half an hour. Items that still fail are asked for again on their next
  # edit, or by `bin/rails shopping_list:enqueue_enrichment`.
  retry_on IncompleteEnrichment, wait: ->(executions) { executions * 5.minutes }, attempts: 4 do |_job, error|
    Rails.logger.warn "[EnrichShoppingListItemsJob] giving up: #{error.message}"
  end

  def perform(ids)
    snapshots = ShoppingListItem.where(id: ids).select(&:needs_enrichment?)
      .map { |item| [ item.id, item.name, item.enrichment_input ] }
    return if snapshots.empty?

    # The LLM call happens outside any transaction or lock.
    parsed = IngredientParser.call(snapshots.map(&:last)).index_by { |hit| hit[:raw] }
    incomplete = []

    snapshots.each do |id, name, input|
      hit = parsed[input]
      unless hit&.dig(:enrichment_version) && hit[:category].present?
        incomplete << id
        next
      end

      item = ShoppingListItem.find_by(id: id)
      next if item.nil?

      item.with_lock do
        # Skip rows renamed or completed while the parser was running.
        next unless item.name == name && item.needs_enrichment?

        # Metadata only: keep updated_at (stale-list nudges read it) and skip
        # the Turbo refresh, since nothing the web list renders changed.
        item.update_columns(
          category: confirmed_category(hit[:category], item.category),
          canonical_name: hit[:canonical_name],
          enrichment_version: hit[:enrichment_version]
        )
      end
    end

    raise IncompleteEnrichment, "parser fallback for items #{incomplete.join(", ")}" if incomplete.any?
  end

  private

  # The parser reports a missing category as "other"; a specific provisional
  # guess is more useful than that.
  def confirmed_category(parsed, provisional)
    parsed == "other" && provisional.present? ? provisional : parsed
  end
end
