class AddProviderToDeviceTokens < ActiveRecord::Migration[8.1]
  def change
    add_column :device_tokens, :provider, :string, null: false, default: "apns"

    remove_index :device_tokens, :token
    add_index :device_tokens, [ :provider, :token ], unique: true
  end
end
