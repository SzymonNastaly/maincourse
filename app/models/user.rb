class User < ApplicationRecord
  include ImportLimitable

  has_secure_password
  has_many :sessions, dependent: :destroy
  has_many :api_tokens, dependent: :destroy
  has_many :device_tokens, dependent: :destroy
  has_many :cookbook_memberships, dependent: :delete_all
  has_many :cookbooks, through: :cookbook_memberships
  has_many :recipes, dependent: :nullify
  has_many :shopping_list_items, dependent: :nullify
  has_many :recipe_engagements, dependent: :delete_all

  normalizes :email_address, with: ->(email) { email.strip.downcase }
  normalizes :name, with: ->(name) { name.to_s.strip }

  validates :email_address, presence: true, uniqueness: true
  validates :name, length: { maximum: 50 }

  after_create :create_personal_cookbook!
  before_destroy :handle_owned_cookbooks!, prepend: true

  def personal_cookbook
    cookbooks.personal.first
  end

  def shared_cookbook
    cookbooks.shared.first
  end

  ACTIVITY_THROTTLE = 1.hour

  # Records that the user did something in the app. Throttled so an active session
  # does not write on every request. Returns true when it actually wrote.
  def touch_last_active!
    return false if last_active_at.present? && last_active_at > ACTIVITY_THROTTLE.ago

    update_column(:last_active_at, Time.current)
    true
  end

  def self.ransackable_attributes(_auth_object = nil)
    %w[id email_address]
  end

  private

  def create_personal_cookbook!
    cookbook = Cookbook.create!(name: "My Recipes", personal: true)
    cookbook_memberships.create!(cookbook: cookbook, role: :owner)
  end

  def handle_owned_cookbooks!
    # Must run before dependent callbacks. For each owned cookbook:
    # - if other members exist, transfer ownership to the oldest remaining collaborator
    #   so their data survives (only personal cookbooks and solo shared cookbooks die).
    # - otherwise destroy the cookbook (cascading to recipes, shopping list, meal plans).
    cookbook_memberships.where(role: :owner).includes(:cookbook).find_each do |membership|
      cookbook = membership.cookbook
      successor = cookbook.cookbook_memberships
                          .where.not(user_id: id)
                          .order(:created_at)
                          .first

      if successor && !cookbook.personal?
        membership.destroy!
        successor.update!(role: :owner)
      else
        cookbook.destroy!
      end
    end
  end
end
