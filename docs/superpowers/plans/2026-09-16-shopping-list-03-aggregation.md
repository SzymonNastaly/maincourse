# Shopping Quantity Aggregation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Implementation requires leaving Plan mode.

**Goal:** Show compatible shopping additions as one summed item, with correct check/delete behavior and no double-counting from retries.

**Architecture:** Preserve individual ShoppingListItem rows and client IDs; compute presentation groups from their enrichment metadata. Ruby, Swift, and Kotlin implement the same small grouping contract against shared fixtures. Selection mutations target captured member IDs, never a dynamic ingredient-name query.

**Tech Stack:** Ruby BigDecimal, Swift Decimal/SwiftData, Kotlin BigDecimal/Room; existing shopping APIs plus transactional selection endpoints.

**Spec:** [Shopping list design](../specs/2026-09-16-shopping-list-design.md), Decision 4. Requires completed phase 2.

## Global Constraints

- Preserve recipe ingredient rows. Quantity aggregation is a shopping presentation concern.
- Keep changes cookbook-scoped. Preserve existing old-list replacement transactions, source-recipe tracking, and request idempotency.
- Store/display original-language input. Canonical English names are matching metadata, never automatic replacement display text.
- Keep iOS offline create/check behavior and Android online-only mutations. Use existing concurrency primitives.
- Native protocol additions are optional/backward-compatible; older payloads and cached JSON remain readable.
- Keep the app's light-only design, semantic tokens, native chrome, and existing tiles/cards. IBM Plex Mono is for numerics only.

All paths are repository-relative. Grouping never modifies persisted rows, upsert arithmetic, recipe prompts, or review selection lines. The first release adds exact-unit positive quantities with matching notes; all other cases use separate rows.

---

## Task 1: Ruby grouping contract and shared fixtures

**Files**
- Create: `app/services/shopping_list/grouping.rb`
- Create: `test/services/shopping_list/grouping_test.rb`
- Create: `test/fixtures/files/shopping_list_grouping.json`
- Modify: `test/services/shopping_list/upsert_items_test.rb`

**Interfaces**
- `ShoppingList::Grouping.call(items) -> Array<ShoppingList::Grouping::Group>`.
- Group exposes `key` (structural array), `members` (original rows), `representative` (oldest member), `total_amount` (BigDecimal for eligible groups, otherwise nil), `category` (normalized), and `checked?`.
- `Group#client_ids` returns sorted member client IDs; `#multiple?` means members.size > 1. Rendering consumes the representative's original name/details for singletons, parsed_name/unit/note plus total_amount for multi-member groups.
- Caller supplies one cookbook's items and uses phase 2 category order. Members/representatives use `(created_at, client_id)` ordering. Checked section ordering uses each group's latest checked_at descending.

- [ ] **1. Create the common fixture and failing Ruby runner.** Fixture schema: a base row plus case-specific row overrides. For each override, merge base with the override; override null explicitly clears a field. All rows in a case represent one cookbook. Expected groups compare sorted client_ids and exact decimal total, ignoring nonsemantic JSON group ID encoding.

