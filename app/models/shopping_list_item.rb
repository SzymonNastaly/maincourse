class ShoppingListItem < ApplicationRecord
  RECIPE_ADDITION_REVIEW_AGE = 36.hours

  belongs_to :cookbook
  belongs_to :user, optional: true
  belongs_to :source_recipe, class_name: "Recipe", optional: true

  validates :name, presence: true
  validates :client_id, presence: true, uniqueness: { scope: :cookbook_id }
  validates :canonical_name, format: { with: Llm::IngredientInstructions::CANONICAL_NAME_FORMAT }, allow_nil: true
  validates :category, inclusion: { in: Llm::IngredientInstructions::CATEGORIES }, allow_nil: true

  # Optional client guesses, used only when the name is new or changed.
  attr_accessor :category_hint, :canonical_name_hint

  before_save :categorize, if: -> { new_record? || will_save_change_to_name? }
  after_commit :enqueue_enrichment, on: %i[create update], if: :wants_enrichment?

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
    [ details.presence, name ].compact.join(" ").squish
  end

  # Collects the enrichment jobs of every item saved in the block into one job
  # (one LLM call) instead of one per item.
  def self.batching_enrichment
    outer = ActiveSupport::IsolatedExecutionState[:shopping_list_enrichment_ids]
    ids = ActiveSupport::IsolatedExecutionState[:shopping_list_enrichment_ids] = []
    yield
  ensure
    ActiveSupport::IsolatedExecutionState[:shopping_list_enrichment_ids] = outer
    if outer
      outer.concat(ids) if ids
    elsif ids.present?
      enqueue_enrichment_for(ids.uniq)
    end
  end

  # Enrichment is best effort: a failed enqueue must never fail a saved write.
  def self.enqueue_enrichment_for(ids)
    EnrichShoppingListItemsJob.perform_later(ids)
  rescue StandardError => error
    Rails.logger.error("[ShoppingListItem] could not enqueue enrichment for #{ids.join(", ")}: #{error.message}")
  end

  private

  # Cheapest source first: the source recipe's own enriched ingredient is
  # authoritative; otherwise take a valid client hint or look the name up, and
  # leave the row unversioned so the enrichment job confirms it. Details never
  # change the aisle, so only a new or renamed item is categorized.
  def categorize
    if (ingredient = source_recipe_ingredient)
      self.category = ingredient.category
      self.canonical_name = ingredient.canonical_name
      self.enrichment_version = ingredient.enrichment_version
      return
    end

    # Invalid hints are ignored rather than failing an otherwise valid save.
    hint = category_hint.to_s.strip.downcase
    if Llm::IngredientInstructions::CATEGORIES.include?(hint)
      self.category = hint
      self.canonical_name = Llm::IngredientInstructions.normalize_name(canonical_name_hint)
    else
      lookup = ShoppingList::CategoryLookup.call(name)
      self.category = lookup&.category
      self.canonical_name = lookup&.canonical_name
    end
    self.enrichment_version = nil
  end

  def source_recipe_ingredient
    return nil if source_recipe.nil?

    source_recipe.ingredients
      .where("lower(name) = lower(?)", name.to_s.strip)
      .detect { |ingredient| !ingredient.needs_enrichment? }
  end

  # Any commit of an unchecked, unconfirmed item asks for enrichment. Dirty
  # tracking would miss items saved twice in one transaction, and re-asking on
  # later edits (such as unchecking) recovers items whose job gave up.
  def wants_enrichment?
    checked_at.nil? && needs_enrichment?
  end

  def enqueue_enrichment
    if (batch = ActiveSupport::IsolatedExecutionState[:shopping_list_enrichment_ids])
      batch << id
    else
      self.class.enqueue_enrichment_for([ id ])
    end
  end
end
