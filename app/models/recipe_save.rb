class RecipeSave < ApplicationRecord
  belongs_to :user
  belongs_to :saved_recipe, class_name: "Recipe", optional: true, inverse_of: :recipe_saves

  validates :request_id, :source_type, :source_key, presence: true
  validates :request_id, uniqueness: { scope: :user_id }
end
