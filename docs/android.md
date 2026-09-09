# Android Development

`maincourse-android/` is the native Kotlin and Jetpack Compose client. The
current scope is the deliberately small MVP defined in
`docs/superpowers/specs/2026-09-09-android-simple-mvp-design.md`.

## MVP Scope

Implemented:

- Email sign-up and sign-in, encrypted session restoration, sign-out, and
  account deletion.
- Cookbook selection and cached recipe list/detail browsing.
- Online recipe move, delete, and reviewed ingredient addition to the shopping
  list.
- Account name and recipe-reminder settings, with the account email displayed
  read-only.
- Four bottom destinations: Recipes and Settings are functional; Shopping and
  Search are clearly labeled placeholders.

Not in this MVP: onboarding, provider sign-in, recipe search, recipe editing or
photo upload, imports/sharing, a shopping-list UI, collaboration, billing,
notifications, a design gallery, or adaptive navigation. Do not reintroduce
these while extending the MVP without an approved scope change.

## Project And Dependencies

The app has one `:app` module, package and release application ID
`com.getmaincourse.app`, and debug application ID
`com.getmaincourse.app.debug`.

| Component | Pinned value |
|---|---|
| Minimum / compile / target SDK | 29 / 37 / 37 |
| Android Gradle Plugin / Gradle | 9.4.0 / 9.6.1 |
| Kotlin / Compose compiler | 2.2.10 / 2.2.10 |
| Java source target / Gradle runtime | Java 17 / JDK or JBR 25 |
| Compose BOM | 2026.08.00 |
| Navigation 3 | 1.1.7 |
| Lifecycle | 2.11.0 |
| Coroutines / serialization | 1.10.2 / 1.9.0 |
| Retrofit / OkHttp | 3.0.0 / 4.12.0 |
| Room / KSP | 2.8.4 / 2.3.11 |
| Coil | 3.3.0 |

`gradle/libs.versions.toml` and the Gradle wrapper are authoritative. Dependency
updates are reviewed as a compatible set. Lint treats warnings as errors and
only disables the three time-dependent dependency freshness checks.

## Architecture

Code is package-by-feature under
`app/src/main/java/com/getmaincourse/app/`:

- `MainCourseApplication.kt` constructs one small manual `AppContainer` with
  the Room database, encrypted session store, in-memory session provider,
  Retrofit service, repositories, and session-owned image loader. Do not add a
  dependency-injection framework for this scale.
- `data/network` defines the Retrofit API and authentication interceptor;
  `data/session` owns credentials; `data/cache` owns Room entities and queries;
  `data/images` owns image URL and cache policy.
- `CookbookRepository` and `RecipeRepository` connect the API to Room. They do
  not own UI state or coroutine scopes.
- Focused lifecycle view models under `features/session`, `features/recipes`,
  and `features/settings` expose `StateFlow` UI state and launch cancellable
  foreground work in `viewModelScope`.
- `MainCourseApp.kt` switches between restoration, authentication, recovery,
  and the protected Navigation 3 shell. Compose screens receive explicit state
  and callbacks.

Keep this direct structure. Do not add coordinators, use-case layers, durable
mutation queues, automatic retries, or custom concurrency frameworks.

## Sessions And Security

`EncryptedSessionStore` atomically stores the API origin, bearer token, user,
and expiry in `noBackupFilesDir`. The record is encrypted with an AES-GCM key
held by Android Keystore. Tokens must never enter Room, preferences, routes,
saved state, logs, image requests, or source control.

`SessionProvider` holds the active session in memory. `AuthInterceptor` removes
the internal anonymous marker from email-auth requests, adds the current bearer
to protected requests, and reports a `401` only for the bearer that made the
request. Recipe and shopping calls always send an explicit `X-Cookbook-Id`.

Startup accepts only an unexpired stored session for the configured API origin.
Sign-out attempts remote revocation for at most five seconds and then clears
local credentials, Room, and image data. Confirmed account deletion and an
authenticated `401` use the same local cleanup. If cleanup fails, the recovery
screen blocks another account until Retry succeeds. Profile updates persist the
server response before publishing it; an explicit matching retry after a local
write failure retries only that write, not the PATCH.

Backups and device transfer remain disabled. Release traffic is HTTPS-only and
release signing material remains outside the repository.

