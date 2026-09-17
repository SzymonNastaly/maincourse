# Shopping Enrichment and Aisles Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Implementation requires leaving Plan mode.

**Goal:** Enrich manually added shopping items, preserve recipe enrichment through shopping addition, and organize To Buy into consistent aisle sections on all three clients.

**Architecture:** Keep submitted name/details and store derived metadata beside them. Recipe additions supply a validated snapshot; plain-text additions enqueue the existing parser after commit. Native caches retain metadata and visible screens perform bounded refreshes while enrichment is incomplete.

**Tech Stack:** Rails/Active Job/Solid Queue, existing RubyLLM parser, Turbo, SwiftData/SwiftUI, Retrofit/Room/Compose.

**Spec:** [Shopping list design](../specs/2026-09-16-shopping-list-design.md), Decisions 2–3. Prerequisite: phase 1's `ShoppingList::Policy` and iOS draft initializer.

## Global Constraints

- Use the existing LLM parser and instruction vocabulary. No new model call on the critical path of Add.
- Store/display original-language input. Canonical English names are matching metadata, never automatic replacement display text.
- Keep changes cookbook-scoped. Preserve existing old-list replacement transactions, source-recipe tracking, and request idempotency.
- Keep iOS offline create/check behavior and Android online-only mutations. Use existing concurrency primitives.
- Native protocol additions are optional/backward-compatible; older payloads and cached JSON remain readable.
- Keep the app's light-only design, semantic tokens, native chrome, and existing tiles/cards. IBM Plex Mono is for numerics only.

All paths are repository-relative. Read `docs/ingredients.md`, `docs/ios-offline-sync-patterns.md`, `docs/web-ui.md`, and platform AGENTS.md before their tasks. Use code snippets as the concrete contracts/core behavior; integrate them with surrounding code rather than replacing whole existing files.

---

## Task 1: Shopping metadata, normalization, and wire representation

**Files**
- Create: `db/migrate/20260916120000_add_enrichment_to_shopping_list_items.rb` (use a newer timestamp if this exact migration name already exists at execution)
- Modify: `db/schema.rb` via migration
- Modify: `app/models/shopping_list_item.rb`
- Create: `app/services/shopping_list/enrichment_attributes.rb`
- Modify: `app/services/shopping_list/payload.rb`
- Modify: `app/serializers/shopping_list_item_serializer.rb`
- Modify: `test/models/shopping_list_item_test.rb`
- Create: `test/services/shopping_list/enrichment_attributes_test.rb`
- Modify: `test/controllers/api/v1/shopping_list_items_controller_test.rb`

**Interfaces**
- `ShoppingList::EnrichmentAttributes.call(attributes) -> Hash | nil`: symbol-keyed normalized fields from the design's flat shopping metadata contract. `nil` means callers must parse the original text.
- `ShoppingListItem#needs_enrichment? -> boolean` and `#enrichment_input -> String`.
- Serializer emits optional metadata, decimal strings/null, and `enrichment_pending`.

- [ ] **1. Write model/normalizer tests.** Core positive case:

```ruby
test "quantityless categorized items are complete" do
  attrs = ShoppingList::EnrichmentAttributes.call(
    parsed_name: "Salz", canonical_name: "salt", canonical_unit: nil,
    category: "oils_spices_condiments", enrichment_version: Llm::IngredientInstructions::VERSION
  )
  item = shopping_list_items(:unchecked_milk)
  item.assign_attributes(attrs)
  assert_not item.needs_enrichment?
  assert_nil item.amount
end
```

Additional explicit cases: blank/invalid canonical_name; unknown category; stale version; string decimal `"0.5"`; negative/nonfinite/out-of-range amount; upper bound below lower; amount_max without amount; absent amount; unsupported unit normalizes nil. Original name/details must not be among normalized assignment keys. Controller response assertions prove new fields are optional and BigDecimals appear as strings.

- [ ] **2. Run focused tests and confirm failures for missing metadata support.**

```bash
bin/rails test test/models/shopping_list_item_test.rb test/services/shopping_list/enrichment_attributes_test.rb test/controllers/api/v1/shopping_list_items_controller_test.rb
```

- [ ] **3. Implement the migration, normalizer, and serialization.** Migration core:

