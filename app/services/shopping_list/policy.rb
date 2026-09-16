module ShoppingList
  module Policy
    STAPLES = [
      "water", "tap water",
      "salt", "table salt", "sea salt", "kosher salt",
      "black pepper", "ground black pepper"
    ].freeze

    def self.default_included?(canonical_name)
      !STAPLES.include?(canonical_name)
    end
  end
end
