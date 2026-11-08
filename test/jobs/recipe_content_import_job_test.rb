require "test_helper"

class RecipeContentImportJobTest < ActiveSupport::TestCase
  setup do
    @user = users(:one)
    @recipe = recipes(:one)
    @recipe.update!(import_status: :pending, name: "Placeholder")
  end

  test "skips cover image download when cover_image_url points to private host" do
    result = success_result(cover_image_url: "http://127.0.0.1/cover.jpg")
    job = RecipeContentImportJob.new

    job.stub(:extract_from_content, result) do
      job.perform(@user.id, @recipe.id, "https://example.com/recipe", [ "{\"@type\":\"Recipe\"}" ], "<body>Recipe</body>", {})
    end

    assert_not_requested(:get, "http://127.0.0.1/cover.jpg")

    @recipe.reload
    assert_equal :completed, @recipe.import_status.to_sym
    assert_equal "Imported Recipe", @recipe.name
    assert_not @recipe.cover_image.attached?
  end

  test "skips cover image that redirects to a private host" do
    redirecting_url = "https://example.com/cover.jpg"
    internal_url = "http://169.254.169.254/latest/meta-data/"
    result = success_result(cover_image_url: redirecting_url)
    job = RecipeContentImportJob.new

    stub_request(:get, redirecting_url)
      .to_return(status: 302, headers: { "Location" => internal_url })
    internal = stub_request(:get, internal_url)
      .to_return(status: 200, body: "fake-image-bytes", headers: { "Content-Type" => "image/jpeg" })

    job.stub(:extract_from_content, result) do
      job.perform(@user.id, @recipe.id, "https://example.com/recipe", [ "{\"@type\":\"Recipe\"}" ], "<body>Recipe</body>", {})
    end

    assert_not_requested internal
    assert_not @recipe.reload.cover_image.attached?
  end

  test "downloads and attaches cover image for public URL" do
    public_image_url = "https://example.com/cover.jpg"
    result = success_result(cover_image_url: public_image_url)
    job = RecipeContentImportJob.new

    stub_request(:get, public_image_url)
      .to_return(status: 200, body: "fake-image-bytes", headers: { "Content-Type" => "image/jpeg" })

    job.stub(:extract_from_content, result) do
      job.perform(@user.id, @recipe.id, "https://example.com/recipe", [ "{\"@type\":\"Recipe\"}" ], "<body>Recipe</body>", {})
    end

    assert_requested(:get, public_image_url, times: 1)

    @recipe.reload
    assert_equal :completed, @recipe.import_status.to_sym
    assert @recipe.cover_image.attached?
  end

  test "uses og:image as fallback when extractor has no cover image URL" do
    og_image_url = "https://example.com/og-cover.jpg"
    result = success_result(cover_image_url: nil)
    job = RecipeContentImportJob.new

    stub_request(:get, og_image_url)
      .to_return(status: 200, body: "fake-image-bytes", headers: { "Content-Type" => "image/jpeg" })

    job.stub(:extract_from_content, result) do
      job.perform(
        @user.id,
        @recipe.id,
        "https://example.com/recipe",
        [ "{\"@type\":\"Recipe\"}" ],
        "<body>Recipe</body>",
        { "og:image" => og_image_url }
      )
    end

    assert_requested(:get, og_image_url, times: 1)

    @recipe.reload
    assert_equal :completed, @recipe.import_status.to_sym
    assert @recipe.cover_image.attached?
  end

  test "uses DOM cover image candidates when extractor and meta image are missing" do
    dom_image_url = "https://example.com/dom-cover.jpg"
    result = success_result(cover_image_url: nil)
    job = RecipeContentImportJob.new

    stub_request(:get, dom_image_url)
      .to_return(status: 200, body: "fake-image-bytes", headers: { "Content-Type" => "image/jpeg" })

    job.stub(:extract_from_content, result) do
      job.perform(
        @user.id,
        @recipe.id,
        "https://example.com/recipe",
        [ "{\"@type\":\"Recipe\"}" ],
        "<body>Recipe</body>",
        {},
        [ dom_image_url ]
      )
    end

    assert_requested(:get, dom_image_url, times: 1)

    @recipe.reload
    assert_equal :completed, @recipe.import_status.to_sym
    assert @recipe.cover_image.attached?
  end

  test "tries next DOM cover image candidate when first one fails" do
    first_candidate = "http://127.0.0.1/private.jpg"
    second_candidate = "https://example.com/dom-cover.jpg"
    result = success_result(cover_image_url: nil)
    job = RecipeContentImportJob.new

    stub_request(:get, second_candidate)
      .to_return(status: 200, body: "fake-image-bytes", headers: { "Content-Type" => "image/jpeg" })

    job.stub(:extract_from_content, result) do
      job.perform(
        @user.id,
        @recipe.id,
        "https://example.com/recipe",
        [ "{\"@type\":\"Recipe\"}" ],
        "<body>Recipe</body>",
        {},
        [ first_candidate, second_candidate ]
      )
    end

    assert_not_requested(:get, first_candidate)
    assert_requested(:get, second_candidate, times: 1)

    @recipe.reload
    assert_equal :completed, @recipe.import_status.to_sym
    assert @recipe.cover_image.attached?
  end

  test "uses tiktok extractor for tiktok source URL and attaches returned cover image" do
    tiktok_url = "https://www.tiktok.com/@creator/video/1234567890"
    cover_image_url = "https://example.com/tiktok-cover.jpg"
    result = success_result(cover_image_url: cover_image_url)
    extractor = Minitest::Mock.new
    extractor.expect(:extract, result)

    stub_request(:get, cover_image_url)
      .to_return(status: 200, body: "fake-image-bytes", headers: { "Content-Type" => "image/jpeg" })

    RecipeImporters::TiktokVideoExtractor.stub(:supports_url?, true) do
      RecipeImporters::TiktokVideoExtractor.stub(:new, extractor) do
        RecipeContentImportJob.new.perform(
          @user.id,
          @recipe.id,
          tiktok_url,
          [],
          "<body>TikTok page</body>",
          {}
        )
      end
    end

    extractor.verify
    assert_requested(:get, cover_image_url, times: 1)

    @recipe.reload
    assert_equal :completed, @recipe.import_status.to_sym
    assert_equal "Imported Recipe", @recipe.name
    assert @recipe.cover_image.attached?
  end

  test "non-tiktok URLs still use JSON-LD extraction flow" do
    source_url = "https://example.com/recipe"
    json_ld_strings = [ "{\"@type\":\"Recipe\"}" ]
    result = success_result(cover_image_url: nil)
    json_ld_extractor = Minitest::Mock.new
    json_ld_extractor.expect(:extract_from_json_ld_strings, result, [ json_ld_strings ])
    job = RecipeContentImportJob.new

    RecipeImporters::TiktokVideoExtractor.stub(:supports_url?, false) do
      RecipeImporters::JsonLdExtractor.stub(:new, json_ld_extractor) do
        extracted = job.send(:extract_from_content, source_url, json_ld_strings, "<body>Recipe</body>")

        assert_equal result, extracted
      end
    end

    json_ld_extractor.verify
  end

  test "marks recipe as failed when extraction fails" do
    result = RecipeImporter::Result.new(
      success?: false,
      recipe_attributes: {},
      cover_image_url: nil,
      error: "No recipe found",
      error_code: :no_recipe_found
    )
    job = RecipeContentImportJob.new

    job.stub(:extract_from_content, result) do
      job.perform(@user.id, @recipe.id, "https://example.com/recipe", [ "{\"@type\":\"Recipe\"}" ], "<body>Recipe</body>", {})
    end

    @recipe.reload
    assert_equal :failed, @recipe.import_status.to_sym
    assert_equal "Import from example.com failed.", @recipe.error_message
  end

  private

  def success_result(cover_image_url:)
    RecipeImporter::Result.new(
      success?: true,
      recipe_attributes: { name: "Imported Recipe", ingredients: [ "1 cup flour" ], instructions: [ "Mix" ] },
      cover_image_url: cover_image_url,
      error: nil,
      error_code: nil
    )
  end
end