```ruby
class AddEnrichmentToShoppingListItems < ActiveRecord::Migration[8.1]
  def change
    %i[parsed_name unit note canonical_name canonical_unit category].each do |column|
      add_column :shopping_list_items, column, :string
    end
    add_column :shopping_list_items, :amount, :decimal, precision: 14, scale: 4
    add_column :shopping_list_items, :amount_max, :decimal, precision: 14, scale: 4
    add_column :shopping_list_items, :enrichment_version, :integer
  end
end
```

Normalize identity with `Llm::IngredientInstructions.normalize_name`; use the existing unit vocabulary, and require an actual supported category before accepting client metadata. Reject unsupported versions. Parse nonblank decimal values with `BigDecimal(value.to_s, exception: false)`; require finite values between 0 and `9999999999.9999`; round half-up to four places and recheck bounds after rounding. Missing numeric values are valid. Validate range ordering/pairing.

Model helpers:

```ruby
def enrichment_input
  [details.presence, name].compact.join(" ")
end

def needs_enrichment?
  enrichment_version != Llm::IngredientInstructions::VERSION ||
    canonical_name.blank? || category.blank?
end
```

Add model canonical-name/category/unit validations with allow_nil, matching Ingredient's conventions. Permit the nine new flat fields in both single and batch shopping payloads. Add serializer fields using the same decimal-string representation as recipes. A nil/stale version produces `enrichment_pending: true`.

- [ ] **4. Run `bin/rails db:migrate` and the focused commands above.** Check the generated schema only has the intended additive columns. Metadata assignment to newly submitted shopping items arrives in Task 2; retain tests accordingly.
- [ ] **5. Commit the task's files.** Commit message: `Add structured enrichment fields to shopping items`.

## Task 2: Shared shopping writes and asynchronous enrichment

**Files**
- Modify: `app/services/shopping_list/upsert_items.rb`
- Modify: `app/controllers/shopping_list_items_controller.rb`
- Modify: `app/controllers/recipes/shopping_list_items_controller.rb`
- Create: `app/jobs/enrich_shopping_list_items_job.rb`
- Create: `lib/tasks/shopping_list.rake`
- Modify: `test/services/shopping_list/upsert_items_test.rb`
- Modify: `test/controllers/shopping_list_items_controller_test.rb`
- Modify: `test/controllers/recipes/shopping_list_items_controller_test.rb`
- Create: `test/jobs/enrich_shopping_list_items_job_test.rb`
- Create: `test/tasks/shopping_list_rake_test.rb`
- Create: `docs/shopping-list.md`

**Interfaces**
- Existing `ShoppingList::UpsertItems.new(user:, cookbook:, items:, clear_existing: false).call` signature and result remain intact.
- `EnrichShoppingListItemsJob.perform_later(ids)` runs one parser batch and applies valid results to unchanged incomplete rows.
- `bin/rails shopping_list:enqueue_enrichment` batches incomplete unchecked rows in groups of 50.

- [ ] **1. Write idempotency and race regression tests.** Add this core test to the existing UpsertItemsTest setup:

```ruby
test "old-client replay preserves enrichment and never adds quantity" do
  row = shopping_list_items(:unchecked_milk)
  row.update!(parsed_name: "milk", amount: 2, unit: "l", canonical_name: "milk",
    canonical_unit: "liter", category: "dairy_eggs", enrichment_version: Llm::IngredientInstructions::VERSION)
  result = ShoppingList::UpsertItems.new(user: @user, cookbook: @cookbook,
    items: [{client_id: row.client_id, name: row.name, details: row.details}]).call
  assert result.success?
  assert_equal BigDecimal("2"), row.reload.amount
  assert_equal "milk", row.canonical_name
end
```

Also assert: new manual rows enqueue after commit; current recipe metadata avoids a job; malformed metadata queues but saves original input; name/details changes clear obsolete fields; unchanged stale supplied bundles do not replace current enrichment; rollback/failed clear-and-add enqueues nothing. Existing concurrent client_id retry and invalid recipe tests still pass.

Job tests stub IngredientParser directly:

```ruby
test "enrichment preserves source input" do
  row = shopping_list_items(:unchecked_milk)
  row.update!(name: "2 EL Olivenöl", details: nil)
  hit = {raw: row.enrichment_input, name: "Olivenöl", amount: 2, unit: "el",
    canonical_name: "olive oil", canonical_unit: "tablespoon",
    category: "oils_spices_condiments", enrichment_version: Llm::IngredientInstructions::VERSION}
  IngredientParser.stub(:call, [hit]) { EnrichShoppingListItemsJob.perform_now([row.id]) }
  assert_equal "2 EL Olivenöl", row.reload.name
  assert_equal "Olivenöl", row.parsed_name
  assert_not row.needs_enrichment?
end
```

