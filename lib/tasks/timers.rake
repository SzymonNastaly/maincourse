namespace :timers do
  desc "Enqueue timer parsing for recipes whose instruction timers are missing or on an old version"
  task enqueue_parsing: :environment do
    recipe_ids = Recipe.needing_timers.pluck(:id)

    recipe_ids.each { |recipe_id| ParseInstructionTimersJob.perform_later(recipe_id) }

    puts "Enqueued timer parsing for #{recipe_ids.size} recipes."
  end
end
