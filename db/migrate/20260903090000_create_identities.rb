class CreateIdentities < ActiveRecord::Migration[8.1]
  # SQLite cannot alter a column in place, so change_column_null rebuilds the
  # users table: copy it, DROP the original, rename. Rails turns foreign keys
  # off around that rebuild, but PRAGMA foreign_keys is a no-op inside a
  # transaction and db:migrate wraps migrations in one -- so the DROP fires
  # every ON DELETE CASCADE / SET NULL pointing at users. Without this
  # directive the migration empties cookbook_memberships and nulls
  # recipes.user_id. Enforced by Maincourse/MigrationTableRebuild.
  disable_ddl_transaction!

  def change
    create_table :identities do |t|
      t.references :user, null: false, foreign_key: true
      t.string :provider, null: false
      t.string :uid, null: false
      t.string :email, null: false
      t.text :apple_refresh_tokens

      t.timestamps
    end

    add_index :identities, [ :provider, :uid ], unique: true
    change_column_null :users, :password_digest, true
  end
end
