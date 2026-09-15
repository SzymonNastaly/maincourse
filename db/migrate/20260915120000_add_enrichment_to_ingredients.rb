class AddEnrichmentToIngredients < ActiveRecord::Migration[8.1]
  def change
    add_column :ingredients, :canonical_name, :string
    add_column :ingredients, :canonical_unit, :string
    add_column :ingredients, :category, :string
    add_column :ingredients, :enrichment_version, :integer
    add_index :ingredients, :enrichment_version
  end
end
