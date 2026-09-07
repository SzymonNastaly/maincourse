# Android App Guidance

These instructions apply to `maincourse-android/`.

## Read First

- `docs/android.md` is the bootstrap and day-to-day Android convention.
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
- Milestone 0 contains the shell, theme, gallery, tooling, and their tests only.
  Do not add networking, Room, Coil, repositories, secure storage, or product
  models until Milestone 1 needs them.
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
- Keep `features/designsystem/DesignSystemScreen.kt` navigable and interactive
  while bootstrapping. It is a development gallery, not a real feature.
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
- When API work begins, preserve `X-Cookbook-Id`, user/cookbook data isolation,
  and the Rails 90-day no-refresh-token session contract.

## Commands And Verification

Use `bin/android-build` for `assembleDebug`, `bin/android-test` for JVM tests and
lint, and `bin/android-test --device` to add connected instrumentation tests.
All delegate to `bin/android-gradle`, which also accepts arbitrary Gradle tasks
and arguments. The native build does not depend on Rails.

Wrappers must work from any current directory, forward arbitrary Gradle
arguments, respect supplied `JAVA_HOME`/`ANDROID_HOME`, and never install tools
or edit global shell configuration.

For bootstrap changes, run the build, JVM tests, lint, and relevant Compose
tests. Use a phone and tablet/emulator for adaptive UI changes. Real provider,
billing, push, app-link, camera, and sharing claims require real service/device
verification; mocks are not sufficient.

Keep executable task detail in GitHub issues. Update documentation only when a
lasting convention, milestone gate, or decision changes. Android external setup
is tracked in [issue #92](https://github.com/SzymonNastaly/maincourse/issues/92).
