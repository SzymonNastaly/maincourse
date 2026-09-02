class Recipe < ApplicationRecord
  # Enums
  enum :import_status, { pending: 0, completed: 1, failed: 2 }

  # Associations
  belongs_to :cookbook
  belongs_to :user, optional: true
  has_many :recipe_tags, dependent: :destroy
  has_many :tags, through: :recipe_tags
  has_many :shopping_list_items, foreign_key: :source_recipe_id, dependent: :nullify
  has_many :meal_plan_entries, dependent: :restrict_with_error
  has_many :ingredients, -> { order(:position) }, dependent: :destroy, inverse_of: :recipe
  has_many :recipe_engagements, dependent: :delete_all
  has_many :notification_deliveries, dependent: :nullify

  # Keeps the web UI live: other members of a shared cookbook see new recipes,
  # and an import that finishes in the background replaces its own spinner card.
  broadcasts_refreshes_to :cookbook

  # Replace the ingredient rows from an array of raw strings.
  # Existing rows are wiped. `name` is left nil until ParseRecipeIngredientsJob
  # fills structured fields; consumers should fall back to `raw` for display.
  def replace_ingredients_from_strings(strings)
    cleaned = Array(strings).map { |s| s.to_s.strip }.reject(&:blank?)

    transaction do
      ingredients.destroy_all
      cleaned.each_with_index do |raw, idx|
        ingredients.create!(position: idx, raw: raw)
      end
    end
  end

  # Replace the ingredient rows from an array of structured hashes
  # (as returned by the recipe extraction LLM call). Each hash should
  # have at least `:raw`. Falls back to `name` if `raw` is missing.
  # If the entry has structured fields, they are persisted directly.
  def replace_ingredients_from_hashes(entries)
    cleaned = Array(entries).filter_map do |entry|
      hash = entry.is_a?(Hash) ? entry.symbolize_keys : { raw: entry.to_s, name: entry.to_s }
      raw = hash[:raw].to_s.strip.presence || hash[:name].to_s.strip
      next nil if raw.blank?
      hash.merge(raw: raw, name: hash[:name].to_s.strip.presence || raw)
    end

    transaction do
      ingredients.destroy_all
      cleaned.each_with_index do |hash, idx|
        ingredients.create!(
          position: idx,
          raw: hash[:raw],
          name: hash[:name],
          amount: hash[:amount],
          amount_max: hash[:amount_max],
          unit: hash[:unit].to_s.strip.presence,
          note: hash[:note].to_s.strip.presence
        )
      end
    end
  end

  # Apply extracted recipe attributes (from RecipeImporter result). Splits
  # ingredients from the rest and persists each appropriately.
  def apply_extracted_attributes!(attrs)
    attrs = attrs.to_h.symbolize_keys
    ingredient_entries = attrs.delete(:ingredients)
    update!(attrs)
    replace_ingredients_from_hashes(ingredient_entries) if ingredient_entries
  end

  COVER_IMAGE_VARIANTS = {
    thumb: :thumb,
    card: :card,
    hero: :hero
  }.freeze

  # File attachments
  has_one_attached :cover_image, dependent: :purge_later do |attachable|
    # Small thumbnails for compact list rows and meal plan pickers.
    attachable.variant :thumb, resize_to_limit: [ 320, 320 ], format: :webp, saver: { quality: 78 }
    # Medium-large artwork for recipe list cards on modern iPhones and iPads.
    attachable.variant :card, resize_to_limit: [ 1280, 960 ], format: :webp, saver: { quality: 82 }
    # Larger artwork for recipe detail hero images.
    attachable.variant :hero, resize_to_limit: [ 1800, 1350 ], format: :webp, saver: { quality: 85 }

    # Legacy aliases kept for compatibility with older callers.
    # TODO: Remove :thumbnail/:display once older iOS builds that still rely on the
    # legacy cover_image_url API contract are no longer supported.
    attachable.variant :thumbnail, resize_to_limit: [ 320, 320 ], format: :webp, saver: { quality: 78 }
    attachable.variant :display, resize_to_limit: [ 1800, 1350 ], format: :webp, saver: { quality: 85 }
  end
  has_one_attached :import_image, dependent: :purge_later

  # Scopes
  scope :favorited, -> { where(favorite: true) }

  # Validations
  validates :name, presence: true
  validate :acceptable_cover_image
  validate :acceptable_import_image

  before_validation :ensure_array_fields

  def self.ransackable_attributes(_auth_object = nil)
    %w[id name source_url]
  end

  def cover_image_variant_url(variant)
    return nil unless cover_image.attached?

    Rails.application.routes.url_helpers.rails_blob_path(
      cover_image.variant(variant),
      only_path: true
    )
  end

  def cover_image_urls
    return nil unless cover_image.attached?

    COVER_IMAGE_VARIANTS.transform_values do |variant|
      cover_image_variant_url(variant)
    end
  end

  private

  def ensure_array_fields
    self.instructions ||= []
  end

  def acceptable_cover_image
    return unless cover_image.attached?

    if cover_image.blob.byte_size > 15.megabytes
      errors.add(:cover_image, "is too big (max 15MB)")
    end

    unless cover_image.blob.content_type&.start_with?("image/")
      errors.add(:cover_image, "must be an image")
    end
  end

  def acceptable_import_image
    return unless import_image.attached?

    if import_image.blob.byte_size > 15.megabytes
      errors.add(:import_image, "is too big (max 15MB)")
    end

    unless import_image.blob.content_type&.start_with?("image/")
      errors.add(:import_image, "must be an image")
    end
  end
end
