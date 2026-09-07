# Native Android Roadmap

## Goal

Ship a native Android app with public-v1 parity with functionality currently visible in the iOS app, while reusing the Rails API and preserving its account, cookbook, subscription, and notification semantics.

This is a living, long-horizon roadmap. GitHub issues are the source of executable tasks; this file records scope, sequence, gates, decisions, and handoff state rather than duplicating issue checklists.

## Current Status

| Milestone | Status | Outcome |
|---|---|---|
| 0. Enablement and scaffold | Complete | Native preview, local tooling, CI definition, and local verification complete; external accounts tracked in #92 |
| 1. First vertical slice | In progress | Design approved; implementation tasks in #93 for email session through cached recipe list/detail |
| 2. Identity and account | Planned | Complete sign-in, onboarding, account, and preferences |
| 3. Recipe workflows | Planned | Search, editing, imports, cooking, and recipe actions |
| 4. Shopping list | Planned | Durable offline shopping workflow |
| 5. Collaboration | Planned | Shared cookbooks and invitations |
| 6. Subscription | Planned | RevenueCat and Google Play billing |
| 7. Notifications | Planned | FCM registration, delivery, tracking, and routing |
| 8. Release | Planned | Production hardening and Play release |

Milestone 0's local scaffold gate passed on 2026-09-07. Milestone 1's design is
approved and implementation is tracked in #93; authentication and recipe data
are not implemented yet.
External service configuration remains open in #92 and does not block Milestone 1.

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

**Design:** `docs/superpowers/specs/2026-09-07-android-milestone-1-design.md`.
**Implementation:** [issue #93](https://github.com/SzymonNastaly/maincourse/issues/93).

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

## Milestone 2: Identity And Account

**Sequence:** Implement onboarding and account/preferences independently of
provider enablement. Google and Apple can be developed and tested before Play
registration with the appropriate Cloud/Apple configuration and local signing
identity. Provider blockers must not stop Milestones 3–5; the full Milestone 2
gate remains open until all three sign-in methods are verified.

**Scope:**
- Google and Apple sign-in in addition to email/password; all three methods are required for public v1.
- First-run onboarding parity: welcome, household size, what the user wants to save, diet choices, skip/resume, then embedded signup.
- Profile name editing, lifecycle-notification preference, account deletion, and sign out.
- Match provider-account linking and deletion/revocation behavior documented in `docs/oauth-sign-in.md`.
- Resolve Apple private-relay duplicate-account behavior before enabling the Android Apple path; track [issue #86](https://github.com/SzymonNastaly/maincourse/issues/86).

**Gate:**
- Email, Google, and Apple succeed with real provider accounts in debug and release-signed builds.
- Returning identities reach the same Rails user; private-relay, cancellation, invalid state/nonce, expired handoff, and deletion paths are verified.

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
- Use a browser-based Apple authorization flow with server-generated `state` and `nonce`; validate both exactly once.
- Keep the existing Apple Services ID grouped under the iOS primary App ID so the stable Apple subject remains shared across web, iOS, and Android handoff.
- After the callback, Rails returns a short-lived, single-use exchange code bound to the initiating Android login transaction. The app exchanges it over TLS for the normal API token.
- Never place an API bearer token in a redirect URL, app link, browser history, logs, or analytics.
- Consume or expire the exchange code atomically and reject replay, mismatched app/transaction binding, invalid state, and invalid nonce.
- Do not launch until [issue #86](https://github.com/SzymonNastaly/maincourse/issues/86) is resolved and private-relay users cannot silently receive duplicate accounts.

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

## Handoff

- Milestone 0 is complete. The preview app is intentionally disconnected from Rails; do not treat its sample controls as product features.
- Active product slice: Milestone 1 email/session/cookbook discovery and switching/recipe list and detail/cache. The user approved its design; execute the plan in [#93](https://github.com/SzymonNastaly/maincourse/issues/93).
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
- [Issue #71: iOS minimum-version enforcement](https://github.com/SzymonNastaly/maincourse/issues/71)
- [Issue #92: Android external-service setup](https://github.com/SzymonNastaly/maincourse/issues/92)
