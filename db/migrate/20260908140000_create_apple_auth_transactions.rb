class CreateAppleAuthTransactions < ActiveRecord::Migration[8.1]
  def change
    create_table :apple_auth_transactions do |t|
      t.string :handle_digest, null: false
      t.string :code_challenge, null: false
      t.string :return_uri, null: false
      t.references :user, foreign_key: { on_delete: :cascade }
      t.string :exchange_digest
      t.datetime :expires_at, null: false
      t.datetime :authorized_at
      t.datetime :consumed_at
      t.datetime :confirmation_required_at

      t.timestamps
    end

    add_index :apple_auth_transactions, :handle_digest, unique: true
    add_index :apple_auth_transactions, :exchange_digest, unique: true
    add_index :apple_auth_transactions, :expires_at
  end
end
