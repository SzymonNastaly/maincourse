class CreateRecipeEngagements < ActiveRecord::Migration[8.1]
  def change
    create_table :recipe_engagements do |t|
      t.references :user, null: false, foreign_key: true
      t.references :recipe, null: false, foreign_key: true
      t.datetime :last_viewed_at
      t.integer :view_count, null: false, default: 0
      t.datetime :added_to_list_at
      t.datetime :cooked_at
      t.datetime :last_suggested_at
      t.integer :suggested_count, null: false, default: 0
      t.timestamps
    end

    add_index :recipe_engagements, [ :user_id, :recipe_id ], unique: true
    add_index :recipe_engagements, [ :user_id, :last_viewed_at ]
  end
end
