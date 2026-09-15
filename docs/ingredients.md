# Recipe Ingredients

Recipe ingredients are stored as their own ActiveRecord model rather than a JSON column on `recipes`. This document explains the data shape, the parsing pipeline, and the contract with API clients.

## Data model

`Ingredient` rows belong to a `Recipe` via `has_many :ingredients, -> { order(:position) }, dependent: :destroy`. Columns:

- `recipe_id` — owner
- `position` — 0-based ordinal within the recipe; the default scope orders by it
- `raw` — the original free-form line as the user entered it (e.g. "2 cups flour, sifted"). **Required.** This is the source of truth and the value displayed in lists/views and returned in the iOS API contract.
- `name` — the food itself, no amount/unit (e.g. "flour"). **Required.** Falls back to `raw` until the parser runs.
- `amount`, `amount_max` — numeric quantity; `amount_max` is the upper bound for ranges ("2-3 cloves").
- `unit` — best-effort lowercased unit string (open vocabulary)
- `note` — qualifier ("chopped", "to taste", "optional")

`Ingredient#parsed?` returns true when `amount` or `unit` is present — the parser's signal that it touched the row.

## Replacing ingredients

Don't assign to the association directly with strings. Use the helpers on `Recipe`:

- `replace_ingredients_from_strings(strings)` — wipes existing rows and creates fresh ones with `name=raw=line`. Use this from controllers and after string-shaped extractor output.
- `replace_ingredients_from_hashes(entries)` — same shape but supports already-structured hashes (with `:amount`, `:unit`, etc.) coming from extractors.
- `apply_extracted_attributes!(attrs)` — used by import/extract jobs: pops `:ingredients` from the hash, then `update!`s the rest.

After replacing strings, enqueue `ParseRecipeIngredientsJob.perform_later(recipe.id)` to run the structured parse asynchronously.

## Parsing pipeline

Free-form strings → structured fields runs through `IngredientParser`:

1. `IngredientParser.call(strings)` makes a single batched LLM call (`Llm::IngredientSchema` × N) and returns an array of hashes aligned to the input order via the echoed `raw` field.
2. The parser is resilient: timeouts, RubyLLM errors, and unexpected exceptions all fall back to `{ name: raw, raw: raw }` so callers always get one entry per input.
3. `ParseRecipeIngredientsJob` calls the parser for any rows where `parsed?` is false, then `update!`s `name`, `amount`, `amount_max`, `unit`, `note` on the existing `Ingredient` rows. It does not change `raw` or `position`.

Extractors (`JsonLdExtractor`, `LlmExtractor`, `RecipeImageLlmService`, `YoutubeVideoExtractor`) populate ingredient hashes during extraction. Import jobs call `apply_extracted_attributes!` and then enqueue `ParseRecipeIngredientsJob` so structured fields land even when the upstream source only has raw lines.

## API contract (iOS)

The `_recipe.json.jbuilder` partial preserves backwards compatibility:

- `ingredients` — array of strings (the `raw` lines, in `position` order). iOS clients depend on this.
- `structured_ingredients` — array of objects with `raw`, `name`, `amount`, `amount_max`, `unit`, `note`. New clients can use this for richer rendering (e.g. shopping list aggregation).

`PATCH /api/v1/recipes/:id` accepts `ingredients: [string, string, ...]`. The controller calls `replace_ingredients_from_strings` and enqueues a parse job, so structured fields fill in over the next seconds.

## Adding ingredients to the shopping list

Web, iOS, and Android review the selected recipe ingredients before adding them.
If any item in the active cookbook was created more than 36 hours ago, the
review flow asks whether to keep the current list or start fresh. Age is based
on `ShoppingListItem#created_at`, not its last update or check-off time, and is
evaluated when the user presses Add so an open review can cross the cutoff.

`POST /api/v1/shopping_list_items` accepts `clear_existing: true` alongside the
normal `items` payload. `ShoppingList::UpsertItems` clears the active cookbook
and creates the submitted rows in one transaction, so invalid replacement rows
leave the previous list intact. Native clients refresh their local shopping-list
cache while loading a recipe and use the cached `created_at` values as an
offline fallback for deciding whether to show the prompt. Add remains disabled
until that refresh attempt has either succeeded or failed. iOS serializes list
network work and replaces its SwiftData cache transactionally after the server
acknowledges a destructive replacement.
