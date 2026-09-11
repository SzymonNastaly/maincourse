require "ruby_llm/schema"

# Extracts recipe data from text using an LLM.
# Supports extraction from webpage content or raw recipe text.
class RecipeLlmService
  include Llm::RecipeExtraction

  MAX_TEXT_LENGTH = 15_000
  MODEL = "google/gemini-3.5-flash-lite"
  OPENROUTER_PROVIDER = "google-vertex/eu"
  PROMPT_TYPES = %i[webpage raw_text].freeze

  def initialize(text, prompt_type: :webpage, source_url: nil)
    raise ArgumentError, "Invalid prompt_type: #{prompt_type}" unless PROMPT_TYPES.include?(prompt_type)

    @text = text
    @prompt_type = prompt_type
    @source_url = source_url
  end

  def extract
    truncated_text = @text.to_s[0, MAX_TEXT_LENGTH]
    return extraction_failed("No text content provided") if truncated_text.blank?

    response = call_llm(truncated_text)
    result = build_result(response.content)

    if result.success? && @source_url.present?
      Result.new(**result.to_h, recipe_attributes: result.recipe_attributes.merge(source_url: @source_url))
    else
      result
    end
  rescue Faraday::TimeoutError, Faraday::ConnectionFailed => error
    error_result("LLM request timed out: #{error.message}", :llm_timeout)
  rescue RubyLLM::Error => error
    error_result("LLM API error: #{error.message}", :llm_error)
  rescue StandardError => error
    error_result("Extraction failed: #{error.message}", :extraction_failed)
  end

  private

  def call_llm(text)
    chat = RubyLLM.chat(model: MODEL, provider: :openrouter, assume_model_exists: true)
    chat.with_params(provider: { only: [ OPENROUTER_PROVIDER ] })
    chat.with_schema(Llm::RecipeSchema).ask(prompt_for(text))
  end

  def prompt_for(text)
    case @prompt_type
    when :webpage
      webpage_prompt(text)
    when :raw_text
      raw_text_prompt(text)
    end
  end

  def webpage_prompt(text)
    <<~PROMPT
      Extract recipe information from the following webpage content.
      Find the recipe name, ingredients list, and cooking instructions.

      #{ingredient_field_instructions}

      Always respond in the same language as the recipe content. If the recipe is in German, return German. If Spanish, return Spanish. And so on. Do not translate ingredient names.

      If you cannot find recipe content, return an empty name field.

      Webpage content:
      ---
      #{text}
      ---
    PROMPT
  end

  def raw_text_prompt(text)
    <<~PROMPT
      Extract recipe information from the following text, which may be a social media caption or website content.
      Parse the recipe name, ingredients list, and cooking instructions.

      For the recipe name: ignore any social media titles, series names, hashtags, or catchphrases.
      Instead, create a short, descriptive name based on the actual dish being made.

      #{ingredient_field_instructions}

      Always respond in the same language as the recipe content. If the recipe is in German, return German. If Spanish, return Spanish. And so on. Do not translate ingredient names.

      If the text does not contain a valid recipe, return an empty name field.

      Recipe text:
      ---
      #{text}
      ---
    PROMPT
  end

  def ingredient_field_instructions
    <<~INGR.strip
      For each ingredient, return structured fields:
      - `raw`: the original ingredient line, echoed verbatim. Required.
      - `name`: the food name only (no amount or unit). Do not translate.
      - `amount`: numeric quantity. Convert fractions to decimals (1/2 -> 0.5; unicode fractions accepted).
      - `amount_max`: upper bound for ranges (e.g. "2-3 cloves" -> amount=2, amount_max=3). Accept en-dash, em-dash, '-', '~', 'to', 'bis'.
      - `unit`: unit of measurement, lowercased best-effort (g, ml, tbsp, el, prise...). Open vocabulary.
      - `note`: qualifier ("chopped", "to taste", "optional"). Optional.
    INGR
  end
end
