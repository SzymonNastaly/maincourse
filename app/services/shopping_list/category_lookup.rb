module ShoppingList
  # Guesses a shopping item's category from recipe ingredients that share its
  # name, so most items land in an aisle without waiting for the LLM.
  # SQLite's lower() only folds ASCII; both sides fold the same way, so
  # non-ASCII names still match when typed with the same case.
  class CategoryLookup
    Match = Data.define(:category, :canonical_name)

    def self.call(name, scope: Ingredient.all)
      name = name.to_s.strip
      return nil if name.blank?

      category, canonical_name = scope
        .where("lower(name) = lower(?)", name)
        .where(enrichment_version: Llm::IngredientInstructions::VERSION)
        .where.not(category: nil)
        .where.not(canonical_name: nil)
        .group(:category, :canonical_name)
        .order(Arel.sql("COUNT(*) DESC"), :category, :canonical_name)
        .limit(1)
        .count
        .keys
        .first

      category && Match.new(category:, canonical_name:)
    end
  end
end
