class AddImportErrorCodeToRecipes < ActiveRecord::Migration[8.1]
  # The down migration rebuilds recipes on SQLite; allow Rails to disable FKs.
  disable_ddl_transaction!

  def up
    add_column :recipes, :import_error_code, :string
    # One-time compatibility conversion; live clients never classify English prose.
    execute <<~SQL
      UPDATE recipes
      SET import_error_code = CASE
        WHEN error_message = 'No recipe found in that photo.' THEN 'no_recipe_in_photo'
        ELSE 'import_failed'
      END
      WHERE import_status = 2
    SQL
  end

  def down
    remove_column :recipes, :import_error_code
  end
end
