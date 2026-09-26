require "faraday"
require "stringio"

class RecipeImportJob < ApplicationJob
  queue_as :default

  def perform(user_id, recipe_id, source_url)
    user = User.find_by(id: user_id)
    return unless user

    recipe = user.recipes.find_by(id: recipe_id)
    return unless recipe
    return if recipe.completed?

    result = RecipeImporter.new(source_url).import

    domain = extract_domain(source_url)

    if result.success?
      recipe.apply_extracted_attributes!(result.recipe_attributes.merge(import_status: :completed))
      enqueue_ingredient_parse(recipe)
      attach_cover_image(recipe, result.cover_image_url) if result.cover_image_url.present?
      Sentry.logger.info("recipe.import.success", domain: domain, channel: "url", recipe_id: recipe_id)
    else
      error_message = build_error_message(source_url, result.error_code)
      recipe.update!(
        import_status: :failed,
        error_message: error_message, import_error_code: "import_failed"
      )
      Sentry.logger.warn("recipe.import.failure", domain: domain, channel: "url", recipe_id: recipe_id, error_code: result.error_code.to_s, error: result.error)
      Rails.logger.error "[RecipeImportJob] Import failed for recipe #{recipe_id} (#{source_url}): #{result.error}"
    end
  rescue => error
    if recipe
      error_message = build_error_message(source_url, :unexpected_error)
      recipe.update(
        import_status: :failed,
        error_message: error_message, import_error_code: "import_failed"
      )
    end
    Rails.logger.error "[RecipeImportJob] Unexpected error for recipe #{recipe_id}: #{error.class} - #{error.message}"
    raise
  end

  private

  def enqueue_ingredient_parse(recipe)
    return unless recipe.ingredients.any?(&:needs_enrichment?)
    ParseRecipeIngredientsJob.perform_later(recipe.id)
  end

  def attach_cover_image(recipe, image_url)
    unless valid_cover_image_url?(recipe, image_url)
      return
    end

    response = cover_image_client.get(image_url)

    unless response.success?
      Rails.logger.info "[RecipeImportJob] Cover image fetch failed (HTTP #{response.status}) for recipe #{recipe.id}"
      return
    end

    content_type = response.headers["content-type"].to_s
    unless content_type.start_with?("image/")
      Rails.logger.info "[RecipeImportJob] Cover image invalid content type for recipe #{recipe.id}: #{content_type}"
      return
    end

    if response.body.bytesize > 15.megabytes
      Rails.logger.info "[RecipeImportJob] Cover image too large for recipe #{recipe.id}: #{response.body.bytesize} bytes"
      return
    end

    filename = extract_filename(image_url)
    recipe.cover_image.attach(
      io: StringIO.new(response.body),
      filename: filename,
      content_type: content_type
    )
  rescue => error
    Rails.logger.error "[RecipeImportJob] Cover image attach failed for recipe #{recipe.id}: #{error.class} - #{error.message}"
  end

  def valid_cover_image_url?(recipe, image_url)
    validation = RecipeImporters::UrlValidator.new(image_url).validate
    return true if validation.success?

    Rails.logger.info(
      "[RecipeImportJob] Skipping cover image for recipe #{recipe.id}: #{validation.error}"
    )
    false
  end

  def cover_image_client
    Faraday.new do |conn|
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

  def build_error_message(url, _error_code = nil)
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
