class RecipesController < ApplicationController
  before_action :require_cookbook
  before_action :set_recipe, only: %i[show edit update destroy move]

  SORTS = {
    "recent" => { label: "Recently updated", order: { updated_at: :desc } },
    "name" => { label: "Name", order: { name: :asc } },
    "oldest" => { label: "Oldest first", order: { created_at: :asc } }
  }.freeze

  DEFAULT_SORT = "recent".freeze

  helper_method :sort_option

  def index
    scope = current_cookbook.recipes.with_attached_cover_image.includes(:tags)

    # Failed imports never appear in the grid; they surface as dismissible
    # banners so a broken import cannot quietly pollute the library.
    @failed_imports = scope.failed.order(created_at: :desc)

    @recipes = scope.where.not(import_status: :failed)
    @active_tag = Tag.find_by(slug: params[:tag]) if params[:tag].present?
    @recipes = @recipes.joins(:tags).where(tags: { id: @active_tag.id }) if @active_tag
    @recipes = @recipes.order(sort_option.fetch(:order))

    @tags = Tag.for_cookbook(current_cookbook)
  end

  def show
    @ingredients = @recipe.ingredients.to_a
    @other_cookbooks = available_cookbooks.reject { |cookbook| cookbook.id == current_cookbook.id }
  end

  def new
    @recipe = current_cookbook.recipes.new(servings: 2)
  end

  def create
    @recipe = current_cookbook.recipes.new(recipe_params.except(:ingredients))
    @recipe.user = Current.user

    if @recipe.save
      apply_ingredients(@recipe)
      redirect_to @recipe, notice: "Recipe saved."
    else
      render :new, status: :unprocessable_entity
    end
  end

  def edit
  end

  def update
    if @recipe.update(recipe_params.except(:ingredients))
      apply_ingredients(@recipe)
      redirect_to @recipe, notice: "Recipe updated."
    else
      render :edit, status: :unprocessable_entity
    end
  end

  def destroy
    if @recipe.destroy
      redirect_to recipes_path, notice: "Recipe deleted.", status: :see_other
    else
      redirect_to @recipe, alert: @recipe.errors.full_messages.to_sentence.presence || "Could not delete recipe."
    end
  end

  # PATCH /recipes/:id/move
  def move
    destination = available_cookbooks.find { |cookbook| cookbook.id == params[:cookbook_id].to_i }

    if destination.nil? || destination.id == @recipe.cookbook_id
      return redirect_back fallback_location: recipes_path, alert: "Pick a different cookbook."
    end

    @recipe.update!(cookbook: destination)
    redirect_to recipes_path, notice: "Moved to #{destination.name}."
  end

  # POST /recipes/import — a link
  def import
    url = params[:url].to_s.strip
    return redirect_to recipes_path, alert: "Paste a link first." if url.blank?

    validation = RecipeImporters::UrlValidator.new(url).validate
    return redirect_to recipes_path, alert: validation.error unless validation.success?

    started = start_import(source_url: url) do |placeholder|
      RecipeImportJob.perform_later(Current.user.id, placeholder.id, url)
    end
    return unless started

    redirect_to recipes_path, notice: "Importing your recipe…"
  end

  # POST /recipes/import_photo — an upload or a camera capture
  def import_photo
    image = params[:image]
    if (error = validate_import_image(image))
      return redirect_to recipes_path, alert: error
    end

    started = start_import do |placeholder|
      placeholder.import_image.attach(image)
      RecipeImageExtractJob.perform_later(Current.user.id, placeholder.id)
    end
    return unless started

    redirect_to recipes_path, notice: "Reading your photo…"
  end

  private

  # Search spans every cookbook the user belongs to, so opening a result may
  # mean the recipe lives in the other one. Follow it and switch, rather than
  # dead-ending on a 404.
  def set_recipe
    @recipe = Recipe.where(cookbook_id: available_cookbooks.map(&:id))
                    .includes(:tags, :ingredients)
                    .find(params[:id])

    switch_cookbook(@recipe.cookbook) unless @recipe.cookbook_id == current_cookbook.id
  rescue ActiveRecord::RecordNotFound
    redirect_to recipes_path, alert: "That recipe is no longer available."
  end

  def sort_option
    SORTS.fetch(params[:sort], SORTS.fetch(DEFAULT_SORT))
  end

  # Mirrors the API: a pending placeholder created under a lock so the monthly
  # limit cannot be raced, then a background job.
  def start_import(source_url: nil)
    placeholder = Current.user.with_lock do
      if Current.user.import_limit_reached?
        nil
      else
        current_cookbook.recipes.create!(
          name: "Importing…",
          source_url: source_url,
          import_status: :pending,
          user: Current.user
        )
      end
    end

    if placeholder.nil?
      redirect_to pro_path, alert: "You've reached your free limit of #{User::FREE_MONTHLY_IMPORT_LIMIT} imports this month."
      return nil
    end

    yield placeholder
    placeholder
  end

  def validate_import_image(image)
    return "Choose a photo first." if image.blank?
    return "That file is not an image." unless image.respond_to?(:content_type) && image.content_type.to_s.start_with?("image/")
    return "That image is too big (max 15MB)." if image.respond_to?(:size) && image.size > 15.megabytes

    nil
  end

  def apply_ingredients(recipe)
    return unless recipe_params.key?(:ingredients)

    recipe.replace_ingredients_from_strings(recipe_params[:ingredients])
    ParseRecipeIngredientsJob.perform_later(recipe.id)
  end

  def recipe_params
    @recipe_params ||= begin
      permitted = params.expect(
        recipe: [ :name, :notes, :servings, :prep_time, :cook_time, :source_url, :cover_image,
                  { tag_ids: [], ingredients: [], instructions: [] } ]
      )

      permitted[:instructions] = Array(permitted[:instructions]).map { |s| s.to_s.strip }.reject(&:blank?) if permitted.key?(:instructions)
      permitted[:tag_ids] = Array(permitted[:tag_ids]).reject(&:blank?) if permitted.key?(:tag_ids)

      # An untouched file field posts "", which Active Storage treats as "purge".
      permitted.delete(:cover_image) if permitted[:cover_image].blank?

      permitted
    end
  end
end
