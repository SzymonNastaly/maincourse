module Llm
  module RecipeExtraction
    Result = Data.define(:success?, :recipe_attributes, :error, :error_code)

    INGREDIENT_KEYS = %w[
      name amount amount_max unit note raw
      canonical_name canonical_unit category
    ].freeze

    private

    def build_result(content)
      return extraction_failed("No recipe data returned") if content.blank?

      name = content["name"].to_s.strip
      return extraction_failed("Could not identify recipe name") if name.blank?

      attributes = {
        name: name,
        ingredients: normalize_ingredients(content["ingredients"]),
        instructions: normalize_array(content["instructions"]),
        prep_time: content["prep_time"],
        cook_time: content["cook_time"],
        servings: content["servings"],
        notes: content["notes"].to_s.strip.presence
      }

      Result.new(success?: true, recipe_attributes: attributes, error: nil, error_code: nil)
    end

    def normalize_array(arr)
      return [] unless arr.is_a?(Array)
      arr.map { |item| item.to_s.strip }.reject(&:blank?)
    end

    def normalize_ingredients(arr)
      return [] unless arr.is_a?(Array)

      arr.filter_map do |entry|
        hash = coerce_ingredient_hash(entry)
        next nil unless hash

        raw = hash["raw"].to_s.strip.presence || synthesize_raw(hash)
        name = hash["name"].to_s.strip.presence || raw
        next nil if raw.blank? && name.blank?
        canonical_name = IngredientInstructions.normalize_name(hash["canonical_name"])

        {
          name: name,
          amount: hash["amount"],
          amount_max: hash["amount_max"],
          unit: hash["unit"].to_s.strip.presence,
          note: hash["note"].to_s.strip.presence,
          canonical_name: canonical_name,
          canonical_unit: IngredientInstructions.normalize_unit(hash["canonical_unit"]),
          category: IngredientInstructions.normalize_category(hash["category"]),
          enrichment_version: canonical_name.present? ? IngredientInstructions::VERSION : nil,
          raw: raw.presence || name
        }
      end
    end

    def coerce_ingredient_hash(entry)
      case entry
      when Hash
        entry.transform_keys(&:to_s).slice(*INGREDIENT_KEYS)
      when String
        s = entry.strip
        s.blank? ? nil : { "raw" => s }
      else
        nil
      end
    end

    def synthesize_raw(hash)
      [
        format_amount_range(hash["amount"], hash["amount_max"]),
        hash["unit"],
        hash["name"],
        hash["note"].present? ? "(#{hash["note"]})" : nil
      ].compact.map(&:to_s).reject(&:blank?).join(" ").squish
    end

    def format_amount_range(amount, amount_max)
      return nil if amount.blank?
      return amount.to_s if amount_max.blank?
      "#{amount}-#{amount_max}"
    end

    def extraction_failed(message)
      error_result(message, :extraction_failed)
    end

    def error_result(message, code)
      Result.new(success?: false, recipe_attributes: {}, error: message, error_code: code)
    end
  end
end
