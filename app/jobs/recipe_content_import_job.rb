require "stringio"

class RecipeContentImportJob < ApplicationJob
  queue_as :default
  CoverImageSource = Data.define(:url, :source)

  def perform(user_id, recipe_id, source_url, json_ld_strings, html, meta_tags = {}, cover_image_candidates = [])
    user = User.find_by(id: user_id)
    return unless user

    recipe = user.recipes.find_by(id: recipe_id)
    return unless recipe
    return if recipe.completed?

    result = extract_from_content(source_url, json_ld_strings, html)
    domain = extract_domain(source_url)

    if result.success?
      recipe.apply_extracted_attributes!(result.recipe_attributes.merge(import_status: :completed))
      ParseRecipeIngredientsJob.perform_later(recipe.id) if recipe.ingredients.any?(&:needs_enrichment?)
      attach_best_cover_image(recipe, result.cover_image_url, meta_tags, cover_image_candidates)
      Sentry.logger.info("recipe.import.success", domain: domain, channel: "share_extension", recipe_id: recipe_id)
    else
      error_message = build_error_message(source_url)
      recipe.update!(
        import_status: :failed,
        error_message: error_message
      )
      Sentry.logger.warn("recipe.import.failure", domain: domain, channel: "share_extension", recipe_id: recipe_id, error_code: result.error_code.to_s, error: result.error)
      Rails.logger.error "[RecipeContentImportJob] Import failed for recipe #{recipe_id} (#{source_url}): #{result.error}"
    end
  rescue => error
    if recipe
      error_message = build_error_message(source_url)
      recipe.update(
        import_status: :failed,
        error_message: error_message
      )
    end
    Rails.logger.error "[RecipeContentImportJob] Unexpected error for recipe #{recipe_id}: #{error.class} - #{error.message}"
    raise
  end

  private

  def extract_from_content(source_url, json_ld_strings, html)
    if RecipeImporters::TiktokVideoExtractor.supports_url?(source_url)
      return RecipeImporters::TiktokVideoExtractor.new(source_url).extract
    end

    json_ld_error = nil
    llm_error = nil

    # Try JSON-LD first: parse provided blocks directly (no HTML reconstruction)
    if json_ld_strings.present?
      result = RecipeImporters::JsonLdExtractor.new("", source_url).extract_from_json_ld_strings(json_ld_strings)
      return result if result.success?
      json_ld_error = "#{result.error_code}: #{result.error}"
      Rails.logger.info "[RecipeContentImportJob] JSON-LD extraction failed for #{source_url}: #{json_ld_error}"
    else
      json_ld_error = "no json_ld_strings provided"
    end

    # Fall back to LLM extraction with the cleaned HTML
    if html.present?
      result = RecipeImporters::LlmExtractor.new(html, source_url).extract
      return result if result.success?
      llm_error = "#{result.error_code}: #{result.error}"
      Rails.logger.info "[RecipeContentImportJob] LLM extraction failed for #{source_url}: #{llm_error}"
    else
      llm_error = "no html provided"
    end

    RecipeImporter::Result.new(
      success?: false,
      recipe_attributes: {},
      cover_image_url: nil,
      error: "All extraction methods failed — json_ld: [#{json_ld_error}], llm: [#{llm_error}]",
      error_code: :no_recipe_found
    )
  end

  def attach_best_cover_image(recipe, extracted_cover_image_url, meta_tags, cover_image_candidates)
    sources = cover_image_sources(extracted_cover_image_url, meta_tags, cover_image_candidates)
    if sources.empty?
      Rails.logger.info("[RecipeContentImportJob] No cover image candidate found (json-ld/meta/dom candidates missing)")
      return
    end

    Rails.logger.info(
      "[RecipeContentImportJob] Cover image candidates for recipe #{recipe.id}: total=#{sources.size}"
    )

    sources.each_with_index do |source_candidate, attempt_index|
      if attach_cover_image(recipe, source_candidate, attempt_index)
        return
      end
    end

    Rails.logger.info("[RecipeContentImportJob] Could not attach cover image for recipe #{recipe.id} after #{sources.size} attempts")
  end

  def attach_cover_image(recipe, source_candidate, attempt_index)
    unless valid_cover_image_url?(recipe, source_candidate, attempt_index)
      return false
    end

    image_url = source_candidate.url
    source = source_candidate.source
    Rails.logger.info("[RecipeContentImportJob] Cover image attempt=#{attempt_index} source=#{source} fetching")

    response = cover_image_client.get(image_url)

    unless response.success?
      Rails.logger.info "[RecipeContentImportJob] Cover image attempt=#{attempt_index} source=#{source} fetch_failed status=#{response.status} recipe=#{recipe.id}"
      return false
    end

    content_type = response.headers["content-type"].to_s
    unless content_type.start_with?("image/")
      Rails.logger.info "[RecipeContentImportJob] Cover image attempt=#{attempt_index} source=#{source} invalid_content_type recipe=#{recipe.id} content_type=#{content_type}"
      return false
    end

    if response.body.blank? || response.body.bytesize == 0
      Rails.logger.info "[RecipeContentImportJob] Cover image attempt=#{attempt_index} source=#{source} empty_body recipe=#{recipe.id}"
      return false
    end

    if response.body.bytesize > 15.megabytes
      Rails.logger.info "[RecipeContentImportJob] Cover image attempt=#{attempt_index} source=#{source} too_large recipe=#{recipe.id} bytes=#{response.body.bytesize}"
      return false
    end

    filename = extract_filename(image_url)
    recipe.cover_image.attach(
      io: StringIO.new(response.body),
      filename: filename,
      content_type: content_type
    )
    Rails.logger.info("[RecipeContentImportJob] Cover image attempt=#{attempt_index} source=#{source} attached recipe=#{recipe.id}")
    true
  rescue => error
    Rails.logger.error "[RecipeContentImportJob] Cover image attempt=#{attempt_index} source=#{source} attach_failed recipe=#{recipe.id}: #{error.class} - #{error.message}"
    false
  end

  def cover_image_sources(extracted_cover_image_url, meta_tags, cover_image_candidates)
    sources = []
    dedupe = {}

    source_candidate = build_cover_image_source(extracted_cover_image_url, "json_ld_or_extractor", dedupe)
    sources << source_candidate if source_candidate

    meta_values = [
      meta_tags["og:image:secure_url"],
      meta_tags["og:image"],
      meta_tags["twitter:image"]
    ]
    meta_values.each do |meta_url|
      source_candidate = build_cover_image_source(meta_url, "meta_tag", dedupe)
      sources << source_candidate if source_candidate
    end

    Array(cover_image_candidates).each do |candidate_url|
      source_candidate = build_cover_image_source(candidate_url, "dom_candidate", dedupe)
      sources << source_candidate if source_candidate
    end

    sources
  end

  def build_cover_image_source(raw_url, source, dedupe)
    image_url = raw_url.to_s.strip
    return if image_url.blank? || dedupe[image_url]

    dedupe[image_url] = true
    CoverImageSource.new(url: image_url, source:)
  end

  def valid_cover_image_url?(recipe, source_candidate, attempt_index)
    validation = RecipeImporters::UrlValidator.new(source_candidate.url).validate
    return true if validation.success?

    Rails.logger.info(
      "[RecipeContentImportJob] Cover image attempt=#{attempt_index} source=#{source_candidate.source} validation_failed recipe=#{recipe.id}: #{validation.error}"
    )
    false
  end

  def cover_image_client
    Faraday.new do |conn|
      conn.response :follow_redirects, limit: 5
      conn.options.timeout = 10
      conn.options.open_timeout = 5
      conn.headers["User-Agent"] = "Mozilla/5.0 (compatible; Hauptgang Recipe Importer)"
    end
  end

  def extract_filename(url)
    uri = URI.parse(url)
    name = File.basename(uri.path.to_s)
    name.presence || "cover-image"
  rescue URI::InvalidURIError
    "cover-image"
  end

  def build_error_message(url)
    domain = extract_domain(url)
    "Import from #{domain} failed."
  end

  def extract_domain(url)
    return "unknown source" if url.blank?

    uri = URI.parse(url)
    host = uri.host || "unknown source"
    host.delete_prefix("www.")
  rescue URI::InvalidURIError
    "unknown source"
  end
end
