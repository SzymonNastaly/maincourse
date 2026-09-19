# iOS Interactive Onboarding Implementation Plan

> **For agentic workers:** Use `executing-plans` for inline implementation, or `subagent-driven-development` if delegation is selected. Implement task by task and review the actual simulator experience before integration.

**Goal:** Replace the iOS signup questionnaire and empty cookbook with a reusable interactive social-post-to-recipe experience that can save the chosen example after authentication.

**Architecture:** A bundled SwiftUI scene owns only its interaction and preview. The existing auth and authenticated-session owners coordinate an explicit keep intent with a reusable, retry-safe recipe-save API. This iteration resolves only trusted sample sources; later public recipes can use the same copy-to-cookbook operation. Reuse the current recipe content view and real navigation once the server recipe exists.

**Tech Stack:** SwiftUI, Observation, Foundation, existing SwiftData cache/APIClient, Rails/SQLite/Active Storage. No new third-party runtime in the first iteration.

**Spec:** `docs/superpowers/specs/2026-09-19-ios-interactive-onboarding-design.md`

## Global constraints

- iOS 18 minimum; first UI rollout is iOS only.
- XcodeGen owns the project. Change `hauptgang-ios/project.yml`; never hand-edit `project.pbxproj`.
- Preserve existing authentication providers, invitation routing, and authenticated-startup ownership.
- Use MainCourse tokens and semantic San Francisco text for the real app; reproduce recognisable social-post structure within the labelled sample.
- The preview has no network dependency. Keep intent is explicit and account-bound. Save failure remains recoverable.
- No duplicate recipes from retrying the same save operation, automatic resurrection after deletion, import-credit charge, or notification-permission prompt caused solely by the starter recipe.
- Save creates an independent editable copy with provenance; source publication/discovery and bulk cookbook copying remain outside this iteration.
- Preserve the questionnaire API for older iOS/Android clients.
- Do not modify the existing unrelated `maincourse-android/version.properties` change.

## Task 1: Build the playable local scene

**Create:**

- `config/starter_recipes/tomato-orzo-v1.json`
- `config/starter_recipes/tomato-orzo-v1.jpg`
- `hauptgang-ios/Hauptgang/Models/DemoRecipe.swift`
- `hauptgang-ios/Hauptgang/Views/Onboarding/ImportDemoView.swift`
- `hauptgang-ios/Hauptgang/Views/Onboarding/DemoSocialPostView.swift`
- `hauptgang-ios/Hauptgang/Views/Onboarding/DemoSharePanel.swift`
- `hauptgang-ios/HauptgangTests/Models/DemoRecipeTests.swift`

**Modify:** `hauptgang-ios/project.yml`, `hauptgang-ios/Hauptgang/Views/RecipeDetail/RecipeDetailContentView.swift`.

**Interfaces:** `DemoRecipe.load() throws -> DemoRecipe` loads the bundled JSON. Its `recipeDetail: RecipeDetail` projection supplies structured numeric amounts for portion scaling. `ImportDemoView(sample:onKeep:)` invokes `onKeep(sample.key)` only after an explicit Keep action. No network service enters this view.

1. Write a fixture-loading test before wiring the view. Assert key, positive servings, matching raw/structured ingredient counts, nonempty steps, and existence of its bundled photograph. Verify a doubled serving amount doubles a representative numeric ingredient. Run `bin/ios-test` to expose missing resources/types.
2. Author the original recipe, caption, and photograph. The photo must depict the selected recipe; use the image-generation skill if producing new artwork. Keep one shared resource pair and include it in the application target:

   ```yaml
   # Additional entry under targets.Hauptgang.sources
   - path: ../config/starter_recipes
     buildPhase: resources
   ```

3. Use a small scene stage enum and view-local `@State`:

   ```swift
   enum ImportDemoStage: Equatable {
       case post
       case instagramShare
       case destinations
       case recipe
   }
   ```

   Share advances to `instagramShare`; Share to advances to `destinations`; MainCourse advances to `recipe` with animation. Back/dismiss returns to the preceding stage. Keep is outside the simulated sharing controls and available on the final recipe. Ignore repeated destination taps once the stage changes.

