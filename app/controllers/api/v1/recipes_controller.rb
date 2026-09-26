require "base64"
require "time"

module Api
  module V1
    class RecipesController < BaseController
      class ImportLimitReachedError < StandardError; end

      before_action :check_import_limit!, only: [ :import, :import_with_content, :extract_from_text, :extract_from_image ]
      after_action :cleanup_old_failed_recipes, only: [ :index ]

      rescue_from ImportLimitReachedError, with: :render_import_limit_reached

      def index
        recipes = current_cookbook.recipes.with_attached_cover_image.includes(:tags)
        recipes = recipes.favorited if params[:favorites] == "true"
        recipes = recipes.order(updated_at: :desc)

        # Track first fetch of failed recipes (before rendering)
        track_failed_recipe_fetches(recipes)

        render json: recipes.map { |recipe| recipe_list_json(recipe) }
      end

      def batch
        limit = normalize_limit(params[:limit])
        recipes = current_cookbook.recipes.with_attached_cover_image.includes(:tags)
        recipes = recipes.order(updated_at: :asc, id: :asc)

        if params[:cursor].present?
          cursor = decode_cursor(params[:cursor])
          return render_api_error "invalid_request", error: "Invalid cursor", status: :unprocessable_entity if cursor.nil?

          updated_at, id = cursor
          recipes = recipes.where("updated_at > ? OR (updated_at = ? AND id > ?)", updated_at, updated_at, id)
        end

        batch = recipes.limit(limit)
        next_cursor = batch.any? ? encode_cursor(batch.last.updated_at, batch.last.id) : nil

        render json: {
          recipes: batch.map { |recipe| recipe_detail_json(recipe) },
          next_cursor: next_cursor
        }
      end

      def show
        recipe = current_cookbook.recipes.with_attached_cover_image.includes(:tags).find(params[:id])
        render json: recipe_detail_json(recipe)
      rescue ActiveRecord::RecordNotFound
        render_api_error "not_found", error: "Recipe not found", status: :not_found
      end

      def update
        recipe = current_cookbook.recipes.find(params[:id])

        if recipe_params[:cookbook_id].present?
          target_cookbook = current_user.cookbooks.find_by(id: recipe_params[:cookbook_id])
          unless target_cookbook
            return render_api_error "cookbook_unavailable", error: "Target cookbook not found or not accessible", status: :unprocessable_entity
          end
        end

        attrs = recipe_params
        ingredients_strings = attrs.delete(:ingredients)

        if recipe.update(attrs)
          if ingredients_strings
            recipe.replace_ingredients_from_strings(ingredients_strings)
            ParseRecipeIngredientsJob.perform_later(recipe.id)
          end
          render json: recipe_detail_json(recipe.reload)
        else
          render_validation_errors(recipe)
        end
      rescue ActiveRecord::RecordNotFound
        render_api_error "not_found", error: "Recipe not found", status: :not_found
      end

      def destroy
        recipe = current_cookbook.recipes.find(params[:id])
        recipe.destroy!
        head :no_content
      rescue ActiveRecord::RecordNotFound
        render_api_error "not_found", error: "Recipe not found", status: :not_found
      rescue ActiveRecord::RecordNotDestroyed
        render_api_error "delete_failed", error: "Could not delete recipe", status: :unprocessable_entity
      end

      def import
        url = params[:url].to_s.strip
        if url.blank?
          return render_api_error "url_required", error: "URL is required", status: :unprocessable_entity
        end

        validation = RecipeImporters::UrlValidator.new(url).validate
        unless validation.success?
          return render_api_error "invalid_import_url", error: validation.error, status: :unprocessable_entity
        end

        recipe = current_user.with_lock do
          check_import_limit_locked!
          current_cookbook.recipes.create!(
            name: "Importing...",
            source_url: url,
            import_status: :pending,
            user: current_user
          )
        end

        RecipeImportJob.perform_later(current_user.id, recipe.id, url)

        render json: { id: recipe.id, import_status: recipe.import_status }, status: :accepted
      end

      def import_with_content
        url = params[:url].to_s.strip
        if url.blank?
          return render_api_error "url_required", error: "URL is required", status: :unprocessable_entity
        end

        validation = RecipeImporters::UrlValidator.new(url).validate
        unless validation.success?
          return render_api_error "invalid_import_url", error: validation.error, status: :unprocessable_entity
        end

        json_ld = params[:json_ld] || []
        html = params[:html].to_s
        meta_tags = normalize_meta_tags(params[:meta_tags])
        cover_image_candidates = normalize_cover_image_candidates(params[:cover_image_candidates])

        # Reject oversized payloads (2MB combined limit for pre-extracted content)
        total_size = json_ld.sum { |json_ld_string| json_ld_string.to_s.bytesize } +
          html.bytesize +
          meta_tags.sum { |key, value| key.to_s.bytesize + value.to_s.bytesize } +
          cover_image_candidates.sum { |value| value.to_s.bytesize }
        if total_size > 2.megabytes
          return render_api_error "content_too_large", error: "Content is too large", status: :payload_too_large
        end

        recipe = current_user.with_lock do
          check_import_limit_locked!
          current_cookbook.recipes.create!(
            name: "Importing...",
            source_url: url,
            import_status: :pending,
            user: current_user
          )
        end

        RecipeContentImportJob.perform_later(
          current_user.id,
          recipe.id,
          url,
          json_ld.map(&:to_s),
          html,
          meta_tags,
          cover_image_candidates
        )

        render json: { id: recipe.id, import_status: recipe.import_status }, status: :accepted
      end

      def extract_from_text
        text = params[:text].to_s.strip
        if text.blank?
          return render_api_error "text_required", error: "Text is required", status: :unprocessable_entity
        end
        if text.length > 50_000
          return render_api_error "text_too_long", error: "Text too long (max 50,000 chars)", error_params: { count: 50_000 }, status: :unprocessable_entity
        end

        recipe = current_user.with_lock do
          check_import_limit_locked!
          current_cookbook.recipes.create!(
            name: "Importing...",
            import_status: :pending,
            user: current_user
          )
        end

        RecipeTextExtractJob.perform_later(current_user.id, recipe.id, text)

        render json: { id: recipe.id, import_status: recipe.import_status }, status: :accepted
      end

      def extract_from_image
        image = params[:image]
        validate_import_image(image)
        return if performed?

        recipe = current_user.with_lock do
          check_import_limit_locked!
          current_cookbook.recipes.create!(
            name: "Importing...",
            import_status: :pending,
            user: current_user
          )
        end

        recipe.import_image.attach(image)

        RecipeImageExtractJob.perform_later(current_user.id, recipe.id)

        render json: { id: recipe.id, import_status: recipe.import_status }, status: :accepted
      end

      private

      def recipe_list_json(recipe)
        {
          id: recipe.id,
          name: recipe.name,
          prep_time: recipe.prep_time,
          cook_time: recipe.cook_time,
          favorite: recipe.favorite,
          # TODO: Remove legacy cover_image_url once older iOS builds have migrated
          # to the structured cover_images payload.
          cover_image_url: recipe.cover_image_variant_url(:card),
          cover_images: recipe.cover_image_urls,
          import_status: recipe.import_status,
          starter_recipe_key: recipe.starter_recipe_key,
          error_message: recipe.error_message,
          import_error_code: recipe.failed? ? (recipe.import_error_code || "import_failed") : nil,
          updated_at: recipe.updated_at
        }
      end

      def recipe_detail_json(recipe)
        {
          id: recipe.id,
          name: recipe.name,
          prep_time: recipe.prep_time,
          cook_time: recipe.cook_time,
          servings: recipe.servings,
          favorite: recipe.favorite,
          ingredients: recipe.ingredients.map(&:raw),
          structured_ingredients: recipe.ingredients.map { |i|
            {
              id: i.id,
              position: i.position,
              amount: i.amount,
              amount_max: i.amount_max,
              unit: i.unit,
              name: i.name.presence || i.raw,
              note: i.note,
              canonical_name: i.canonical_name,
              canonical_unit: i.canonical_unit,
               category: i.category,
               enrichment_version: i.enrichment_version,
               shopping_default_included: ShoppingList::Policy.default_included?(i.canonical_name),
               raw: i.raw
            }
          },
          instructions: recipe.instructions,
          notes: recipe.notes,
          source_url: recipe.source_url,
          starter_recipe_key: recipe.starter_recipe_key,
          tags: recipe.tags.map { |tag| { id: tag.id, name: tag.name } },
          # TODO: Remove legacy cover_image_url once older iOS builds have migrated
          # to the structured cover_images payload.
          cover_image_url: recipe.cover_image_variant_url(:hero),
          cover_images: recipe.cover_image_urls,
          created_at: recipe.created_at,
          updated_at: recipe.updated_at
        }
      end

      def recipe_params
        permitted = params.permit(:name, :prep_time, :cook_time, :servings, :notes, :source_url, :favorite, :cookbook_id,
          :cover_image, ingredients: [], instructions: [])

        if permitted[:ingredients].is_a?(Array)
          permitted[:ingredients] = permitted[:ingredients].reject(&:blank?)
        end

        if permitted[:instructions].is_a?(Array)
          permitted[:instructions] = permitted[:instructions].reject(&:blank?)
        end

        permitted
      end

      def normalize_limit(raw_limit)
        limit = raw_limit.to_i
        limit = 100 if limit <= 0
        [ limit, 500 ].min
      end

      def normalize_meta_tags(raw_meta_tags)
        return {} unless raw_meta_tags.respond_to?(:to_unsafe_h) || raw_meta_tags.is_a?(Hash)

        meta_tags_hash = if raw_meta_tags.respond_to?(:to_unsafe_h)
          raw_meta_tags.to_unsafe_h
        else
          raw_meta_tags
        end

        meta_tags_hash.each_with_object({}) do |(key, value), result|
          next if key.blank? || value.blank?

          result[key.to_s] = value.to_s
        end
      end

      def normalize_cover_image_candidates(raw_candidates)
        candidates = raw_candidates.is_a?(Array) ? raw_candidates : []

        candidates
          .map { |value| value.to_s.strip }
          .reject(&:blank?)
          .uniq
          .first(5)
      end

      def encode_cursor(updated_at, id)
        payload = "#{updated_at.iso8601(6)}|#{id}"
        Base64.urlsafe_encode64(payload)
      end

      def decode_cursor(cursor)
        decoded = Base64.urlsafe_decode64(cursor.to_s)
        parts = decoded.split("|", 2)
        return nil unless parts.length == 2

        timestamp = Time.iso8601(parts[0])
        id = Integer(parts[1])
        [ timestamp, id ]
      rescue ArgumentError
        nil
      end

      def check_import_limit!
        return unless current_user.import_limit_reached?

        render_import_limit_reached
      end

      def render_import_limit_reached
        render_api_error "import_limit_reached", error: "Monthly import limit reached", error_params: { count: User::FREE_MONTHLY_IMPORT_LIMIT }, limit: User::FREE_MONTHLY_IMPORT_LIMIT, status: :forbidden
      end

      def check_import_limit_locked!
        return unless current_user.import_limit_reached?

        raise ImportLimitReachedError
      end

      def validate_import_image(image)
        return render_api_error "image_required", error: "Image is required", status: :unprocessable_entity if image.blank?
        unless image.respond_to?(:content_type) && image.respond_to?(:size)
          return render_api_error "invalid_image", error: "Invalid image upload", status: :unprocessable_entity
        end
        unless image.content_type.to_s.start_with?("image/")
          return render_api_error "invalid_image", error: "Image must be an image", status: :unprocessable_entity
        end
        if image.size > 15.megabytes
          render_api_error "image_too_large", error: "Image is too big (max 15MB)", status: :unprocessable_entity
        end

        nil
      end

      def track_failed_recipe_fetches(recipes)
        current_cookbook.recipes
          .where(id: recipes.select(:id))
          .where(import_status: :failed, failed_recipe_fetched_at: nil)
          .update_all(failed_recipe_fetched_at: Time.current)
      end

      def cleanup_old_failed_recipes
        current_cookbook.recipes
          .where(import_status: :failed)
          .where("failed_recipe_fetched_at < ?", 1.minute.ago)
          .destroy_all
      end
    end
  end
end
