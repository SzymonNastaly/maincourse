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

  test "keeps a valid client hint and drops an invalid one" do
    hinted = create_item(name: "Hafermilch", category: "beverages")
    assert_equal "beverages", hinted.category

    invalid = create_item(name: "Something", category: "spaceship", canonical_name: "Not Valid!")
    assert_nil invalid.category
    assert_nil invalid.canonical_name
  end

  test "renaming clears confirmed enrichment and queues again" do
    item = create_item(name: "Milk")
    item.update!(category: "dairy_eggs", canonical_name: "milk", enrichment_version: @version)

    assert_enqueued_with(job: EnrichShoppingListItemsJob) do
      item.update!(name: "Dish soap")
    end
    assert_nil item.enrichment_version
    assert_not_equal "dairy_eggs", item.category
  end

  test "checking an item does not recategorize it" do
    item = create_item(name: "Milk")
    item.update!(category: "dairy_eggs", canonical_name: "milk", enrichment_version: @version)

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