Add stub callbacks that delete, replace, or edit the row during the parser call, and one that completes enrichment concurrently. Assert no stale write/recreation. Fallback output retains input and triggers bounded retry; mixed success/failure retries only incomplete rows. Cover non-food `dish soap` with category household and no invented amount.

- [ ] **2. Run focused tests to demonstrate the missing behavior.**

```bash
bin/rails test test/services/shopping_list/upsert_items_test.rb test/jobs/enrich_shopping_list_items_job_test.rb test/controllers/shopping_list_items_controller_test.rb test/controllers/recipes/shopping_list_items_controller_test.rb test/tasks/shopping_list_rake_test.rb
```

- [ ] **3. Implement the write/job path.** In UpsertItems, capture content changes before assignment; assign existing input/source/checked fields; apply a complete supplied bundle when content changed or enrichment is incomplete. If content changed without a usable bundle, clear all nine derived fields. Preserve enrichment on unchanged legacy replay. Use one assignment helper for normal save and existing RecordNotUnique recovery so semantics cannot diverge.

After a successful transaction, compute the IDs that still need enrichment and defer enqueue through the outermost transaction:

```ruby
ids = created.select(&:needs_enrichment?).map(&:id).uniq
ActiveRecord.after_all_transactions_commit do
  EnrichShoppingListItemsJob.perform_later(ids) if ids.any?
end
```

Keep collaborator notifications and engagement at the existing service boundary. Web controllers build the same item hashes, using SecureRandom.uuid, and delegate both ordinary additions and transactional clear-and-add to UpsertItems. Preserve existing success redirects/notices; show a useful error redirect if the service fails. Recipe selection remains the payload's explicit selection, never server-filtered by staple policy.

The job defines `IncompleteEnrichment < StandardError` and `retry_on IncompleteEnrichment, wait: :polynomially_longer, attempts: 3`. Snapshot `[id, name, details, enrichment_input]`, parse outside a transaction, normalize parser result by renaming `name` to `parsed_name`, then `with_lock` each surviving row before assignment. Compare name/details to the snapshot, skip if no longer incomplete. Raise IncompleteEnrichment only after persisting all valid results if any unchanged row still received fallback/invalid output. Use a read-then-lock record lookup that treats missing rows as no-ops.

Add the rake task with a query selecting null/old version or missing identity/category, restricted to unchecked rows; avoid loading entire lists. Document the new raw/derived contract, retries, source snapshots, and backfill command in `docs/shopping-list.md`.

- [ ] **4. Rerun the focused suite.** Add a controller regression proving manually typed salt is stored and queued. Ensure no LLM call occurs synchronously in any create action.
- [ ] **5. Commit the task's files.** Commit message: `Enrich shopping additions asynchronously through shared writes`.

## Task 3: Web metadata propagation and aisle sections

**Files**
- Modify: `app/services/shopping_list/policy.rb`
- Modify: `app/views/recipes/_add_to_list_dialog.html.erb`
- Modify: `app/javascript/controllers/portion_scaler_controller.js`
- Modify: `app/controllers/recipes/shopping_list_items_controller.rb`
- Modify: `app/controllers/shopping_list_items_controller.rb`
- Create: `app/helpers/shopping_list_items_helper.rb`
- Modify: `app/views/shopping_list_items/index.html.erb`
- Modify: `config/locales/en.yml`
- Modify: `test/services/shopping_list/policy_test.rb`
- Modify: `test/controllers/recipes/shopping_list_items_controller_test.rb`
- Modify: `test/controllers/shopping_list_items_controller_test.rb`
- Modify: `test/system/recipes_test.rb`

**Interfaces**
- `ShoppingList::Policy::CATEGORY_ORDER = Llm::IngredientInstructions::CATEGORIES`.
- `ShoppingList::Policy.category(value) -> supported category string`, falling back to other.
- `ShoppingListItemsHelper#shopping_category_label(category) -> localized label` under `shopping_list.categories`.
- Web recipe items POST the flat metadata fields; amount/amount_max hidden fields hold scaled decimal strings, not formatted fractions.

- [ ] **1. Write a browser regression for the full scaling path.** Set the existing recipe to 4 servings and an ingredient to 2 EL oil with complete metadata; increase to 8 servings, review, submit, then assert:

