class AddInstructionTimersToRecipes < ActiveRecord::Migration[8.1]
  def change
    add_column :recipes, :instruction_timers, :json, default: [], null: false
    add_column :recipes, :timers_version, :integer
  end
end
