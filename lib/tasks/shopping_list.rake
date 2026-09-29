namespace :shopping_list do
  desc "Enqueue category enrichment for unchecked shopping list items on an old or incomplete contract"
  task enqueue_enrichment: :environment do
    scope = ShoppingListItem.unchecked.where(
      "enrichment_version IS NULL OR enrichment_version != ? OR canonical_name IS NULL OR category IS NULL",
      Llm::IngredientInstructions::VERSION
    )

    batches = 0
    scope.in_batches(of: 50) do |batch|
      EnrichShoppingListItemsJob.perform_later(batch.ids)
      batches += 1
    end

    puts "Enqueued shopping list enrichment in #{batches} batches."
  end
end
