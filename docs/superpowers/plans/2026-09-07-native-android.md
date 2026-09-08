# Native Android Roadmap

## Goal

Ship a native Android app with public-v1 parity with functionality currently visible in the iOS app, while reusing the Rails API and preserving its account, cookbook, subscription, and notification semantics.

This is a living, long-horizon roadmap. GitHub issues are the source of executable tasks; this file records scope, sequence, gates, decisions, and handoff state rather than duplicating issue checklists.

## Current Status

| Milestone | Status | Outcome |
|---|---|---|
| 0. Enablement and scaffold | Complete | Native preview, local tooling, CI definition, and local verification complete; external accounts tracked in #92 |
| 1. First vertical slice | Complete | Email session through cached recipe list/detail passed local API 37 acceptance and final code review; evidence in #93 |
| 2. Identity and account | In progress | Core, Google, and Apple handoff implementation gates passed locally; live providers, release signing/App Links, and full review remain |
| 3. Recipe workflows | Planned | Search, editing, imports, cooking, and recipe actions |
| 4. Shopping list | Planned | Durable offline shopping workflow |
| 5. Collaboration | Planned | Shared cookbooks and invitations |
| 6. Subscription | Planned | RevenueCat and Google Play billing |
| 7. Notifications | Planned | FCM registration, delivery, tracking, and routing |
| 8. Release | Planned | Production hardening and Play release |

Milestones 0 and 1 are complete. Milestone 2's onboarding/account core, Google,
shared Rails/web/iOS Apple account-creation prerequisite, and Android Apple
browser handoff passed their separate local implementation gates on 2026-09-08.
Real Google and Apple accounts, registered HTTPS Apple acceptance, release-signed
provider verification, production App Link association, and the full milestone
review remain, so the milestone is not complete. Core evidence is tracked in
#97, Google evidence in #98, Apple prerequisite evidence in #99, handoff evidence
in #100, and external setup in #92.

### Working sequence while Play registration waits

The owner has no physical Android phone yet and cannot finish Play registration
for the next few days. Continue emulator-first development. Milestone numbers
identify scope, not a requirement to wait for every earlier external gate:

1. **Milestone 1:** complete email/session, cookbook switching, and cached recipe
   browsing against Rails. No Google/Apple/Firebase/Play setup is required.
2. **Milestone 2 core, then 3–5:** build onboarding/account settings, recipe
   workflows, offline shopping, and collaboration. Integrate Google/Apple as
   their own configuration becomes available; their gates do not block these
   core workflows.
3. **Milestone 7 can precede 6:** implement and exercise FCM on a Google-enabled
   emulator once Firebase/server credentials exist. Keep physical-device
   delivery verification outstanding until hardware is available.
4. **Milestone 6 and the store-dependent parts of 8:** finish Play billing,
   Play-signed identity checks, internal testing, and distribution after Play
   account access is available. Accessibility, performance, localization
   readiness, and release preparation can progress earlier.

Google Cloud OAuth, Apple Developer configuration, Firebase, and a development
HTTPS host are separate dependencies from Play Console. The local debug
certificate already enables Android OAuth client registration; see
`docs/android.md` for the package/fingerprint procedure. Debug app-link
verification uses a separate development host rather than adding debug signing
certificates to production trust.

Record emulator evidence as such. Camera/hardware behavior, physical-device
push reliability, real billing, and final Play-distributed sign-in/app links
retain their integration gates. A partially verified milestone stays partial;
unrelated milestones can continue. Public-v1 parity remains the release goal.

## Product Boundary

- Build in `maincourse-android/` with native Kotlin, Jetpack Compose, and Material 3.
- Start with one Gradle `:app` module organized into feature packages. Split modules only when demonstrated build or ownership pressure justifies it.
- Use `minSdk 29` as the baseline proposed and accepted for the scaffold.
- Keep the implemented compatible toolchain set pinned as recorded in `docs/android.md`; review upgrades deliberately and validate the set together.
- Support phones and tablets with adaptive navigation, such as bottom navigation at compact widths and a navigation rail at expanded widths. Do not build a dedicated two-pane product.
- Match the MainCourse light-only grey/green visual language. Android body text uses the native sans family; bundled IBM Plex Mono is reserved for numerics. Use Material Symbols for icons.
- Treat `app/assets/tailwind/application.css` as the web token source and `hauptgang-ios/Hauptgang/Utilities/MainCourseTheme.swift` as the native mapping reference. Preserve semantic roles rather than copying platform chrome.
- Public-v1 parity means visible iOS functionality, delivered through smaller internal milestones. It does not mean porting dormant source code or Apple-only platform mechanics.

## Architecture Constraints

### Client shape
- Compose screens receive observable state and emit user actions; repositories own local/remote reconciliation.
- Use coroutines and structured concurrency. Cancellation, retry, loading, empty, degraded, and error states must be explicit.
- Store bearer credentials in Android secure storage, never preferences or URLs.
- Room owns durable cached product data. Partition every user-owned row and query by user identity and cookbook identity where applicable.
- Keep navigation destinations and deep-link parsing typed and testable. Phone and tablet layouts share the same destinations and behavior.
- Android resources expose the MainCourse semantic colors, dimensions, and typography. Light mode is pinned until a dark palette is designed.

