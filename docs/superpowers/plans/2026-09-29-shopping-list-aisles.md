# Shopping List Aisles Implementation Plan (iOS first)

> Supersedes the aisle parts of [phase 2](2026-09-16-shopping-list-02-enrichment-aisles.md).
> That plan also added quantity metadata (amounts, units, notes) that only
> [phase 3 aggregation](2026-09-16-shopping-list-03-aggregation.md) needs; those
> columns are deferred until aggregation is picked up.

**Goal:** Group the iOS "To Buy" list into aisle sections, for recipe additions and
manually typed items alike, at negligible running cost. Web and Android keep working
unchanged and get the aisle UI later (tracked in GitHub issues).

**Architecture:** Shopping list items gain `category`, `canonical_name`, and
`enrichment_version`. The server categorizes each item on save with the cheapest
source that works, and confirms incomplete rows with the existing `IngredientParser`
in a background job. iOS shows categories immediately from recipe data and a local
dictionary, then adopts the server's values on sync.

## Decisions

1. Keep the 10 existing categories (`Llm::IngredientInstructions::CATEGORIES`) in
   their current order. Unknown or missing categories display as Other.
2. Fixed aisle order for v1. No per-user correction or custom order yet.
3. Only three new columns. Quantity metadata waits for aggregation.
4. Categorization lives in `ShoppingListItem` callbacks so every write path (API
   upserts, web manual add, web recipe add) is covered without controller changes.

## Categorization order (server)

Runs before save when an item is new or its `name`/`details` change. An unchanged
replay keeps existing values.

1. **Source recipe match (complete, no LLM).** If `source_recipe` has an enriched
   ingredient whose `name` equals the item name case-insensitively, copy its
   `category`, `canonical_name`, and `enrichment_version`. Recipe additions keep the
   parsed ingredient name on every client, so this covers them, including old apps.
2. **Client hint (provisional).** A valid `category` sent by the client is kept.
3. **Global lookup (provisional).** The most common category/canonical name among
   current-version `Ingredient` rows with the same lowercased name.
4. **LLM confirmation (background).** Rows still lacking a current
   `enrichment_version` enqueue `EnrichShoppingListItemsJob` after commit. The job
   parses `"#{details} #{name}"` outside a transaction, then locks each row and
   applies the result only if the row still exists, its name/details are unchanged,
   and it still needs enrichment. Parser fallbacks leave the provisional category and
   retry (3 attempts total); exhausted retries are logged, not raised.

`category_pending` in API responses is `needs_enrichment?`.

## Task 1: Rails

- Migration: nullable `category`, `canonical_name` (strings), `enrichment_version`
  (integer) on `shopping_list_items`; expression index on `lower(name)` for
  `ingredients` to keep the lookup cheap.
- `ShoppingListItem`: validations matching `Ingredient`, `needs_enrichment?`,
  `enrichment_input`, categorize callback, after-commit enqueue.
- `ShoppingList::CategoryLookup.call(name)` for the global lookup.
- `EnrichShoppingListItemsJob`.
- `ShoppingList::Payload` permits `category` and `canonical_name` hints;
  `UpsertItems` passes them through.
- `ShoppingListItemSerializer` adds `category`, `canonical_name`, `category_pending`.
- `bin/rails shopping_list:enqueue_enrichment` queues incomplete unchecked rows in
  batches of 50.
- Tests: model callback order, replay preservation, lookup, job races/retries,
  serializer, payload hints, rake task. Docs: new `docs/shopping-list.md`, update
  `docs/ingredients.md`.

## Task 2: iOS data

- `ShoppingListItemResponse`: optional `category`, `canonicalName`,
  `categoryPending` (`decodeIfPresent`, default false).
- `ShoppingListItemCreate`: optional `category`, `canonicalName` hints.
- `PersistedShoppingListItem`: optional `category`, `canonicalName` via a new
  SwiftData schema version with a lightweight migration stage.
- `ShoppingListDraftItem(ingredient:scale:)` carries the ingredient's category and
  canonical name through to the local item and create payload.
- Manual adds: look up the lowercased name in a dictionary built from cached
  recipes' structured ingredients; a hit sets the local category and is sent as a
  hint. Misses start in Other.
- `update(from:)` merges server metadata without touching pending check state.

## Task 3: iOS UI

- `ShoppingCategory: String, CaseIterable` in server order with localized titles
  (en/de/pl); `init(serverValue:)` falls back to `.other`.
- `ShoppingListSectionsContent` renders one header per nonempty aisle under To Buy,
  keeping the existing within-list order. Already Got stays one section. The recipe
  review sheet stays flat.
- Refresh on appear and pull-to-refresh as today. Add bounded polling only if items
  visibly linger in Other.

## Verification and rollout

- `bin/ci > tmp/ci.log 2>&1`, `bin/ios-build`, `bin/ios-test`.
- Simulator: recipe addition lands in the right aisles; offline manual "Milch" lands
  in Dairy & eggs; an unknown item moves from Other after refresh.
- Deploy backend first, run `bin/rails shopping_list:enqueue_enrichment`, then ship iOS.

## Later

- Web aisle sections and Android aisle sections (GitHub issues).
- v2: user correction of an item's aisle, custom aisle order, finer categories.
- Phase 3 quantity aggregation, which adds the quantity columns.
