require "schematist"

# Extracts recipe data from an image using a vision-capable LLM.
class RecipeImageLlmService
  include Llm::RecipeExtraction

  MODEL = "google/gemini-3.5-flash-lite"
  OPENROUTER_PROVIDER = "google-vertex/eu"

  def initialize(image_path)
    @image_path = image_path
  end

  def extract
    return extraction_failed("No image provided") if @image_path.blank?

    response = call_llm
    build_result(response.parsed)
  rescue Faraday::TimeoutError, Faraday::ConnectionFailed => error
    error_result("LLM request timed out: #{error.message}", :llm_timeout)
  rescue RubyLLM::Error => error
    error_result("LLM API error: #{error.message}", :llm_error)
  rescue StandardError => error
    error_result("Extraction failed: #{error.message}", :extraction_failed)
  end

  private

  def call_llm
    chat = RubyLLM.chat(model: MODEL, provider: :openrouter, assume_model_exists: true)
    chat.with_provider_options(provider: { only: [ OPENROUTER_PROVIDER ] })
    chat.with_schema(Llm::RecipeSchema).ask(prompt, with: @image_path)
  end

  def prompt
    <<~PROMPT
      Extract recipe information from the image.
      Identify the recipe name, ingredients list, and cooking instructions.

      #{Llm::IngredientInstructions.prompt}

      If the image does not contain a valid recipe, return an empty name field.
    PROMPT
  end
end
