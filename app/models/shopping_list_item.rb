class ShoppingListItem < ApplicationRecord
  belongs_to :cookbook
  belongs_to :user, optional: true
  belongs_to :source_recipe, class_name: "Recipe", optional: true

  validates :name, presence: true
  validates :client_id, presence: true, uniqueness: { scope: :cookbook_id }

  # A shared shopping list updates for everyone looking at it.
  broadcasts_refreshes_to :cookbook

  scope :unchecked, -> { where(checked_at: nil) }
  scope :checked, -> { where.not(checked_at: nil) }
  scope :stale_checked, -> { where("checked_at < ?", 1.hour.ago) }

  def self.cleanup_stale_checked_for(cookbook)
    cookbook.shopping_list_items.checked.stale_checked.destroy_all
  end

  def self.ransackable_attributes(_auth_object = nil)
    %w[id name details client_id]
  end
end