```ruby
row = @recipe.cookbook.shopping_list_items.find_by!(source_recipe_id: @recipe.id, canonical_name: "olive oil")
assert_equal BigDecimal("4"), row.amount
assert_equal "tablespoon", row.canonical_unit
assert_equal "oils_spices_condiments", row.category
```

Add a range test (2–3 becomes 4–6), no-base-servings factor-one case, and excluded-row pairing test. Controller/view tests assert aisle order, only nonempty headings, Other fallback, oldest-first ordering, and unchanged Already Got behavior. Add a Turbo/system test or browser proof where a saved row's category changes and the tile moves sections.

- [ ] **2. Run `bin/rails test test/controllers/shopping_list_items_controller_test.rb test/controllers/recipes/shopping_list_items_controller_test.rb test/services/shopping_list/policy_test.rb` and `bin/rails test:system test/system/recipes_test.rb`; confirm intended failures.**
- [ ] **3. Implement typed hidden fields and grouping.** Emit parsed_name, unit, note, canonical fields, version, and numeric fields alongside name/details inside each review row. `list_review` already disables all hidden fields together. Permit these fields in the web recipe controller's normalization.

Extend portion_scaler's targets with `amount` and `amountMax`. Store original base values in data attributes; update their `.value` on every render using factor and four-place decimal strings (finite/blank checks). Keep visible details on the existing formatter. Example numeric assignment:

```javascript
const base = Number.parseFloat(element.dataset.baseAmount)
element.value = Number.isFinite(base) ? (base * factor).toFixed(4) : ""
```

Use the corresponding base max for amountMax. Validate positive-only serving factors at the UI boundary, with current 1..64 limits. Avoid scaling a previously scaled value.

Sort web unchecked rows by created_at ascending, then client_id. Group using the policy's category order and helper labels:

```ruby
by_category = @to_buy.group_by { |item| ShoppingList::Policy.category(item.category) }
@to_buy_sections = ShoppingList::Policy::CATEGORY_ORDER.filter_map do |category|
  rows = by_category[category]
  [category, rows] if rows.present?
end
```

Render the existing tile partial inside each nonempty section. Preserve To Buy totals, empty state, checked section, and Turbo subscription. Add all ten default English labels from the design to Rails locale resources.

- [ ] **4. Rerun tests and inspect desktop/mobile web layouts.** Verify scaled structured fields and displayed details agree. Do not introduce a new stylesheet or redesign tiles.
- [ ] **5. Commit the task's files.** Commit message: `Carry recipe metadata into web shopping aisles`.

## Task 4: iOS metadata and reliable cache reconciliation

**Files**
- Modify: `hauptgang-ios/Hauptgang/Models/ShoppingListItem.swift`
- Modify: `hauptgang-ios/Hauptgang/Models/ShoppingListDraftItem.swift`
- Modify: `hauptgang-ios/Hauptgang/Models/PersistedShoppingListItem.swift`
- Modify: `hauptgang-ios/Hauptgang/Services/ShoppingListRepository.swift`
- Modify: `hauptgang-ios/Hauptgang/ViewModels/ShoppingListViewModel.swift`
- Modify: `hauptgang-ios/HauptgangTests/Mocks/MockShoppingListRepository.swift`
- Modify: `hauptgang-ios/HauptgangTests/Mocks/MockShoppingListService.swift`
- Modify: `hauptgang-ios/HauptgangTests/ViewModels/ShoppingListViewModelTests.swift`
- Modify: `hauptgang-ios/HauptgangTests/Models/ShoppingListDraftItemTests.swift`
- Create: `hauptgang-ios/HauptgangTests/Models/ShoppingListItemTests.swift`
- Create: `hauptgang-ios/HauptgangTests/Services/ShoppingListRepositoryTests.swift`

**Interfaces**
- Shopping response/create/draft/persisted models carry the nine flat metadata fields. Use optional String amount/amountMax on shopping wire/cache models; convert to Decimal only for arithmetic. This matches the server's decimal-string contract and avoids a SwiftData Decimal migration.
- Response/persisted model `enrichmentPending` defaults false for missing old data; a newly created plain-text local item sets it true. Complete recipe metadata sets it false until server validation.
- All initializers retain defaults so existing call sites and fixtures compile.
- `ShoppingListRepositoryProtocol` preserves existing signatures; add optional sent-state information to reconciliation only where necessary to distinguish a delayed acknowledgement from a newer local toggle.

