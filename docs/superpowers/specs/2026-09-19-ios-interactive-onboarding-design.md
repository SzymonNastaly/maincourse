# iOS interactive recipe-import onboarding

## Product direction

The agreed experience is an interactive Instagram-style example inside MainCourse: tap Share, choose MainCourse, and watch the same post become a readable recipe. The user stays in the app. Replace the iOS questionnaire with this experience and reuse it in the empty cookbook for people who skip the introduction.

This document proposes the technical implementation. It does not record a shipped change. Android and web keep their existing entry flows during the iOS iteration.

## Experience

1. Start on a recognisable social post with a food photograph, MainCourse example creator, caption, and sharing control. Label the scene “Interactive example”. Keep Sign in and Skip accessible outside the simulated interface.
2. Tapping Share reveals an Instagram-style sharing panel. Its “Share to” action reveals a compact practice destination picker containing MainCourse. These are SwiftUI controls within the scene, not external apps or the operating system's share sheet.
3. Selecting MainCourse dismisses the sharing panel and transforms the post into a recipe. Keep the same photograph visually continuous. Fade the social chrome away, resize the photograph into the recipe hero, and reveal ingredients and instructions.
4. End on an interactive recipe using the existing recipe content and portion controls. The example has no artificial extraction delay. The interface calls it a preview until it is saved to an account.
5. “Keep this recipe” records the user's intent and opens the existing signup/sign-in flow. Successful authentication saves the prepared recipe and continues to its normal recipe detail screen.
6. If authentication or saving fails, retain the preview and offer Retry or Continue without saving. Never claim a save succeeded before the server confirms it.

The first version uses one original vegetarian recipe, One-pan tomato and chickpea orzo. Use one original photograph and matching caption, structured ingredients, instructions, servings, and times. Validate that the caption and final recipe agree. An image is sufficient for this iteration; a clip is not needed for the interaction.

Animation target: roughly one second after selecting MainCourse. Tune this by watching the simulator. Reduce Motion uses a short crossfade, and VoiceOver reaches the same result without relying on animation. Maintain real text, buttons, large tap targets, and a scrollable layout at accessibility text sizes.

## Implementation choice

Use native SwiftUI on the existing iOS 18 minimum. `matchedGeometryEffect` links photograph geometry across the two layouts; normal transitions handle rendering, opacity, and ingredient/step reveal. Use animation completion to settle into the final recipe, not unrelated timers. Phase/keyframe animation is available if the initial transition needs more control, but is not required up front.

One small value-type scene state owns mutually exclusive stages. No general animation engine, web view, remote configuration, or actual share extension is needed. Keep a single view hierarchy for the transformation so matched geometry is not expected to cross independent sheets or navigation stacks.

Rive supports interactive state machines and runtimes across Apple, Android, and web, but requires authoring and maintaining a Rive asset. It becomes attractive if the design develops substantial custom illustration. Lottie plays authored vector animation and can supply a small decorative segment, but does not remove the need for the native interaction and usable recipe. Neither dependency is proposed for this first version.

## Reuse and boundaries

- `ImportDemoView`: reusable scene with a callback when the user chooses to keep the example. It has no authentication or network dependencies.
- `DemoSocialPostView` and `DemoSharePanel`: the few simulated controls needed for this example, with MainCourse-owned sample content.
- `DemoRecipe`: bundled content that can project to the existing `RecipeDetail` presentation. The temporary preview identity is never stored or sent to a recipe endpoint.
- `RecipeDetailContentView`: accept a local hero-image override while retaining its current remote-image path. Reuse its ingredient/instruction display and portion scaling.
- `OnboardingFlowView`: replaces the three question screens with the reusable scene and existing `LoginView`.
- `OnboardingCoordinator`: owns durable completion, the explicit keep intent, account binding, save/retry state, and the pending continuation. The root owns its lifecycle.
- `RecipeWelcomeView`: replaces the unhelpful empty state with Save your own recipe and Try this example. Reuses the existing import actions and `ImportDemoView`.

The normal recipe detail wrapper currently fetches from the API and enables authenticated actions. Reuse its content component in the preview, not that wrapper. After a successful save, use the normal wrapper and actual server recipe ID.

## Reusable recipe saving

The user expects future public recipes and public cookbooks to support saving recipes into a personal cookbook. Use one reusable authenticated operation: `POST /api/v1/cookbooks/:cookbook_id/recipe_saves`. Its meaning is “save an independent editable copy of this source in this destination cookbook”. This is a proposed product default: changes or deletion of the original must not silently change the user's saved ingredients or instructions. Keep source attribution. Following a live public cookbook is a different future action.

For onboarding, the body is `{ "source": { "type": "sample", "key": "tomato-orzo-v1" }, "request_id": "<UUID>" }`. The client creates and persists the request UUID when Keep is tapped, and reuses it for every retry. The destination is explicit in the path; onboarding resolves the authenticated user's personal cookbook and displays My Recipes. Authorize writes to that cookbook independently of any ambient active-cookbook header. The current owner/collaborator model permits both members to contribute; no new roles are needed.