```json
{
  "base": {
    "name": "Olivenöl", "details": "2 EL", "parsed_name": "Olivenöl",
    "amount": "2", "amount_max": null, "unit": "EL", "note": null,
    "canonical_name": "olive oil", "canonical_unit": "tablespoon",
    "category": "oils_spices_condiments", "enrichment_version": 2,
    "enrichment_pending": false, "checked_at": null, "source_recipe_id": null,
    "created_at": "2026-09-16T10:00:00Z", "updated_at": "2026-09-16T10:00:00Z"
  },
  "cases": [
    {"name": "multilingual", "rows": [{"client_id":"a"},{"client_id":"b","name":"huile d’olive","parsed_name":"huile d’olive","unit":"c. à soupe"}], "groups": [{"client_ids":["a","b"],"total":"4"}]},
    {"name": "exact_decimal", "rows": [{"client_id":"a","amount":"0.1"},{"client_id":"b","amount":"0.2"}], "groups": [{"client_ids":["a","b"],"total":"0.3"}]},
    {"name": "duplicate_client_id", "rows": [{"client_id":"a"},{"client_id":"a"},{"client_id":"b"}], "groups": [{"client_ids":["a","b"],"total":"4"}]},
    {"name": "different_units", "rows": [{"client_id":"a"},{"client_id":"b","canonical_unit":"teaspoon"}], "groups": [{"client_ids":["a"],"total":"2"},{"client_ids":["b"],"total":"2"}]},
    {"name": "missing_amount", "rows": [{"client_id":"a","amount":null},{"client_id":"b","amount":null}], "groups": [{"client_ids":["a"],"total":null},{"client_ids":["b"],"total":null}]},
    {"name": "ranges", "rows": [{"client_id":"a","amount_max":"3"},{"client_id":"b","amount_max":"3"}], "groups": [{"client_ids":["a"],"total":null},{"client_ids":["b"],"total":null}]},
    {"name": "variable_packages", "rows": [{"client_id":"a","canonical_unit":"bottle"},{"client_id":"b","canonical_unit":"bottle"}], "groups": [{"client_ids":["a"],"total":null},{"client_ids":["b"],"total":null}]},
    {"name": "checked_separate", "rows": [{"client_id":"a"},{"client_id":"b","checked_at":"2026-09-16T11:00:00Z"}], "groups": [{"client_ids":["a"],"total":"2"},{"client_ids":["b"],"total":"2"}]},
    {"name": "different_notes", "rows": [{"client_id":"a","note":"optional"},{"client_id":"b"}], "groups": [{"client_ids":["a"],"total":"2"},{"client_ids":["b"],"total":"2"}]},
    {"name": "different_food", "rows": [{"client_id":"a","canonical_name":"fresh tomato"},{"client_id":"b","canonical_name":"canned tomato"}], "groups": [{"client_ids":["a"],"total":"2"},{"client_ids":["b"],"total":"2"}]},
    {"name": "incomplete", "rows": [{"client_id":"a","enrichment_pending":true},{"client_id":"b","enrichment_pending":true}], "groups": [{"client_ids":["a"],"total":null},{"client_ids":["b"],"total":null}]},
    {"name": "different_versions", "rows": [{"client_id":"a"},{"client_id":"b","enrichment_version":3}], "groups": [{"client_ids":["a"],"total":"2"},{"client_ids":["b"],"total":"2"}]},
    {"name": "unknown_units", "rows": [{"client_id":"a","canonical_unit":null},{"client_id":"b","canonical_unit":null}], "groups": [{"client_ids":["a"],"total":null},{"client_ids":["b"],"total":null}]}
  ]
}
```

Use a test-only row struct with the stored fields and `needs_enrichment?` bound to fixture enrichment_pending; it must match the model's grouping-facing interface. Parse amount and timestamps before grouping. Add individual tests for reversed input order choosing the same representative, unknown category -> Other, nonpositive/nonfinite amounts staying separate, same note with surrounding whitespace merging, and preservation of all members/source_recipe_ids.

Add an UpsertItems integration test: create client a amount 2, client b amount 2, replay client a amount 2, then group queried rows -> one total 4; a new client c -> total 6. Assert the original stored amounts are still 2, 2, 2 and recipe rows are unchanged.

- [ ] **2. Run `bin/rails test test/services/shopping_list/grouping_test.rb test/services/shopping_list/upsert_items_test.rb` and confirm grouping is missing.**
- [ ] **3. Implement the pure grouping function.** Use the design's explicit additive-unit allowlist and canonical-name format. Key construction:

```ruby
if eligible?(item)
  ["food", item.canonical_name, item.canonical_unit,
   item.enrichment_version, item.note.to_s.strip, item.checked_at.present?]
else
  ["item", item.client_id, item.checked_at.present?]
end
```

Sort before deduplicating by client_id, then group by key. Sum eligible groups with `members.sum(BigDecimal("0"), &:amount)`. A singleton eligible group still has total_amount but its renderer preserves original name/details. Never use Float for sum/comparison. Select category and display attributes from the oldest representative, regardless of input order. Group objects hold underlying rows for actions and source tracking.

- [ ] **4. Rerun the focused suite.** Check the shared fixture runner compares exact numeric values and memberships, not implementation-specific hashes. Confirm no model write is performed by grouping.
- [ ] **5. Commit the task's files.** Commit message: `Define conservative shopping quantity display groups`.