4. Keep post, panels, and result inside one `ZStack` with one namespace. Match the hero geometry with the same ID in the mutually exclusive layouts:

   ```swift
   @Namespace private var heroNamespace
   @Environment(\.accessibilityReduceMotion) private var reduceMotion

   // Apply to the image in each mutually exclusive layout.
   // .matchedGeometryEffect(id: "recipe-photo", in: heroNamespace)

   // Destination selection:
   // withAnimation(reduceMotion ? .easeOut(duration: 0.15) : .smooth(duration: 0.8)) {
   //     stage = .recipe
   // }
   ```

   Use opacity/move transitions for social chrome and recipe text; matched geometry itself does not render the transition. Add a local-image override to the existing recipe content view so the result does not duplicate ingredient, instruction, or portion UI. Keep the demo preview outside the API/cache/navigation ID space.
5. Add previews for each stage, Reduce Motion, a small phone, and accessibility text. Run `bin/ios-build`, then interact on Simulator. Verify Share → Share to → MainCourse is understandable and that the image remains visually continuous. Save screenshots for review. Tune the visual interaction here before auth/server integration; this scene is retained production UI.

## Task 2: Implement reusable recipe saving with sample as the first source

**Create:**

- migration adding `recipes.starter_recipe_key` and `recipe_saves` with user, request UUID, original destination cookbook ID, normalized source type/key, nullable saved-recipe reference, timestamps, and unique user/request index
- `app/models/recipe_save.rb`
- `app/services/recipes/save.rb`
- `app/services/recipes/sample_source.rb`
- `app/controllers/api/v1/recipe_saves_controller.rb`
- `test/controllers/api/v1/recipe_saves_controller_test.rb`

**Modify:** `config/routes.rb`, `app/models/user.rb`, `app/models/recipe.rb`, `app/models/concerns/import_limitable.rb`, `app/controllers/api/v1/recipes_controller.rb`, `app/models/notifications/import_follow_up_campaign.rb`, `app/models/notifications/resurface_campaign.rb`, and their existing tests.

**Interfaces:** authenticated `POST /api/v1/cookbooks/:cookbook_id/recipe_saves`, body `{ "source": { "type": "sample", "key": "tomato-orzo-v1" }, "request_id": "<UUID>" }`; successful initial/retry calls return `{ "recipe_id": 123, "cookbook_id": 456 }`. Unknown source types/keys or invalid request UUIDs return 422, missing auth 401, unauthorized destination 403, request UUID reused with different source/destination 409, and replay whose saved recipe was deleted/inaccessible 410. The server resolves destination from the path after authorization, independently of the active-cookbook header.

1. Add focused request tests for authentication, exact fixture contents, attached photo, structured amounts, authorized owner/collaborator destinations, forbidden destination, conflicting ambient header, source/key rejection, repeated calls, conflicting UUID reuse, replay after deletion, and deliberate new save after deletion. A core regression test is:

   ```ruby
   test "retry returns the same recipe without consuming import allowance" do
     user = users(:one)
     _record, token = ApiToken.generate_for(user)
     headers = { "Authorization" => "Bearer #{token}" }
     before = user.monthly_import_count
     ids = []
     request_id = SecureRandom.uuid

     assert_difference("Recipe.count", 1) do
       2.times do
         post api_v1_cookbook_recipe_saves_url(user.personal_cookbook),
           params: { source: { type: "sample", key: "tomato-orzo-v1" }, request_id: request_id },
           headers: headers, as: :json
         assert_response :success
         ids << response.parsed_body.fetch("recipe_id")
       end
     end

     assert_equal 1, ids.uniq.length
     assert_equal before, user.reload.monthly_import_count
   end
   ```

