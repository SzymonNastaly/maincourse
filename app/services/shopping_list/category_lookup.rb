module ShoppingList
  # Guesses a shopping item's category from confirmed recipe ingredients and
  # shopping list items that share its name, so most items land in an aisle
  # without waiting for the LLM. Counting confirmed shopping items means a
  # non-recipe item (dish soap, coffee) needs the LLM only the first time.
  # SQLite's lower() only folds ASCII; both sides fold the same way, so
  # non-ASCII names still match when typed with the same case.
  class CategoryLookup
    Match = Data.define(:category, :canonical_name)

    def self.call(name)
      name = name.to_s.strip
      return nil if name.blank?

      counts = confirmed_counts(Ingredient, name)
        .merge(confirmed_counts(ShoppingListItem, name)) { |_key, a, b| a + b }
      # Ties break alphabetically so the result doesn't depend on row order.
      category, canonical_name = counts.min_by { |(category, canonical_name), count| [ -count, category, canonical_name ] }&.first

      category && Match.new(category:, canonical_name:)
    end

    def self.confirmed_counts(model, name)
      model
        .where("lower(name) = lower(?)", name)
        .where(enrichment_version: Llm::IngredientInstructions::VERSION)
        .where.not(category: nil)
        .where.not(canonical_name: nil)
        .group(:category, :canonical_name)
        .count
    end
    private_class_method :confirmed_counts
  end
end
