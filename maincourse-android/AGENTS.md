# Android App Guidance

These instructions apply to `maincourse-android/`.

## Read First

- `docs/android.md` is the architecture, local integration, and day-to-day
  Android convention.
- `docs/superpowers/plans/2026-09-07-native-android.md` is the milestone roadmap.
- `docs/oauth-sign-in.md`, `docs/ios-authenticated-startup.md`,
  `docs/ios-offline-sync-patterns.md`, and `docs/lifecycle-notifications.md`
  define shared backend behavior when those features are implemented.
- `app/assets/tailwind/application.css` and
  `hauptgang-ios/Hauptgang/Utilities/MainCourseTheme.swift` are the design-token
  sources.

## Project Rules

- Keep one `:app` module and package code under `com.getmaincourse.app` by
  feature. Do not add modules or architectural layers speculatively.
- Release uses `com.getmaincourse.app`; debug uses the `.debug` application ID
  suffix. Keep `minSdk 29`, `compileSdk 37`, and `targetSdk 37`.
- Use the pinned compatible toolchain and UI set in `docs/android.md`. Review
  upgrades deliberately and validate the set together. Lint warnings are errors;
  only the three documented dependency-freshness checks are disabled.
- Keep the implemented manual application container: one API, encrypted session
  store, Room database/store, repository, and session image owner. Do not add a
  DI framework or speculative layers at this scale.
- Keep `data/{model,network,session,cache,images}` as the shared data boundary
  and `features/{auth,session,recipes,settings,preview,designsystem}` as the
  package-by-feature UI/orchestration layout.
- Prefer small Compose screens with explicit state and event parameters. Add a
  view model or shared abstraction only when behavior warrants it.

## UI

- Use `MainCourseTheme` and semantic `MainCourseColors`; no raw colors in
  composables.
- Keep the app light-only with dynamic color disabled.
- Use native Material sans for text and bundled Plex Mono only for numerics.
- Use 8dp/10dp/12dp control/card/panel radii, flat hairline surfaces, native
  Material behavior, and checked-in Material Symbols vectors.
- Preserve the four shell destinations: Recipes, Shopping, Search, Settings.
- Recipes and minimal account/sign-out Settings are implemented. Keep Shopping
  and Search explicitly labeled as previews until their roadmap milestones.
- Keep `features/designsystem/DesignSystemScreen.kt` navigable and interactive
  from Settings. It is a development gallery, not a real feature.
- Adapt navigation between compact and expanded widths; do not create a
  dedicated tablet two-pane flow.
- Preserve the brown cookbook launcher artwork; the monochrome H/book stencil
  is only for explicitly themed launcher icons.

## Security And Configuration

- Never commit secrets, signing keys, private provider files, or bearer tokens.
- Keep release API traffic on `https://app.getmaincourse.com/` and deny
  cleartext. Debug HTTP is limited to `10.0.2.2`, `localhost`, and `127.0.0.1`;
  use `adb reverse` or HTTPS rather than arbitrary LAN HTTP.
- Keep Google Services/provider plugins disabled until actual console files are
  available. Release is unsigned in source control.
- Preserve `allowBackup="false"`, `fullBackupContent="false"`, and the API 31+
  cloud-backup/device-transfer exclusions.
- Preserve `X-Cookbook-Id`, user/cookbook data isolation, and the Rails 90-day
  no-refresh-token session contract. Login `401` is a form error; authenticated
  `401` clears protected local state.
- Keep credentials only in the AES-GCM Android-Keystore-backed atomic session
  file. Do not put tokens in Room, preferences, routes, saved state, logs, or
  image requests.
- Scope Room rows and queries by user and cookbook where applicable. Full list
  responses may replace only their captured scope; detail fetches are partial,
  on-demand updates and must not prune peers. Keep schema exports and require
  deliberate migrations rather than destructive fallback.
- Preserve request generation/ownership checks, structured cancellation, and
  serialized cache writes across refresh, switch, logout, `401`, `403`, and
  `404` handling. Do not let stale requests write into a newer scope.
- Keep Coil loaders user/session-owned and bearer-free. Images are best effort;
  only details successfully fetched after opening are available offline.
- Cleanup must finish before admitting another account. Treat disk/storage
  deletion failure as recoverable failure, not successful logout. Cross-process
  cleanup/purge durability remains tracked in
  [issue #94](https://github.com/SzymonNastaly/maincourse/issues/94).

## Commands And Verification

Use `bin/android-build` for `assembleDebug`, `bin/android-test` for JVM tests and
lint, and `bin/android-test --device` to add connected instrumentation tests.
All delegate to `bin/android-gradle`, which also accepts arbitrary Gradle tasks
and arguments. The native build does not depend on Rails.

Wrappers must work from any current directory, forward arbitrary Gradle
arguments, respect supplied `JAVA_HOME`/`ANDROID_HOME`, and never install tools
or edit global shell configuration.

For Android changes, run the build, JVM tests, lint, and relevant device tests.
Use phone and tablet emulator configurations for adaptive UI changes. Device UI
tests use the non-exported debug-only `MainCourseTestActivity`; live integration
claims must use the real `MainActivity`. Real provider, billing, push, app-link,
camera, sharing, and physical-device claims require their real service/device;
mocks and the local API 37 emulator are not sufficient.

Local Rails integration uses the default emulator URL `http://10.0.2.2:3000/`.
On Android 17/API 37, preserve the debug-only `ACCESS_LOCAL_NETWORK` request and
the denial recovery path through Settings → MainCourse Dev → Permissions →
Nearby devices. Public HTTPS debug overrides and release must not request this
permission. Release remains unsigned in source control and fixed to the public
HTTPS API.

Keep executable task detail in GitHub issues. Update documentation only when a
lasting convention, milestone gate, or decision changes. Android external setup
is tracked in [issue #92](https://github.com/SzymonNastaly/maincourse/issues/92).
