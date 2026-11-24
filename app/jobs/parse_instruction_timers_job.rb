class ParseInstructionTimersJob < ApplicationJob
  queue_as :default
  # An import saves the recipe several times; run one parse at a time per
  # recipe so the duplicates find it already parsed instead of paying again.
  limits_concurrency to: 1, key: ->(recipe_id) { recipe_id }

  def perform(recipe_id)
    recipe = Recipe.find_by(id: recipe_id)
    return unless recipe&.needs_timers?

    steps = recipe.instructions
    timers = InstructionTimerParser.call(steps)
    return unless timers

    recipe.with_lock do
      # The steps were edited while the LLM ran; that edit queued its own parse.
      next unless recipe.instructions == steps

      recipe.update!(instruction_timers: timers, timers_version: Llm::TimerInstructions::VERSION)
    end
  end
end
