# Shopping List Improvements: Design and Execution Map

## Goal

Make recipe review skip common staples by default, categorize all shopping-list additions across languages, and display compatible quantities as one summed item.

This design builds on commits `6410565` and `618a9a9`. The user approved the direction in the conversation; the concrete choices below are proposed implementation defaults for review. No application changes have been made while preparing these documents.

## Delivery sequence

1. [Staple review defaults](../plans/2026-09-16-shopping-list-01-staples.md): Rails policy and web/iOS/Android review behavior. Independently releasable.
2. [Shopping enrichment and aisles](../plans/2026-09-16-shopping-list-02-enrichment-aisles.md): structured shopping metadata, asynchronous manual-entry enrichment, metadata propagation, cache updates, and aisle sections. Depends on phase 1 for the shared policy file.
3. [Quantity aggregation](../plans/2026-09-16-shopping-list-03-aggregation.md): conservative, non-destructive grouping and group actions across all clients. Depends on phase 2.

Implement each phase on its own branch or as a separately reviewed change. Use the repository's existing deployment process only when explicitly asked to deploy. Backfill commands below are execution instructions, not commands run during planning.

## Existing architecture to preserve

- `IngredientParser.call(strings)` returns one entry per input; `ParseRecipeIngredientsJob` enriches existing recipe rows without rewriting original recipe lines.
- `Llm::IngredientInstructions` owns normalized names, unit vocabulary, category vocabulary, and enrichment version (currently 2).
- Recipe API `structured_ingredients` is assembled in `app/controllers/api/v1/recipes_controller.rb#recipe_detail_json`, including the batch endpoint. The documentation's Jbuilder reference is stale; use the controller.
- Shopping API writes use `ShoppingList::UpsertItems`; web manual and recipe writes currently create rows directly. Route these web writes through the shared service in phase 2.
- `(cookbook_id, client_id)` identifies an addition. The same client ID is an upsert/retry; a new client ID is an intentional additional quantity.
- iOS uses SwiftData pending creates/updates and serializes network operations with `ShoppingListNetworkGate`. Full list responses prune orphans; partial responses do not.
- Android uses Retrofit plus a Room JSON cache. Mutations are online-only and cache changes follow server acknowledgement.
- Checked rows expire after one hour. The recipe review's 36-hour old-list decision uses underlying row creation dates.

## Global constraints

- Rails 8.1 / Ruby 3.4.7; Hotwire and Tailwind CSS v4; no Node.js/npm required.
- Native SwiftUI iOS 18+; Kotlin/Jetpack Compose Android with existing SDK settings.
- Keep the app's light-only design, semantic tokens, native chrome, and existing tiles/cards. IBM Plex Mono is for numerics only.
- Read root, iOS, and Android AGENTS.md before implementing their respective changes.
- Keep changes cookbook-scoped. Preserve existing old-list replacement transactions, source-recipe tracking, and request idempotency.
- Use the existing LLM parser and instruction vocabulary. No new model call on the critical path of Add.
- Store/display original-language input. Canonical English names are matching metadata, never automatic replacement display text.
- Preserve recipe ingredient rows. Quantity aggregation is a shopping presentation concern.
- Keep iOS offline create/check behavior and Android online-only mutations. Use existing concurrency primitives.
- Native protocol additions are optional/backward-compatible; older payloads and cached JSON remain readable.

## Decision 1: explicit staple defaults

Create `ShoppingList::Policy.default_included?(canonical_name)` with the following exact exclusion set:

```ruby
STAPLES = [
  "water", "tap water",
  "salt", "table salt", "sea salt", "kosher salt",
  "black pepper", "ground black pepper"
].freeze
```

All other values, including nil and the ambiguous `pepper`, remain included. No substring matching: sparkling water, peppers, and salt substitutes must stay included. This is a small product policy, not a personalized pantry inventory.

Expose `shopping_default_included: boolean` per structured recipe ingredient. Clients default to true if the field is absent/null. Web calls the same policy directly. iOS maps exclusion to its existing `isChecked` review state; Android/web use their existing inclusion checkboxes. Review copy explains that common staples were excluded and can be included again. An all-staple recipe has Add disabled until an item is included.

Manual entry is always respected, including a manually entered staple. The policy does not filter POST payloads or silently remove selected entries.

## Decision 2: shopping enrichment data contract

Add nullable columns to `shopping_list_items`:

| Column | Type | Meaning |
|---|---|---|
| `parsed_name` | string | Original-language food/product name, separate from the untouched input name |
| `amount`, `amount_max` | decimal(14,4) | Actual shopping quantity, already serving-scaled |
| `unit`, `note` | string | Original-language unit and qualifier |
| `canonical_name`, `canonical_unit`, `category` | string | Existing shared enrichment vocabulary |
| `enrichment_version` | integer | Successfully applied instruction version |

