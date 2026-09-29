require "test_helper"
require "rake"

Rake::Task.define_task(:environment) unless Rake::Task.task_defined?(:environment)
load Rails.root.join("lib/tasks/shopping_list.rake")

class ShoppingListRakeTest < ActiveSupport::TestCase
  include ActiveJob::TestHelper

  setup do
    Rake::Task["shopping_list:enqueue_enrichment"].reenable
  end

  test "queues only unchecked items needing enrichment" do
    ShoppingListItem.update_all(category: "other", canonical_name: "thing", enrichment_version: Llm::IngredientInstructions::VERSION)
    stale = shopping_list_items(:unchecked_milk)
    stale.update_columns(enrichment_version: nil)
    shopping_list_items(:checked_eggs).update_columns(enrichment_version: nil)

    assert_enqueued_with(job: EnrichShoppingListItemsJob, args: [ [ stale.id ] ]) do
      assert_output(/1 batches/) { Rake::Task["shopping_list:enqueue_enrichment"].invoke }
    end
    assert_enqueued_jobs 1, only: EnrichShoppingListItemsJob
  end
end
