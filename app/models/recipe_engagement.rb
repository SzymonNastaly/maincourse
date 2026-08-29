# One row per (user, recipe). Per-user rather than columns on Recipe because recipes
# live in shared cookbooks, where two members have different histories with the same
# recipe. Rows are created lazily on first view, list add, or suggestion.
class RecipeEngagement < ApplicationRecord
  belongs_to :user
  belongs_to :recipe

  def self.record_view!(user:, recipe:, viewed_at:)
    upsert_for(user_id: user.id, recipe_id: recipe.id) do |engagement|
      engagement.last_viewed_at = [ engagement.last_viewed_at, viewed_at ].compact.max
      engagement.view_count += 1
    end
  end

  # A shared cookbook is treated as a household that cooks together, so one member
  # shopping or checking off credits everyone who is a member at that moment.
  def self.mark_added_to_list!(recipe:, at: Time.current)
    fan_out(recipe) { |engagement| engagement.added_to_list_at ||= at }
  end

  def self.mark_cooked!(recipe:, at: Time.current)
    fan_out(recipe) { |engagement| engagement.cooked_at = at }
  end

  def self.mark_suggested!(user:, recipe:, at: Time.current)
    upsert_for(user_id: user.id, recipe_id: recipe.id) do |engagement|
      engagement.last_suggested_at = at
      engagement.suggested_count += 1
    end
  end

  def self.fan_out(recipe)
    recipe.cookbook.cookbook_memberships.pluck(:user_id).each do |user_id|
      upsert_for(user_id: user_id, recipe_id: recipe.id) { |engagement| yield engagement }
    end
    nil
  end

  # find_or_initialize races against the unique index; one retry resolves it.
  def self.upsert_for(user_id:, recipe_id:)
    attempts = 0
    begin
      engagement = find_or_initialize_by(user_id: user_id, recipe_id: recipe_id)
      yield engagement
      engagement.save!
      engagement
    rescue ActiveRecord::RecordNotUnique
      attempts += 1
      retry if attempts < 2
      raise
    end
  end
end
