require "test_helper"

module RecipeImporters
  class YoutubeVideoExtractorTest < ActiveSupport::TestCase
    # ===================
    # URL MATCHING
    # ===================

    test "supports standard watch URL" do
      assert YoutubeVideoExtractor.supports_url?("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
    end

    test "supports shorts URL" do
      assert YoutubeVideoExtractor.supports_url?("https://www.youtube.com/shorts/dQw4w9WgXcQ")
    end

    test "supports youtu.be short link" do
      assert YoutubeVideoExtractor.supports_url?("https://youtu.be/dQw4w9WgXcQ")
    end

    test "supports embed URL" do
      assert YoutubeVideoExtractor.supports_url?("https://www.youtube.com/embed/dQw4w9WgXcQ")
    end

    test "supports live URL" do
      assert YoutubeVideoExtractor.supports_url?("https://www.youtube.com/live/dQw4w9WgXcQ")
    end

    test "supports mobile YouTube URL" do
      assert YoutubeVideoExtractor.supports_url?("https://m.youtube.com/watch?v=dQw4w9WgXcQ")
    end

    test "supports watch URL with extra params" do
      assert YoutubeVideoExtractor.supports_url?("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=120")
    end

    test "rejects non-YouTube URL" do
      assert_not YoutubeVideoExtractor.supports_url?("https://vimeo.com/12345")
    end

    test "rejects YouTube channel URL" do
      assert_not YoutubeVideoExtractor.supports_url?("https://www.youtube.com/@username")
    end

    test "rejects YouTube homepage" do
      assert_not YoutubeVideoExtractor.supports_url?("https://www.youtube.com/")
    end

    test "rejects invalid URI" do
      assert_not YoutubeVideoExtractor.supports_url?("not a url at all ://")
    end

    # ===================
    # VIDEO ID EXTRACTION
    # ===================

    test "extracts video ID from watch URL" do
      assert_equal "dQw4w9WgXcQ", YoutubeVideoExtractor.video_id_from_url("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
    end

    test "extracts video ID from shorts URL" do
      assert_equal "abc123XYZ_-", YoutubeVideoExtractor.video_id_from_url("https://www.youtube.com/shorts/abc123XYZ_-")
    end

    test "extracts video ID from youtu.be" do
      assert_equal "dQw4w9WgXcQ", YoutubeVideoExtractor.video_id_from_url("https://youtu.be/dQw4w9WgXcQ")
    end

    test "returns nil for unsupported URL" do
      assert_nil YoutubeVideoExtractor.video_id_from_url("https://example.com/page")
    end

    # ===================
    # SUCCESSFUL EXTRACTION
    # ===================

    test "extracts recipe from YouTube video description" do
      stub_youtube_video_api("abc123", description: "My Pasta Recipe\n\nIngredients:\n- 500g pasta\n- 2 cloves garlic")
      stub_youtube_comments_api("abc123")
      stub_llm_response(name: "My Pasta Recipe", ingredients: [ "500g pasta", "2 cloves garlic" ], instructions: [ "Boil pasta" ])

      result = build_extractor("https://www.youtube.com/watch?v=abc123").extract

      assert result.success?
      assert_equal "My Pasta Recipe", result.recipe_attributes[:name]
      assert_equal [ "500g pasta", "2 cloves garlic" ], result.recipe_attributes[:ingredients].map { |i| i[:raw] }
    end

    test "returns thumbnail URL as cover image" do
      stub_youtube_video_api("abc123", description: "A recipe", thumbnail_url: "https://i.ytimg.com/vi/abc123/maxresdefault.jpg")
      stub_youtube_comments_api("abc123")
      stub_llm_response(name: "Recipe", ingredients: [ "1 thing" ], instructions: [ "Do it" ])

      result = build_extractor("https://www.youtube.com/watch?v=abc123").extract

      assert result.success?
      assert_equal "https://i.ytimg.com/vi/abc123/maxresdefault.jpg", result.cover_image_url
    end

    # ===================
    # PINNED AUTHOR COMMENT
    # ===================

    test "appends author comment to description" do
      stub_youtube_video_api("abc123", description: "Check out this recipe!", channel_id: "UC_chef")
      stub_youtube_comments_api("abc123", comments: [
        build_comment(author_channel_id: "UC_chef", text: "Ingredients:\n- 2 cups flour\n- 1 egg"),
        build_comment(author_channel_id: "UC_other", text: "Looks delicious!")
      ])
      stub_llm_response(name: "Recipe", ingredients: [ "2 cups flour", "1 egg" ], instructions: [ "Mix" ])

      result = build_extractor("https://www.youtube.com/watch?v=abc123").extract

      assert result.success?
    end

    test "ignores comments not by the video author" do
      stub_youtube_video_api("abc123", description: "My recipe video", channel_id: "UC_chef")
      stub_youtube_comments_api("abc123", comments: [
        build_comment(author_channel_id: "UC_viewer", text: "Great recipe!"),
        build_comment(author_channel_id: "UC_other", text: "Love it")
      ])
      stub_llm_response(name: "Recipe", ingredients: [ "1 thing" ], instructions: [ "Do it" ])

      result = build_extractor("https://www.youtube.com/watch?v=abc123").extract

      assert result.success?
    end

    test "succeeds when comments API fails" do
      stub_youtube_video_api("abc123", description: "Recipe in description")
      stub_youtube_comments_api_raw("abc123", status: 403)
      stub_llm_response(name: "Recipe", ingredients: [ "1 thing" ], instructions: [ "Do it" ])

      result = build_extractor("https://www.youtube.com/watch?v=abc123").extract

      assert result.success?
    end

    test "succeeds when comments are disabled" do
      stub_youtube_video_api("abc123", description: "Recipe in description")
      stub_youtube_comments_api("abc123", comments: [])
      stub_llm_response(name: "Recipe", ingredients: [ "1 thing" ], instructions: [ "Do it" ])

      result = build_extractor("https://www.youtube.com/watch?v=abc123").extract

      assert result.success?
    end

    test "extracts from author comment even when description is empty for shorts" do
      stub_youtube_video_api("abc123", description: "", channel_id: "UC_chef")
      stub_youtube_comments_api("abc123", comments: [
        build_comment(author_channel_id: "UC_chef", text: "Recipe:\n- 1 cup rice\n- Cook for 20 min")
      ])
      stub_llm_response(name: "Rice", ingredients: [ "1 cup rice" ], instructions: [ "Cook for 20 min" ])

      result = build_extractor("https://www.youtube.com/shorts/abc123").extract

      assert result.success?
    end

    # ===================
    # ERROR HANDLING
    # ===================

    test "returns failure when API key is missing" do
      result = build_extractor("https://www.youtube.com/watch?v=abc123", api_key: nil).extract

      assert_not result.success?
      assert_equal :youtube_missing_api_key, result.error_code
    end

    test "returns failure when video is not found" do
      stub_youtube_video_api_raw("abc123", body: { "items" => [] })

      result = build_extractor("https://www.youtube.com/watch?v=abc123").extract

      assert_not result.success?
      assert_equal :youtube_video_not_found, result.error_code
    end

    test "returns failure when description and comment are both empty" do
      stub_youtube_video_api("abc123", description: "", channel_id: "UC_chef")
      stub_youtube_comments_api("abc123", comments: [])

      result = build_extractor("https://www.youtube.com/watch?v=abc123").extract

      assert_not result.success?
      assert_equal :youtube_no_description, result.error_code
    end

    test "returns failure on API HTTP error" do
      stub_youtube_video_api_raw("abc123", status: 403)

      result = build_extractor("https://www.youtube.com/watch?v=abc123").extract

      assert_not result.success?
      assert_equal :youtube_fetch_failed, result.error_code
    end

    test "returns failure on timeout" do
      stub_youtube_video_api_timeout("abc123")

      result = build_extractor("https://www.youtube.com/watch?v=abc123").extract

      assert_not result.success?
      assert_includes [ :youtube_timeout, :youtube_connection_failed ], result.error_code
    end

    private

    API_KEY = "test-youtube-api-key"
    DEFAULT_CHANNEL_ID = "UC_default_channel"

    def build_extractor(url, api_key: API_KEY)
      extractor = YoutubeVideoExtractor.new(url)
      extractor.define_singleton_method(:youtube_api_key) { api_key }
      extractor
    end

    def stub_youtube_video_api(video_id, description: "A recipe", thumbnail_url: "https://i.ytimg.com/vi/test/hqdefault.jpg", channel_id: DEFAULT_CHANNEL_ID)
      body = {
        "items" => [ {
          "snippet" => {
            "description" => description,
            "channelId" => channel_id,
            "thumbnails" => { "high" => { "url" => thumbnail_url } }
          }
        } ]
      }
      stub_youtube_video_api_raw(video_id, body: body)
    end

    def stub_youtube_video_api_raw(video_id, body: {}, status: 200)
      stub_request(:get, "https://www.googleapis.com/youtube/v3/videos")
        .with(query: { "part" => "snippet", "id" => video_id, "key" => API_KEY })
        .to_return(status: status, body: body.to_json, headers: { "Content-Type" => "application/json" })
    end

    def stub_youtube_video_api_timeout(video_id)
      stub_request(:get, "https://www.googleapis.com/youtube/v3/videos")
        .with(query: { "part" => "snippet", "id" => video_id, "key" => API_KEY })
        .to_timeout
    end

    def stub_youtube_comments_api(video_id, comments: [])
      body = { "items" => comments }
      stub_youtube_comments_api_raw(video_id, body: body)
    end

    def stub_youtube_comments_api_raw(video_id, body: {}, status: 200)
      stub_request(:get, "https://www.googleapis.com/youtube/v3/commentThreads")
        .with(query: { "part" => "snippet", "videoId" => video_id, "order" => "relevance", "textFormat" => "plainText", "maxResults" => "5", "key" => API_KEY })
        .to_return(status: status, body: body.to_json, headers: { "Content-Type" => "application/json" })
    end

    def build_comment(author_channel_id:, text:)
      {
        "snippet" => {
          "topLevelComment" => {
            "snippet" => {
              "textOriginal" => text,
              "authorChannelId" => { "value" => author_channel_id }
            }
          }
        }
      }
    end
  end
end
