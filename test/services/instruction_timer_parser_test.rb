require "test_helper"

class InstructionTimerParserTest < ActiveSupport::TestCase
  test "returns one empty list per step without calling the LLM when steps are blank" do
    assert_equal [ [], [] ], InstructionTimerParser.call([ "", " " ])
    assert_not_requested(:post, LlmStubHelper::OPENROUTER_ENDPOINT)
  end

  test "aligns timers to their steps" do
    stub_timers([
      timer(step: 1, text: "10–12 minutes", min: 600, max: 720, label: "Simmer"),
      timer(step: 3, text: "3 minutes", min: 180, max: 180, label: "Sear each side", per_side: true)
    ])

    result = InstructionTimerParser.call([
      "Simmer for 10–12 minutes.",
      "Season to taste.",
      "Sear 3 minutes per side."
    ])

    assert_equal [
      [ { "text" => "10–12 minutes", "min_seconds" => 600, "max_seconds" => 720, "label" => "Simmer", "per_side" => false } ],
      [],
      [ { "text" => "3 minutes", "min_seconds" => 180, "max_seconds" => 180, "label" => "Sear each side", "per_side" => true } ]
    ], result
  end

  test "drops timers whose text is not in the step, whose seconds are not positive, or that exceed a day" do
    stub_timers([
      timer(step: 1, text: "15 minutes", min: 900, max: 900),
      timer(step: 1, text: "0 minutes", min: 0, max: 0),
      timer(step: 1, text: "2 days", min: 86_400, max: 172_800),
      timer(step: 2, text: "5 minutes", min: 300, max: 300)
    ])

    result = InstructionTimerParser.call([ "Bake 10 minutes, rest 0 minutes, age 2 days.", "Done." ])

    assert_equal [ [], [] ], result
  end

  test "keeps a whole-day timer and repairs dash and space variants to the step's own text" do
    stub_timers([
      timer(step: 1, text: "20-24 hours", min: 72_000, max: 86_400),
      timer(step: 1, text: "10 minutes", min: 600, max: 600)
    ])

    result = InstructionTimerParser.call([ "Prove for 20–24 hours, then knead for 10 minutes." ]).sole

    assert_equal [ "20–24 hours", "10 minutes" ], result.map { |t| t["text"] }
  end

  test "orders timers by appearance and gives repeated text separate occurrences" do
    stub_timers([
      timer(step: 1, text: "10 minutes", min: 600, max: 600, label: "Rest"),
      timer(step: 1, text: "1 hour 10 minutes", min: 4200, max: 4200, label: "Bake"),
      timer(step: 1, text: "10 minutes", min: 600, max: 600, label: "Cool"),
      timer(step: 1, text: "10 minutes", min: 600, max: 600, label: "Extra")
    ])

    result = InstructionTimerParser.call([ "Bake 1 hour 10 minutes, rest 10 minutes, cool 10 minutes." ]).sole

    assert_equal [ "1 hour 10 minutes", "10 minutes", "10 minutes" ], result.map { |t| t["text"] }
    assert_equal [ "Bake", "Rest", "Cool" ], result.map { |t| t["label"] }
  end

  test "swaps a reversed range" do
    stub_timers([ timer(step: 1, text: "6-8 minutes", min: 480, max: 360) ])

    result = InstructionTimerParser.call([ "Cook 6-8 minutes." ]).sole.sole

    assert_equal [ 360, 480 ], result.values_at("min_seconds", "max_seconds")
  end

  test "sends the shared timer instructions and numbered steps" do
    stub_timers([])

    InstructionTimerParser.call([ "Boil pasta.", "Drain." ])

    assert_requested(:post, LlmStubHelper::OPENROUTER_ENDPOINT) do |req|
      content = JSON.parse(req.body).dig("messages", -1, "content")
      content.include?(Llm::TimerInstructions.prompt) && content.include?("1. Boil pasta.\n2. Drain.")
    end
  end

  test "returns nil on timeout so the caller can retry" do
    stub_request(:post, LlmStubHelper::OPENROUTER_ENDPOINT).to_raise(Faraday::TimeoutError.new("timeout"))

    assert_nil InstructionTimerParser.call([ "Simmer 10 minutes." ])
  end

  test "returns nil on API error" do
    stub_request(:post, LlmStubHelper::OPENROUTER_ENDPOINT).to_return(status: 500, body: "{}")

    assert_nil InstructionTimerParser.call([ "Simmer 10 minutes." ])
  end

  private

  def timer(step:, text:, min:, max:, label: "Cook", per_side: false)
    { "step" => step, "text" => text, "min_seconds" => min, "max_seconds" => max, "label" => label, "per_side" => per_side }
  end

  def stub_timers(timers)
    stub_openrouter_api(response_body: build_openrouter_response({ "timers" => timers }))
  end
end
