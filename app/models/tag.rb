class Tag < ApplicationRecord
  # Associations
  has_many :recipe_tags, dependent: :destroy
  has_many :recipes, through: :recipe_tags

  # Validations
  validates :name, presence: true, uniqueness: true
  validates :slug, presence: true, uniqueness: true, format: { with: /\A[a-z0-9\-]+\z/, message: "only lowercase letters, numbers, and hyphens" }

  # Callbacks - auto-generate slug from name if not provided
  before_validation :generate_slug, if: -> { slug.blank? && name.present? }

  # Tags are global, so "Collections" only lists the ones that actually have a
  # recipe in the cookbook you are looking at. Each row carries `recipes_count`.
  def self.for_cookbook(cookbook)
    return none if cookbook.blank?

    joins(recipe_tags: :recipe)
      .where(recipes: { cookbook_id: cookbook.id })
      .where.not(recipes: { import_status: :failed })
      .group("tags.id")
      .select("tags.*, COUNT(recipes.id) AS recipes_count")
      .order("tags.name")
  end

  def self.ransackable_attributes(_auth_object = nil)
    %w[id name slug]
  end

  private

  def generate_slug
    self.slug = name.downcase.strip.gsub(/\s+/, "-").gsub(/[^a-z0-9\-]/, "")
  end
end