## Room Cache

`maincourse.db` is the source of truth for cookbook and recipe screens. Room
`Flow`s update those screens immediately. Every row and query is scoped by user,
and recipes are additionally scoped by cookbook.

A successful cookbook refresh replaces that user's memberships and keeps a
valid selection, otherwise selecting the first cookbook. A successful recipe
list refresh replaces only that cookbook's list, preserves cached details for
retained recipe IDs, and removes missing recipes. Opening a recipe fetches its
detail only when absent; Pull to Refresh fetches it explicitly. Previously
cached lists and details remain readable when refresh fails.

Move, delete, ingredient addition, profile save, account deletion, and sign-out
are direct online actions with no automatic replay. A confirmed move is
reconciled locally before a best-effort target refresh; confirmed deletion
removes the cached row. Malformed cached JSON maps to absence and is repaired by
the next successful refresh. Keep schema exports and add explicit Room
migrations rather than destructive fallback.

Coil image caches are user-owned and best effort. Image requests never carry the
API bearer token.

## UI Contract

Use `MainCourseTheme` and semantic `MainCourseColors`. The app is light-only,
uses native Material typography with bundled IBM Plex Mono for numeric content,
and keeps bottom navigation at every width. Preserve the existing brown cookbook
launcher artwork; it is the intentional exception to the grey/deep-green app
palette.

## API Configuration

Gradle validates `BuildConfig.API_BASE_URL` during configuration:

- Debug defaults to `http://10.0.2.2:3000/`.
- Debug may use `-Pmaincourse.apiBaseUrl=https://example.test/`.
- Release is fixed to `https://app.getmaincourse.com/` and ignores overrides.

The URL must be HTTP(S), include a host, end in `/`, and contain no credentials,
query, or fragment. Debug cleartext is restricted to `10.0.2.2`, `localhost`,
and `127.0.0.1`. For a USB device, run `adb reverse tcp:3000 tcp:3000` and use
`-Pmaincourse.apiBaseUrl=http://localhost:3000/`.

Android 17/API 37 requires debug-only `ACCESS_LOCAL_NETWORK` permission for
loopback development. If prompting is suppressed, enable **Nearby devices** in
the MainCourse Dev system app settings. Public HTTPS debug builds and release do
not request this permission.

## Local Setup

Install Android Studio plus JDK/JBR 25, Android 37, Build Tools 36.0.0, Platform
Tools, Emulator, and an Android 37 Google APIs image. The established local AVD
is `MainCourse_Phone_API37`.

Run against local Rails:

```bash
bin/setup --skip-server
bin/rails server -b 127.0.0.1 -p 3000
# In another terminal:
"$ANDROID_HOME/emulator/emulator" -avd MainCourse_Phone_API37
bin/android-gradle :app:installDebug
```

The emulator maps `10.0.2.2` to the host loopback address. Use only dedicated
development accounts. Never record credentials, tokens, private signing files,
or downloaded service configuration.

## Commands And Verification

| Command | Purpose |
|---|---|
| `bin/android-gradle ARGS...` | Run the project Gradle wrapper with arbitrary arguments |
| `bin/android-build` | Build the debug APK (`assembleDebug`) |
| `bin/android-test` | Run JVM tests and debug lint |
| `bin/android-test --device` | Also run connected instrumentation tests |
| `bin/android-gradle :app:assembleRelease` | Compile, shrink, lint-check, and package the unsigned release APK |

The wrappers work from any directory, honor explicit `JAVA_HOME` and
`ANDROID_HOME`, install nothing, and do not edit shell configuration. Device
tests require a running emulator or connected device. Instrumentation covers
Room, encrypted session storage, recipe actions, and navigation through the
debug-only, non-exported `MainCourseTestActivity`.

`.github/workflows/android.yml` runs JVM tests, lint, and a debug build on JDK
25, plus connected tests on API 29 and 36 emulators. Local release assembly is
compile evidence only.

Automated tests do not prove the real Rails flow. Final live acceptance must use
the installed `MainActivity` and a dedicated account to verify sign-up/sign-in,
cookbook switching, list/detail and offline reopening, move/delete/add-to-shopping,
profile save, failure messages, logout, account deletion, and the two placeholder
tabs. Account deletion is destructive and must remain an explicit manual check
with disposable data.
