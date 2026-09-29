class Cookbook < ApplicationRecord
  has_many :cookbook_memberships, dependent: :destroy
  has_many :users, through: :cookbook_memberships
  # Meal plans (and entries) must be destroyed before recipes so Recipe#destroy is not
  # blocked by restrict_with_error on meal_plan_entries.
  has_many :meal_plans, dependent: :destroy
  has_many :recipes, dependent: :destroy
  has_many :shopping_list_items, dependent: :destroy
  has_many :cookbook_invitations, dependent: :destroy
  has_many :notification_deliveries, dependent: :nullify

  scope :personal, -> { where(personal: true) }
  scope :shared, -> { where(personal: false) }

  validates :name, presence: true

  # A rename (for example through the admin) makes the name user content.
  before_update -> { self.default_name = false }, if: -> { name_changed? && !default_name_changed? }

  # Stored for every personal cookbook and still sent as `name` to older apps.
  DEFAULT_NAME = "My Recipes"

  # Name to show people. A cookbook flagged `default_name` is presented in the
  # viewer's language; stored names are user content and are never translated or
  # compared against the English default.
  def display_name
    default_name? ? I18n.t("cookbooks.default_name") : name
  end

  def owner
    cookbook_memberships.find_by(role: :owner)&.user
  end

  def owner?(user)
    cookbook_memberships.exists?(user: user, role: :owner)
  end

  def self.ransackable_attributes(_auth_object = nil)
    %w[id name]
  end
end
