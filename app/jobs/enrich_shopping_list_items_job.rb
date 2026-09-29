# Confirms shopping list item categories with the ingredient parser. Items are
# usable (and usually already provisionally categorized) before this runs.
class EnrichShoppingListItemsJob < ApplicationJob
  class IncompleteEnrichment < StandardError; end

  queue_as :default

  retry_on IncompleteEnrichment, wait: :polynomially_longer, attempts: 3 do |_job, error|
    Rails.logger.warn "[EnrichShoppingListItemsJob] giving up: #{error.message}"
  end

  def perform(ids)
    snapshots = ShoppingListItem.where(id: ids).select(&:needs_enrichment?)
      .map { |item| [ item.id, item.name, item.details, item.enrichment_input.strip ] }
    return if snapshots.empty?

    # The LLM call happens outside any transaction or lock.
    parsed = IngredientParser.call(snapshots.map(&:last)).index_by { |hit| hit[:raw] }
    incomplete = []

    snapshots.each do |id, name, details, input|
      hit = parsed[input]
      unless hit&.dig(:enrichment_version) && hit[:category].present?
        incomplete << id
        next
      end

      item = ShoppingListItem.find_by(id: id)
      next if item.nil?

      item.with_lock do
        # Skip rows edited or completed while the parser was running.
        next unless item.name == name && item.details == details && item.needs_enrichment?

        item.update!(
          category: hit[:category],
          canonical_name: hit[:canonical_name],
          enrichment_version: hit[:enrichment_version]
        )
      end
    end

    raise IncompleteEnrichment, "parser fallback for items #{incomplete.join(", ")}" if incomplete.any?
  end
end
