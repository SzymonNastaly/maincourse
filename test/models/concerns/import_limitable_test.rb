require "test_helper"

class ImportLimitableTest < ActiveSupport::TestCase
  setup do
    @user = users(:one)
    @cookbook = cookbooks(:one_personal)
  end

  test "monthly_import_count counts recipes created this month" do
    assert_equal @user.recipes.where(created_at: Time.current.beginning_of_month..).where.not(import_status: :failed).count,
      @user.monthly_import_count
  end

  test "monthly_import_count excludes failed imports" do
    @cookbook.recipes.create!(name: "Failed", import_status: :failed, user: @user)
    count_before = @user.monthly_import_count
    @cookbook.recipes.create!(name: "Success", import_status: :completed, user: @user)
    assert_equal count_before + 1, @user.monthly_import_count
  end

  test "monthly_import_count excludes starter recipes" do
    count_before = @user.monthly_import_count
    @cookbook.recipes.create!(
      name: "Starter",
      import_status: :completed,
      starter_recipe_key: "tomato-orzo-v1",
      user: @user
    )

    assert_equal count_before, @user.monthly_import_count
    assert_equal User::FREE_MONTHLY_IMPORT_LIMIT - count_before, @user.remaining_imports
  end

  test "import_limit_reached? returns false when under limit" do
    assert_not @user.import_limit_reached?
  end

  test "import_limit_reached? returns true when at limit" do
    create_recipes_up_to_limit(@user)
    assert @user.import_limit_reached?
  end

  test "import_limit_reached? returns false for pro users regardless of count" do
    @user.update!(pro: true)
    create_recipes_up_to_limit(@user)
    assert_not @user.import_limit_reached?
  end

  test "remaining_imports returns correct count" do
    assert_equal User::FREE_MONTHLY_IMPORT_LIMIT - @user.monthly_import_count,
      @user.remaining_imports
  end

  test "remaining_imports returns infinity for pro users" do
    @user.update!(pro: true)
    assert_equal Float::INFINITY, @user.remaining_imports
  end

  test "remaining_imports does not go below zero" do
    create_recipes_up_to_limit(@user)
    @cookbook.recipes.create!(name: "Extra", import_status: :completed, user: @user)
    assert_equal 0, @user.remaining_imports
  end

  private

  def create_recipes_up_to_limit(user)
    needed = User::FREE_MONTHLY_IMPORT_LIMIT - user.monthly_import_count
    needed.times { |i| @cookbook.recipes.create!(name: "Recipe #{i}", import_status: :completed, user: user) }
  end
end
