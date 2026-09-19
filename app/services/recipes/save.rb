require "stringio"

module Recipes
  class Save
    UUID_PATTERN = /\A[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\z/i

    class InvalidRequest < StandardError; end
    class Conflict < StandardError; end
    class Unavailable < StandardError; end

    def self.call(user:, cookbook:, source:, request_id:)
      new(user:, cookbook:, source:, request_id:).call
    end

    def initialize(user:, cookbook:, source:, request_id:)
      @user = user
      @cookbook = cookbook
      @source = normalize_source(source)
      @request_id = normalize_request_id(request_id)
    end

    def call
      user.with_lock do
        receipt = user.recipe_saves.find_by(request_id: request_id)
        return replay(receipt) if receipt

        resolved_source = resolve_source
        create_recipe_and_receipt(resolved_source)
      end
    end

    private

    attr_reader :user, :cookbook, :source, :request_id

    def normalize_source(value)
      hash = if value.is_a?(ActionController::Parameters)
        value.to_unsafe_h
      elsif value.is_a?(Hash)
        value
      else
        raise InvalidRequest, "Source must be an object"
      end

      type = hash["type"] || hash[:type]
      key = hash["key"] || hash[:key]
      unless type.is_a?(String) && key.is_a?(String)
        raise InvalidRequest, "Source type and key must be strings"
      end

      type = type.strip
      key = key.strip
      raise InvalidRequest, "Source type and key are required" if type.blank? || key.blank?

      { type: type, key: key }
    end

    def normalize_request_id(value)
      normalized = value.to_s.strip.downcase
      raise InvalidRequest, "Invalid request ID" unless UUID_PATTERN.match?(normalized)

      normalized
    end

    def replay(receipt)
      unless receipt.original_destination_cookbook_id == cookbook.id &&
          receipt.source_type == source.fetch(:type) &&
          receipt.source_key == source.fetch(:key)
        raise Conflict, "Request ID has already been used"
      end

      recipe = receipt.saved_recipe
      raise Unavailable, "Saved recipe is no longer available" unless recipe
      raise Unavailable, "Saved recipe is no longer available" unless user.cookbooks.exists?(id: recipe.cookbook_id)

      recipe
    end

    def resolve_source
      raise InvalidRequest, "Unknown source type" unless source.fetch(:type) == "sample"

      SampleSource.resolve(key: source.fetch(:key))
    rescue SampleSource::UnknownKey => error
      raise InvalidRequest, error.message
    end

    def create_recipe_and_receipt(resolved_source)
      content = resolved_source.content
      recipe = cookbook.recipes.create!(
        user: user,
        name: content.fetch("name"),
        notes: resolved_source.attribution,
        prep_time: content.fetch("prep_time"),
        cook_time: content.fetch("cook_time"),
        servings: content.fetch("servings"),
        instructions: content.fetch("instructions"),
        source_url: nil,
        import_status: :completed,
        starter_recipe_key: resolved_source.source_key
      )
      recipe.replace_ingredients_from_hashes(content.fetch("ingredients"))
      attach_image!(recipe, resolved_source)
      user.recipe_saves.create!(
        request_id: request_id,
        original_destination_cookbook_id: cookbook.id,
        source_type: resolved_source.source_type,
        source_key: resolved_source.source_key,
        saved_recipe: recipe
      )
      recipe
    end

    def attach_image!(recipe, resolved_source)
      blob = ActiveStorage::Blob.create_and_upload!(
        io: StringIO.new(resolved_source.image_path.binread),
        filename: resolved_source.content.fetch("image_name"),
        content_type: "image/jpeg"
      )
      recipe.cover_image.attach(blob)
      recipe.save!
    end
  end
end
