module Llm
  module IngredientInstructions
    VERSION = 1

    CATEGORIES = %w[
      produce
      bakery
      meat_seafood
      dairy_eggs
      pantry
      oils_spices_condiments
      frozen
      beverages
      household
      other
    ].freeze

    UNITS = %w[
      milligram gram kilogram
      milliliter centiliter deciliter liter
      teaspoon tablespoon cup fluid_ounce pint quart gallon
      ounce pound
      piece clove pinch bunch slice
      can package bottle jar sprig
    ].freeze

    module_function

    def prompt
      <<~INSTRUCTIONS.strip
        For each ingredient, return structured fields:
        - `raw`: the original ingredient line, echoed verbatim. Required.
        - `name`: the food name only (no amount or unit). Do not translate `name`.
        - `amount`: numeric quantity. Convert fractions to decimals (1/2 -> 0.5; unicode fractions accepted).
        - `amount_max`: upper bound for ranges (for example, 2-3 cloves -> amount=2, amount_max=3).
        - `unit`: the original unit, lowercased best-effort. Open vocabulary.
        - `note`: a qualifier such as "chopped", "to taste", or "optional".
        - `canonical_name`: a concise lowercase English identity for the food, without amount, unit, preparation, or brand. For example, Olivenöl and huile d'olive both become "olive oil". This is matching metadata only.
        - `canonical_unit`: normalize the unit to one of: #{UNITS.join(", ")}. Omit it when there is no unit or no listed unit is equivalent.
        - `category`: choose exactly one of: #{CATEGORIES.join(", ")}.

        Preserve meaning: distinguish materially different products such as fresh tomatoes and canned tomatoes. Do not invent quantities or units.
      INSTRUCTIONS
    end

    def normalize_category(value)
      candidate = value.to_s.strip.downcase
      CATEGORIES.include?(candidate) ? candidate : "other"
    end

    def normalize_unit(value)
      candidate = value.to_s.strip.downcase
      UNITS.include?(candidate) ? candidate : nil
    end
  end
end
