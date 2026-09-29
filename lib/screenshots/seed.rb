require "digest"
require "json"

module Screenshots
  # Resets the showcase account, retaining recipe IDs for stable capture selectors.
  class Seed
    EMAIL = "screenshots@example.test"
    PASSWORD = "maincourse-screenshots"

    LOCALES = %w[en-US de-DE pl-PL].freeze

    def self.call(locale: "en-US")
      new(locale).call
    end

    # English is the source fixture; other locales translate the same slugs.
    def self.fixture(locale)
      raise ArgumentError, "Unsupported screenshot locale: #{locale}" unless LOCALES.include?(locale)

      name = locale == "en-US" ? "recipes.json" : "recipes.#{locale}.json"
      JSON.parse(Rails.root.join("screenshots", name).read)
    end

    def initialize(locale)
      @fixture = self.class.fixture(locale)
    end

    def call
      unless Rails.env.test? || (Rails.env.development? && screenshot_database?)
        raise "Screenshot seeding requires the dedicated screenshot database. Use bin/screenshots seed."
      end

      catalog = @fixture.fetch("recipes")
      images = catalog.to_h do |entry|
        path = Rails.root.join("screenshots/photos", "#{entry.fetch('slug')}.png")
        raise "Missing #{path}. Run bin/screenshots photos first." unless path.file?
        [ entry.fetch("slug"), path ]
      end

      result = ApplicationRecord.transaction do
        user = User.find_or_initialize_by(email_address: EMAIL)
        user.update!(name: "Alex Morgan", password: PASSWORD, lifecycle_notifications_enabled: false)
        cookbook = user.personal_cookbook
        cookbook.update!(name: Cookbook::DEFAULT_NAME, default_name: true)
        cookbook.recipes.where.not(name: catalog.map { |r| r.fetch("name") }).destroy_all
        recipe_ids = {}

        catalog.each do |entry|
          recipe = cookbook.recipes.find_or_initialize_by(name: entry.fetch("name"))
          recipe.assign_attributes(entry.slice("name", "prep_time", "cook_time", "servings", "instructions", "notes", "favorite"))
          recipe.assign_attributes(user: user, import_status: :completed, source_url: nil)
          recipe.save!
          recipe.tags = entry.fetch("tags").map { |name| Tag.find_or_create_by!(name: name) }
          recipe.replace_ingredients_from_hashes(entry.fetch("ingredients"))
          attach_photo(recipe, images.fetch(entry.fetch("slug")))
          recipe_ids[entry.fetch("slug")] = recipe.id
        end

        cookbook.shopping_list_items.destroy_all
        @fixture.fetch("shopping_list").each_with_index do |entry, index|
          item = cookbook.shopping_list_items.create!(
            user: user, name: entry.fetch("name"), details: entry.fetch("details"), client_id: "showcase-#{index}",
            created_at: Time.current - index.seconds
          )
          # Confirmed aisles, so captures are deterministic and queue no LLM work.
          item.update_columns(category: entry.fetch("category"), canonical_name: entry.fetch("canonical_name"),
            enrichment_version: Llm::IngredientInstructions::VERSION)
        end
        # Each capture logs in again; keep the dedicated fixture account's token set bounded.
        user.api_tokens.destroy_all
        { user_id: user.id, cookbook_id: cookbook.id, recipes: recipe_ids }
      end

      # Generate real image variants up front so capture doesn't race first-use processing.
      unless Rails.env.test?
        Recipe.where(id: result.fetch(:recipes).values).each do |recipe|
          %i[card hero].each { |variant| recipe.cover_image.variant(variant).processed }
        end
      end
      # Attachment commit/variant callbacks can touch recipes. Set the final order
      # after they complete, rather than inside the attachment transaction.
      result.fetch(:recipes).values.each_with_index do |id, index|
        timestamp = Time.utc(2026, 1, 15, 12) - index.minutes
        Recipe.find(id).update_columns(created_at: timestamp, updated_at: timestamp)
      end
      result
    end

    private

    def screenshot_database?
      expected = Rails.root.join("storage/screenshots/database.sqlite3").to_s
      File.expand_path(ApplicationRecord.connection_db_config.database) == expected
    end

    def attach_photo(recipe, path)
      checksum = Digest::MD5.file(path).base64digest
      if recipe.cover_image.attached?
        blob = recipe.cover_image.blob
        return if blob.checksum == checksum && blob.service.exist?(blob.key)
      end

      File.open(path, "rb") do |image|
        # Upload while the IO is open; attach's after_commit runs outside this block.
        blob = ActiveStorage::Blob.create_and_upload!(io: image, filename: path.basename.to_s, content_type: "image/png")
        blob.analyze
        recipe.cover_image.attach(blob)
      end
    end
  end
end
