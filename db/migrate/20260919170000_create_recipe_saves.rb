class CreateRecipeSaves < ActiveRecord::Migration[8.1]
  def change
    add_column :recipes, :starter_recipe_key, :string

    create_table :recipe_saves do |t|
      t.references :user, null: false, foreign_key: { on_delete: :cascade }
      t.string :request_id, null: false
      t.integer :original_destination_cookbook_id, null: false
      t.string :source_type, null: false
      t.string :source_key, null: false
      t.references :saved_recipe, foreign_key: { to_table: :recipes, on_delete: :nullify }

      t.timestamps
    end

    add_index :recipe_saves, [ :user_id, :request_id ], unique: true
    add_index :recipe_saves, :original_destination_cookbook_id
  end
end
