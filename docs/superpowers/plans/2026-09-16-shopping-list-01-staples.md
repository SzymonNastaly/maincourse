# Staple Review Defaults Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Implementation requires leaving Plan mode; do not modify application files while still planning.

**Goal:** Default common staples to excluded in recipe shopping review on web, iOS, and Android, with an explicit user override.

**Architecture:** Rails owns an exact canonical-name policy and exposes a boolean on structured recipe ingredients. Clients consume the boolean in their existing review state; actual shopping writes honor the user's selections.

**Tech Stack:** Rails 8.1, Hotwire, SwiftUI iOS 18+, Kotlin/Compose.

**Spec:** [Shopping list design](../specs/2026-09-16-shopping-list-design.md), Decisions 1 and Global constraints.

## Global Constraints

- Rails 8.1 / Ruby 3.4.7; Hotwire and Tailwind CSS v4; no Node.js/npm required.
- Native SwiftUI iOS 18+; Kotlin/Jetpack Compose Android with existing SDK settings.
- Keep the app's light-only design, semantic tokens, native chrome, and existing tiles/cards. IBM Plex Mono is for numerics only.
- Store/display original-language input. Canonical English names are matching metadata, never automatic replacement display text.
- Native protocol additions are optional/backward-compatible; older payloads and cached JSON remain readable.

All repository paths below are relative to the repository root. Follow root and platform AGENTS.md. This phase has three independently testable tasks.

---

## Task 1: Rails policy, recipe API, and web review

**Files**
- Create: `app/services/shopping_list/policy.rb`
- Modify: `app/controllers/api/v1/recipes_controller.rb`
- Modify: `app/views/recipes/_add_to_list_dialog.html.erb`
- Modify: `app/javascript/controllers/list_review_controller.js` (update its all-selected assumption/comment)
- Create: `test/services/shopping_list/policy_test.rb`
- Modify: `test/controllers/api/v1/recipes_controller_test.rb`
- Modify: `test/system/recipes_test.rb`
- Modify: `docs/ingredients.md`

**Interfaces**
- Produces `ShoppingList::Policy.default_included?(canonical_name) -> boolean`.
- Adds `shopping_default_included` to every `structured_ingredients` object in detail and batch JSON.
- Web still posts only selected rows using the current hidden-field disabling mechanism.

- [ ] **1. Write policy and boundary tests.** In a Minitest `ShoppingList::PolicyTest`, cover the exact staple set and safe negatives:

```ruby
test "excludes only explicit common staple identities" do
  %w[water salt].concat(["tap water", "table salt", "sea salt", "kosher salt", "black pepper", "ground black pepper"]).each do |name|
    assert_equal false, ShoppingList::Policy.default_included?(name), name
  end
  [nil, "", "pepper", "red pepper", "sparkling water", "salt substitute", "olive oil"].each do |name|
    assert_equal true, ShoppingList::Policy.default_included?(name), name.inspect
  end
end
```

Add API assertions for a salt ingredient with canonical_name `salt` and a quantity-less ingredient without metadata, in both show and batch responses. Extend the existing browser review test with an enriched salt row: it is visible but unchecked, count excludes it, checking it restores the count, and submitting includes it. Add an all-staple case with Add disabled. Do not globally change existing fixtures to enriched salt: legacy default-true tests must still exercise compatibility.

- [ ] **2. Run the new tests and confirm the expected missing policy/field failures.**

```bash
bin/rails test test/services/shopping_list/policy_test.rb test/controllers/api/v1/recipes_controller_test.rb
bin/rails test:system test/system/recipes_test.rb
```

- [ ] **3. Implement the policy and consume it.**

```ruby
module ShoppingList
  module Policy
    STAPLES = ["water", "tap water", "salt", "table salt", "sea salt", "kosher salt", "black pepper", "ground black pepper"].freeze

    def self.default_included?(canonical_name)
      !STAPLES.include?(canonical_name)
    end
  end
end
```

In `recipe_detail_json`, add `shopping_default_included: ShoppingList::Policy.default_included?(i.canonical_name)`. Render web checkboxes with the boolean attribute present only when included (never `checked="false"`). Initialize the server-rendered count/disabled state from the same policy; Stimulus still recomputes after interaction. Set initially excluded rows' hidden fields disabled server-side as well, so the initial form cannot accidentally submit them before Stimulus connects. Keep the user able to check every row. Use copy: `Common staples are excluded by default. Include anything you need.`

Document the boolean in `docs/ingredients.md` and correct the recipe serializer location to `recipe_detail_json` in the API controller.

- [ ] **4. Rerun the commands above.** Confirm the original 36-hour review/replace tests pass alongside the new defaults and the recipe records' raw text/positions are unchanged.
- [ ] **5. Review the diff and commit the task's files.** Commit message: `Default common staples out of recipe shopping review`.

## Task 2: iOS defaults, cached decoding, and review draft creation

**Files**
- Modify: `hauptgang-ios/Hauptgang/Models/StructuredIngredient.swift`
- Modify: `hauptgang-ios/Hauptgang/Models/ShoppingListDraftItem.swift`
- Modify: `hauptgang-ios/Hauptgang/Views/RecipeDetail/RecipeDetailView.swift`
- Modify: `hauptgang-ios/Hauptgang/Views/ShoppingListReviewSheet.swift`
- Modify: `hauptgang-ios/HauptgangTests/Models/StructuredIngredientTests.swift`
- Create: `hauptgang-ios/HauptgangTests/Models/ShoppingListDraftItemTests.swift`