### Rails contract
- Reuse the existing Rails backend; do not create an Android-specific backend or rewrite the API.
- Authenticated cookbook-scoped requests send `X-Cookbook-Id`. Without it, Rails selects the personal cookbook; clients must not depend on that fallback after active-cookbook resolution.
- User and cookbook isolation applies to caches, search data, pending work, image metadata, and every repository query. Logout or account switching must not expose the previous user's data.
- Preserve authenticated startup ordering from `docs/ios-authenticated-startup.md`: establish user scope, load cookbooks, resolve the active cookbook, configure cookbook-scoped repositories/search, then refresh content. A failed refresh may enter a cached degraded state rather than block forever.
- The API issues opaque 90-day `ApiToken` sessions and has no refresh-token flow. Expiry or `401` returns the app to authentication after clearing protected local state.
- Offline recipe imports, recipe edits, cookbook administration, and collaboration are not promised. Queueing an operation is allowed only when the server contract makes retries safe.
- Follow full-versus-partial reconciliation in `docs/ios-offline-sync-patterns.md`; partial responses must never prune unrelated cached rows.

## External Enablement

External setup begins in Milestone 0 rather than waiting for the feature milestone and is tracked in [issue #92](https://github.com/SzymonNastaly/maincourse/issues/92).

| System | Setup action | Required by |
|---|---|---|
| Google Play Console | Reserve package/application identity, establish signing and tester access | 6 and 8 |
| Google Cloud | Register the local debug package/SHA-1 now; add production package/signing fingerprints later; no Play prerequisite | 2 provider track |
| Apple Developer | Confirm the existing web Services ID and grouped primary App ID can serve Android's web handoff without changing Apple subjects | 2 |
| Firebase | Register tested debug/release packages, obtain configuration safely, and plan FCM credentials; no Play prerequisite | 7 |
| RevenueCat | Add the Android app and Play products to the existing project and entitlement | 6 |
| Rails production config | Add only the provider credentials, callback allowlists, webhook configuration, and secrets required by approved milestones | 2, 6, and 7 |

Secrets and downloaded console configuration stay out of documentation and source control unless the platform explicitly defines a public client configuration file as safe to commit.

## Milestone 0: Enablement And Scaffold

**Status:** Complete (local scaffold gate, 2026-09-07).

**Scope:**
- Create `maincourse-android/` with a single `:app` module, package-by-feature source layout, pinned stable toolchain, and `minSdk 29`.
- Establish debug and release configuration without embedding production secrets.
- Add only Compose Material 3, Navigation 3, and scaffold test foundations. Defer HTTP/JSON, secure credential storage, Room, Coil, repositories, and product models until Milestone 1 uses them.
- Implement semantic light-theme tokens, native sans typography, bundled Plex Mono numeric typography, Material Symbols, and adaptive navigation primitives.
- Define local/development API configuration and a production configuration seam.
- Start every external enablement track above and record blocking console ownership or credential needs in GitHub issues.
- Add Android build, lint, unit-test, and Compose-test entry points suitable for CI.

**Gate:**
- A clean checkout can build and test the app with documented local configuration.
- A phone and tablet/emulator render the themed shell and adaptive navigation without claiming product parity.
- Toolchain and dependencies are pinned to versions actually resolved by the scaffold.
- External setup owners and blockers are represented in GitHub issues; configuration itself need not be complete to pass the scaffold gate.

**Verification recorded on 2026-09-07:**
- A fresh `clean assembleDebug assembleRelease testDebugUnitTest lintDebug lintRelease connectedDebugAndroidTest --no-build-cache --continue` through `bin/android-gradle` passed. Release output is unsigned; no store release was attempted.
- Two JVM tests and four Compose device tests passed on the API 37 ARM64 emulator. Device coverage includes top-level navigation, system Back, activity/state recreation, and compact/rail selection.
- The device suite also passed with tablet-sized bounds (1800x1200 at 240 dpi) and phone bounds with 200% text scaling. Screenshots were inspected for both layouts and system dark mode; the app remained light. Emulator settings were restored afterward.
- The SDK, command-line tools, image, and `MainCourse_Phone_API37` AVD are installed. Build scripts discover the SDK and Studio JBR without changing global shell configuration.
- CI is defined for API 29/36 instrumentation plus build/lint/unit tests, but has not run on GitHub yet. API 29/36 runtime results remain to be confirmed there; local runtime verification used API 37.
- Shared Rails/iOS code was not changed, and their suites were not run for this scaffold. Owner-controlled services, signing, and credentials remain tracked in [#92](https://github.com/SzymonNastaly/maincourse/issues/92).

## Milestone 1: First Real Vertical Slice

**Status:** Complete. Local acceptance and final code review passed on 2026-09-07.

**Design:** [`docs/superpowers/specs/2026-09-07-android-milestone-1-design.md`](../specs/2026-09-07-android-milestone-1-design.md).
**Implementation:** [issue #93](https://github.com/SzymonNastaly/maincourse/issues/93).
**Cleanup follow-up:** [issue #94](https://github.com/SzymonNastaly/maincourse/issues/94).

**Dependencies:** Rails API and local Android tooling only. Validate against a
local Rails server and emulator; no Play account, physical phone, provider
credentials, billing, or push configuration is needed.

**Scope:**
- Email signup/sign-in, secure 90-day session restoration, logout, and expired-session handling.
- Authenticated startup with strict user/cookbook scoping and a non-blocking cached degraded state.
- Cookbook discovery, active-cookbook persistence, cookbook switching, and forbidden-membership recovery.
- Cached recipe list, pull-to-refresh, recipe images/placeholders, and recipe detail.
- Detail parity for name, image, prep/cook times, servings, ingredients, instructions, and notes; actions arrive in later milestones.
- Room cache replacement only from authoritative full responses; cookbook switching cannot flash another cookbook's recipes.

**Gate:**
- A new or returning email user can authenticate, discover/switch cookbooks, browse cached recipes, refresh, and open detail on phone and tablet.
- Cold start, offline start with cache, empty state, server failure, `401`, and `403` are exercised without cross-user or cross-cookbook leakage.

**Local verification recorded on 2026-09-07:**
- The clean Android gate passed debug and minified unsigned release builds,
  debug/release lint, 73 JVM tests, and 62 device tests (135 total, no failures).
  Runtime device evidence is from the local API 37 emulator only; this is not a
  remote-CI or physical-device claim.
- The real `MainActivity` and local Rails server passed signup, rejected and
  successful login, restart/session restore, cookbook discovery/switching,
  cached list and previously opened detail under true airplane-mode network
  loss, an explicit connection state for unopened detail, empty/server-down
  recovery, and authenticated `401`, cookbook `403`, and recipe `404` recovery.
- Phone and tablet layouts were inspected at normal size; the tablet was also
  inspected at 200% text under a dark system appearance. The app remained light
  and usable. The real app was left installed and signed in only to a dedicated
  local development account.
- The unchanged Rails auth/cookbook/recipe contract suite remained green at 91
  tests and 271 assertions. No Rails code changed, and it was not rerun after the
  Android-only acceptance fixes.
- Release remains unsigned and fixed to the public HTTPS API. Provider auth,
  Play signing/distribution, remote CI, and physical-device validation remain
  independent gates in #92. The owner-recorded Android OAuth client ID is not
  integrated yet.
- Logout and purge coordinate local session, Room, and image removal, but do not
  promise flawless deletion across process death or an operating-system disk
  failure. The cross-process cleanup/purge protocol remains follow-up #94.
- Final review and scoped re-review accepted the implementation after regression
  fixes for cleanup cancellation, concurrent cookbook selection, and restored
  detail navigation. The final code fix is commit `31eaf60`; the fresh full gate
  includes those changes.

## Milestone 2: Identity And Account

**Status:** In progress. The onboarding/account core, Google implementation,
shared Rails/web/iOS Apple account-creation prerequisite, and Android Apple
handoff passed local implementation gates on 2026-09-08. The Apple handoff is
ready for the final whole-branch review, which has not yet occurred.
Owner-controlled real provider identities, registered HTTPS Apple acceptance,
release-signed verification, production App Link association, and the full
milestone gate remain open.

**Core design:** [`docs/superpowers/specs/2026-09-08-android-milestone-2-core-design.md`](../specs/2026-09-08-android-milestone-2-core-design.md).
**Implementation:** [issue #97](https://github.com/SzymonNastaly/maincourse/issues/97).
**Google design:** [`docs/superpowers/specs/2026-09-08-android-milestone-2-google-design.md`](../specs/2026-09-08-android-milestone-2-google-design.md).
**Google implementation:** [issue #98](https://github.com/SzymonNastaly/maincourse/issues/98).
**Apple prerequisite design:** [`docs/superpowers/specs/2026-09-08-apple-account-confirmation-design.md`](../specs/2026-09-08-apple-account-confirmation-design.md).
**Apple prerequisite implementation:** [issue #99](https://github.com/SzymonNastaly/maincourse/issues/99), addressing [issue #86](https://github.com/SzymonNastaly/maincourse/issues/86).
**Android Apple design:** [`docs/superpowers/specs/2026-09-08-android-apple-handoff-design.md`](../specs/2026-09-08-android-apple-handoff-design.md).
**Android Apple implementation:** [issue #100](https://github.com/SzymonNastaly/maincourse/issues/100).

**Sequence:** Implement onboarding and account/preferences independently of
provider enablement. Google and Apple can be developed and tested before Play
registration with the appropriate Cloud/Apple configuration and local signing
identity. Provider blockers must not stop Milestones 3–5; the full Milestone 2
gate remains open until all three sign-in methods are verified.
After the pending final whole-branch review, continue automatically into
Milestone 3 rather than waiting for owner-controlled provider and release gates.

**Scope:**
- Google and Apple sign-in in addition to email/password; all three methods are required for public v1.
- First-run onboarding parity: welcome, household size, what the user wants to save, diet choices, skip/resume, then embedded signup.
- Profile name editing, lifecycle-notification preference, account deletion, and sign out.
- Match provider-account linking and deletion/revocation behavior documented in `docs/oauth-sign-in.md`.
- Reuse the explicit Apple new-account rule before enabling the Android Apple
  path. The shared prerequisite is implemented under
  [#99](https://github.com/SzymonNastaly/maincourse/issues/99), but
  [#86](https://github.com/SzymonNastaly/maincourse/issues/86) stays open until
  the registered-HTTPS, real-account, and Hide My Email acceptance gates pass.

**Gate:**
- Email, Google, and Apple succeed with real provider accounts in debug and release-signed builds.
- Returning identities reach the same Rails user; private-relay, cancellation, invalid state/nonce, expired handoff, and deletion paths are verified.

**Core local verification recorded on 2026-09-08:**
- A clean API 37 gate passed debug and minified unsigned release builds,
  debug/release lint, 123 JVM tests, and 91 device tests with no failures or
  skips. Release remained fixed to the public HTTPS API, unsigned, and free of
  provider integrations.
- The real `MainActivity`, Keystore session, Room cache, and local Rails API
  passed answered onboarding linked to signup, skip, interrupted AUTH resume,
  failed signup then explicit retry, and upgrade from a valid Milestone 1
  session. Server-side inspection confirmed the expected answer attribution and
  no eager submission when restoring the AUTH step.
- Name and account-wide recipe-reminder changes survived force-stop and matched
  Rails. Offline attempts showed recoverable failures; name draft and the last
  acknowledged reminder value remained intact until explicit retry.
- A dedicated account deletion returned to signed-out state and rejected later
  login. Rails transferred its shared cookbook to the dedicated collaborator;
  that collaborator then signed in and retained the shared recipe. No production
  account or data was used.
- Phone and tablet layouts, embedded-auth system insets, keyboard action
  reachability, and 200% text were inspected on the API 37 emulator. Under dark
  system appearance the app remained light. Size, density, font, network, and
  night settings were restored, and the app was left signed in to the populated
  surviving development account.
- Rails onboarding/account/session/registration contracts passed 31 tests and
  73 assertions. Authenticated account `401` and local-cleanup failure are
  covered by automated fault-injection tests, not claimed as live Rails cases.
- Final code review and scoped re-review accepted the core slice after explicit
  onboarding schema serialization, stronger cancellation coverage, and clearer
  pending-local-save feedback. Final core code commit: `29d9a14`; the 214-test
  clean Android gate includes those fixes.
- The core gate does not close Milestone 2. Google then Apple integration, real
  provider identities, release signing, and the full milestone review remain
  pending. Cross-process cleanup after OS termination or broken storage remains
  the explicit #94 gap.

**Google local verification recorded on 2026-09-08:**
- Credential Manager 1.6.0 and Google ID 1.2.0 are integrated for an explicit
  Google button in standalone and onboarding auth. The public web/server client
  remains the token audience; the Android OAuth client is only the debug
  package/SHA-1 registration recorded in #92.
- The real `MainActivity` on the API 37 emulator opened Google's real
  add-account surface with zero Google accounts. Cancellation, rotation while
  the chooser was active, explicit retry, and subsequent email login remained
  usable. Phone and tablet layouts at 200% text exposed the Google action.
- Automated Android build, lint, JVM, and device coverage passed the local code
  gate: 140 JVM and 103 device tests, no failures/errors/skips, debug/release
  builds and both lint variants. Release compiled minified but remained unsigned.
  Rails production code was unchanged; its OAuth/identity/verifier baseline
  passed 17 tests and 66 assertions.
- Whole-slice review and scoped re-review accepted the Google implementation.
  Final code fix `5b40576` adds method-specific loading feedback, shared auth
  preparation, stronger failure/retry tests, and a smaller licensed Google font.
  The 243-test clean gate includes those fixes.
- No provider credential was returned, so the owner-recorded Cloud registration,
  consent, returning identity, and live Rails exchange remain unverified. These
  require an owner-added account and explicit authorization; they are distinct
  from the completed implementation gate. The Android Apple handoff now has its
  own local implementation gate, while the full Milestone 2 gate remains open;
  the local Google code review is complete.

**Shared Apple prerequisite verification recorded on 2026-09-08:**
- Rails, web, and iOS now require an explicit decision before an unknown Apple
  identity creates an account. Returning Apple identities keep their existing
  owner; the rule neither guesses by unlike email nor automatically merges or
  links accounts.
- Mocked-provider Rails acceptance covers the rendered web confirmation page,
  request-phase intent, callback tampering, strict native boolean intent,
  legacy-client guidance, no first-attempt user/session/token creation, a single
  confirmed creation, and unused refresh-token revocation. Native fake-provider
  tests cover a fresh second credential/code/nonce and cancellation recovery.
- Whole-slice review and scoped re-review passed. Production code is at
  `eaa1d02`; final regression strengthening is `820e7d8`. Rails CI passed 931
  tests plus 9 system tests (2 expected corpus skips). The iOS result bundle
  reports 261 tests: 259 passed and 2 recipe-fixture skips. Android's 140 JVM
  tests and lint passed for shared-contract continuity.
- These are implementation gates, not real-provider proof. A registered HTTPS
  callback, owner Apple account, Hide My Email, and the possible absence of the
  one-time Apple name on the second authorization remain acceptance checks.
  The full Milestone 2 provider and owner checks remain open. Keep #86 open until
  that evidence exists.

**Android Apple handoff implementation verification recorded on 2026-09-08:**
- Rails now owns a digest-only, PKCE-bound, five-minute handoff transaction and
  one-minute single-use exchange. The existing Apple callback uses strict
  Android-only state/nonce handling, preserves explicit account creation, and
  creates no browser login session. Browser form pages use `strict-origin` so
  normal CSRF origin checks work without disclosing handle-bearing URLs; all
  non-form responses remain `no-referrer`. Full Rails CI passed 989 tests plus
  9 system
  tests; the only 2 skips were the expected recipe-corpus snapshot skips.
- The clean Android gate passed debug and minified unsigned release builds,
  debug/release lint, 165 JVM tests, and 109 device tests with no failures,
  errors, or skips on the API 37 emulator.
- The real `MainActivity` opened the same-origin local landing in Chrome, which
  showed actionable HTTPS-required guidance rather than fake Apple consent. Its
  CSRF-protected Cancel followed the real 302 custom-scheme return and released
  auth admission. A callback after process death was discarded without an
  exchange, explicit retry remained available, and email login still reached
  the protected app.
- A controlled local transaction authorized to the existing dedicated fixture
  completed the real implicit callback, PKCE exchange, encrypted session write,
  and Room-backed startup exactly once. This is local fixture proof, not Apple
  credential validation or live-provider evidence.
- The unchanged iOS source passed all 261 tests: 259 passed and 2 API recipe
  fixture tests skipped because the fixture account returned no recipes. This
  preserves the shared OAuth contract after the Android-only strategy change.
- Real Apple credential validation, registered-HTTPS callback, Hide My Email,
  fresh-confirmation name fallback, real Google identity, release signing, and
  production `assetlinks.json` verification remain owner gates in #86/#92/#100.
  The final whole-branch review is still pending; after it, Milestone 3 proceeds
  independently of those external gates.

## Milestone 3: Recipe Workflows

**Scope:**
- Local cookbook-scoped search backed by cached recipe data.
- Recipe editing for cover image, name, prep/cook time, servings, ingredients, instructions, notes, and source URL.
- Serving scaling, formatted quantities, ingredient review before adding to shopping, and cooking mode that keeps the screen awake.
- In-app imports from camera/gallery images and pasted text; Android share targets accept URL, text, and image input.
- Show pending/slow import state, poll without blocking navigation, surface failures, and refresh completed recipes.
- Online delete and move-to-other-cookbook actions with confirmation.

**Gate:**
- Every workflow handles success, validation, cancellation, process recreation, slow server completion, and server failure.
- Import retries cannot silently create duplicate recipes; until the API supplies idempotency, automatic POST retry is prohibited.

## Milestone 4: Durable Offline Shopping

**Scope:**
- Cookbook-scoped Room list with add, check/uncheck, delete, checked-section handling, remove-all, pull-to-refresh, and recipe ingredient review/add.
- Local actions update the UI immediately and survive process death.
- A Room outbox stores only operations proven retry-safe by the Rails contract. Every operation carries stable client identity and user/cookbook scope.
- Full refresh may prune synchronized rows; partial push responses may not. Preserve pending local intent during reconciliation.

**Gate:**
- Airplane-mode create/check workflows survive restart and converge after reconnection without duplicates or lost unrelated items.
- Logout, cookbook switching, stale checked-item cleanup, conflict/error recovery, and interrupted synchronization are covered.

## Milestone 5: Collaboration And Invites

**Scope:**
- List personal/shared cookbooks and members; create a shared cookbook with the move-existing-data option.
- Generate and share invitation links; open links before or after authentication; preview, accept, decline, expire, and fail safely.
- Owner delete and member leave flows, including switching away from lost membership.
- Preserve the current one-shared-cookbook constraint and cookbook-scoped recipe/shopping behavior.

**Gate:**
- Two real accounts can complete invite, join, shared edits, leave, owner delete, expired-link, and already-member scenarios across app links and copied links.

## Milestone 6: RevenueCat And Play Billing

**Dependency:** Play account, signing/tester setup, and real catalog configuration.
While unavailable, continue Milestone 7 and store-independent release preparation.
Mock purchases do not satisfy this milestone's gate.

**Scope:**
- Configure RevenueCat's Android SDK and Google Play Billing against the existing RevenueCat project.
- Identify customers with the same Rails user ID used by iOS and retain the exact `Hauptgang Pro` entitlement.
- Provide paywall, purchase, restore, entitlement refresh, and subscription-management surfaces equivalent to visible iOS behavior.
- Validate cross-store ownership and Rails webhook updates rather than treating either device's local SDK state as the global source of truth.

**Gate:**
- Play license testers can buy, cancel/expire, restore, reinstall, and switch accounts without entitlement leakage.
- An entitlement bought on either store resolves for the same Rails user, and webhook ordering/retries cannot incorrectly remove an entitlement still active through the other store.

## Milestone 7: FCM And Lifecycle Notifications

**Dependencies:** Firebase client/server configuration and a Google-enabled
emulator for initial integration. Can run before Milestone 6; Play Console is
not required. Physical Android hardware is required for the final delivery gate.

**Scope:**
- Add FCM registration, token rotation/removal, timezone/activity updates, notification preference handling, and contextual permission prompts.
- Route recipe notifications across cookbook switches and route shopping-list notifications to the correct destination.
- Report notification opens and preserve the lifecycle campaign semantics in `docs/lifecycle-notifications.md`.
- Evolve device-token storage and delivery to be provider-aware. Existing APNs tokens, environments, invalid-token cleanup, and iOS delivery must continue working while FCM is added.

**Gate:**
- Real Android devices receive and route foreground, background, and terminated-state pushes; token rotation, logout, denied permission, and invalid tokens are verified.
- Rails delivery tests prove APNs behavior remains intact and one user's or cookbook's payload cannot route into another scope.

## Milestone 8: Release

**Scope:**
- Accessibility, localization readiness, privacy/data-safety declarations, crash reporting, performance, backup policy, signing, screenshots/listing, internal testing, staged rollout, and rollback runbook.
- Verify adaptive phone/tablet behavior without introducing a separate two-pane feature model.
- Define Android minimum/recommended-version behavior alongside the server policy discussion in [issue #71](https://github.com/SzymonNastaly/maincourse/issues/71); do not assume an endpoint exists.
- Close or explicitly defer every parity gap through GitHub issues before public release.

**Gate:**
- Production-signed builds pass the complete verification matrix on representative API 29 and current Android devices.
- Play Console checks, provider sign-in, billing, FCM, app links, account deletion, and Rails production compatibility pass with real services.

## Visible iOS Parity Matrix

The source baseline is the current SwiftUI app, especially `RootView.swift`, `MainTabView.swift`, the views under `Views/`, and their services/view models.

| Visible iOS behavior | Android target | Milestone |
|---|---|---|
| First-run welcome and household/save/diet questions, skip/resume, embedded signup | Equivalent first-run flow and persisted completion | 2 |
| Email signup/sign-in and sign out | Native Compose flow with secure session storage | 1 |
| Apple and Google provider buttons | Google native flow and secure Apple web handoff | 2 |
| Authenticated splash/startup ordering and degraded cache state | Explicit startup state machine with scoped repositories | 1 |
| Recipes tab, adaptive card grid, image cache/placeholders, refresh and offline indication | Phone/tablet adaptive list/grid with Room cache | 1 |
| Cookbook title menu and switching | Active cookbook picker on recipe and shopping surfaces | 1 |
| Local recipe search with detail navigation and move/delete actions | Room-backed search; actions share recipe workflow behavior | 3 |
| Recipe detail: image, time, servings, ingredients, steps, notes | Full readable detail, cached when available | 1 |
| Portion scaling and formatted ingredient quantities | Equivalent scaling and formatting | 3 |
| Keep Screen On cooking mode | Activity-scoped keep-awake toggle, reset on exit | 3 |
| Edit cover, fields, ordered ingredients/steps, notes, and source URL | Online edit with validation and image upload | 3 |
| Camera, photo-library, and clipboard text imports | Camera/gallery and pasted-text imports | 3 |
| Share extension imports URL, image, or URL-like text | Android share target for URL/text/image | 3 |
| Pending/slow import polling, errors, and failed-item dismissal | Non-blocking status and recoverable errors | 3 |
| Move recipe and delete recipe confirmations | Equivalent online actions | 3 |
| Review selected/scaled ingredients before adding | Equivalent review and add flow | 3/4 |
| Shopping add, check, uncheck, delete, checked grouping, remove all, refresh | Durable offline Room workflow | 4 |
| Cookbook members, create, invite-link sharing, join/decline, leave/delete | App-link collaboration flows | 5 |
| Settings name, reminders preference, account deletion | Equivalent account/preferences screens | 2 |
| Free/Pro state, paywall, restore, and subscription management | RevenueCat plus Play billing | 6 |
| Contextual notification prompts and recipe/shopping deep-link routing | Android permission timing, FCM routing, open tracking | 7 |

## Explicit Exclusions

- Meal planning: its iOS code exists, but `MainTabView` deliberately hides the tab.
- Widgets, timers, App Shortcuts/App Intents, and other non-visible platform extensions.
- Favorites and tags: no visible iOS UI exists to match.
- Dark mode and a dedicated tablet two-pane experience.
- General recipe sharing: invitation sharing and share-to-import are in scope, but a user-facing share-recipe feature is not currently visible.
- Offline imports, recipe edits, cookbook administration, collaboration, or arbitrary mutation replay.

## Security And Integration Decisions

### Apple on Android
- Use a browser-based Apple authorization flow with OmniAuth-generated `state`
  and Apple nonce; validate and consume both exactly once. Android handoffs use
  an explicitly loaded stricter Apple strategy without changing ordinary web or
  native iOS authentication.
- Keep the existing Apple Services ID grouped under the iOS primary App ID so the stable Apple subject remains shared across web, iOS, and Android handoff.
- Apply the shared explicit-creation contract: first try unconfirmed, explain
  that a new account creates a separate cookbook, and begin an entirely fresh
  authorization after confirmation. Never infer a link from email or carry a
  rejected provider credential into the confirmed attempt.
- Android starts an anonymous five-minute transaction with a PKCE S256 challenge.
  Rails stores only handle and exchange-code digests, a server-selected return
  URI, and state timestamps; it stores no verifier or provider credential.
- After the callback, Rails returns an exchange code that expires after one
  minute or with the transaction, whichever comes first. The app exchanges it
  once for the normal API token; wrong proof does not consume the valid code.
- Never place an API bearer token in a redirect URL, app link, browser history, logs, or analytics.
- Consume or expire the exchange code atomically and reject replay, mismatched app/transaction binding, invalid state, and invalid nonce.
- Local debug may return through `com.getmaincourse.app.debug:/oauth/apple`; only
  release accepts `https://app.getmaincourse.com/android/auth/apple`. Process
  death discards the in-memory verifier, and network ambiguity requires an
  explicit fresh attempt rather than exchange retry.
- The shared prerequisite for
  [#86](https://github.com/SzymonNastaly/maincourse/issues/86) is implemented
  under [#99](https://github.com/SzymonNastaly/maincourse/issues/99). Design and
  Android handoff implementation is locally gated; do not close #86 or call the
  provider gate complete until registered-HTTPS and real Hide My Email
  acceptance passes.

### Imports And Android Shares
- Current import POST endpoints have no idempotency contract. Do not automatically retry an ambiguous timeout or replay work after process death; let the user deliberately retry after checking status.
- iOS Safari shares can provide JavaScript-preprocessed DOM, JSON-LD, metadata, image candidates, and HTML. Android URL shares do not provide that Safari preprocessing, so URL imports use backend extraction unless an explicit Android-safe content source is later designed.
- Image and text shares may be copied into temporary app storage for one foreground import attempt; they are not an offline outbox.

### Notifications
- Add an explicit provider/platform discriminator to device registrations and deliveries rather than interpreting every token as APNs.
- Preserve APNs production/sandbox handling and invalid-token cleanup while adding FCM credentials, payload formatting, error mapping, and token cleanup.
- Keep campaign selection provider-neutral; dispatch provider-specific payloads only at the delivery boundary.

### RevenueCat
- Both apps call RevenueCat login with the same stable Rails user ID and use the existing `Hauptgang Pro` entitlement string.
- The webhook must compute cross-store entitlement truth correctly under retries and out-of-order events. A single expiration event must not revoke Pro while another store still grants it.
- Google Play product identifiers and offering configuration are console decisions recorded in issues, not invented in this roadmap.

## Verification Strategy

Verification evidence is attached to milestone issues and release artifacts. The following is required before each milestone gate can be called complete:

| Milestone | Automated verification | Integration and device verification |
|---|---|---|
| 0 | Gradle build, Android lint, JVM tests, Compose smoke tests | API 29/36 instrumentation, phone/tablet launch, reproducible clean setup |
| 1 | JVM repository/session tests, Compose state/navigation tests, Room migration/isolation tests, affected Rails API tests | Real API email auth; offline/cached starts and cookbook switching on phone/tablet |
| 2 | JVM auth/state tests, Compose onboarding/account tests, affected Rails OAuth/account tests, iOS auth regression checks | Real Google and Apple accounts on release-signed Android; deletion and relay-account cases |
| 3 | JVM formatting/import polling tests, Compose workflow tests, Room search/cache tests, affected Rails recipe/import tests, iOS import regression checks | Real camera/gallery/share intents and slow imports on devices from representative apps |
| 4 | JVM sync-policy tests, Compose shopping tests, Room migration/outbox tests, affected Rails shopping tests, iOS shopping regression checks | Airplane mode, process death, reconnect, cookbook switch, and two-device convergence |
| 5 | JVM deep-link/state tests, Compose invite tests, affected Rails cookbook/invitation tests, iOS universal-link regression checks | Two real accounts exercising app links, copied links, join/leave/delete |
| 6 | JVM entitlement-state tests, Compose paywall tests, affected Rails webhook tests, iOS RevenueCat regression checks | Play license purchase/restore/expiry and cross-store entitlement checks |
| 7 | JVM routing/token tests, Compose permission tests, affected Rails delivery/campaign tests, iOS APNs regression checks | Real FCM foreground/background/terminated delivery and real APNs preservation check |
| 8 | Full Android suite, affected Rails suite, iOS build/tests, release static checks | Production-signed API 29/current devices; Play internal/staged release smoke tests |

Tests should be added at the lowest useful layer. Compose tests protect user-visible state and navigation; JVM tests protect pure policy; Room tests protect migrations, scoping, and reconciliation; Rails tests protect shared contracts. Provider, billing, push, camera, share-target, app-link, and release-signing claims require real services or devices and cannot be declared verified from mocks alone.

## Decision Log

| Date | Decision |
|---|---|
| 2026-09-07 | Native Kotlin/Compose Material 3 app under `maincourse-android/`; one `:app` module with feature packages; release ID `com.getmaincourse.app` and debug `.debug` suffix. |
| 2026-09-07 | `minSdk 29`, compile/target SDK 37, and the compatible toolchain/UI set in `docs/android.md` are pinned; upgrades are deliberate. |
| 2026-09-07 | MainCourse grey/green, light-only tokens; Android native sans plus bundled Plex Mono for numerics; Material Symbols. |
| 2026-09-07 | Adaptive phone/tablet navigation, not a dedicated two-pane product. |
| 2026-09-07 | Public v1 targets visible iOS parity through Milestones 0-8; hidden and absent features are excluded. |
| 2026-09-07 | Rails remains the backend; cookbook headers, isolation, 90-day sessions, and conservative offline boundaries are preserved. |
| 2026-09-07 | All email, Google, and Apple sign-in methods are required; Apple uses a single-use app-bound web handoff. |
| 2026-09-07 | Shopping is the only durable mutation outbox initially, and only for retry-safe operations. |
| 2026-09-07 | External console enablement starts in Milestone 0 and is tracked in [issue #92](https://github.com/SzymonNastaly/maincourse/issues/92). |
| 2026-09-07 | Develop emulator-first while owner Play registration/hardware waits: 1 → 2 core → 3–5; provider tracks as configured; 7 may precede 6. Keep physical-device and Play gates explicit without blocking unrelated implementation. |
| 2026-09-07 | Milestone 1 local acceptance and final code review passed with the API 37 emulator, real Rails API, and `MainActivity`; remote CI, physical-device, provider, and Play gates remain distinct. |
| 2026-09-08 | Milestone 2 onboarding/account core passed its local Rails/API 37 gate; Google then Apple remain separate required provider slices, so the full milestone stays in progress. |
| 2026-09-08 | Android Google Credential Manager implementation passed its local code and zero-account chooser gate; real account/consent, release signing, Apple, and full Milestone 2 review remain separate gates. |
| 2026-09-08 | Shared Rails/web/iOS Apple account-creation confirmation passed local component gates under #99. Android handoff and real registered-HTTPS/Hide My Email acceptance remain; #86 and the full Milestone 2 review stay open. |
| 2026-09-08 | Android's PKCE-bound Apple browser handoff passed its local Rails/API 37 implementation gate, including real browser cancellation and controlled fixture exchange. Real Apple/Google identities, registered HTTPS, production App Links, release signing, #86, and final whole-branch review stay open; Milestone 3 follows independently. |

## Handoff

- Milestones 0 and 1 are complete. The auth-first native email/session,
  cookbook switching, and cached recipe list/detail slice passed its local
  acceptance gate and final code review. Evidence and integration tracking are in
  [#93](https://github.com/SzymonNastaly/maincourse/issues/93).
- Milestone 2 onboarding/account passed its local core gate and final code review;
  evidence is in #97. Google code and zero-account chooser behavior passed their
  local gate in #98, but owner-authorized live identity and release-signed checks
  remain. The shared Apple account-creation prerequisite passed Rails/web/iOS
  component gates under #99, and Android's Apple handoff passed its local
  implementation gate under #100. Real Apple/Hide My Email, registered HTTPS,
  production App Links, release-signed Google/Apple checks, #86, full Milestone
  2, and final whole-branch review remain open.
- Continue Milestones 3–5 without treating provider or Play gates as blockers.
  Milestone 3 is the automatic next implementation scope after the pending final
  review. All external tracks remain open in #92.
- Play registration is temporarily owner-blocked. Follow the working sequence above; local OAuth SHA-1 discovery and Google Cloud/Firebase configuration do not depend on Play access.
- Before implementation work, consult the milestone's GitHub issues and update this roadmap only when scope, sequencing, gates, or decisions change.
- Do not mark a milestone complete from code presence alone; its gate and listed verification must have recorded evidence.

## References

- `docs/oauth-sign-in.md`
- `docs/ios-offline-sync-patterns.md`
- `docs/ios-authenticated-startup.md`
- `docs/lifecycle-notifications.md`
- `app/assets/tailwind/application.css`
- `hauptgang-ios/Hauptgang/Utilities/MainCourseTheme.swift`
- [Issue #86: Apple Hide My Email silently creates a second account](https://github.com/SzymonNastaly/maincourse/issues/86)
- [Issue #99: Apple sign-in explicit new-account confirmation implementation](https://github.com/SzymonNastaly/maincourse/issues/99)
- [Issue #71: iOS minimum-version enforcement](https://github.com/SzymonNastaly/maincourse/issues/71)
- [Issue #92: Android external-service setup](https://github.com/SzymonNastaly/maincourse/issues/92)
