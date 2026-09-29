class ShoppingListItem < ApplicationRecord
  RECIPE_ADDITION_REVIEW_AGE = 36.hours

  belongs_to :cookbook
  belongs_to :user, optional: true
  belongs_to :source_recipe, class_name: "Recipe", optional: true

  validates :name, presence: true
  validates :client_id, presence: true, uniqueness: { scope: :cookbook_id }
  validates :canonical_name, format: { with: Llm::IngredientInstructions::CANONICAL_NAME_FORMAT }, allow_nil: true
  validates :category, inclusion: { in: Llm::IngredientInstructions::CATEGORIES }, allow_nil: true

  before_validation :drop_invalid_category_hints
  before_save :categorize, if: :categorization_input_changed?
  after_commit :enqueue_enrichment, on: %i[create update], if: :enrichment_input_saved?

  # A shared shopping list updates for everyone looking at it.
  broadcasts_refreshes_to :cookbook

  scope :unchecked, -> { where(checked_at: nil) }
  scope :checked, -> { where.not(checked_at: nil) }
  scope :stale_checked, -> { where("checked_at < ?", 1.hour.ago) }
  scope :old_for_recipe_addition, -> { where("created_at < ?", RECIPE_ADDITION_REVIEW_AGE.ago) }

  def self.cleanup_stale_checked_for(cookbook)
    cookbook.shopping_list_items.checked.stale_checked.destroy_all
  end

  def self.ransackable_attributes(_auth_object = nil)
    %w[id name details client_id]
  end

  # Complete once the LLM (or a matching recipe ingredient) has confirmed the
  # category. Until then `category` may hold a provisional guess.
  def needs_enrichment?
    enrichment_version != Llm::IngredientInstructions::VERSION || canonical_name.blank? || category.blank?
  end

  def enrichment_input
    [ details.presence, name ].compact.join(" ")
  end

  private

  # Clients may send category/canonical_name as hints. A bad hint must never
  # make an otherwise valid shopping item fail to save.
  def drop_invalid_category_hints
    self.category = nil if category.present? && !Llm::IngredientInstructions::CATEGORIES.include?(category)
    self.canonical_name = Llm::IngredientInstructions.normalize_name(canonical_name) if canonical_name.present?
  end

  def categorization_input_changed?
    new_record? || will_save_change_to_name? || will_save_change_to_details?
  end

  # Cheapest source first: the source recipe's own enriched ingredient is
  # authoritative; otherwise keep the client's hint or look the name up, and
  # leave the row unversioned so the enrichment job confirms it.
  def categorize
    if (ingredient = source_recipe_ingredient)
      self.category = ingredient.category
      self.canonical_name = ingredient.canonical_name
      self.enrichment_version = ingredient.enrichment_version
      return
    end

    hinted = will_save_change_to_category? && category.present?
    lookup = ShoppingList::CategoryLookup.call(name) unless hinted

    self.category = hinted ? category : lookup&.category
    self.canonical_name = hinted ? (canonical_name if will_save_change_to_canonical_name?) : lookup&.canonical_name
    self.enrichment_version = nil
  end

  def source_recipe_ingredient
    return nil if source_recipe.nil?

    source_recipe.ingredients
      .where("lower(name) = lower(?)", name.to_s.strip)
      .detect { |ingredient| !ingredient.needs_enrichment? }
  end

  def enrichment_input_saved?
    needs_enrichment? && (previously_new_record? || saved_change_to_name? || saved_change_to_details?)
  end

  def enqueue_enrichment
    EnrichShoppingListItemsJob.perform_later([ id ])
  end
end