2. Run `bin/rails test test/controllers/api/v1/recipe_saves_controller_test.rb` and confirm the new contract is absent before implementation.
3. Add `resources :recipe_saves, only: [:create]` inside the existing API `resources :cookbooks` block. In this controller, skip the ambient active-cookbook callback; after token authentication, resolve `current_user.cookbooks.find_by(id: params[:cookbook_id])` and reject inaccessible destinations. `Recipes::Save.call(user:, cookbook:, source:, request_id:)` validates source/request syntax, locks the user, and checks the receipt before creating anything. `Recipes::SampleSource.resolve(key:)` returns trusted fixture content and provenance for the allowlisted key; reject any other source type. Keep this as a simple branch, not a plugin framework. Later `type: recipe` will resolve an authorized readable/copyable Recipe and feed the same persistence path.
4. For a new operation, create the prepared recipe and receipt transactionally. Set its user to the authenticated actor and its cookbook to the authorized destination. Persist structured ingredients with `replace_ingredients_from_hashes`, attach the local photograph, and set the sample marker from trusted resolver metadata. Enqueue no extraction jobs. For a receipt retry, require its original destination/source to match before returning its saved recipe. Nullify the saved-recipe reference on deletion while preserving the receipt; replay returns 410. A genuinely new request UUID is a new explicit save and may recreate a deleted copy. If the saved recipe has moved, authorize its current cookbook before returning it. Do not use a title or source-URL match for idempotency. Record the normalized source reference in the receipt to retain provenance without requiring the source to exist forever.
5. Add nullable `starter_recipe_key` to existing list/detail JSON. Exclude marked recipes from `monthly_import_count` and recipe-based lifecycle candidates:

   ```ruby
   # Add this filter to the relevant existing relations.
   .where(starter_recipe_key: nil)
   ```

   Add regression tests showing the starter does not change remaining imports or become a campaign candidate. Existing real-recipe behaviour must continue to pass.
6. Run the new request tests, existing recipe controller tests, import-limit tests, and both recipe campaign suites. Review the full API result by fetching the returned recipe through the normal authenticated show endpoint.

## Task 3: Carry Keep through authentication and failure

**Create:**

- `hauptgang-ios/Hauptgang/Services/RecipeSaveService.swift`
- `hauptgang-ios/Hauptgang/ViewModels/OnboardingCoordinator.swift`
- `hauptgang-ios/HauptgangTests/ViewModels/OnboardingCoordinatorTests.swift`

**Modify:** `OnboardingFlowView.swift`, `RootView.swift`, `AuthenticatedAppShell.swift`, and `MainTabView.swift` under the existing Views directory; `Services/OnboardingService.swift`; `App/HauptgangApp.swift`.

**Interfaces:**

```swift
struct RecipeSaveResult: Decodable, Equatable, Sendable {
    let recipeId: Int
    let cookbookId: Int
}

struct RecipeSaveSource: Codable, Equatable, Sendable {
    let type: String
    let key: String
}

protocol RecipeSaving: Sendable {
    func save(source: RecipeSaveSource, toCookbookId: Int, requestId: UUID) async throws -> RecipeSaveResult
}

struct PendingStarterRecipe: Codable, Equatable, Sendable {
    let sampleKey: String
    let requestId: UUID
    var userId: Int?
    var cookbookId: Int?
}
```

`RecipeSaveService` uses the existing authenticated `APIClient` and encodes source/request ID in the body with the explicit destination in the path. `RecipeSaveSource` only exposes the sample key in this iteration; extend its coding for a numeric recipe ID when that source is actually implemented. `OnboardingCoordinator` receives the service and an injected UserDefaults store; the root owns its observable lifetime. It persists only completion/auth boundaries and the explicit pending record, never animation progress or credentials.

1. Test the coordinator against an injected service before networking: explicit Keep and its UUID survive reconstruction; repeated taps/retries reuse the UUID; Skip creates no keep intent; authentication binds the pending intent to its user and personal cookbook; a failed request retains Retry; a successful request is consumed once; logout clears the bound intent; a late result cannot navigate another account; a 410 stops replaying the old operation, while a later explicit Keep gets a new UUID.
2. Implement those transitions with one mutually exclusive save state and a cancellable task. Before publishing a response, verify the authenticated user and pending record still match those captured at request start. Never infer a keep intent from simply viewing or completing the animation.
3. Replace the question sequence in `OnboardingFlowView` with `ImportDemoView` and the existing `LoginView`. Keep old completed-install semantics. Stop sending quiz answers from the new iOS entry flow. Retain compatibility helpers still used by auth/older clients. Extend the debug reset scheme's existing cleanup to include the new local progress keys.
4. After existing authenticated startup resolves cookbook context, bind the destination to the personal cookbook and attempt the pending save. Never silently change the destination on retry. Show the selected preview with a Saving/Retry/Continue affordance during this continuation. Do not make server success a precondition for the global session reaching its terminal startup state. Clear explicit pending intent when the user chooses Continue without saving.
5. Refresh/switch through `AuthenticatedSessionViewModel` and navigate using the returned real recipe ID. Reuse the existing pending-recipe navigation pattern in `MainTabView`; do not route a local preview through `RecipeDetailView(recipeId:)`. External invitation/notification navigation takes priority; preserve a way to open the newly saved example without overriding that destination.
6. Run coordinator tests and existing auth/startup tests. Manually exercise email signup, existing-account sign-in, provider cancellation, process restart at auth, save failure, and retry. Confirm no flash of the old empty state between authentication and the continued preview.

