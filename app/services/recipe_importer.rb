require "faraday"
require "faraday/follow_redirects"

# Imports a recipe from a URL by extracting structured data
# Tries JSON-LD first, then falls back to LLM extraction
class RecipeImporter
  Result = Data.define(:success?, :recipe_attributes, :cover_image_url, :error, :error_code)

  MAX_REDIRECTS = 5
  USER_AGENT = "Mozilla/5.0 (compatible; Hauptgang Recipe Importer)"

  def initialize(url, http_client: nil)
    @url = url
    @http_client = http_client || build_http_client
  end

  def import
    return failure("Please enter a URL", :blank_url) if @url.blank?

    validation = RecipeImporters::UrlValidator.new(@url).validate
    return failure(validation.error, :invalid_url) unless validation.success?

    if RecipeImporters::InstagramReelExtractor.supports_url?(@url)
      result = RecipeImporters::InstagramReelExtractor.new(@url).extract
      return result
    end

    if RecipeImporters::TiktokVideoExtractor.supports_url?(@url)
      result = RecipeImporters::TiktokVideoExtractor.new(@url).extract
      return result
    end

    if RecipeImporters::YoutubeVideoExtractor.supports_url?(@url)
      return RecipeImporters::YoutubeVideoExtractor.new(@url).extract
    end

    fetch_result = fetch_html
    return failure(fetch_result[:error], fetch_result[:error_code]) unless fetch_result[:success]

    html = fetch_result[:body]

    # Try JSON-LD extraction first (most recipe sites use this)
    json_ld_result = RecipeImporters::JsonLdExtractor.new(html, @url).extract
    return json_ld_result if json_ld_result.success?
    Rails.logger.info "[RecipeImporter] JSON-LD extraction failed for #{@url}: #{json_ld_result.error_code}: #{json_ld_result.error}"

    # Fall back to LLM extraction
    llm_result = RecipeImporters::LlmExtractor.new(html, @url).extract
    return llm_result if llm_result.success?
    Rails.logger.info "[RecipeImporter] LLM extraction failed for #{@url}: #{llm_result.error_code}: #{llm_result.error}"

    # Nothing worked
    failure(
      "All extraction methods failed — json_ld: [#{json_ld_result.error_code}: #{json_ld_result.error}], llm: [#{llm_result.error_code}: #{llm_result.error}]",
      :no_recipe_found
    )
  end

  private

  def fetch_html
    fetch_result = RecipeImporters::HtmlFetcher.new(
      @url,
      http_client: @http_client,
      log_prefix: "RecipeImporter"
    ).fetch

    return { success: false, error: fetch_result.error, error_code: fetch_result.error_code } unless fetch_result.success?

    { success: true, body: fetch_result.body }
  end

  def build_http_client
    Faraday.new do |conn|
      conn.options.timeout = 10
      conn.options.open_timeout = 5
      conn.headers["User-Agent"] = USER_AGENT
      conn.headers["Accept"] = "text/html"
      conn.response :follow_redirects, limit: MAX_REDIRECTS, callback: RecipeImporters::UrlValidator::REJECT_UNSAFE_REDIRECT
    end
  end

  def failure(message, code)
    Result.new(success?: false, recipe_attributes: {}, cover_image_url: nil, error: message, error_code: code)
  end
end
