require "test_helper"

class RecipeTimersTest < ActiveSupport::TestCase
  TIMER = { "text" => "10 minutes", "min_seconds" => 600, "max_seconds" => 600, "label" => "Boil", "per_side" => false }.freeze

  setup do
    @recipe = recipes(:one)
    @recipe.update_columns(
      instructions: [ "Boil for 10 minutes.", "Drain." ],
      instruction_timers: [ [ TIMER ], [] ],
      timers_version: Llm::TimerInstructions::VERSION
    )
  end

  test "creating a recipe with steps queues a timer parse" do
    assert_enqueued_with(job: ParseInstructionTimersJob) do
      @recipe.cookbook.recipes.create!(name: "Soup", instructions: [ "Simmer 20 minutes." ])
    end
  end

  test "creating a recipe without steps does not queue a timer parse" do
    assert_no_enqueued_jobs(only: ParseInstructionTimersJob) do
      @recipe.cookbook.recipes.create!(name: "Importing...", import_status: :pending)
    end
  end

  test "changing the steps clears their timers at once and queues a parse" do
    assert_enqueued_with(job: ParseInstructionTimersJob, args: [ @recipe.id ]) do
      @recipe.update!(instructions: [ "Boil for 12 minutes." ])
    end

    assert_equal [], @recipe.reload.instruction_timers
    assert_nil @recipe.timers_version
    assert_equal [ [] ], @recipe.timers_by_step
  end

  test "saving other fields or the same steps keeps parsed timers and queues nothing" do
    assert_no_enqueued_jobs(only: ParseInstructionTimersJob) do
      @recipe.update!(name: "Renamed", instructions: [ "Boil for 10 minutes.", "Drain." ])
    end

    assert_equal [ [ TIMER ], [] ], @recipe.reload.timers_by_step
  end

  test "saving a recipe whose timers were never parsed queues a parse" do
    @recipe.update_columns(instruction_timers: [], timers_version: nil)

    assert_enqueued_with(job: ParseInstructionTimersJob) do
      @recipe.update!(favorite: true)
    end
  end

  test "timers by step pads to one list per step" do
    @recipe.update_columns(instruction_timers: [])

    assert_equal [ [], [] ], @recipe.reload.timers_by_step
  end
end
