require "test_helper"

class ParseInstructionTimersJobTest < ActiveSupport::TestCase
  setup do
    @recipe = recipes(:one)
    @recipe.update_columns(instructions: [ "Boil pasta for 10 minutes.", "Drain." ], instruction_timers: [], timers_version: nil)
  end

  test "stores timers aligned to the steps and marks the current version" do
    stub_timers([ { "step" => 1, "text" => "10 minutes", "min_seconds" => 600, "max_seconds" => 600, "label" => "Boil", "per_side" => false } ])

    ParseInstructionTimersJob.perform_now(@recipe.id)

    @recipe.reload
    assert_equal [ [ { "text" => "10 minutes", "min_seconds" => 600, "max_seconds" => 600, "label" => "Boil", "per_side" => false } ], [] ], @recipe.instruction_timers
    assert_equal Llm::TimerInstructions::VERSION, @recipe.timers_version
    assert_not @recipe.needs_timers?
  end

  test "leaves a failed parse unversioned for a retry" do
    stub_request(:post, LlmStubHelper::OPENROUTER_ENDPOINT).to_raise(Faraday::TimeoutError.new("timeout"))

    ParseInstructionTimersJob.perform_now(@recipe.id)

    assert_nil @recipe.reload.timers_version
    assert @recipe.needs_timers?
  end

  test "discards the result when the steps changed while the LLM ran" do
    stub_request(:post, LlmStubHelper::OPENROUTER_ENDPOINT).to_return do
      Recipe.where(id: @recipe.id).update_all(instructions: [ "Something else." ].to_json)
      { status: 200, headers: { "Content-Type" => "application/json" }, body: build_openrouter_response({ "timers" => [] }).to_json }
    end

    ParseInstructionTimersJob.perform_now(@recipe.id)

    assert_nil @recipe.reload.timers_version
  end

  test "skips recipes already parsed with the current version" do
    @recipe.update_columns(timers_version: Llm::TimerInstructions::VERSION)

    ParseInstructionTimersJob.perform_now(@recipe.id)

    assert_not_requested(:post, LlmStubHelper::OPENROUTER_ENDPOINT)
  end

  test "skips recipes without steps and missing recipes" do
    @recipe.update_columns(instructions: [])

    ParseInstructionTimersJob.perform_now(@recipe.id)
    ParseInstructionTimersJob.perform_now(0)

    assert_not_requested(:post, LlmStubHelper::OPENROUTER_ENDPOINT)
  end

  private

  def stub_timers(timers)
    stub_openrouter_api(response_body: build_openrouter_response({ "timers" => timers }))
  end
end
