require "test_helper"

class EnrichShoppingListItemsJobTest < ActiveSupport::TestCase
  include ActiveJob::TestHelper

  setup do
    @version = Llm::IngredientInstructions::VERSION
    @item = ShoppingListItem.create!(cookbook: cookbooks(:one_personal), user: users(:one),
      client_id: "oil", name: "Olivenöl", details: "2 EL")
  end

  test "applies parser results without touching the typed input" do
    updated_at = @item.updated_at
    stub_parser("2 EL Olivenöl" => hit("olive oil", "oils_spices_condiments")) do
      EnrichShoppingListItemsJob.perform_now([ @item.id ])
    end

    @item.reload
    assert_equal updated_at, @item.updated_at
    assert_equal "Olivenöl", @item.name
    assert_equal "2 EL", @item.details
    assert_equal "oils_spices_condiments", @item.category
    assert_equal "olive oil", @item.canonical_name
    assert_not @item.needs_enrichment?
  end

  test "skips rows edited while the parser was running" do
    parser = lambda do |_inputs|
      @item.update!(name: "Rapsöl")
      [ hit("olive oil", "oils_spices_condiments").merge(raw: "2 EL Olivenöl") ]
    end

    IngredientParser.stub(:call, parser) { EnrichShoppingListItemsJob.perform_now([ @item.id ]) }

    assert_equal "Rapsöl", @item.reload.name
    assert_nil @item.enrichment_version
  end

  test "sends collapsed whitespace so the echoed line still matches" do
    @item.update_columns(name: "Oliven\nöl", details: "2  EL")

    stub_parser("2 EL Oliven öl" => hit("olive oil", "oils_spices_condiments")) do
      EnrichShoppingListItemsJob.perform_now([ @item.id ])
    end

    assert_equal "olive oil", @item.reload.canonical_name
  end

  test "keeps a specific provisional category when the parser only says other" do
    @item.update_columns(category: "oils_spices_condiments")

    stub_parser("2 EL Olivenöl" => hit("olive oil", "other")) do
      EnrichShoppingListItemsJob.perform_now([ @item.id ])
    end

    assert_equal "oils_spices_condiments", @item.reload.category
    assert_not @item.needs_enrichment?
  end

  test "skips rows deleted while the parser was running" do
    parser = lambda do |_inputs|
      @item.destroy!
      [ hit("olive oil", "oils_spices_condiments").merge(raw: "2 EL Olivenöl") ]
    end

    assert_nothing_raised do
      IngredientParser.stub(:call, parser) { EnrichShoppingListItemsJob.perform_now([ @item.id ]) }
    end
  end

  test "parser fallback retries without losing the provisional category" do
    @item.update_columns(category: "pantry")

    stub_parser({}) do
      assert_enqueued_with(job: EnrichShoppingListItemsJob) do
        EnrichShoppingListItemsJob.perform_now([ @item.id ])
      end
    end
    assert_equal "pantry", @item.reload.category
  end

  test "does nothing for already enriched rows" do
    @item.update_columns(category: "oils_spices_condiments", canonical_name: "olive oil", enrichment_version: @version)

    IngredientParser.stub(:call, ->(_) { flunk "parser should not run" }) do
      assert_nothing_raised { EnrichShoppingListItemsJob.perform_now([ @item.id ]) }
    end
  end

  private

  def hit(canonical_name, category)
    { canonical_name:, category:, enrichment_version: @version }
  end

  # Maps parser input to a hit; unmapped inputs get the parser's fallback shape.
  def stub_parser(hits, &block)
    parser = ->(inputs) { inputs.map { |raw| (hits[raw] || { name: raw }).merge(raw: raw) } }
    IngredientParser.stub(:call, parser, &block)
  end
end
