require "json"
require "faraday"
require "faraday/follow_redirects"

module RecipeImporters
  # Extracts recipe data from TikTok videos via oEmbed and photo posts via Apify.
  class TiktokVideoExtractor
    Result = RecipeImporter::Result
    PostTarget = Data.define(:kind, :url, :oembed_item)

    OEMBED_ENDPOINT = "https://www.tiktok.com/oembed"
    APIFY_ENDPOINT = "https://api.apify.com/v2/acts/scraptik~tiktok-api/run-sync-get-dataset-items"
    REDIRECT_LIMIT = 5
    SHORTLINK_HOSTS = %w[vm.tiktok.com vt.tiktok.com].freeze
    USER_AGENT = "Mozilla/5.0 (compatible; Hauptgang TikTok Importer)"

    class FetchFailed < StandardError; end
    class InvalidResponse < StandardError; end

    def self.supports_url?(url)
      post_type_for_url(url).present?
    end

    def self.post_type_for_url(url)
      uri = URI.parse(url.to_s)
      host = uri.host.to_s.downcase
      path = uri.path.to_s
      return nil unless tiktok_host?(host)

      if SHORTLINK_HOSTS.include?(host)
        return path.present? && path != "/" ? :shortlink : nil
      end

      return :video if path.include?("/video/")
      return :photo if path.include?("/photo/")

      nil
    rescue URI::InvalidURIError
      nil
    end

    def self.tiktok_host?(host)
      host == "tiktok.com" || host.end_with?(".tiktok.com")
    end

    def initialize(url, http_client: nil, redirect_client: nil)
      @url = url
      @http_client = http_client || build_http_client
      @redirect_client = redirect_client || build_redirect_client
    end

    def extract
      return failure("Please enter a URL", :blank_url) if @url.blank?

      post_target = resolve_post_target
      return extract_video_post(post_target) if post_target.kind == :video
      return extract_photo_post(post_target) if post_target.kind == :photo

      failure("Could not fetch TikTok data", :tiktok_fetch_failed)
    rescue InvalidResponse
      failure("Invalid TikTok response", :tiktok_invalid_response)
    rescue FetchFailed
      failure("Could not fetch TikTok data", :tiktok_fetch_failed)
    rescue Faraday::TimeoutError
      failure("TikTok import timed out", :tiktok_timeout)
    rescue Faraday::ConnectionFailed
      failure("Could not connect to TikTok importer", :tiktok_connection_failed)
    rescue StandardError => error
      Rails.logger.error "[TikTokVideoExtractor] Unexpected error for #{sanitized_url}: #{error.class} - #{error.message}"
      failure("TikTok import failed", :tiktok_fetch_failed)
    end

    private

    def resolve_post_target
      case self.class.post_type_for_url(sanitized_url)
      when :video
        PostTarget.new(kind: :video, url: sanitized_url, oembed_item: nil)
      when :photo
        PostTarget.new(kind: :photo, url: sanitized_url, oembed_item: nil)
      when :shortlink
        item, direct_error = try_fetch_oembed_item(sanitized_url)
        return PostTarget.new(kind: :video, url: sanitized_url, oembed_item: item) if item

        resolved_url = resolved_short_url
        raise(direct_error || FetchFailed) if resolved_url.blank?

        case self.class.post_type_for_url(resolved_url)
        when :video
          PostTarget.new(kind: :video, url: resolved_url, oembed_item: nil)
        when :photo
          PostTarget.new(kind: :photo, url: resolved_url, oembed_item: nil)
        else
          raise(direct_error || FetchFailed)
        end
      else
        raise FetchFailed
      end
    end

    def extract_video_post(post_target)
      item = post_target.oembed_item || fetch_oembed_item(post_target.url)
      caption = item["title"].to_s.strip
      image_url = item["thumbnail_url"].to_s.strip.presence
      convert_llm_result(caption, image_url)
    end

    def extract_photo_post(post_target)
      token = apify_token
      return failure("TikTok import is not configured", :apify_missing_token) if token.blank?

      aweme_id = photo_aweme_id(post_target.url)
      raise InvalidResponse, "Missing photo aweme_id" if aweme_id.blank?

      metadata = fetch_photo_metadata(token, aweme_id)
      convert_llm_result(metadata[:caption], metadata[:image_url])
    end

    def convert_llm_result(caption, image_url)
      return failure("TikTok caption missing", :tiktok_no_caption) if caption.blank?

      service_result = RecipeLlmService.new(caption, prompt_type: :raw_text, source_url: @url).extract
      return Result.new(
        success?: false,
        recipe_attributes: {},
        cover_image_url: image_url,
        error: service_result.error,
        error_code: service_result.error_code
      ) unless service_result.success?

      Result.new(
        success?: true,
        recipe_attributes: service_result.recipe_attributes,
        cover_image_url: image_url,
        error: nil,
        error_code: nil
      )
    end

    def fetch_oembed_item(candidate_url)
      item, error = try_fetch_oembed_item(candidate_url)
      return item if item

      raise(error || FetchFailed)
    end

    def fetch_photo_metadata(token, aweme_id)
      response = @http_client.post(APIFY_ENDPOINT) do |req|
        req.params["token"] = token
        req.headers["Content-Type"] = "application/json"
        req.body = { post_awemeId: aweme_id }.to_json
      end

      unless response.success?
        Rails.logger.info "[TikTokVideoExtractor] Apify HTTP #{response.status} for #{sanitized_url}"
        raise FetchFailed, "HTTP #{response.status}"
      end

      items = JSON.parse(response.body)
      unless items.is_a?(Array) && items.any?
        Rails.logger.info "[TikTokVideoExtractor] Empty Apify response for #{sanitized_url}"
        raise InvalidResponse, "Empty Apify response"
      end

      aweme_detail = items.first["aweme_detail"]
      unless aweme_detail.is_a?(Hash)
        Rails.logger.info "[TikTokVideoExtractor] Missing aweme_detail in Apify response for #{sanitized_url}"
        raise InvalidResponse, "Missing aweme_detail"
      end

      {
        caption: aweme_detail["desc"].to_s.strip,
        image_url: photo_thumbnail_url(aweme_detail)
      }
    rescue JSON::ParserError => error
      Rails.logger.info "[TikTokVideoExtractor] Invalid Apify JSON for #{sanitized_url}: #{error.message}"
      raise InvalidResponse, error.message
    end

    def photo_thumbnail_url(aweme_detail)
      aweme_detail.dig("image_post_info", "images", 0, "thumbnail", "url_list", 0).to_s.strip.presence
    end

    def photo_aweme_id(url)
      URI.parse(url).path.to_s[%r{/photo/(\d+)}, 1]
    rescue URI::InvalidURIError
      nil
    end

    def try_fetch_oembed_item(candidate_url)
      response = @http_client.get(OEMBED_ENDPOINT) do |req|
        req.params["url"] = candidate_url
      end

      unless response.success?
        status = response.status
        error = FetchFailed.new("HTTP #{status}")
        Rails.logger.info "[TikTokVideoExtractor] HTTP #{status} for #{candidate_url}"
        return [ nil, error ]
      end

      item = JSON.parse(response.body)
      unless item.is_a?(Hash)
        error = InvalidResponse.new("Unexpected response shape")
        Rails.logger.info "[TikTokVideoExtractor] Unexpected response shape for #{candidate_url}"
        return [ nil, error ]
      end

      [ item, nil ]
    rescue JSON::ParserError => error
      invalid_response = InvalidResponse.new(error.message)
      Rails.logger.info "[TikTokVideoExtractor] Invalid JSON for #{candidate_url}: #{error.message}"
      [ nil, invalid_response ]
    end

    def resolved_short_url
      uri = URI.parse(sanitized_url)
      return nil unless SHORTLINK_HOSTS.include?(uri.host.to_s.downcase)

      response = @redirect_client.get(sanitized_url)
      resolved_url = response.env.url&.to_s
      return nil if resolved_url.blank? || resolved_url == sanitized_url

      validation = UrlValidator.new(resolved_url).validate
      unless validation.success? && self.class.post_type_for_url(resolved_url).present?
        Rails.logger.info "[TikTokVideoExtractor] Ignoring redirect target #{resolved_url}: #{validation.error || 'unsupported path'}"
        return nil
      end

      URI.parse(resolved_url).tap { |resolved_uri| resolved_uri.query = nil; resolved_uri.fragment = nil }.to_s
    rescue Faraday::Error, URI::InvalidURIError => error
      Rails.logger.info "[TikTokVideoExtractor] Could not resolve short URL #{sanitized_url}: #{error.class}"
      nil
    end

    def build_http_client
      Faraday.new do |conn|
        options = conn.options
        # Apify run-sync endpoints can take a long time to start and complete
        options.timeout = 90
        options.open_timeout = 10
        conn.headers["User-Agent"] = USER_AGENT
      end
    end

    def build_redirect_client
      Faraday.new do |conn|
        options = conn.options
        options.timeout = 10
        options.open_timeout = 5
        conn.headers["User-Agent"] = USER_AGENT
        conn.response :follow_redirects, limit: REDIRECT_LIMIT, callback: UrlValidator::REJECT_UNSAFE_REDIRECT
      end
    end

    def apify_token
      ENV["APIFY_API_KEY"] || Rails.application.credentials.dig(:apify, :api_key)
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
