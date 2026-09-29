require "test_helper"

class ShoppingListItemCategorizationTest < ActiveSupport::TestCase
  include ActiveJob::TestHelper

  setup do
    @cookbook = cookbooks(:one_personal)
    @recipe = recipes(:one)
    @version = Llm::IngredientInstructions::VERSION
  end

  test "copies complete enrichment from the source recipe's matching ingredient" do
    ingredients(:one_eggs).update!(name: "Eier", canonical_name: "egg", category: "dairy_eggs", enrichment_version: @version)

    assert_no_enqueued_jobs only: EnrichShoppingListItemsJob do
      item = create_item(name: "eier", details: "3", source_recipe: @recipe)
      assert_equal "dairy_eggs", item.category
      assert_equal "egg", item.canonical_name
      assert_not item.needs_enrichment?
    end
  end

  test "falls back to a provisional lookup and queues confirmation" do
    ingredients(:two_chicken).update!(name: "Milch", canonical_name: "milk", category: "dairy_eggs", enrichment_version: @version)

    item = nil
    assert_enqueued_with(job: EnrichShoppingListItemsJob) do
      item = create_item(name: "Milch")
    end
    assert_equal "dairy_eggs", item.category
    assert_equal "milk", item.canonical_name
    assert_nil item.enrichment_version
    assert item.needs_enrichment?
  end

  test "keeps a valid client hint and ignores an invalid one" do
    hinted = create_item(name: "Hafermilch", category_hint: " Beverages ", canonical_name_hint: "Oat Milk")
    assert_equal "beverages", hinted.category
    assert_equal "oat milk", hinted.canonical_name

    invalid = create_item(name: "Something", category_hint: "spaceship", canonical_name_hint: "Not Valid!")
    assert_nil invalid.category
    assert_nil invalid.canonical_name
  end

  test "a rename honours a hint equal to the stored category" do
    item = create_item(name: "Milk", category_hint: "dairy_eggs")

    item.update!(name: "Buttermilk", category_hint: "dairy_eggs", canonical_name_hint: "buttermilk")

    assert_equal "dairy_eggs", item.category
    assert_equal "buttermilk", item.canonical_name
  end

  test "editing details keeps confirmed enrichment" do
    item = create_item(name: "Sriracha", details: "1")
    item.update_columns(category: "oils_spices_condiments", canonical_name: "sriracha", enrichment_version: @version)

    assert_no_enqueued_jobs only: EnrichShoppingListItemsJob do
      item.update!(details: "2 bottles", category_hint: "other")
    end
    assert_equal [ "oils_spices_condiments", "sriracha", @version ],
      item.reload.values_at(:category, :canonical_name, :enrichment_version)
  end

  test "an item saved twice in one transaction is still enriched" do
    assert_enqueued_jobs 1, only: EnrichShoppingListItemsJob do
      ShoppingListItem.transaction do
        item = create_item(name: "Dragon fruit")
        item.update!(details: "2")
      end
    end
  end

  test "batching collects every item into one job" do
    items = []
    assert_enqueued_jobs 1, only: EnrichShoppingListItemsJob do
      ShoppingListItem.batching_enrichment do
        ShoppingListItem.transaction { items = [ create_item(name: "Dragon fruit"), create_item(name: "Yuzu") ] }
      end
    end
    assert_enqueued_with(job: EnrichShoppingListItemsJob, args: [ items.map(&:id) ])
  end

  test "a failed enqueue does not fail the saved write" do
    failing = ->(_ids) { raise ActiveJob::EnqueueError, "queue database is locked" }

    item = EnrichShoppingListItemsJob.stub(:perform_later, failing) { create_item(name: "Yuzu") }

    assert item.persisted?
  end

  test "unchecking an unconfirmed item asks for enrichment again" do
    item = create_item(name: "Yuzu", checked_at: Time.current)

    assert_enqueued_with(job: EnrichShoppingListItemsJob, args: [ [ item.id ] ]) do
      item.update!(checked_at: nil)
    end
  end

  test "renaming clears confirmed enrichment and queues again" do
    item = create_item(name: "Milk")
    item.update_columns(category: "dairy_eggs", canonical_name: "milk", enrichment_version: @version)

    assert_enqueued_with(job: EnrichShoppingListItemsJob) do
      item.update!(name: "Dish soap")
    end
    assert_nil item.enrichment_version
    assert_not_equal "dairy_eggs", item.category
  end

  test "checking an item does not recategorize it" do
    item = create_item(name: "Milk")
    item.update_columns(category: "dairy_eggs", canonical_name: "milk", enrichment_version: @version)

    assert_no_enqueued_jobs only: EnrichShoppingListItemsJob do
      item.update!(checked_at: Time.current)
    end
    assert_equal "dairy_eggs", item.reload.category
    assert_equal @version, item.enrichment_version
  end

  private

  def create_item(**attributes)
    ShoppingListItem.create!(cookbook: @cookbook, user: users(:one), client_id: SecureRandom.uuid, **attributes)
  end
end
