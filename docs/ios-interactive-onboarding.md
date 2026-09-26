# Interactive recipe-saving example

The iOS introduction has four steps, owned by `OnboardingFlowView`:

1. **Welcome** (`OnboardingWelcomeView`): brand, the promise, and `RecipeSourcesAnimation` — a post, a cluttered website, a photographed cookbook page and a screenshot drift in, then merge into one clean recipe card built from the sample. It plays once and rests on the card. Reduce Motion shows only the final card; accessibility text sizes drop the illustration. "Get started" and "I already have an account" stay pinned to the bottom.
2. **Demo** (`ImportDemoView`): the playable sharing example described below. Its bottom action is "Continue".
3. **What a saved recipe unlocks** (`OnboardingFeaturesView`): portions (the stepper doubles once on appear), meal plan, shopping list and shared cookbooks, each as a small illustrated fragment of the real interface. "Sign up to keep this recipe" records the keep intent; "Continue without it" clears any intent. Both lead to signup. This step exists only in the pre-signup flow, not in the authenticated replay.
4. **Signup** (the embedded `LoginView`). Back returns to step 3 once the demo has finished, so the demo does not have to be replayed.

The quiz that used to precede signup is gone from iOS, together with its answer upload and device-ID linking. Android still posts quiz answers to `onboarding_response`; the Rails side is unchanged and ignores a missing `onboarding_device_id`.

The demo is a local, playable social-post sharing example. It follows the familiar Share → Share to → MainCourse route, then reveals a usable recipe with portion scaling. The simulated sharing panels stay inside the app; real imports still use the existing share extension, clipboard, and photo flows.

The original sample lives in `config/starter_recipes/tomato-orzo-v1.json` and its adjacent JPEG. XcodeGen includes those exact resources in the app, and Rails reads the same files when saving. The photograph was generated with the approved imagegen CLI fallback; its final prompt is stored alongside it. Loose JPEG resources are rendered through UIKit-backed SwiftUI images. Keep the sample key versioned: changing the meaning of an existing source would make persisted retry intents ambiguous.

`ImportDemoView` owns presentation only. It has no account or network dependency and never sends the preview's synthetic ID to the API. The signup flow and the empty cookbook reuse it. The empty invitation appears only after a successful empty load and can be dismissed per user. Completing the sharing exercise records a pending dismissal that binds to the next authenticated user, so the same example is not offered again if they continue without keeping it. Settings does not offer an example replay. The empty cookbook leads with the compact “Save recipes” heading, followed by the import button and sharing instructions as alternative ways to start.

`OnboardingCoordinator` is owned by `RootView`. Only "Sign up to keep this recipe" or an authenticated Keep creates a durable request UUID. Anonymous intent binds at the first authentication boundary; an already authenticated Keep binds immediately. Destination binding waits for the existing session owner to resolve the personal cookbook. Retries retain the original source, destination, and UUID. A session change clears bound intent and stale UI state; late results cannot navigate or alter another account.

Saving displays the selected preview while normal authenticated startup reaches its usual terminal state. Network failure offers Retry and Continue. Successful saving refreshes the destination and opens the real recipe detail. An invitation or notification retains navigation priority; the saved example remains accessible through an Open banner rather than replacing that destination.

## Save operation

`POST /api/v1/cookbooks/:cookbook_id/recipe_saves` creates an independent editable copy, currently from the trusted sample source only:

```json
{
  "source": { "type": "sample", "key": "tomato-orzo-v1" },
  "request_id": "550e8400-e29b-41d4-a716-446655440000"
}
```

The response supplies `recipe_id` and `cookbook_id`. Authorization uses the destination path independently of the active-cookbook header. Future public recipe saves can extend the source resolver without adding an onboarding-specific endpoint; public visibility and bulk cookbook copying are not implemented here.

A user-locked transaction records a `RecipeSave` receipt with the normalized source and original destination. Image upload completes before the receipt is committed. Replaying the same request returns its recipe; changing the payload returns 409. Deleted or inaccessible saved recipes return 410. Receipts survive recipe deletion, so old retries cannot resurrect content. A new explicit Keep uses a new UUID and may save another copy.

The server assigns `starter_recipe_key`; clients cannot grant themselves that policy exception. It round-trips through list/detail responses and SwiftData schema V7. Starter recipes do not consume free imports, qualify for recipe lifecycle campaigns, or by themselves trigger the library notification prompt. Explicit shopping-list and Settings permission actions retain their usual behavior.

## Local iteration

Use the `Hauptgang (Reset Onboarding)` scheme or `-resetOnboarding YES` to reset local introduction state in a debug build. The example itself works without a server. To exercise signup and saving, run Rails from the same checkout on port 3000. Run `bin/rails db:seed` before running the existing live iOS integration tests; they expect the documented development test account.

The interface uses native SwiftUI transitions and shared image geometry. Selecting MainCourse first shows the same processing treatment as the share extension for 1.5 seconds, giving the import action time to register before the recipe appears. Reduce Motion uses a short crossfade. Sharing panels keep real controls and text, with a scrolling fallback for large text. At accessibility text sizes the bottom action wraps and the cooking-mode control moves into the recipe content flow to avoid overlapping the title. Historical SwiftData schemas must stay frozen when adding cache fields; V6-to-V7 migration preserves both cached recipe details and pending shopping-list edits.

The demo shows Back (to the welcome step) and Log in in its navigation bar. After the sharing exercise, Continue becomes the bottom recipe action and leads to the features step, where keeping records the intent so successful registration automatically saves the sample. Each sharing step gets a hint after 1.2 active seconds: Share and Share to… gain a solid green fill and pulsing outline, and MainCourse gains the same outline. Hints reset when leaving a step or backgrounding the app, and reappear when revisiting a step. Reduce Motion keeps the highlights static.

The simulated social panel uses fictional contacts; the app panel uses muted placeholders and a horizontal row initially sized to leave half of MainCourse visible at the trailing edge. After the idle delay, the row demonstrates a short leftward swipe and return, then gently scrolls to fully reveal MainCourse. This plays once per panel visit; touching the row cancels the demonstration immediately. Reduce Motion and VoiceOver skip the automatic scrolling. The instruction changes from “Swipe left to find MainCourse” to “Tap MainCourse to save the recipe” when the destination is fully visible. These placeholders never send content to another app. The creator avatar is a bundled crop of the owner-provided photo, separate from the cookbook brand mark.

Android and web retain their current introduction flows during the iOS iteration. Their future rollout can reuse the sample resources and save operation.
