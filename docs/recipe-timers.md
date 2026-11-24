# Recipe Timers

A recipe step such as "Simmer for 10–12 minutes" carries a timer the cook can start from the step. An LLM pass finds those timers after the steps are saved, the recipe stores them next to `instructions`, and the API returns them so native clients can highlight the text and start a timer from it.

## Data model

Two columns on `recipes`:

- `instruction_timers` — JSON array aligned to `instructions`: one array of timers per step. It is `[]` until the steps have been parsed.
- `timers_version` — the `Llm::TimerInstructions::VERSION` that produced the timers, or `nil` when they are missing or stale.

Each timer is an object:

| Field | Meaning |
| --- | --- |
| `text` | The duration exactly as it appears in the step, e.g. `"10–12 minutes"`, `"eine halbe Stunde"`. Only the number(s) and unit, without "about", "for" or "per side". |
| `min_seconds`, `max_seconds` | The duration in seconds. Equal for a single value; a range keeps both ends. |
| `label` | A 1–3 word imperative in the recipe's own language naming what the cook waits for: "Simmer", "Sear each side", "Köcheln lassen", "Dusić". May be `null`. |
| `per_side` | `true` when the time applies to each side or piece in turn ("3 minutes per side"). |

`Recipe#needs_timers?` is true when a recipe has steps and its `timers_version` is not the current one. `Recipe#timers_by_step` always returns one array per step, padding with empty arrays.

## What counts as a timer

A timer is an active wait the cook would time: simmer 10 min, bake 25–30 min, rest 15 min, chill 1 hour, 3 minutes per side. The prompt in `Llm::TimerInstructions` excludes vague amounts ("a few minutes"), overnight and day-scale waits, maximums and storage times ("up to 3 days", "keeps for 2 weeks"), frequencies ("every 5 minutes"), references to a time already running ("after 5 minutes, stir", "Nach 10 Minuten den Deckel abnehmen"), temperatures, and other numbers. In "15 minutes or up to 2 hours" only the first value is a timer.

## Pipeline

1. `Recipe` clears `instruction_timers` and `timers_version` in `before_save` whenever `instructions` change, so a client never highlights text that is no longer in the step.
2. An `after_commit` enqueues `ParseInstructionTimersJob` whenever the saved recipe `needs_timers?`. Imports, extractions, web and API edits, sample-recipe saves and admin edits all go through this one callback. The condition is the recipe's state rather than "instructions just changed", so a recipe saved twice in one transaction, or one an earlier parse failed on, is still picked up. Saving a recipe whose timers are current enqueues nothing.
3. `ParseInstructionTimersJob` calls `InstructionTimerParser` and, if the steps did not change while the LLM ran, stores the result with the current version (bumping `updated_at`, so syncing clients receive it). Solid Queue runs one parse per recipe at a time, so the duplicate jobs an import's several saves enqueue find the recipe already parsed.
4. `InstructionTimerParser` sends every step of the recipe in one structured-output call (`Llm::Chat`, `Llm::TimerSchema`). It then validates each timer: it drops timers whose text is not in their step, whose seconds are not positive, or whose maximum exceeds 24 hours. It gives each timer its own non-overlapping occurrence of its text (longest first, so "1 hour 10 minutes" wins over the "10 minutes" inside it) and returns the timers in order of appearance. Whitespace and dash variants are tolerated, but the stored `text` is always the step's own substring.
5. Timeouts and LLM errors return `nil`; the job then leaves `timers_version` unset so the next save or backfill retries. Nothing raises into imports.

Full-recipe LLM extraction does not produce timers; the follow-up job handles every source the same way. A parse costs one Haiku call of about 2,000 prompt tokens plus the steps, and a few hundred output tokens.

## API contract

`Api::V1::RecipesController#recipe_detail_json` (detail and batch) returns:

- `instructions` — unchanged array of step strings.
- `instruction_timers` — always an array with one array per step, empty arrays until parsed.

```json
{
  "instructions": ["Simmer the sauce for 10–12 minutes.", "Sear the steaks 3 minutes per side."],
  "instruction_timers": [
    [{ "text": "10–12 minutes", "min_seconds": 600, "max_seconds": 720, "label": "Simmer", "per_side": false }],
    [{ "text": "3 minutes", "min_seconds": 180, "max_seconds": 180, "label": "Sear each side", "per_side": true }]
  ]
}
```

The API returns no character offsets. Clients find each timer by searching its step for `text`, starting where the previous timer's match ended. That keeps repeated durations in one step ("bake 25 minutes … bake another 25 minutes") on separate occurrences. Clients should skip a timer whose text they cannot find.

After a `PATCH` that changes `instructions`, the response carries empty timer arrays; the parsed timers arrive with a later `updated_at`.

## Backfill

After deploying, or after bumping `Llm::TimerInstructions::VERSION` because the prompt or timer shape changed, enqueue every recipe whose timers are missing or outdated:

```bash
bin/rails timers:enqueue_parsing
```

Recipes are also reparsed lazily the next time they are saved.
