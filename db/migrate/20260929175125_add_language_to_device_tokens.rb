class AddLanguageToDeviceTokens < ActiveRecord::Migration[8.1]
  def change
    add_column :device_tokens, :language, :string
  end
end
