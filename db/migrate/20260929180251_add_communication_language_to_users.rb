class AddCommunicationLanguageToUsers < ActiveRecord::Migration[8.1]
  def change
    add_column :users, :communication_language, :string, null: false, default: "en"
  end
end
