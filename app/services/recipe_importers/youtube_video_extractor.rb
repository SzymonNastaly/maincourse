require "json"
require "faraday"

module RecipeImporters
  # Extracts recipe data from YouTube videos using the YouTube Data API v3.
  class YoutubeVideoExtractor
    Result = RecipeImporter::Result

    VIDEOS_ENDPOINT = "https://www.googleapis.com/youtube/v3/videos"
    COMMENT_THREADS_ENDPOINT = "https://www.googleapis.com/youtube/v3/commentThreads"
    USER_AGENT = "Mozilla/5.0 (compatible; Hauptgang YouTube Importer)"
    MAX_COMMENT_RESULTS = 5

    YOUTUBE_HOSTS = %w[youtube.com www.youtube.com m.youtube.com youtu.be].freeze

    def self.supports_url?(url)
      video_id_from_url(url).present?
    end

    def self.video_id_from_url(url)
      uri = URI.parse(url.to_s)
      host = uri.host.to_s.downcase
      return nil unless YOUTUBE_HOSTS.include?(host)

      if host == "youtu.be"
        uri.path.to_s.delete_prefix("/").presence
      else
        path = uri.path.to_s
        if path.start_with?("/shorts/", "/embed/", "/live/")
          path.split("/")[2].presence
        elsif path == "/watch"
          params = URI.decode_www_form(uri.query.to_s).to_h
          params["v"].presence
        end
      end
    rescue URI::InvalidURIError
      nil
    end

    def initialize(url, http_client: nil)
      @url = url
      @http_client = http_client || build_http_client
    end

    def extract
      return failure("Please enter a URL", :blank_url) if @url.blank?

      api_key = youtube_api_key
      return failure("YouTube import is not configured", :youtube_missing_api_key) if api_key.blank?

      video_id = self.class.video_id_from_url(@url)
      return failure("Could not extract YouTube video ID", :youtube_fetch_failed) if video_id.blank?

      response = @http_client.get(VIDEOS_ENDPOINT) do |req|
        req.params["part"] = "snippet"
        req.params["id"] = video_id
        req.params["key"] = api_key
      end

      unless response.success?
        Rails.logger.info "[YoutubeVideoExtractor] HTTP #{response.status} for #{sanitized_url}"
        return failure("Could not fetch YouTube data", :youtube_fetch_failed)
      end

      data = JSON.parse(response.body)
      items = data["items"]

      unless items.is_a?(Array) && items.any?
        return failure("YouTube video not found", :youtube_video_not_found)
      end

      snippet = items.first["snippet"] || {}
      description = snippet["description"].to_s.strip
      channel_id = snippet["channelId"].to_s
      thumbnail_url = best_thumbnail(snippet["thumbnails"])

      author_comment = fetch_author_comment(video_id, channel_id, api_key)
      combined_text = build_combined_text(description, author_comment)

      return failure("YouTube description is empty", :youtube_no_description) if combined_text.blank?

      service_result = RecipeLlmService.new(combined_text, prompt_type: :raw_text, source_url: @url).extract
      return Result.new(
        success?: false,
        recipe_attributes: {},
        cover_image_url: thumbnail_url,
        error: service_result.error,
        error_code: service_result.error_code
      ) unless service_result.success?

      Result.new(
        success?: true,
        recipe_attributes: service_result.recipe_attributes,
        cover_image_url: thumbnail_url,
        error: nil,
        error_code: nil
      )
    rescue JSON::ParserError
      failure("Invalid YouTube response", :youtube_invalid_response)
    rescue Faraday::TimeoutError
      failure("YouTube import timed out", :youtube_timeout)
    rescue Faraday::ConnectionFailed
      failure("Could not connect to YouTube", :youtube_connection_failed)
    rescue StandardError => error
      Rails.logger.error "[YoutubeVideoExtractor] Unexpected error for #{sanitized_url}: #{error.class} - #{error.message}"
      failure("YouTube import failed", :youtube_fetch_failed)
    end

    private

    def fetch_author_comment(video_id, channel_id, api_key)
      return nil if channel_id.blank?

      response = @http_client.get(COMMENT_THREADS_ENDPOINT) do |req|
        req.params["part"] = "snippet"
        req.params["videoId"] = video_id
        req.params["order"] = "relevance"
        req.params["textFormat"] = "plainText"
        req.params["maxResults"] = MAX_COMMENT_RESULTS
        req.params["key"] = api_key
      end

      return nil unless response.success?

      threads = JSON.parse(response.body)["items"]
      return nil unless threads.is_a?(Array)

      threads.each do |thread|
        comment_snippet = thread.dig("snippet", "topLevelComment", "snippet") || {}
        author_channel = comment_snippet.dig("authorChannelId", "value").to_s
        next unless author_channel == channel_id

        return comment_snippet["textOriginal"].to_s.strip.presence
      end

      nil
    rescue Faraday::Error, JSON::ParserError => error
      Rails.logger.info "[YoutubeVideoExtractor] Could not fetch comments for #{sanitized_url}: #{error.class}"
      nil
    end

    def build_combined_text(description, author_comment)
      sections = []
      sections << description if description.present?
      sections << "Comment by the video author:\n#{author_comment}" if author_comment.present?
      sections.join("\n\n---\n").presence
    end

    def best_thumbnail(thumbnails)
      return nil unless thumbnails.is_a?(Hash)

      %w[maxres high medium default].each do |quality|
        url = thumbnails.dig(quality, "url").to_s.strip
        return url if url.present?
      end

      nil
    end

    def build_http_client
      Faraday.new do |conn|
        conn.options.timeout = 10
        conn.options.open_timeout = 5
        conn.headers["User-Agent"] = USER_AGENT
      end
    end

    def youtube_api_key
      Rails.application.credentials.dig(:youtube, :api_key)
    end

    def sanitized_url
      URI.parse(@url).tap { |uri| uri.query = nil; uri.fragment = nil }.to_s
    rescue URI::InvalidURIError
      "[invalid URL]"
    end

    def failure(message, code)
      Result.new(success?: false, recipe_attributes: {}, cover_image_url: nil, error: message, error_code: code)
    end
  end
end