- [ ] **1. Write wire, draft scaling, and real SwiftData persistence tests.** Core draft test:

```swift
@Test func draftCarriesScaledNumericMetadata() {
    let ingredient = StructuredIngredient(
        id: 1, position: 0, amount: 2, amountMax: 3, unit: "EL", name: "Olivenöl",
        canonicalName: "olive oil", canonicalUnit: "tablespoon",
        category: "oils_spices_condiments", enrichmentVersion: 2, raw: "2–3 EL Olivenöl"
    )
    let draft = ShoppingListDraftItem(ingredient: ingredient, scale: 2)
    #expect(draft.amount.flatMap { Decimal(string: $0) } == 4)
    #expect(draft.amountMax.flatMap { Decimal(string: $0) } == 6)
    #expect(draft.canonicalUnit == "tablespoon")
}
```

Use an in-memory ModelContainer to test local create -> reload -> outgoing payload -> server acknowledgement -> reload. Verify amount `0.1000` plus metadata survives. Add an on-disk old-schema upgrade test or simulator upgrade check with a pre-change shopping cache.

Extend ViewModelTests with delayed create/update responses: user checks/undoes while the response is suspended; enrichment arrives without reverting newer check state. Full fetch updates metadata on a pending row but retains its pending operation; partial save does not prune other rows. Mocks must mirror the production merge rule rather than blindly copying checkedAt/syncState.

- [ ] **2. Run `bin/ios-test`; confirm failures are the new model/persistence assertions.**
- [ ] **3. Implement data flow end-to-end.** Add metadata encode/decode with absent defaults, optional SwiftData properties, initializer/update mapping, draft scaling, outgoing recipe creates, and pending-create serialization. Scale using Decimal and `NSDecimalRound(..., 4, .plain)` before converting to a POSIX decimal string. Do not rescale on sync.

```swift
func scaledAmountString(_ amount: Decimal?, scale: Decimal) -> String? {
    guard let amount else { return nil }
    var scaled = amount * scale
    var rounded = Decimal()
    NSDecimalRound(&rounded, &scaled, 4, .plain)
    return NSDecimalNumber(decimal: rounded).stringValue
}
```

Keep this helper beside the draft initializer, and use it for amount and amountMax. For the response's nonoptional enrichmentPending flag, implement `decodeIfPresent(Bool.self, forKey: .enrichmentPending) ?? false`; a default initializer value alone does not change Swift's synthesized decoding behavior.

Cache reconciliation rule:

```text
Always merge server metadata/source fields for the matching client ID.
For a full fetch, retain pending local checkedAt and pending sync state.
For an acknowledged mutation, clear the pending state only if the current
local desired checkedAt still matches the sent snapshot. Otherwise retain
the newer state (pendingUpdate once a server ID exists).
```

Snapshot sent values before awaiting network, not through mutable model references afterward. Preserve `pruneOrphans: false` for create responses. Keep cookbook-switch/session handling in existing owners. Do not introduce another queue/gate.

- [ ] **4. Run `bin/ios-build` and `bin/ios-test`.** Exercise offline recipe addition, app restart, reconnect, manual addition, and late enrichment. Inspect that no pending row disappears on partial response.
- [ ] **5. Commit the task's files.** Commit message: `Persist shopping enrichment through iOS offline sync`.

## Task 5: iOS aisle sections and bounded enrichment refresh

**Files**
- Create: `hauptgang-ios/Hauptgang/Models/ShoppingCategory.swift`
- Modify: `hauptgang-ios/Hauptgang/Views/ShoppingListSectionsContent.swift`
- Modify: `hauptgang-ios/Hauptgang/Views/ShoppingListView.swift`
- Modify: `hauptgang-ios/Hauptgang/ViewModels/ShoppingListViewModel.swift`
- Create: `hauptgang-ios/HauptgangTests/Models/ShoppingCategoryTests.swift`
- Modify: `hauptgang-ios/HauptgangTests/ViewModels/ShoppingListViewModelTests.swift`

**Interfaces**
- `ShoppingCategory: String, CaseIterable` has the ten ordered IDs and localized display headings, with `init(serverValue: String?)` falling back to other.
- Extend ShoppingListDisplayItem with category defaulting to other. ShoppingListSectionsContent accepts `groupByCategory: Bool = false`; the actual list passes true, the ingredient review keeps its current selection presentation.
- `ShoppingListViewModel.refreshEnrichmentWhileVisible() async` runs at most 15 read refreshes, separated by cancellable two-second sleeps; inject a sleep closure in the initializer for deterministic tests.