## Task 2: Transactional member-selection actions and grouped web list

**Files**
- Create: `app/services/shopping_list/change_selection.rb`
- Modify: `config/routes.rb`
- Modify: `app/controllers/api/v1/shopping_list_items_controller.rb`
- Modify: `app/controllers/shopping_list_items_controller.rb`
- Modify: `app/helpers/shopping_list_items_helper.rb`
- Modify: `app/views/shopping_list_items/index.html.erb`
- Create: `app/views/shopping_list_items/_group_tile.html.erb`
- Create: `test/services/shopping_list/change_selection_test.rb`
- Modify: `test/controllers/api/v1/shopping_list_items_controller_test.rb`
- Modify: `test/controllers/shopping_list_items_controller_test.rb`
- Modify: `test/system/recipes_test.rb`
- Modify: `docs/shopping-list.md`

**Interfaces**
- `ShoppingList::ChangeSelection.call(cookbook:, client_ids:, action:, checked: nil) -> Array<ShoppingListItem>`; action is `:set_checked` or `:delete`. Bad arguments raise ArgumentError (controller renders 422). Delete returns an empty array after success.
- PATCH `/api/v1/shopping_list_items/selection` -> updated raw member array; DELETE same path -> 204.
- Web PATCH/DELETE `/shopping_list/selection` -> redirect. Use controller actions `update_selection`/`destroy_selection` and named routes `update_selection_shopping_list_items`/`destroy_selection_shopping_list_items`.
- Existing per-item API endpoints remain compatible. Selection routes are collection routes declared before member routes.

- [ ] **1. Write service/controller tests for captured membership and idempotency.** Example in a service test with normal fixture setup:

```ruby
test "checking a captured selection leaves a later matching addition unchecked" do
  cookbook = cookbooks(:one_personal)
  a = cookbook.shopping_list_items.create!(client_id: "a", name: "oil")
  b = cookbook.shopping_list_items.create!(client_id: "b", name: "oil")
  c = cookbook.shopping_list_items.create!(client_id: "c", name: "oil")
  ShoppingList::ChangeSelection.call(cookbook: cookbook, client_ids: [a.client_id, b.client_id], action: :set_checked, checked: true)
  assert a.reload.checked_at
  assert b.reload.checked_at
  assert_nil c.reload.checked_at
end
```

Add repeated check preserving checked_at, repeated uncheck preserving created_at after the first transition, duplicate IDs applied once, nonexistent IDs as no-op, foreign-cookbook IDs untouched, empty/invalid arrays and malformed booleans -> 422, transaction rollback if any save/destroy fails, and two source recipes both receiving cooked engagement only on actual transitions. Delete retries must succeed without recreating rows. Validate legacy item endpoints and clear-and-add still pass.

Browser scenario: two oil additions appear as one tile with total 4; clicking its check action checks both rows; uncheck returns one tile; delete removes both. A separate item with another unit remains visible. Counts reflect visible groups, while 36-hour review still checks underlying rows.

- [ ] **2. Run focused tests and confirm missing routes/service/group rendering failures.**

```bash
bin/rails test test/services/shopping_list/change_selection_test.rb test/controllers/api/v1/shopping_list_items_controller_test.rb test/controllers/shopping_list_items_controller_test.rb
bin/rails test:system test/system/recipes_test.rb
```

- [ ] **3. Implement selection mutation and web presentation.** Validate a nonempty array of nonblank string client IDs, normalize/deduplicate, and require a literal boolean for set_checked. Parse the web form's checked value explicitly from `"true"`/`"false"`; reject other values. Scope lookup through cookbook.shopping_list_items and mutate inside a transaction. Use one timestamp per operation. Actual state transition core:

```ruby
if checked && item.checked_at.nil?
  item.update!(checked_at: now)
elsif !checked && item.checked_at.present?
  item.update!(checked_at: nil, created_at: now)
end
```

Collect distinct source_recipe_ids from transitions to checked and record engagement after successful mutation, with the existing logging convention for engagement failures. Do not infer membership from canonical names or category. Use row save/destroy methods so callbacks and Turbo refreshes run.

