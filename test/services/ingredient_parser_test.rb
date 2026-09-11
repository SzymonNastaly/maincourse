require "test_helper"

class IngredientParserTest < ActiveSupport::TestCase
  test "returns empty array for empty input" do
    assert_equal [], IngredientParser.call([])
  end

  test "returns empty array for blank-only input" do
    assert_equal [], IngredientParser.call([ "", "  ", nil ])
  end

  test "parses ingredients aligning by raw" do
    stub_ingredient_parse_response([
      { "raw" => "2 cups flour", "name" => "flour", "amount" => 2, "unit" => "cup" },
      { "raw" => "1 tsp salt", "name" => "salt", "amount" => 1, "unit" => "tsp" }
    ])

    result = IngredientParser.call([ "2 cups flour", "1 tsp salt" ])

    assert_equal 2, result.length
    assert_equal "flour", result[0][:name]
    assert_equal 2, result[0][:amount]
    assert_equal "cup", result[0][:unit]
    assert_equal "2 cups flour", result[0][:raw]
    assert_equal "salt", result[1][:name]
  end

  test "preserves input order even when LLM reorders" do
    stub_ingredient_parse_response([
      { "raw" => "1 tsp salt", "name" => "salt" },
      { "raw" => "2 cups flour", "name" => "flour" }
    ])

    result = IngredientParser.call([ "2 cups flour", "1 tsp salt" ])

    assert_equal [ "2 cups flour", "1 tsp salt" ], result.map { |h| h[:raw] }
    assert_equal [ "flour", "salt" ], result.map { |h| h[:name] }
  end

  test "routes the configured model exclusively through EU Vertex" do
    stub_ingredient_parse_response([ { "raw" => "salt", "name" => "salt" } ])

    IngredientParser.call([ "salt" ])

    assert_requested(:post, LlmStubHelper::OPENROUTER_ENDPOINT) do |req|
      body = JSON.parse(req.body)
      body["model"] == "google/gemini-3.5-flash-lite" &&
        body.dig("provider", "only") == [ "google-vertex/eu" ]
    end
  end

  test "falls back to raw=name on missing entries" do
    stub_ingredient_parse_response([
      { "raw" => "2 cups flour", "name" => "flour", "amount" => 2 }
    ])

    result = IngredientParser.call([ "2 cups flour", "1 tsp salt" ])

    assert_equal "flour", result[0][:name]
    assert_equal "1 tsp salt", result[1][:raw]
    assert_equal "1 tsp salt", result[1][:name]
    assert_nil result[1][:amount]
  end

  test "falls back on LLM timeout" do
    stub_request(:post, LlmStubHelper::OPENROUTER_ENDPOINT)
      .to_raise(Faraday::TimeoutError.new("timeout"))

    result = IngredientParser.call([ "1 cup flour" ])

    assert_equal 1, result.length
    assert_equal "1 cup flour", result[0][:name]
    assert_equal "1 cup flour", result[0][:raw]
  end

  test "falls back on API error" do
    stub_request(:post, LlmStubHelper::OPENROUTER_ENDPOINT)
      .to_return(status: 500, body: "{}")

    result = IngredientParser.call([ "salt" ])

    assert_equal [ { name: "salt", raw: "salt" } ], result
  end

  private

  def stub_ingredient_parse_response(parsed_ingredients)
    content = { "ingredients" => parsed_ingredients }
    stub_openrouter_api(response_body: build_openrouter_response(content))
  end
end
