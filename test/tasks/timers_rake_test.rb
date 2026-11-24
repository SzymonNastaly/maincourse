require "test_helper"
require "rake"

Rake::Task.define_task(:environment) unless Rake::Task.task_defined?(:environment)
load Rails.root.join("lib/tasks/timers.rake")

class TimersRakeTest < ActiveSupport::TestCase
  setup do
    Rake::Task["timers:enqueue_parsing"].reenable
    Recipe.update_all(instructions: [].to_json, timers_version: nil)
  end

  test "queues recipes with steps whose timers are missing or outdated" do
    missing = recipes(:one)
    outdated = recipes(:two)
    current = recipes(:three)
    missing.update_columns(instructions: [ "Boil 10 minutes." ])
    outdated.update_columns(instructions: [ "Bake 20 minutes." ], timers_version: Llm::TimerInstructions::VERSION - 1)
    current.update_columns(instructions: [ "Rest 5 minutes." ], timers_version: Llm::TimerInstructions::VERSION)

    assert_enqueued_jobs 2, only: ParseInstructionTimersJob do
      assert_output(/Enqueued timer parsing for 2 recipes/) { Rake::Task["timers:enqueue_parsing"].invoke }
    end

    queued = enqueued_jobs.select { |entry| entry[:job] == ParseInstructionTimersJob }.map { |entry| entry[:args].first }
    assert_equal [ missing.id, outdated.id ].sort, queued.sort
  end
end