Render group tiles using phase 2 styling. Multi-member details use existing `format_amount(total_amount)`, representative unit (canonical fallback), and the shared note. Single-member details stay verbatim. Both check and delete forms include hidden `client_ids[]` for exactly the captured group members; check submits a desired boolean. Reuse existing source-recipe display where there is one source; for multiple sources show a neutral `From N recipes` label rather than attributing the total to one recipe. Totals count rendered groups. Underlying creation/checked timestamps remain untouched by rendering.

- [ ] **4. Rerun tests and browser checks.** Verify two browsers can add/check simultaneously without checking a newly added matching item. Document group semantics and endpoints in `docs/shopping-list.md`.
- [ ] **5. Commit the task's files.** Commit message: `Add shopping selection actions and summed web tiles`.

## Task 3: iOS grouping, offline check actions, and acknowledged deletion

**Files**
- Create: `hauptgang-ios/Hauptgang/Models/ShoppingListGrouping.swift`
- Modify: `hauptgang-ios/Hauptgang/Views/ShoppingListView.swift`
- Modify: `hauptgang-ios/Hauptgang/ViewModels/ShoppingListViewModel.swift`
- Modify: `hauptgang-ios/Hauptgang/Services/ShoppingListRepository.swift`
- Modify: `hauptgang-ios/Hauptgang/Services/ShoppingListService.swift`
- Modify: `hauptgang-ios/Hauptgang/Models/ShoppingListItem.swift`
- Modify: `hauptgang-ios/HauptgangTests/Mocks/MockShoppingListRepository.swift`
- Modify: `hauptgang-ios/HauptgangTests/Mocks/MockShoppingListService.swift`
- Modify: `hauptgang-ios/HauptgangTests/ViewModels/ShoppingListViewModelTests.swift`
- Modify: `hauptgang-ios/HauptgangTests/Services/ShoppingListRepositoryTests.swift`
- Create: `hauptgang-ios/HauptgangTests/Models/ShoppingListGroupingTests.swift`
- Modify: `hauptgang-ios/project.yml` (test fixture resource only)

**Interfaces**
- `ShoppingListGrouping.groups(_ items: [PersistedShoppingListItem]) -> [ShoppingListGroup]`.
- `ShoppingListGroup` contains stable id, memberClientIds, representative, totalAmount: Decimal?, category, isChecked, displayName, displayDetails. Quantities are added as Decimal; membership/key rules match the shared fixture.
- `ShoppingListViewModel.setGroupChecked(clientIds: [String], checked: Bool)` performs one local transaction then schedules existing sync.
- `ShoppingListViewModel.deleteGroup(clientIds: [String]) async -> Bool` serializes through the network gate, calls selection DELETE, then removes local members transactionally. Failure keeps local members and publishes a visible error.
- Repository adds `setItemsChecked(clientIds: [String], checkedAt: Date?) throws` and `deleteItems(clientIds: [String]) throws` to its protocol and implementation.
- Service adds `deleteSelection(clientIds: [String]) async throws` with a JSON body `{client_ids: [...]}`.

- [ ] **1. Add shared-fixture, local transaction, and delayed-sync tests.** Add the single fixture file as a HauptgangTests resource in project.yml:

```yaml
- path: ../test/fixtures/files/shopping_list_grouping.json
  buildPhase: resources
```

Load it via a test Bundle marker class, merge base JSON with each row override before decoding, materialize in-memory PersistedShoppingListItem rows, and compare sorted memberships/Decimal totals. Map fixture enrichment_pending to the persisted boolean exactly; no hard-coded current version in the grouping implementation.

ViewModel tests assert two-member check marks both pending in one repository operation, a later third addition stays unchecked, and the existing pending sync sends original per-row quantities. A simulated one-member network failure leaves remaining work pending. Delayed responses must preserve a newer undo (phase 2 reconciliation rule).

Deletion tests: API error/offline leaves members cached and exposes an error; success removes exactly acknowledged requested members; an addition with a new client ID during deletion survives; a pending create with lost acknowledgement is deleted by client ID once online and never resurrects from a late sync response.

- [ ] **2. Run `bin/ios-test`; confirm missing grouping/batch-repository methods.**
- [ ] **3. Implement the group projector and actions.** Use the structural key with canonical identity/unit/version/trimmed note/checked state. Validate names against the same ASCII pattern and units against the design's allowlist; nil/nonfinite/nonpositive/range/pending rows are singleton keys. Deduplicate by clientId before summing. For original-language multi-member output use the representative parsedName and existing IngredientFormatter.

