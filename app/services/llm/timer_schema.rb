require "schematist"

module Llm
  class TimerSchema < Schematist::Schema
    integer :step, description: "Number of the step the timer appears in, as given in the input."
    string :text, description: "The duration copied verbatim from the step: only the number(s) and unit, e.g. '10–12 minutes'."
    integer :min_seconds, description: "Shortest duration in seconds."
    integer :max_seconds, description: "Longest duration in seconds; equal to min_seconds for a single value."
    string :label, description: "1-3 word imperative in the step's language naming what the cook waits for, e.g. 'Simmer', 'Köcheln lassen'."
    boolean :per_side, description: "True when the time applies to each side or piece in turn."
  end
end