The current source resolver supports only the trusted sample key. Later the same request shape can support `{ "source": { "type": "recipe", "id": 123 }, "request_id": "<UUID>" }`, with a read/copy permission check on that recipe. A recipe reached through a public cookbook uses the same recipe source reference. Do not implement public visibility, discovery, public pages, bulk cookbook copying, or a pluggable source registry during this iteration. Unknown source types are rejected today. Arbitrary web/Instagram URLs continue through the existing asynchronous import endpoints because extraction is a different operation.

Store the canonical sample JSON and image under `config/starter_recipes/`. Include those same resources in the iOS application through XcodeGen so the preview and server copy cannot drift. Android/web can reuse this content and endpoint later.

The save response is `{ "recipe_id": 123, "cookbook_id": 456 }`. An authenticated user who opens the example from another cookbook sees “Keep in My Recipes”. The recipe-copy service handles the resolved content, structured ingredients, photograph, destination, and provenance. Source-specific behaviour stays in the small source-resolution step.

Use `current_user.with_lock` and a persistent `RecipeSave` receipt keyed uniquely by user and request UUID. Store the original destination and normalized source reference with the receipt. Create the recipe, structured ingredients, image attachment, and receipt transactionally. A retry with the same payload returns the existing recipe, including after a lost response or app restart; reusing that UUID for a different source or destination returns 409. Do not deduplicate forever by user/sample pair.

Preserve the receipt with a null recipe reference after deletion; replaying that old request returns HTTP 410 and never resurrects deleted content. A new explicit Save action has a new request UUID and may intentionally save the source again. Repeated taps while one Save is in progress reuse the existing request. If the previously saved recipe has moved, return its current cookbook only if it remains accessible; otherwise return an unavailable result without leaking content or creating another copy. Provenance and save status must not depend on recipe titles or source URLs matching.

Sample-specific policy remains narrow: the trusted sample resolver marks the copy with nullable `starter_recipe_key`. Clients cannot assign that marker. Return it in recipe list/detail responses, preserve it in the iOS cache, and exclude marked recipes from the free monthly import count and recipe-based lifecycle campaigns. This exception must not automatically apply to future public-recipe saves. A library containing only the starter must not trigger the iOS notification permission request. User-initiated shopping-list actions retain their existing permission behaviour.

The endpoint performs no scraping, extraction job, purchase check, or anonymous recipe persistence. Preview works offline; account creation and saving require connectivity.

## Authentication and lifecycle

Persist a keep intent and request UUID before opening authentication. Once authentication succeeds, bind it to that user ID and personal cookbook before attempting the server request. Keep that source/destination/request combination stable across retries. An in-flight result must be discarded if the session changes. Signing out clears a bound pending intent so another account cannot inherit it.

Run the continuation after the existing `AuthenticatedSessionViewModel` has resolved the cookbook context. Do not introduce another owner of authenticated startup. Show the selected preview while saving instead of briefly flashing an empty list. A slow or failed save must not hold the global startup splash indefinitely.

After the save response, refresh the destination cookbook and open the returned recipe through the existing recipe navigation mechanism. If an invitation or external destination is pending, preserve that navigation's priority and retain a nonintrusive continuation to the saved example.

Existing completion flags still prevent replaying onboarding on an established installation. Remove iOS quiz submission from the new flow, but retain the backend questionnaire endpoint and compatibility helpers used by Android/older builds. A user who skipped and still has no recipes can launch the same scene from the cookbook. Distinguish a successfully loaded empty collection from failed loading or lack of connectivity.

Dismissal of the example invitation is remembered per user; it does not reappear merely because a recipe is deleted. An optional replay entry in Settings keeps the example discoverable without making it a recurring interruption.

## Iteration and verification

First inspect the actual playable SwiftUI scene on the simulator. Tune the social-post appearance, sharing route, image continuity, and final recipe readability before connecting authentication. Then complete saving, continuation, and the empty-cookbook integration.

Verify: full sample flow; skip everything then try from the cookbook; direct personal import; email and provider authentication; cancelled signup; relaunch at authentication; failed save and retry; repeated requests; deleted sample; logout/account switch; invitation priority; existing installations; offline preview; actual empty versus failed loading; small iPhone, iPad, large text, VoiceOver, and Reduce Motion.

Run `bin/ios-build` and `bin/ios-test`. Backend changes also require focused Rails tests and `bin/ci` captured to a log with its actual exit status. Visual acceptance requires simulator interaction and screenshots, not only a successful compile.

## Sources checked

- Apple matched geometry: https://developer.apple.com/documentation/swiftui/view/matchedgeometryeffect(id:in:properties:anchor:issource:)
- Apple animation tools: https://developer.apple.com/videos/play/wwdc2023/10157/
- Apple Reduce Motion: https://developer.apple.com/documentation/swiftui/environmentvalues/accessibilityreducemotion
- Rive Apple runtime: https://rive.app/docs/runtimes/apple/apple
- Lottie: https://github.com/airbnb/lottie-ios
- Documented Instagram sharing route: https://recime.app/help/en/articles/11659005-import-from-instagram
