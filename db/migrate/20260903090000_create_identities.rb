class CreateIdentities < ActiveRecord::Migration[8.1]
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
