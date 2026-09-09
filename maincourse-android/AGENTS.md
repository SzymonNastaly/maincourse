# Android App Guidance

These instructions apply to `maincourse-android/`.

## Read First

- `docs/android.md` is the durable architecture, setup, and verification guide.
- `docs/superpowers/specs/2026-09-09-android-simple-mvp-design.md` defines the
  current MVP. The older native Android roadmap is historical only.
- `app/assets/tailwind/application.css` and
  `hauptgang-ios/Hauptgang/Utilities/MainCourseTheme.swift` are the shared
  design-token sources.

## Scope And Structure

- Keep one `:app` module and package code under `com.getmaincourse.app` by
  feature. Do not add speculative modules, DI, use-case layers, coordinators,
  custom concurrency frameworks, or durable mutation queues.
- Keep the manual application container small: Room database, encrypted session
  store, in-memory session provider, Retrofit service, cookbook and recipe
  repositories, and session-owned image loader.
- Repositories connect Retrofit to Room and own no coroutine scope or UI state.
  Use focused lifecycle view models with `StateFlow` and ordinary
  `viewModelScope` cancellation.
- Prefer small Compose screens with explicit state and callback parameters.
- Current features are email auth, cached cookbook/recipe browsing, recipe
  move/delete/add-to-shopping actions, profile settings with read-only account
  email, account deletion, and logout. Shopping and Search are labeled
  placeholders.
- Do not restore onboarding, provider sign-in, search, recipe editing/photo
  upload, imports/sharing, design gallery, or adaptive navigation without an
  approved scope change.

## Platform And UI

- Release uses `com.getmaincourse.app`; debug uses the `.debug` suffix. Keep
  `minSdk 29`, `compileSdk 37`, `targetSdk 37`, and Java 17.
- Use the pinned dependency set in `gradle/libs.versions.toml`; validate upgrades
  together. Lint warnings are errors except for the three documented dependency
  freshness checks.
- Use `MainCourseTheme` and semantic `MainCourseColors`; keep the app light-only
  and dynamic color disabled. Use native Material sans and Plex Mono only for
  numeric content.
- Keep 8dp/10dp/12dp control/card/panel radii, flat hairline surfaces, native
  Material behavior, checked-in Material Symbols, four bottom destinations at
  every width, and the existing brown cookbook launcher artwork.

## Security And Data

- Never commit secrets, signing keys, private service files, credentials, or
  bearer tokens. Release remains unsigned in source control.
- Keep release API traffic fixed to `https://app.getmaincourse.com/` with
  cleartext denied. Debug cleartext is limited to emulator and loopback hosts.
- Keep credentials only in the AES-GCM Android-Keystore-backed atomic session
  file. Never put tokens in Room, preferences, routes, saved state, logs, or
  image requests.
- Preserve backup/device-transfer exclusions, the Rails 90-day no-refresh-token
  session contract, and local cleanup on logout, confirmed deletion, or an
  authenticated `401`. Cleanup failure must block admission of another account.
- Keep `X-Cookbook-Id` explicit on recipe/shopping requests and scope all Room
  data by user and cookbook where applicable.
- Room is the observable source of truth. Full list responses replace only their
  scope while preserving retained details; detail fetches update one row and do
  not prune peers. Keep schema exports and explicit migrations.
- Mutations are online-only and are never automatically replayed. Keep profile
  persistence-before-publication and local-only retry for a matching
  server-accepted profile response.
- Keep Coil image caches user-owned, best effort, and bearer-free.

## Commands And Verification

Use `bin/android-build`, `bin/android-test`,
`bin/android-gradle :app:assembleRelease`, and, with an emulator/device running,
`bin/android-test --device`. Wrappers must work from any directory, forward
arguments, honor supplied `JAVA_HOME`/`ANDROID_HOME`, install nothing, and never
edit global shell configuration.

Local Rails uses `http://10.0.2.2:3000/` from an emulator. Preserve the API 37
debug-only local-network permission and its denial recovery path. Public HTTPS
debug overrides and release must not request that permission.

Device tests use the non-exported debug-only `MainCourseTestActivity`. Live-flow
claims require the real `MainActivity` and a dedicated account. Never automate
private credentials or delete real user data; account-deletion acceptance uses
only a disposable account and remains manual when none is available.

Keep deferred work in GitHub issues and documentation focused on durable
architecture and operating conventions.