Keep `name` and `details` as the submitted input/display fallback. Build the parser input with `[details.presence, name].compact.join(" ")`; snapshot both original fields before a background call. Parsed results populate `parsed_name`, not `name`. No additional association to a recipe ingredient is required: additions are snapshots and must survive later recipe edits.

POST accepts the new fields as optional flat item fields. Recipe clients send them from the review draft; manual clients send their current plain-text payload. Validate/normalize supplied metadata through `ShoppingList::EnrichmentAttributes.call(attributes) -> Hash | nil`. Accept a bundle only if its version is current, canonical identity is valid, category is supported, numeric values are finite/nonnegative and fit decimal(14,4), and any range is valid. Unsupported canonical units normalize to nil. Invalid/incomplete bundles are discarded and queued for parsing while the original item still saves.

GET/POST/PATCH item responses include those fields and computed `enrichment_pending: boolean`. Decimal quantities are strings or null, consistent with recipe responses. Missing `enrichment_pending` decodes as false on older payloads; absent metadata still prevents grouping.

`ShoppingListItem#needs_enrichment?` checks the current version, valid canonical name, and category. Quantity-less products can be complete. `parsed_name` is required by the shopping metadata normalizer and falls back to the original input if a successful parse omitted a useful name.

### Write and retry rules

- Detect changes to `name`/`details` before assignment; clear old derived fields if content changed and no usable new bundle was supplied.
- An identical old-client replay lacking metadata preserves existing enrichment. A replay never adds to stored quantities.
- Replayed stale metadata must not downgrade a current server-enriched row with unchanged content.
- Queue one batch job for the successful batch's incomplete rows after the enclosing transaction commits. No jobs or notifications for rolled-back additions.
- `EnrichShoppingListItemsJob.perform(ids)` batches parser inputs. Make LLM calls outside a DB transaction. For each result, lock/reload the row and apply only if the row still exists, its source input is unchanged, and it still needs enrichment.
- Deleted/replaced/changed rows are skipped. A concurrent successful enrichment wins; the later job is a no-op.
- Parser fallback leaves metadata incomplete. Retry an incomplete batch up to three total attempts with Active Job's polynomial backoff. Successful rows are skipped on retry. A later identical add replay or explicit backfill can requeue incomplete rows after exhaustion.
- `shopping_list:enqueue_enrichment` queues incomplete unchecked rows in batches of 50. It does not call the LLM itself.

### Scaling

Scale both numeric quantity fields once, while building the review draft/payload. Keep the rendered details synchronized with these fields. Round stored/transmitted amounts to four decimal places, half-up; format visible quantities with the existing two-decimal/fraction formatters. Missing/invalid base servings means factor 1. Never recover numeric amounts by parsing formatted details when structured metadata is available.

### Enrichment updates in native clients

Web receives existing Turbo model refreshes. Native shopping screens run a bounded read-refresh loop while visible/active and an unchecked item has `enrichment_pending == true`. Fetch after delays of 2 seconds, at most 15 times per visible refresh cycle; stop on completion, error, sign-out, cookbook change, background, or leaving the screen. Restart on an explicit refresh, a successful new addition, or returning to the screen. No indefinite polling and no automatic mutation replay on Android.

Use task/coroutine cancellation and existing network serialization. Treat refresh responses as full snapshots; metadata must never overwrite pending iOS check state or mark a newer local change synced. Test delayed acknowledgements as well as ordinary cache round trips.

## Decision 3: aisle grouping

Use the current category order:

`produce`, `bakery`, `meat_seafood`, `dairy_eggs`, `pantry`, `oils_spices_condiments`, `frozen`, `beverages`, `household`, `other`.

Display labels: Produce; Bakery; Meat & seafood; Dairy & eggs; Pantry; Oils, spices & condiments; Frozen; Beverages; Household; Other.

Only nonempty categories appear under To Buy. Unknown/null categories map to Other. Within a category, sort oldest-first by `(created_at, client_id)` for consistent results across clients. Already Got remains a separate section sorted newest-checked-first. Put labels in platform-appropriate localization resources; this phase adds default English UI labels, not new app language packs. Ingredient language and category identifiers are independent.

## Decision 4: aggregate display groups, preserve individual additions

Do not merge/delete ingredient additions in the database. On each list render, form groups from the original rows. This keeps retry IDs, original input, recipe attribution, checked expiry, and the 36-hour decision intact. Older clients continue to see the original rows.

Create small pure grouping functions in Ruby/Swift/Kotlin and verify them against one shared JSON fixture. No new server aggregate table, cross-platform runtime, or event ledger is needed.

### Eligibility and grouping key

A row is eligible when enrichment is present and not pending; `enrichment_version` is positive; canonical name/unit are valid; amount is finite and positive; amount_max is null; and the unit belongs to:

