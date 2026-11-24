require "faraday"
require "nokogiri"

module RecipeImporters
  # Extracts recipe data from Instagram reels via the caption in the public page's
  # og:description: the one the client read on the device when given, otherwise one we
  # fetch ourselves. When Instagram serves a login page instead of the post there is no
  # caption, and the import fails.
  class InstagramReelExtractor
    Result = RecipeImporter::Result

    USER_AGENT = "Mozilla/5.0 (compatible; Hauptgang Instagram Importer)"

    def self.supports_url?(url)
      uri = URI.parse(url.to_s)
      host = uri.host.to_s.downcase
      return false unless host == "instagram.com" || host.end_with?(".instagram.com")

      path = uri.path.to_s
      path.include?("/reel/") || path.include?("/reels/") || path.include?("/p/")
    rescue URI::InvalidURIError
      false
    end

    def initialize(url, page_description: nil)
      @url = url
      @page_description = page_description.to_s.strip.presence
    end

    def extract
      return failure("Please enter a URL", :blank_url) if @url.blank?

      caption, image_url = @page_description ? [ @page_description, nil ] : fetch_page_caption
      return failure("Instagram caption missing", :instagram_no_caption) if caption.blank?

      service_result = RecipeLlmService.new(caption, prompt_type: :raw_text, source_url: @url).extract
      Result.new(
        success?: service_result.success?,
        recipe_attributes: service_result.success? ? service_result.recipe_attributes : {},
        cover_image_url: image_url,
        error: service_result.error,
        error_code: service_result.error_code
      )
    rescue StandardError => error
      Rails.logger.error "[InstagramReelExtractor] Unexpected error for #{sanitized_url}: #{error.class} - #{error.message}"
      failure("Instagram import failed", :instagram_failed)
    end

    private

    def fetch_page_caption
      response = page_client.get(sanitized_url)
      unless response.status == 200
        Rails.logger.info "[InstagramReelExtractor] Page fetch HTTP #{response.status} for #{sanitized_url}"
        return [ nil, nil ]
      end

      doc = Nokogiri::HTML(response.body)
      [ "og:description", "og:image" ].map { |property| doc.at_css(%(meta[property="#{property}"]))&.[]("content").to_s.strip.presence }
    rescue Faraday::Error => error
      Rails.logger.info "[InstagramReelExtractor] Page fetch failed for #{sanitized_url}: #{error.class}"
      [ nil, nil ]
    end

    def page_client
      Faraday.new do |conn|
        conn.options.timeout = 10
        conn.options.open_timeout = 5
        conn.headers["User-Agent"] = USER_AGENT
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
