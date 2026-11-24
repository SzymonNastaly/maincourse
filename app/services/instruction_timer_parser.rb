require "schematist"

# Finds the cooking timers in a recipe's steps with one batched LLM call.
# Returns one array of timers per step, aligned to the input, or nil when the
# LLM call fails so the caller can leave the recipe unparsed and retry later.
class InstructionTimerParser
  class TimerListSchema < Schematist::Schema
    array :timers, of: Llm::TimerSchema, description: "Every timer found in the steps, in order of appearance. Empty when there are none."
  end

  def self.call(steps)
    new(steps).call
  end

  def initialize(steps)
    @steps = Array(steps).map(&:to_s)
  end

  def call
    return @steps.map { [] } if @steps.all?(&:blank?)

    response = call_llm
    by_step = Array(response.parsed&.fetch("timers", nil)).select { |entry| entry.is_a?(Hash) }.group_by { |entry| entry["step"] }
    @steps.each_with_index.map { |step, index| validate(step, by_step.fetch(index + 1, [])) }
  rescue Faraday::TimeoutError, Faraday::ConnectionFailed => e
    Rails.logger.warn "[InstructionTimerParser] timeout: #{e.message}"
    nil
  rescue RubyLLM::Error => e
    Rails.logger.warn "[InstructionTimerParser] LLM error: #{e.message}"
    nil
  rescue StandardError => e
    Rails.logger.warn "[InstructionTimerParser] unexpected: #{e.class}: #{e.message}"
    nil
  end

  private

  def call_llm
    Llm::Chat.build.with_schema(TimerListSchema).ask(prompt)
  end

  def prompt
    numbered = @steps.each_with_index.map { |step, index| "#{index + 1}. #{step.gsub(/\s*\n\s*/, " ")}" }
    <<~PROMPT
      #{Llm::TimerInstructions.prompt}

      Steps:
      ---
      #{numbered.join("\n")}
      ---
    PROMPT
  end

  # Keeps timers whose text is really in the step, giving each its own
  # non-overlapping occurrence (longest first, so "1 hour 10 minutes" wins over
  # the "10 minutes" inside it), and returns them in order of appearance.
  # Whitespace and dash variants are tolerated, but the stored text is always
  # the step's own substring.
  def validate(step, entries)
    claimed = []
    entries.filter_map { |entry| coerce(entry) }
      .sort_by.with_index { |timer, index| [ -timer["text"].length, index ] }
      .each do |timer|
        match = free_occurrence(step, timer["text"], claimed)
        claimed << timer.merge("text" => match[0], "start" => match.begin(0)) if match
      end

    claimed.sort_by { |timer| timer["start"] }.map { |timer| timer.except("start") }
  end

  def free_occurrence(step, text, claimed)
    pattern = Regexp.new(text.scan(/[[:space:]]+|[-–—]|[^[:space:]\-–—]+/).map { |part|
      if part.match?(/\A[[:space:]]/) then "[[:space:]]+"
      elsif part.match?(/\A[-–—]\z/) then "[-–—]"
      else Regexp.escape(part)
      end
    }.join)
    offset = 0
    while (match = pattern.match(step, offset))
      return match if claimed.none? { |other| other["start"] < match.end(0) && match.begin(0) < other["start"] + other["text"].length }
      offset = match.begin(0) + 1
    end
  end

  def coerce(entry)
    text = entry["text"].to_s.strip
    min, max = [ entry["min_seconds"], entry["max_seconds"] ].map { |value| value.is_a?(Numeric) ? value.round : nil }
    return nil if text.empty? || min.nil? || max.nil?

    min, max = [ min, max ].minmax
    return nil unless min.positive? && max <= Llm::TimerInstructions::MAX_SECONDS

    {
      "text" => text,
      "min_seconds" => min,
      "max_seconds" => max,
      "label" => entry["label"].to_s.squish.truncate(Llm::TimerInstructions::MAX_LABEL_LENGTH, omission: "").presence,
      "per_side" => entry["per_side"] == true
    }
  end
end