**Interfaces**
- `StructuredIngredient.shoppingDefaultIncluded: Bool`, initializer default true; absent/null JSON decodes true and encoding preserves false.
- `ShoppingListDraftItem.init(ingredient: StructuredIngredient, scale: Decimal)` centralizes the existing name/details split and sets `isChecked = !ingredient.shoppingDefaultIncluded`.

- [ ] **1. Write decoding and review-draft tests.**

```swift
@Test func stapleDraftStartsExcluded() {
    let ingredient = StructuredIngredient(
        id: 1, position: 0, name: "Salz", canonicalName: "salt",
        shoppingDefaultIncluded: false, raw: "Salz nach Geschmack"
    )
    let draft = ShoppingListDraftItem(ingredient: ingredient, scale: 1)
    #expect(draft.isChecked)
}

@Test func oldIngredientDefaultsToIncluded() {
    let ingredient = StructuredIngredient(id: 1, position: 0, raw: "salt")
    #expect(!ShoppingListDraftItem(ingredient: ingredient, scale: 1).isChecked)
}
```

Extend existing Codable tests with false round trips through both API snake_case decoding and cached camelCase JSON; legacy/missing/null boolean -> true. Cover toggling the excluded draft back to `isChecked = false`, all-excluded Add disabled, and quantity-less raw fallback. Preserve the existing distinction that checked review rows are excluded, whereas submitted items always have checkedAt nil.

- [ ] **2. Run `bin/ios-test`; confirm the new property/initializer tests fail before implementation.**
- [ ] **3. Implement and connect the draft initializer.** Add the property to StructuredIngredient's initializer, CodingKeys, decode, and encode paths:

```swift
self.shoppingDefaultIncluded = try container.decodeIfPresent(
    Bool.self, forKey: .shoppingDefaultIncluded
) ?? true
```

Move the existing `shoppingListSplit` behavior from RecipeDetailView into the draft initializer without changing raw fallback or formatter semantics. The view builds drafts through that initializer, filters blank names, and presents its existing sheet. Add the explanatory staple copy to the sheet using semantic text styles. Initialize defaults once per presentation, not after each rendering or toggle.

- [ ] **4. Run `bin/ios-build` and `bin/ios-test`.** Inspect review in Simulator with one ordinary row and one excluded staple; include the staple and submit; verify all-excluded Add behavior. Preserve 36-hour confirmation behavior.
- [ ] **5. Commit the task's files.** Commit message: `Use staple defaults in iOS shopping review`.

## Task 3: Android defaults and phase verification

**Files**
- Modify: `maincourse-android/app/src/main/java/com/getmaincourse/app/data/model/ApiModels.kt`
- Modify: `maincourse-android/app/src/main/java/com/getmaincourse/app/features/recipes/IngredientReview.kt`
- Modify: `maincourse-android/app/src/main/java/com/getmaincourse/app/features/recipes/IngredientReviewScreen.kt`
- Modify: `maincourse-android/app/src/main/res/values/strings.xml`
- Modify: `maincourse-android/app/src/test/java/com/getmaincourse/app/features/recipes/IngredientReviewTest.kt`
- Modify: `maincourse-android/app/src/test/java/com/getmaincourse/app/data/network/MainCourseServiceTest.kt`
- Create: `maincourse-android/app/src/androidTest/java/com/getmaincourse/app/features/recipes/IngredientReviewDefaultsTest.kt`

**Interfaces**
- Add `@SerialName("shopping_default_included") val shoppingDefaultIncluded: Boolean? = null` to StructuredIngredient; read as `!= false` at the review boundary.
- `IngredientReviewItem.included` initializes from the flag; existing `withIncluded` and `includedPayload` preserve stable client IDs and explicit selections.

- [ ] **1. Write the review test and JSON compatibility assertions.**

```kotlin
@Test fun stapleDefaultCanBeOverridden() {
    val ingredient = StructuredIngredient(
        1, 0, null, null, null, "sól", null, "sól do smaku",
        canonicalName = "salt", shoppingDefaultIncluded = false,
    )
    val review = IngredientReview.create(9, listOf(ingredient), 1, null) { "salt-id" }
    assertTrue(review.includedPayload().isEmpty())
    assertEquals(listOf("salt-id"), review.withIncluded("salt-id", true).includedPayload().map { it.clientId })
}
```

Keep existing default-included tests, add mixed and all-excluded Compose tests, and decode old/missing/null/false flags using the existing Retrofit JSON configuration. Include a JSON cache round trip of false.

- [ ] **2. Run the focused unit test and confirm the intended failure.**

```bash
bin/android-gradle :app:testDebugUnitTest --tests '*IngredientReviewTest' --tests '*MainCourseServiceTest'
```

- [ ] **3. Implement the optional model field and review assignment.**

```kotlin
IngredientReviewItem(
    newId(), name, details, recipeId,
    included = ingredient.shoppingDefaultIncluded != false,
)
```

Add the explanatory text via `strings.xml`. Keep the UI list containing excluded items and leave the 36-hour confirm flow intact. No Room schema change is needed: recipe details are JSON and optional fields decode from old cache records.

- [ ] **4. Verify Android and the complete phase.** Run `bin/android-build`, `bin/android-test`, and `bin/android-test --device` with an emulator. Run `bin/ci > tmp/ci.log 2>&1`, inspect exit status and summary. Native checks already run need repeating only if later changes affect them. Verify manual addition of salt remains included on every platform.
- [ ] **5. Commit the task's files.** Commit message: `Use staple defaults in Android shopping review`. Phase is ready for review; backend must deploy before native releases for the new defaults to become available.
