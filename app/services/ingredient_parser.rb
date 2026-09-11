require "ruby_llm/schema"

# Parses an array of free-form ingredient strings into structured hashes.
# Single batched LLM call; aligns output to inputs by the echoed `raw` field.
class IngredientParser
  MODEL = "google/gemini-3.5-flash-lite"
  OPENROUTER_PROVIDER = "google-vertex/eu"

  class IngredientListSchema < RubyLLM::Schema
    array :ingredients, of: Llm::IngredientSchema, description: "Parsed ingredient list. Echo each input line verbatim into 'raw'."
  end

  def self.call(strings, language: nil)
    new(strings, language: language).call
  end

  def initialize(strings, language: nil)
    @strings = Array(strings).map { |s| s.to_s.strip }.reject(&:blank?)
    @language = language
  end

  def call
    return [] if @strings.empty?

    response = call_llm
    align(response.content["ingredients"])
  rescue Faraday::TimeoutError, Faraday::ConnectionFailed => e
    Rails.logger.warn "[IngredientParser] timeout: #{e.message} — falling back"
    fallback
  rescue RubyLLM::Error => e
    Rails.logger.warn "[IngredientParser] LLM error: #{e.message} — falling back"
    fallback
  rescue StandardError => e
    Rails.logger.warn "[IngredientParser] unexpected: #{e.class}: #{e.message} — falling back"
    fallback
  end

  private

  def call_llm
    chat = RubyLLM.chat(model: MODEL, provider: :openrouter, assume_model_exists: true)
    chat.with_params(provider: { only: [ OPENROUTER_PROVIDER ] })
    chat.with_schema(IngredientListSchema).ask(prompt)
  end

  def prompt
    lang_hint = @language.present? ? "\nThe ingredients are in #{@language}. Keep the names in that language." : ""
    <<~PROMPT
      Parse the following ingredient lines into structured fields.

      For each line, return:
      - `raw`: the original line, echoed verbatim. This is required and is used to align output to input.
      - `name`: the food name only (no amount or unit). Do not translate.
      - `amount`: numeric quantity. Convert fractions (1/2 -> 0.5). Unicode fractions accepted.
      - `amount_max`: upper bound for ranges (e.g. "2-3 cloves" -> amount=2, amount_max=3). Accept en-dash, em-dash, '-', '~', 'to', 'bis'.
      - `unit`: unit lowercased best-effort. Open vocabulary.
      - `note`: qualifier ("chopped", "to taste", "optional").

      Return one entry per input line, in the same order.#{lang_hint}

      Lines:
      ---
      #{@strings.join("\n")}
      ---
    PROMPT
  end

  def align(parsed)
    parsed_by_raw = {}
    Array(parsed).each do |entry|
      next unless entry.is_a?(Hash)
      raw = entry["raw"].to_s.strip
      next if raw.blank?
      parsed_by_raw[raw] ||= entry
    end

    @strings.map do |raw|
      hit = parsed_by_raw[raw]
      hit ? coerce(hit, raw) : { name: raw, raw: raw }
    end
  end

  def coerce(entry, raw)
    name = entry["name"].to_s.strip.presence || raw
    {
      name: name,
      amount: entry["amount"],
      amount_max: entry["amount_max"],
      unit: entry["unit"].to_s.strip.presence,
      note: entry["note"].to_s.strip.presence,
      raw: raw
    }
  end

  def fallback
    @strings.map { |raw| { name: raw, raw: raw } }
  end
end
