class Ingredient < ApplicationRecord
  belongs_to :recipe, inverse_of: :ingredients

  validates :raw, presence: true
  validates :canonical_unit, inclusion: { in: Llm::IngredientInstructions::UNITS }, allow_nil: true
  validates :category, inclusion: { in: Llm::IngredientInstructions::CATEGORIES }, allow_nil: true

  def parsed?
    amount.present? || unit.present?
  end

  def needs_enrichment?
    enrichment_version != Llm::IngredientInstructions::VERSION || canonical_name.blank? || category.blank?
  end
end
