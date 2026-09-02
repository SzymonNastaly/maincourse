require "test_helper"

class RecipesHelperTest < ActionView::TestCase
  include RecipesHelper

  # --- format_quantity -----------------------------------------------------

  test "formats an amount with a unit" do
    assert_equal "200 g", format_quantity(build_ingredient(amount: 200, unit: "g"))
  end

  test "formats a fractional amount as a glyph" do
    assert_equal "½ tsp", format_quantity(build_ingredient(amount: 0.5, unit: "tsp"))
  end

  test "formats a range" do
    assert_equal "2–3", format_quantity(build_ingredient(amount: 2, amount_max: 3))
    assert_equal "200–250 g", format_quantity(build_ingredient(amount: 200, amount_max: 250, unit: "g"))
  end

  test "formats a unit with no amount" do
    assert_equal "pinch", format_quantity(build_ingredient(unit: "pinch"))
  end

  test "formats nothing when there is nothing" do
    assert_equal "", format_quantity(build_ingredient)
  end

  test "drops trailing zeros" do
    assert_equal "1.5", format_amount(BigDecimal("1.50"))
    assert_equal "2", format_amount(BigDecimal("2.00"))
  end

  # --- format_duration -----------------------------------------------------

  test "formats durations the way the cards do" do
    assert_equal "35m", format_duration(35)
    assert_equal "1h 25m", format_duration(85)
    assert_equal "4h", format_duration(240)
    assert_equal "", format_duration(0)
    assert_equal "", format_duration(nil)
  end

  # --- recipe_meta ---------------------------------------------------------

  test "recipe meta combines total time and servings" do
    recipe = Recipe.new(prep_time: 20, cook_time: 65, servings: 4)
    assert_equal "1h 25m · serves 4", recipe_meta(recipe)
  end

  test "recipe meta omits missing parts" do
    assert_equal "serves 2", recipe_meta(Recipe.new(servings: 2))
    assert_equal "20m", recipe_meta(Recipe.new(prep_time: 20))
    assert_equal "", recipe_meta(Recipe.new)
  end

  # --- source links --------------------------------------------------------

  test "links an http source and shows the bare host" do
    recipe = Recipe.new(source_url: "https://www.smittenkitchen.com/2026/chicken")

    assert_equal "https://www.smittenkitchen.com/2026/chicken", safe_source_url(recipe)
    assert_equal "smittenkitchen.com", source_domain(recipe)
  end

  test "refuses to link a non-http scheme" do
    [ "javascript:alert(1)", "data:text/html,<script>", "file:///etc/passwd", "not a url at all" ].each do |value|
      recipe = Recipe.new(source_url: value)

      assert_nil safe_source_url(recipe), "#{value} should not be linkable"
      assert_nil source_domain(recipe)
    end
  end

  test "handles a missing source" do
    assert_nil safe_source_url(Recipe.new)
    assert_nil source_domain(Recipe.new)
  end

  # --- placeholder art -----------------------------------------------------

  test "placeholder gradient is stable per recipe" do
    recipe = recipes(:one)

    assert_equal recipe_placeholder_gradient(recipe), recipe_placeholder_gradient(recipe)
    assert_includes RecipesHelper::PLACEHOLDER_GRADIENTS, recipe_placeholder_gradient(recipe)
  end

  private

  def build_ingredient(amount: nil, amount_max: nil, unit: nil)
    Ingredient.new(amount: amount, amount_max: amount_max, unit: unit, raw: "x", position: 0)
  end
end