Repository group check runs inside `modelContext.transaction`, captures a single timestamp, changes only actual transitions, and leaves pendingCreate rows pendingCreate. Server-backed rows become pendingUpdate. Uncheck changes createdAt only on a checked -> unchecked transition. Publish/reload UI once after the transaction, then sync once. Do not loop `toggleItem` because that launches independent syncs and can invert a state after a retry.

```swift
func setItemsChecked(clientIds: [String], checkedAt: Date?) throws {
    guard let modelContext else { throw ShoppingListRepositoryError.notConfigured }
    let selected = Set(clientIds)
    try modelContext.transaction {
        for item in try self.getAllItems() where selected.contains(item.clientId) {
            guard item.isChecked != (checkedAt != nil) else { continue }
            try self.updateItem(clientId: item.clientId, checkedAt: checkedAt)
        }
        try modelContext.save()
    }
}
```

This reuses the existing per-row sync-state assignment inside one transaction; the ViewModel calls it once, then reloads once and starts one sync task.

Deletion always uses online acknowledgement, including local pending creates: nil serverId does not prove a row was never sent. Acquire the existing network gate before deleting to serialize against pending creates/updates. Send captured client IDs, then remove those local members transactionally; no name-based deletion and no local-first deletion of server-backed groups. Show errors with existing danger tokens and allow explicit retry. No additional durable deletion queue.

Use display groups only in the actual shopping view. Keep review drafts individual. Category sections and group counts consume the new projected rows, while stale-list checks still inspect original items. Update mocks to implement the real transaction semantics.

- [ ] **4. Run `bin/ios-build`, `bin/ios-test`, and Simulator checks.** Exercise offline recipe add/check, reconnect/retry, check/undo during sync, enrichment moving/combining an item, and delete failure. Verify two physical rows display as one and no background response doubles the total.
- [ ] **5. Commit the task's files.** Commit message: `Aggregate iOS shopping quantities without merging stored additions`.

## Task 4: Android grouping, atomic actions, and full integration checks

**Files**
- Create: `maincourse-android/app/src/main/java/com/getmaincourse/app/features/shopping/ShoppingListGrouping.kt`
- Modify: `maincourse-android/app/src/main/java/com/getmaincourse/app/features/shopping/ShoppingListViewModel.kt`
- Modify: `maincourse-android/app/src/main/java/com/getmaincourse/app/features/shopping/ShoppingListScreen.kt`
- Modify: `maincourse-android/app/src/main/java/com/getmaincourse/app/MainCourseApp.kt`
- Modify: `maincourse-android/app/src/main/java/com/getmaincourse/app/data/model/ShoppingItemModels.kt`
- Modify: `maincourse-android/app/src/main/java/com/getmaincourse/app/data/network/MainCourseService.kt`
- Modify: `maincourse-android/app/src/main/java/com/getmaincourse/app/data/ShoppingListRepository.kt`
- Modify: `maincourse-android/app/src/main/java/com/getmaincourse/app/data/cache/CatalogDao.kt`
- Modify: `maincourse-android/app/src/main/res/values/strings.xml`
- Modify: `maincourse-android/app/build.gradle.kts` (unit-test resource directory only)
- Create: `maincourse-android/app/src/test/java/com/getmaincourse/app/features/shopping/ShoppingListGroupingTest.kt`
- Modify: `maincourse-android/app/src/test/java/com/getmaincourse/app/features/shopping/ShoppingListViewModelTest.kt`
- Modify: `maincourse-android/app/src/test/java/com/getmaincourse/app/data/network/MainCourseServiceTest.kt`
- Modify: `maincourse-android/app/src/androidTest/java/com/getmaincourse/app/features/shopping/ShoppingListScreenTest.kt`
- Modify: `docs/shopping-list.md`