## Task 4: Reuse the experience after Skip

**Create:** `hauptgang-ios/Hauptgang/Views/Onboarding/RecipeWelcomeView.swift`.

**Modify:** `Views/RecipesView.swift`, `Views/SettingsView.swift`, `Models/Recipe.swift`, `Models/PersistedRecipe.swift`, `Services/RecipeRepository.swift`, and related model/repository tests.

**Interfaces:** `RecipeWelcomeView` receives the existing import actions and a callback to open `ImportDemoView`. List/detail/cache models preserve optional `starterRecipeKey`. The welcome state is eligible only after the active cookbook resolves successfully with zero recipes.

1. Add behavioural tests for backward-compatible decoding (old responses omit the marker), marker round-trip through cache, successful-empty eligibility versus load failure, and a library containing only starter content being ineligible for automatic notification prompting.
2. Replace “No recipes yet / Your recipes will appear here / Refresh” with Save your own recipe and the interactive-example invitation. Reuse Take Photo, Choose from Library, and Paste from Clipboard actions already present in the toolbar. Failed loading gets Retry; it must not masquerade as a new cookbook.
3. Present the same `ImportDemoView`. In an authenticated context, Keep saves immediately with visible My Recipes destination copy and a new persisted request UUID; it does not reopen auth. Remember invitation dismissal per user and expose Replay example in Settings. Completion or deletion does not reset dismissal. A later deliberate Keep can save a new copy; replaying an old request cannot.
4. Preserve `starterRecipeKey` across API decoding and local persistence. Gate the existing library notification request on a genuine nonstarter recipe:

   ```swift
   let hasPersonalContent = recipeViewModel.recipes.contains {
       $0.starterRecipeKey == nil
   }
   ```

   Also retain the existing transient-UI suppression. This change does not alter explicit shopping-list or Settings notification requests.
5. Run focused model/repository tests and `bin/ios-build`. Interact with a clean installation that skips immediately, completes signup, tries the sample, saves it, and returns to the cookbook. Verify that an existing nonempty account opens normally and an existing empty account can dismiss the invitation.

## Task 5: Verify the complete iOS iteration

1. Run `bin/ios-build` and `bin/ios-test`; inspect actual results and the build wrapper's warnings/errors summary.
2. Run `bin/ci > tmp/ci.log 2>&1` with the command's actual exit status preserved. Inspect the summary and any failed-step output; never rely on a piped tail's exit code.
3. Capture and inspect the post, both sharing stages, transformed recipe, signup continuation, and skipped-onboarding cookbook. Include small iPhone, iPad, large text, Reduce Motion, and VoiceOver checks. Ensure the demo still works without connectivity.
4. Validate save/retry against a local Rails instance: interrupted response does not duplicate; old-request replay after deletion does not resurrect; a deliberate new save after deletion works; changed source/destination with the same UUID is rejected; signup/provider cancellation preserves a recoverable state; logout/account change does not transfer a pending save; invitations still reach their destination.
5. Confirm normal imports remain unchanged, the starter does not consume import allowance, and the starter alone does not request notifications or qualify for recipe lifecycle campaigns.
6. Update `docs/ios-authenticated-startup.md` and `docs/lifecycle-notifications.md` with the final ownership/filter behaviour. Add `docs/ios-interactive-onboarding.md` for durable replay, resource, and retry conventions once implementation has been verified. Record any genuinely deferred work in GitHub Issues, not source TODOs.
7. Review the resulting diff and simulator evidence before proposing release. A TestFlight/App Store release is outside this implementation plan and requires an explicit release request.

## Expected implementation order

The playable native scene is the first reviewable result. Complete the reusable recipe-save operation and authentication continuation after the interaction feels right, then reuse it in the empty cookbook. This keeps early visual iteration inexpensive while the final scope still covers both first-use entry points. Source visibility, public browsing, and copying an entire cookbook are future features, not additional onboarding tasks.
