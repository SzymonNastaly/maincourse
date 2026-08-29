class AddLifecycleFieldsToUsers < ActiveRecord::Migration[8.1]
  def change
    add_column :users, :time_zone, :string, null: false, default: "UTC"
    add_column :users, :last_active_at, :datetime
    add_column :users, :lifecycle_notifications_enabled, :boolean, null: false, default: true
  end
end
