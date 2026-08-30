class Avo::Resources::Recipe < Avo::BaseResource
  # self.includes = []
  # self.attachments = []
  self.search = {
    query: -> { query.ransack(id_eq: q, name_cont: q, source_url_cont: q, m: "or").result(distinct: false) }
  }

  def fields
    field :id, as: :id
    field :name, as: :text
    field :cookbook, as: :belongs_to
    field :user, as: :belongs_to
    field :favorite, as: :boolean
    field :import_status, as: :select, enum: ::Recipe.import_statuses
    field :source_url, as: :text
    field :prep_time, as: :number
    field :cook_time, as: :number
    field :servings, as: :number
    field :ingredients, as: :textarea, readonly: true, format_using: -> { value&.map(&:raw)&.join("\n") }
    field :instructions, as: :textarea, readonly: true, format_using: -> { value&.join("\n") }
    field :notes, as: :textarea
    field :cover_image, as: :file
    field :error_message, as: :textarea, hide_on: :index
    field :failed_recipe_fetched_at, as: :date_time, hide_on: :index
    field :import_image, as: :file, hide_on: :index
    field :tags, as: :has_many, through: :recipe_tags
    field :shopping_list_items, as: :has_many
    field :meal_plan_entries, as: :has_many
  end
end
