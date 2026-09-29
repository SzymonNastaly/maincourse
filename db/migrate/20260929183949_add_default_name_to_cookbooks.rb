class AddDefaultNameToCookbooks < ActiveRecord::Migration[8.1]
  # The down migration rebuilds cookbooks on SQLite; allow Rails to disable FKs.
  disable_ddl_transaction!

  def up
    add_column :cookbooks, :default_name, :boolean, null: false, default: false
    # Every personal cookbook was created with this literal and none can be renamed,
    # so this one-time backfill is exact. Presentation never compares names.
    execute "UPDATE cookbooks SET default_name = 1 WHERE personal = 1 AND name = 'My Recipes'"
  end

  def down
    remove_column :cookbooks, :default_name
  end
end
