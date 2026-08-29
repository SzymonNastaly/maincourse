module ShoppingList
  class UpsertItems
    Result = Data.define(:success?, :items, :errors)

    def initialize(user:, cookbook:, items:)
      @user = user
      @cookbook = cookbook
      @items = items
    end

    def call
      return Result.new(success?: false, items: [], errors: [ empty_items_error ]) if @items.empty?

      created = []
      errors = []
      newly_added = []

      ActiveRecord::Base.transaction do
        @items.each do |item_params|
          client_id = item_params[:client_id]
          name = item_params[:name]

          if client_id.blank? || name.blank?
            errors << { client_id: client_id, error: "client_id and name are required" }
            next
          end

          source_recipe_id = resolve_source_recipe_id(item_params[:source_recipe_id])
          if item_params[:source_recipe_id].present? && source_recipe_id.nil?
            errors << { client_id: client_id, error: "Recipe not found" }
            next
          end

          item = upsert_item(client_id, name, source_recipe_id, item_params[:checked_at], item_params[:details])
          if item.persisted? && item.errors.empty?
            created << item
            newly_added << item if item.previously_new_record?
          else
            errors << { client_id: client_id, error: item.errors.full_messages.to_sentence }
          end
        end

        if errors.any?
          raise ActiveRecord::Rollback
        end
      end

      if errors.any?
        Result.new(success?: false, items: [], errors: errors)
      else
        notify_collaborators(newly_added)
        record_engagement(newly_added)
        Result.new(success?: true, items: created, errors: [])
      end
    end

    private

    def upsert_item(client_id, name, source_recipe_id, checked_at, details)
      item = @cookbook.shopping_list_items.find_or_initialize_by(client_id: client_id)
      item.user = @user
      item.name = name
      item.details = details.presence
      item.source_recipe_id = source_recipe_id
      item.checked_at = checked_at.presence
      item.save
      item
    rescue ActiveRecord::RecordNotUnique
      item = @cookbook.shopping_list_items.find_by!(client_id: client_id)
      item.user = @user
      item.name = name
      item.details = details.presence
      item.source_recipe_id = source_recipe_id
      item.checked_at = checked_at.presence
      item.save
      item
    rescue ActiveRecord::InvalidForeignKey
      item = @cookbook.shopping_list_items.find_or_initialize_by(client_id: client_id)
      item.user = @user
      item.name = name
      item.details = details.presence
      item.source_recipe_id = nil
      item.checked_at = checked_at.presence
      item.errors.add(:base, "Recipe not found")
      item
    end

    def resolve_source_recipe_id(source_recipe_id)
      return nil if source_recipe_id.blank?
      return source_recipe_id if @cookbook.recipes.exists?(id: source_recipe_id)

      nil
    end

    def record_engagement(items)
      items.filter_map(&:source_recipe_id).uniq.each do |recipe_id|
        recipe = Recipe.find_by(id: recipe_id)
        next if recipe.nil?

        RecipeEngagement.mark_added_to_list!(recipe: recipe)
      end
    end

    def notify_collaborators(items)
      return if items.empty?

      PendingNotification.record_events!(
        cookbook: @cookbook,
        actor: @user,
        category: :shopping_list,
        events: items.map { |item| { name: item.name } }
      )
    end

    def empty_items_error
      { client_id: nil, error: "No items provided" }
    end
  end
end
