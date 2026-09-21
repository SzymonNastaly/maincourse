require "schematist"

module Llm
  class IngredientSchema < Schematist::Schema
    string :raw, description: "The original ingredient line, echoed back verbatim. Used to align parsed output to inputs."
    string :name, description: "The food name only (e.g. 'tomato', 'olive oil'). Do not translate. Do not include amount or unit."
    number :amount, required: false, description: "Numeric quantity. Convert fractions to decimals (1/2 -> 0.5; unicode fractions handled). For ranges, store the lower bound here."
    number :amount_max, required: false, description: "Upper bound for ranges (e.g. '2-3 cloves' -> amount=2, amount_max=3). Accept en-dash, em-dash, '-', '~', 'to', 'bis'."
    string :unit, required: false, description: "Unit of measurement, lowercased best-effort (e.g. 'g', 'ml', 'tbsp', 'el', 'prise'). Open vocabulary."
    string :note, required: false, description: "Qualifier (e.g. 'chopped', 'to taste', 'optional')."
    string :canonical_name, description: "Concise lowercase English food identity using only ASCII letters, digits, and single spaces (e.g. 'olive oil' or '2 percent milk')."
    string :canonical_unit, required: false, description: "Language-independent unit from the application's supported unit vocabulary."
    string :category, description: "Shopping category from the application's supported category vocabulary."
  end
end