```text
milligram gram kilogram milliliter centiliter deciliter liter
teaspoon tablespoon cup fluid_ounce pint quart gallon ounce pound
piece clove slice
```

Use the structural key:

```text
["food", canonical_name, canonical_unit, enrichment_version, trimmed_note_or_empty, is_checked]
```

Otherwise use `["item", client_id, is_checked]`. The caller supplies rows from one cookbook only. Deduplicate identical `client_id` inputs before grouping; no record can contribute twice.

Exact canonical units only: no g↔kg or teaspoon↔tablespoon conversions in this phase. Unknown quantities, ranges, nil units, and variable-size packages/bottles/cans/bunches stay separate. Differing notes stay separate, protecting qualifiers such as optional versus required. These are deliberate first-version limits.

Use exact decimal addition. Select the oldest member (then client_id) as representative. Multi-member rows display its `parsed_name` (fallback name), the summed amount, its original unit (fallback canonical unit), and the shared note. Singletons keep original `name`/`details`. Category comes from the representative, with Other fallback. Thus two languages can combine while display stays in the representative's original language.

Group IDs can be JSON-encoded structural keys, used locally as opaque UI identities. APIs never accept a grouping key as a mutation selector.

### Group operations

Checking, unchecking, or deleting a group applies to a captured list of its member client IDs. A concurrently added matching item is not part of that action. Checked and unchecked rows never aggregate together.

Add cookbook-scoped `PATCH .../shopping_list_items/selection` and `DELETE .../shopping_list_items/selection` API collection routes, plus equivalent web collection routes under `/shopping_list/selection`. PATCH accepts `{client_ids: [...], checked: boolean}` and returns the matching updated item array; DELETE returns 204. Validate nonempty client ID arrays and strict booleans (invalid -> 422). Missing IDs are idempotent no-ops, including IDs from another cookbook. Only current-cookbook rows can change.

Server selection mutations are transactional. Repeating a check request does not move an already-checked row's timestamp; repeating uncheck does not keep resetting its creation date. On an actual uncheck transition, reset created_at consistently with current iOS behavior. Record cooked engagement once per distinct source recipe that actually transitions to checked. Preserve row callbacks/broadcasts.

- Android uses selection endpoints and applies acknowledged rows/deletes transactionally to Room.
- iOS checking uses one local SwiftData transaction for all captured members, then its existing pending-update/create sync. A partial network failure keeps the remaining rows pending. It need not use the new bulk check endpoint.
- iOS group deletion uses the existing network gate, sends the explicit client IDs online, and removes local rows after acknowledgement. This includes pending creates: a missing server ID can mean an acknowledgement was lost, so it does not prove an item was never sent. On offline/error retain the group and show a visible error. No new durable delete queue.
- Web submits explicit member IDs and desired checked state, never a blind toggle.

The recipe review still shows ingredient lines individually so selection remains unambiguous. Compatible additions become one row on the actual shopping list, including repeated ingredients from one recipe, different recipes, and manual additions.

## Acceptance scenarios

1. German salt, French water, and Polish black pepper enriched to the staple identities start excluded; the user can include them. Sparkling water and red peppers remain included.
2. Manually adding `salt` persists it normally and later categorizes it.
3. `2 EL Olivenöl` manually added appears immediately, then gains oil/spice category without another submission.
4. Recipe quantities scale once, survive iOS offline persistence, and reach Rails without precision/display drift beyond the defined rounding.
5. Two eligible 2-tablespoon olive-oil additions display as 4 in one row. Retrying one client ID still shows 4; adding a third distinct ID shows 6.
6. g versus kg, unknown quantities, ranges, variable-size packages, differing notes, and distinct food identities stay separate.
7. Checking a two-source group checks both captured additions and records both source recipes; a concurrent third addition stays unchecked.
8. Clear-and-add, expiration, old-list review, partial/full sync, cookbook switching, and legacy payload behavior remain covered.

## Verification and rollout

- Use focused Rails tests during tasks; at each completed phase run `bin/ci > tmp/ci.log 2>&1` and inspect both its exit status and summary. Never pipe CI straight into tail/head.
- iOS: `bin/ios-build`, `bin/ios-test`; never manually edit the generated pbxproj.
- Android: `bin/android-build`, `bin/android-test`, `bin/android-test --device` with an emulator/device.
- Run browser/system and native UI checks for each phase's visible behavior, using stubbed enrichment in automated tests rather than live model assertions.
- Backend additions ship before native clients. Run recipe backfill when enabling staple defaults if existing rows are incomplete; run shopping backfill after phase 2 migration/deploy. Inspect actual completion in the deployment environment rather than assuming it.
- Update `docs/ingredients.md` and add `docs/shopping-list.md` during implementation to document durable contracts, not these task checklists.
