module Llm
  # What counts as a cooking timer and how to describe one. Bump VERSION when
  # the prompt or the stored timer shape changes, then run
  # `bin/rails timers:enqueue_parsing` to reparse existing recipes.
  module TimerInstructions
    VERSION = 1

    MAX_SECONDS = 24.hours.to_i
    MAX_LABEL_LENGTH = 40

    module_function

    def prompt
      <<~INSTRUCTIONS.strip
        Find the cooking timers in recipe steps. A timer is a span of time in a step that tells the cook how long an action takes or how long to wait, so they could start a countdown for it right then: "simmer for 10 minutes", "bake 25–30 minutes", "rest 15 minutes", "chill for 1 hour", "3 minutes per side", "about 5 minutes", "at least 30 minutes", "another 5 minutes", "eine halbe Stunde", "pół godziny", "soak 12 hours".

        For each timer return:
        - `step`: the number of the step it appears in.
        - `text`: the duration exactly as written in the step, copied character for character (same digits, dashes, spaces, accents, and case). Cover the number(s) and the unit and any words between them, for example "10–12 minutes", "1 1/2 hours", "1 hour 10 minutes", "5 to 10 more minutes", "an hour", "eine halbe Stunde". Leave out words around it such as "about", "for", "ca.", "ok.", "per side", or "each side".
        - `min_seconds` and `max_seconds`: the duration in seconds. A range like "6–8 minutes" is one timer with min 360 and max 480. A single value has min equal to max. A compound value like "1 hour 10 minutes" is one timer (4200). Fractions and number words count ("1 ½ hours" = 5400, "half an hour" = 1800, "two minutes" = 120). "At least 30 minutes" is 1800.
        - `label`: a 1 to 3 word imperative (never more than 3 words) naming what the cook is waiting for, written in the same language as the step: "Simmer", "Bake", "Rest the dough", "Sear each side", "Köcheln lassen", "Backen", "Dusić", "Piec". Never translate the label into another language.
        - `per_side`: true when the time applies to each side or each piece in turn ("3 minutes per side", "2 Minuten von jeder Seite", "po 3 minuty z każdej strony"), otherwise false.

        These are NOT timers. Leave them out:
        - Vague amounts: "a few minutes", "several minutes", "a couple of minutes", "ein paar Minuten", "kilka minut", "quelques minutes", "un par de minutos".
        - Overnight or day-scale waits: "overnight", "über Nacht", "przez noc", "the next day", and anything whose shortest time is 24 hours or more, or is in days or weeks. "Rise for 20-24 hours" is still a timer.
        - Maximums, storage, and make-ahead times: "up to 24 hours", "for up to 3 days", "keeps for 2 weeks", "can be made 3 days ahead", "no more than 48 hours", "bis zu 2 Stunden".
        - Frequencies: "every 5 minutes", "alle 10 Minuten", "co 5 minut".
        - References to a time that is already running or is described elsewhere: "after 5 minutes, stir", "Nach 10 Minuten den Deckel abnehmen", "Po 20 minutach pieczenia zmniejszyć temperaturę", "during the last 10 minutes", "2 minutes before the pasta is done", "at the 26-minute mark", "after these 10 minutes", "add 1-2 minutes to the cooking time", "one hour before baking". The cook does not start a new countdown there.
        - Statements about how long something needs in general rather than an instruction to wait: "the large loaf will need the full 40-45 minutes in the oven".
        - The fallback in an alternative: in "15 minutes or up to 2 hours" or "at least 30 minutes, preferably an hour", only the first value is a timer.
        - Temperatures, sizes, quantities, page numbers, times of day, and dates.

        When one step gives alternatives for different pans or sizes ("34 minutes for a 9x13 pan, 28 to 30 minutes for 9-inch layers"), each alternative is a timer. When the same duration appears twice in a step for two separate waits, return it twice, in order. List timers in the order they appear in each step. Return an empty list when no step has a timer.
      INSTRUCTIONS
    end
  end
end
