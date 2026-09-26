# Android App Guidance

`maincourse-android/` is a Kotlin/Jetpack Compose client. Keep it simple: one
`:app` module, feature-based packages under `com.getmaincourse.app`, and a small
manual application container. Do not add speculative modules, DI, use-case
layers, coordinators, custom concurrency frameworks, or durable mutation queues.
Prefer established, well-tested, production-proven libraries to hand-rolled
solutions.

## Architecture and data

- Repositories bridge Retrofit and Room; they own neither UI state nor coroutine
  scopes. Use focused `StateFlow` view models with `viewModelScope`, and Compose
  screens with explicit state and callbacks.
- Room is the observable cache, scoped by user and (where applicable) cookbook.
  Keep schema exports and explicit migrations. Network mutations are online-only:
  update cache only after server acknowledgement and never replay automatically.
- Send `X-Cookbook-Id` on cookbook-scoped requests. Keep Coil caches user-owned,
  best-effort, and bearer-free.
- Store credentials only in the encrypted Android-Keystore session file—never in
  Room, preferences, routes, saved state, logs, image requests, or source control.
  Preserve cleanup on sign-out, confirmed deletion, and authenticated `401`.

## Platform and UI

- `build.gradle.kts`, `gradle/libs.versions.toml`, and the Gradle wrapper are
  authoritative for SDKs, Java, dependencies, application IDs, and API URLs.
  Release uses HTTPS; debug cleartext stays limited to emulator/loopback hosts.
- Use `MainCourseTheme` and semantic `MainCourseColors`: light-only, native
  Material behavior, and Plex Mono only for numeric content. Shared tokens live
  in `app/assets/tailwind/application.css` and
  `hauptgang-ios/Hauptgang/Utilities/MainCourseTheme.swift`. The launcher uses the
  green cookbook with a lime M on the grey canvas; see `docs/brand-icons.md`.
- Never commit credentials, signing keys, private service files, or bearer tokens.
- Keep English, Polish, and German native string/plural resources complete. Retain
  `UiMessage` in view-model state and resolve it at display time; follow the system
  app language. See `docs/android-localization.md` for formatting and content rules.

## Verification

API errors are defined in `config/api_errors.yml`. Regenerate wire enums with
`bin/api-error-contract --generate` and implement every case in the dedicated
`*CodeMessage.kt` renderers using native string resources. Keep known-code `when`
expressions exhaustive. CI checks both clients and their resource references;
see `docs/api-localization.md` for the workflow and runtime contract.

Run `bin/android-build`, `bin/android-test`, and `bin/android-test --device`
(with an emulator or device); use `bin/android-gradle :app:assembleRelease` for
release compilation. Device tests use debug-only `MainCourseTestActivity`; live
checks use `MainActivity`, a dedicated account, and disposable data for deletion.
Use `bin/android-release VERSION` only for an intentional live upload to Google
Play closed testing, after completing the setup in `docs/android-release.md`.

For local Rails, the emulator uses `http://10.0.2.2:3000/`. Preserve the
debug-only local-network permission and its denial path; public HTTPS debug builds
and release must not request it. Track deferred work in GitHub issues, not TODOs.