- [ ] **1. Write category and polling tests.**

```swift
@Test func unknownCategoryIsOther() {
    #expect(ShoppingCategory(serverValue: "new_server_category") == .other)
    #expect(ShoppingCategory(serverValue: nil) == .other)
    #expect(ShoppingCategory.allCases.first == .produce)
    #expect(ShoppingCategory.allCases.last == .other)
}
```

In ViewModelTests, use immediate injected sleeps and queued mocked responses: pending -> enriched stops after the first successful complete response; always pending stops after 15; thrown error stops; cancelled task performs no further fetches; no pending unchecked rows performs zero fetches. Assert polling does not clear pending toggle state or start mutation retries by itself.

- [ ] **2. Run `bin/ios-test`; confirm category/polling methods are missing.**
- [ ] **3. Implement grouping and visible-task lifecycle.** Category headings use String(localized:) or LocalizedStringKey literals for the ten default labels. The UI sorts members oldest-first, groups in enum order, and reuses existing grids/tiles. Checked items retain a separate section. Preserve stable client-based tile IDs.

Polling core:

```swift
for _ in 0..<15 {
    guard self.items.contains(where: { !$0.isChecked && $0.enrichmentPending }) else { return }
    try Task.checkCancellation()
    try await self.enrichmentSleep()
    try Task.checkCancellation()
    // Fetch under the existing network gate; save as a full response while
    // preserving pending state, then reload cached items. Return on fetch error.
}
```

Bind the task to screen visibility, active scene, cookbook ID, and a refresh generation advanced by an explicit refresh or successful addition. Do not advance that generation from the polling fetch itself. Cancel on leaving/background/sign-out/cookbook change, and recheck the captured cookbook identity before applying fetched data. Avoid resetting the add input/focus during refreshes.

- [ ] **4. Run `bin/ios-build`, `bin/ios-test`, and Simulator checks.** Manual oil initially appears in Other then moves to Oils, spices & condiments without pulling to refresh; a failed parse remains usable after polling stops. Verify typing focus and staple review are intact.
- [ ] **5. Commit the task's files.** Commit message: `Group iOS shopping items by aisle and refresh enrichment`.

## Task 6: Android enrichment propagation, aisle UI, and refresh lifecycle

**Files**
- Modify: `maincourse-android/app/src/main/java/com/getmaincourse/app/data/model/ShoppingItemModels.kt`
- Modify: `maincourse-android/app/src/main/java/com/getmaincourse/app/features/recipes/IngredientReview.kt`
- Modify: `maincourse-android/app/src/main/java/com/getmaincourse/app/features/shopping/ShoppingListViewModel.kt`
- Modify: `maincourse-android/app/src/main/java/com/getmaincourse/app/features/shopping/ShoppingListScreen.kt`
- Modify: `maincourse-android/app/src/main/java/com/getmaincourse/app/MainCourseApp.kt`
- Create: `maincourse-android/app/src/main/java/com/getmaincourse/app/features/shopping/ShoppingCategory.kt`
- Modify: `maincourse-android/app/src/main/res/values/strings.xml`
- Modify: `maincourse-android/app/src/test/java/com/getmaincourse/app/features/recipes/IngredientReviewTest.kt`
- Modify: `maincourse-android/app/src/test/java/com/getmaincourse/app/features/shopping/ShoppingListViewModelTest.kt`
- Modify: `maincourse-android/app/src/test/java/com/getmaincourse/app/data/network/MainCourseServiceTest.kt`
- Create: `maincourse-android/app/src/test/java/com/getmaincourse/app/features/shopping/ShoppingCategoryTest.kt`
- Modify: `maincourse-android/app/src/androidTest/java/com/getmaincourse/app/features/shopping/ShoppingListScreenTest.kt`
- Modify: `docs/shopping-list.md`
- Modify: `docs/ingredients.md`

**Interfaces**
- ShoppingItem and ShoppingItemRequest add optional flat metadata fields with @SerialName mappings; decimal quantities remain String?. ShoppingItem adds `enrichmentPending: Boolean = false` for old-cache compatibility.
- IngredientReviewItem/ShoppingItemInput carry metadata from the recipe draft to `toRequest()`.
- `ShoppingCategory` defines ordered IDs/resource labels and Other fallback.
- `suspend ShoppingListViewModel.refreshEnrichmentWhileVisible()` performs the design's bounded read loop, invoked from lifecycle-aware Compose scope; inject delay for tests.

