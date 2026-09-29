class ShoppingListItemSerializer
  def initialize(item)
    @item = item
  end

  def as_json(*)
    {
      id: @item.id,
      client_id: @item.client_id,
      name: @item.name,
      details: @item.details,
      checked_at: @item.checked_at,
      source_recipe_id: @item.source_recipe_id,
      category: @item.category,
      canonical_name: @item.canonical_name,
      category_pending: @item.needs_enrichment?,
      created_at: @item.created_at,
      updated_at: @item.updated_at
    }
  end
end
