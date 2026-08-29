class CreateNotificationDeliveries < ActiveRecord::Migration[8.1]
  def change
    create_table :notification_deliveries do |t|
      t.references :user, null: false, foreign_key: true
      t.string :campaign, null: false
      t.references :recipe, foreign_key: true
      t.references :cookbook, foreign_key: true
      t.datetime :sent_at, null: false
      t.datetime :opened_at
      t.string :action_taken
      t.timestamps
    end

    add_index :notification_deliveries, [ :user_id, :sent_at ]
  end
end
