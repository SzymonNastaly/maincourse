require "test_helper"

# The web import mirrors the API: a pending placeholder created under a lock,
# then a background job. Nothing is extracted inside the request.
class RecipeImportsTest < ActionDispatch::IntegrationTest
  setup do
    @user = users(:one)
    @cookbook = cookbooks(:one_personal)
    sign_in_as @user
  end

  test "importing a link creates a pending placeholder and enqueues the job" do
    url = "https://smittenkitchen.com/2026/miso-butter-roast-chicken"

    assert_enqueued_with(job: RecipeImportJob) do
      assert_difference "Recipe.count", 1 do
        post import_recipes_path, params: { url: url }
      end
    end

    placeholder = Recipe.order(:created_at).last
    assert placeholder.pending?
    assert_equal url, placeholder.source_url
    assert_equal @cookbook, placeholder.cookbook
    assert_redirected_to recipes_path
  end

  test "importing without a link does nothing" do
    assert_no_difference "Recipe.count" do
      post import_recipes_path, params: { url: "  " }
    end

    assert_equal "Paste a link first.", flash[:alert]
  end

  test "importing rejects a URL the SSRF guard refuses" do
    assert_no_difference "Recipe.count" do
      post import_recipes_path, params: { url: "http://127.0.0.1/secrets" }
    end

    assert flash[:alert].present?
    assert_redirected_to recipes_path
  end

  test "importing a photo attaches it and enqueues the image job" do
    assert_enqueued_with(job: RecipeImageExtractJob) do
      assert_difference "Recipe.count", 1 do
        post import_photo_recipes_path, params: { image: uploaded_image }
      end
    end

    placeholder = Recipe.order(:created_at).last
    assert placeholder.pending?
    assert placeholder.import_image.attached?
  end

  test "importing a photo rejects a non-image" do
    file = Rack::Test::UploadedFile.new(
      StringIO.new("not an image"), "text/plain", original_filename: "notes.txt"
    )

    assert_no_difference "Recipe.count" do
      post import_photo_recipes_path, params: { image: file }
    end

    assert_equal "That file is not an image.", flash[:alert]
  end

  test "a free user at the monthly limit is sent to the Pro page" do
    User::FREE_MONTHLY_IMPORT_LIMIT.times do |index|
      @cookbook.recipes.create!(name: "Imported #{index}", user: @user, import_status: :completed)
    end

    assert_no_enqueued_jobs only: RecipeImportJob do
      assert_no_difference "Recipe.count" do
        post import_recipes_path, params: { url: "https://example.com/recipe" }
      end
    end

    assert_redirected_to pro_path
    assert_match "free limit", flash[:alert]
  end

  test "a pro user is not limited" do
    @user.update!(pro: true)
    User::FREE_MONTHLY_IMPORT_LIMIT.times do |index|
      @cookbook.recipes.create!(name: "Imported #{index}", user: @user, import_status: :completed)
    end

    assert_difference "Recipe.count", 1 do
      post import_recipes_path, params: { url: "https://example.com/recipe" }
    end

    assert_redirected_to recipes_path
  end

  private

  def uploaded_image
    Rack::Test::UploadedFile.new(
      Rails.root.join("app/assets/images/icon.png"), "image/png"
    )
  end
end
