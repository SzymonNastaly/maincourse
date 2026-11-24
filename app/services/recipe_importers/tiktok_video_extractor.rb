require "json"
require "faraday"
require "faraday/follow_redirects"

module RecipeImporters
  # Extracts recipe data from TikTok video and photo posts via oEmbed.
  class TiktokVideoExtractor
    Result = RecipeImporter::Result

    OEMBED_ENDPOINT = "https://www.tiktok.com/oembed"
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

      return :post if path.include?("/video/") || path.include?("/photo/")

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

      item = fetch_post_oembed_item
      convert_llm_result(item["title"].to_s.strip, item["thumbnail_url"].to_s.strip.presence)
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

    def fetch_post_oembed_item
      case self.class.post_type_for_url(sanitized_url)
      when :post
        fetch_oembed_item(sanitized_url)
      when :shortlink
        item, direct_error = try_fetch_oembed_item(sanitized_url)
        return item if item

        resolved_url = resolved_short_url
        raise(direct_error || FetchFailed) if resolved_url.blank?

        fetch_oembed_item(resolved_url)
      else
        raise FetchFailed
      end
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
      unless validation.success? && self.class.post_type_for_url(resolved_url) == :post
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
        options.timeout = 10
        options.open_timeout = 5
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