- [ ] **1. Write propagation, cache, and lifecycle tests.** Extend the existing review test:

```kotlin
@Test fun reviewCarriesScaledEnrichment() {
    val ingredient = StructuredIngredient(
        1, 0, "2", null, "EL", "Olivenöl", null, "2 EL Olivenöl",
        canonicalName = "olive oil", canonicalUnit = "tablespoon",
        category = "oils_spices_condiments", enrichmentVersion = 2,
    )
    val request = IngredientReview.create(9, listOf(ingredient), 8, 4) { "oil-id" }
        .includedPayload().single().toRequest()
    assertEquals(java.math.BigDecimal("4"), request.amount!!.toBigDecimal().stripTrailingZeros())
    assertEquals("tablespoon", request.canonicalUnit)
}
```

Check old ShoppingItem JSON decodes, full metadata round trips through existing CatalogJson, and create acknowledgement updates the Room JSON cache. Add known/unknown-category and section order tests. ViewModel coroutine tests use virtual time: completion/error/cancellation/max-15 stops, cookbook switch discards old results, no network mutations replay. Compose tests assert headings and an item changing from Other to its enriched aisle.

- [ ] **2. Run focused Android tests.**

```bash
bin/android-gradle :app:testDebugUnitTest --tests '*IngredientReviewTest' --tests '*ShoppingListViewModelTest' --tests '*MainCourseServiceTest' --tests '*ShoppingCategoryTest'
```

- [ ] **3. Implement the model/draft/UI flow.** Scale with the existing servingRatio and `BigDecimal.multiply(...).setScale(4, RoundingMode.HALF_UP).toPlainString()`; factor one when base servings is absent. Preserve raw fallback and selection IDs. Add all fields to `ShoppingItemInput.toRequest` and its serialized review state.

```kotlin
val scale = if (baseServings != null && baseServings > 0) {
    IngredientFormatter.servingRatio(portions, baseServings)
} else {
    BigDecimal.ONE
}
val scaledAmount = ingredient.amount?.toBigDecimalOrNull()
    ?.multiply(scale)?.setScale(4, RoundingMode.HALF_UP)?.toPlainString()
val scaledAmountMax = ingredient.amountMax?.toBigDecimalOrNull()
    ?.multiply(scale)?.setScale(4, RoundingMode.HALF_UP)?.toPlainString()
```

Use these values in the review item that `includedPayload()` serializes, alongside ingredient.name as parsedName and the unchanged canonical fields.

Room already stores shopping item JSON in `ShoppingItemEntity.itemJson`; optional fields need JSON mapping coverage, not new DB columns or a schema bump. Confirm CatalogJson automatically serializes the updated model. Build aisle sections from state.items with `(createdAt, clientId)` ordering; use actual timestamp parsing for offsets rather than lexical ordering of arbitrary ISO strings. Map headings through strings.xml resources.

Invoke polling via a screen lifecycle-aware LaunchedEffect/`repeatOnLifecycle(STARTED)` using current cookbook and refresh generation. The ViewModel loop calls the existing repository refresh path, not refreshCookbooks or mutation retry. Use cancellable `delay(2_000)`, max 15 iterations, and return on error/completion. Verify cookbook/session identity remains valid before a response is applied. Keep the repository's existing Mutex and server-acknowledgement write rule.

- [ ] **4. Verify the full phase.** Run `bin/android-build`, `bin/android-test`, and `bin/android-test --device`. Run `bin/ci > tmp/ci.log 2>&1` and inspect exit status/summary. Repeat iOS checks only if this task changes its shared contract. Update durable docs with metadata and native refresh behavior. Manually verify English/German/French manual input, household items, unavailable LLM, and old clients.
- [ ] **5. Commit the task's files.** Commit message: `Carry shopping enrichment into Android aisle sections`. Review phase 2 independently before proceeding to aggregation.

## Deployment acceptance for this phase

- Backend migration and optional API fields precede native releases.
- In the chosen deployment environment, run `bin/rails shopping_list:enqueue_enrichment` through the normal remote execution mechanism. Verify actual unchecked rows gain metadata and the queue processes jobs.
- A recipe addition with current metadata saves without a redundant LLM call; manual input remains instant and usable even when background parsing fails.