**Interfaces**
- `ShoppingListGrouping.groups(items: List<ShoppingItem>): List<ShoppingListGroup>` mirrors the shared grouping fixture.
- Group stores memberClientIds, representative, totalAmount: BigDecimal?, category, checked state, display strings, and stable structural key.
- Retrofit adds PATCH selection returning List<ShoppingItem>, DELETE selection returning Unit. For DELETE with a body use `@HTTP(method = "DELETE", path = "api/v1/shopping_list_items/selection", hasBody = true)` in the existing service convention.
- Repository adds `setSelectionChecked(userId, cookbookId, clientIds, checked)` and `deleteSelection(userId, cookbookId, clientIds)`; use the existing Mutex and server-acknowledgement rule.
- ViewModel adds `setGroupChecked(clientIds: List<String>, checked: Boolean)` and `deleteGroup(clientIds: List<String>)`; callbacks capture explicit member snapshots.

- [ ] **1. Add the shared fixture runner and group operation tests.** Point unit-test resources at the common fixture directory:

```kotlin
android.sourceSets.getByName("test").resources.srcDir("../../test/fixtures/files")
```

Read shopping_list_grouping.json from the classloader, merge base/override JsonObjects, decode ShoppingItem inputs (supply deterministic numeric IDs in the test adapter), and compare exact BigDecimal totals and member sets for every shared case. Add request tests for method/path/header/body and mock API responses with two updated members.

ViewModel/Room tests: check/delete targets all captured IDs and no later addition; failed requests leave cache unchanged; successful partial member arrays upsert without pruning unrelated rows; local deletion matches user/cookbook/client IDs only. Compose assertions show one total-4 row, correct group count, and one bulk action rather than one action on just the representative.

- [ ] **2. Run focused tests and confirm the new grouping/service methods fail.**

```bash
bin/android-gradle :app:testDebugUnitTest --tests '*ShoppingListGroupingTest' --tests '*ShoppingListViewModelTest' --tests '*MainCourseServiceTest'
```

- [ ] **3. Implement grouping and mutations.** Use Instant parsing for timestamp order, BigDecimal for arithmetic, exact canonical key matching, and the fixture's singleton rules. Render existing cards from display groups with stable keys; use strings.xml for any group/source/error copy. Keep checked/unchecked groups separate and counts based on visible groups.

```kotlin
@Serializable
data class ShoppingSelectionRequest(
    @SerialName("client_ids") val clientIds: List<String>,
    val checked: Boolean? = null,
)

// Add to the existing MainCourseService interface.
@PATCH("api/v1/shopping_list_items/selection")
suspend fun setShoppingSelectionChecked(
    @Header("X-Cookbook-Id") cookbookId: Long,
    @Body request: ShoppingSelectionRequest,
): List<ShoppingItem>

@HTTP(method = "DELETE", path = "api/v1/shopping_list_items/selection", hasBody = true)
suspend fun deleteShoppingSelection(
    @Header("X-Cookbook-Id") cookbookId: Long,
    @Body request: ShoppingSelectionRequest,
)
```

Put the request data class in ShoppingItemModels.kt and use these exact service method names in the repository's new selection methods.

In the repository's Mutex, call the selection endpoint and then apply results transactionally. For deletion use a scoped DAO query against JSON-backed rows' existing client IDs via decoded membership if needed: fetch the active cookbook's cached rows, match requested clientIds in application code, and delete by scoped item IDs in one Room transaction. No schema change or JSON SQL extension is required. Do not clear/refetch the entire cache as the only success response handling.

Keep screen-level operation errors and explicit retry. A request replay uses the same captured IDs and desired state; it never recomputes a larger group from newly arrived items. Use the current mutation busy state to prevent overlapping UI mutations, and keep all Android mutation behavior online-only.

- [ ] **4. Verify the complete feature.** Run `bin/android-build`, `bin/android-test`, `bin/android-test --device`; run `bin/ci > tmp/ci.log 2>&1` and inspect exit/summary. Run the iOS checks if shared fixtures/contracts changed after its task. Complete all eight design acceptance scenarios on web and both native clients. Include 0.1 + 0.2, same-request retry, third intentional addition, incompatible units, source attribution, old-list replacement, and cookbook switch during enrichment. Document aggregation limits and captured-member actions in `docs/shopping-list.md`.
- [ ] **5. Commit the task's files.** Commit message: `Aggregate Android shopping quantities with captured-member actions`. The complete feature is ready for review; deployment/release remains a separate user instruction.
