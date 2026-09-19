module ImportLimitable
  extend ActiveSupport::Concern

  FREE_MONTHLY_IMPORT_LIMIT = 15

  def monthly_import_count
    recipes
      .where(created_at: Time.current.beginning_of_month..)
      .where.not(import_status: :failed)
      .where(starter_recipe_key: nil)
      .count
  end

  def import_limit_reached?
    return false if pro?

    monthly_import_count >= FREE_MONTHLY_IMPORT_LIMIT
  end

  def remaining_imports
    return Float::INFINITY if pro?

    [ FREE_MONTHLY_IMPORT_LIMIT - monthly_import_count, 0 ].max
  end
end
