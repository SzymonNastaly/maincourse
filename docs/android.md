# Android Development
`maincourse-android/` is the native Kotlin/Jetpack Compose client. It launches
into native email signup/sign-in, then provides cookbook-scoped recipe browsing
with offline cache support and an interactive development gallery. It uses the
same Rails API and accounts as the web and iOS clients. See
`docs/superpowers/plans/2026-09-07-native-android.md` for the product roadmap and
[issue #92](https://github.com/SzymonNastaly/maincourse/issues/92) for Android
external-service setup.

Milestone 1 behavior follows its
[approved design](superpowers/specs/2026-09-07-android-milestone-1-design.md) and
is tracked in [issue #93](https://github.com/SzymonNastaly/maincourse/issues/93).

## Bootstrap Contract
| Setting | Pinned value |
|---|---|
| Project directory | `maincourse-android/` |
| Modules | One `:app` module |
| Package / release `applicationId` | `com.getmaincourse.app` |
| Debug application ID | `com.getmaincourse.app.debug` via `.debug` suffix |
| Minimum SDK | 29 |
| Compile / target SDK | 37 / 37 |
| Android Gradle Plugin | 9.4.0, built-in Kotlin enabled |
| Kotlin / Compose compiler | 2.2.10 / 2.2.10 |
| Gradle wrapper | 9.6.1 |
| Gradle runtime / source and bytecode target | JDK/JBR 25 / Java 17 |
| Compose BOM | 2026.08.00 |
| Material 3 | Stable 1.4 line |
| Navigation 3 | 1.1.7 |
| Coroutines / serialization | 1.10.2 / 1.9.0 |
| OkHttp | 4.12.0 |
| Room / KSP | 2.8.4 / 2.3.11 |
| Coil | 3.3.0 |
| Lifecycle | Declared 2.9.4; resolved atomic group 2.10.0 |

The version catalog and Gradle wrapper are the source of truth. This is a pinned
compatible set: update reviews are deliberate and must validate the set
together. Lint treats warnings as errors and disables only the time-dependent
`NewerVersionAvailable`, `AndroidGradlePluginVersion`, and `GradleDependency`
freshness checks; it does not suppress a blanket baseline.

Navigation 3 runtime 1.1.7 aligns the AndroidX Lifecycle atomic group to 2.10.0
even though the two direct Lifecycle dependencies remain declared at 2.9.4. Do
not force one Lifecycle artifact back to 2.9.4: the group must resolve together.
Milestone 1 uses OkHttp/kotlinx.serialization, Room, Android Keystore storage,
and a session-owned Coil image loader.

## Source Layout
Use package-by-feature directories inside
`app/src/main/java/com/getmaincourse/app/`:

- Root: `MainCourseApplication.kt` constructs dependencies, `MainActivity.kt`
  owns lifecycle integration, and `MainCourseApp.kt` owns auth-first UI and
  typed navigation.
- `data/model` mirrors the Rails wire contract; `data/network` is the API
  boundary; `data/session` owns protected credentials; `data/cache` owns Room;
  and `data/images` owns image URL and cache policy.
- `features/auth`, `features/session`, `features/recipes`, and
  `features/settings` contain the implemented product slice.
- `features/preview` contains the explicit Shopping/Search placeholders;
  `features/designsystem` is the development gallery; theme code is under
  `ui/theme`.

`MainCourseApplication` manually constructs one application-level API, encrypted
session store, Room database/store, repository, and image owner. Do not add a DI
framework for this app's current scale. `MainActivity` obtains the
lifecycle-retained `MainCourseViewModel`; the view model owns the session
coordinator and calls restore once. `onResume` rechecks session expiry.

The protected shell preserves four destinations: Recipes, Shopping, Search, and
Settings. Recipes and minimal account/sign-out Settings are real; Shopping and
Search remain explicitly labeled previews. The design gallery remains reachable
from Settings during development.

`DesignSystemScreen.kt` is a navigable, interactive token/component gallery for phone/tablet validation, not a production
destination or substitute for feature tests.

## Session, Cache, And Image Ownership

`EncryptedSessionStore` keeps the opaque bearer token, user, expiry, and API
origin together in an atomically written file under `noBackupFilesDir`. The file
is encrypted with an AES-GCM key generated and held by Android Keystore. Tokens
never belong in Room, preferences, routes, saved-instance state, logs, or image
requests.

Startup resolves the saved session before showing protected data. A missing,
corrupt, origin-mismatched, or expired session goes through protected-state
cleanup before authentication; cleanup failures stay on a recovery surface.
Expiry is also checked while the app resumes. A valid session establishes its
user scope, loads cached memberships, resolves and persists the active cookbook,
then refreshes memberships and recipes. A login `401` stays a form error, while
a `401` from an authenticated request clears protected state. Rails sessions
last 90 days and have no refresh-token flow.

Room database `maincourse.db` stores cookbook membership and selection, recipe
summaries, fetch state, and optional details. Every owned key/query includes the
user and, where applicable, cookbook. Schema version 1 is exported under
`app/schemas/`; do not add destructive migration fallback. Full cookbook and
recipe responses replace their scope transactionally, including successful
empty lists. A detail response updates only its recipe and never prunes peers.
Corrupt cached records are invalidated at their narrowest safe scope.

Recipe details are cached on demand when opened, not during initial list sync.
A previously opened detail remains readable offline with saved/offline feedback;
an unopened detail requires a connection and must not be rendered from summary
data as if complete. The list can be fully available offline only after it has
been fetched. There is no mutation outbox in this milestone.

The session coordinator owns the authenticated coroutine lifetime. User,
cookbook, catalog, and detail generations plus serialized cache writes prevent a
late request from publishing or persisting into a newer scope. Switching a
cookbook immediately removes the previous scope from view. A cookbook `403`
hides and purges that membership before one bounded rediscovery; a detail `404`
removes the stale recipe. Cancellation remains cancellation rather than a
user-visible server error.

`SessionImages` owns one Coil loader/client for the current user. It reuses that
user's private disk cache across process restarts, removes obsolete user
directories when preparing another account, and never attaches the API bearer
header to image requests. Image caching is best effort: placeholders are valid
offline even when an image was not retained. Cookbook switching removes old
images from view; account cleanup disposes requests and memory/disk ownership.

Logout first cancels authenticated work and hides protected content, attempts
remote revocation with a bounded timeout, then clears the encrypted session,
Room data, and image cache before admitting another account. A completed offline
logout removes local data but cannot promise server revocation. If local storage
or disk deletion fails, the app exposes cleanup recovery and blocks a new login
rather than claiming success. Cross-process durability for an interrupted
cleanup or purge is not complete; follow
[issue #94](https://github.com/SzymonNastaly/maincourse/issues/94) and do not
promise flawless logout when the operating-system delete fails.

## Theme Contract
`MainCourseTheme.kt` maps semantic roles from `app/assets/tailwind/application.css` and
`hauptgang-ios/Hauptgang/Utilities/MainCourseTheme.swift` into Material 3; extra roles live in `MainCourseColors`.

- Light mode only; ignore system dark mode and disable dynamic color.
- Use Material's native default sans for text.
- Bundle IBM Plex Mono regular/medium and use it only for times, servings, quantities, counts, and identifiers.
- Use mobile radii of 8dp for controls, 10dp for cards, and 12dp for panels.
- Keep surfaces flat with hairline borders; reserve elevation for native transient/floating UI.
- Keep native Material navigation, dialogs, sheets, fields, touch feedback, accessibility, and adaptive behavior.
- Use Material Symbols as checked-in vectors, not an icon font, emoji, or a competing family.
- Keep the adaptive launcher icon's original brown cookbook artwork. Its
  monochrome H/book stencil is only for launchers when the user explicitly
  enables themed icons.

The compact shell uses bottom navigation; expanded layouts use a navigation
rail. Both expose the same destinations and state. Tablet support is adaptive,
not a dedicated two-pane product.

## Local Tooling
The repository entry points are thin wrappers:

| Command | Behavior |
|---|---|
| `bin/android-gradle ARGS...` | Invoke `maincourse-android/gradlew` from any current directory and forward all Gradle arguments unchanged |
| `bin/android-build` | Run `assembleDebug` through `bin/android-gradle` |
| `bin/android-test` | Run `testDebugUnitTest lintDebug` through `bin/android-gradle` |
| `bin/android-test --device` | Also run `connectedDebugAndroidTest` |

Wrappers install nothing. Explicit `JAVA_HOME` and `ANDROID_HOME` win; otherwise they discover Android Studio's JBR and the
standard SDK. They never edit global shell profiles.

`bin/android-test --device` requires a running emulator or connected device.
Builds and automated Android tests are independent of the Rails bundle. The real
email and recipe flows need a reachable Rails server for live integration; a
valid unexpired session can start from previously fetched cache while offline.

Raw Gradle remains available with explicit environment control:
`ANDROID_HOME="$HOME/Library/Android/sdk" JAVA_HOME="/path/to/jdk-25"
./maincourse-android/gradlew -p maincourse-android :app:assembleDebug`.

In Android Studio's SDK Manager, install Command-line Tools (latest), Android 37, Build Tools 36.0.0, Platform Tools, Emulator,
and the Android 37 Google APIs ARM64 image. Create a Pixel AVD; the established local name is `MainCourse_Phone_API37`.

Command-line Tools 23.0 provides the preferred `android` package command. If
Studio installed it under `cmdline-tools/latest`, use that directory instead
of `cmdline-tools/23.0` in these examples:

```bash
"$ANDROID_HOME/cmdline-tools/23.0/bin/android" sdk install --sdk="$ANDROID_HOME" --platform=mac_arm64 platform-tools build-tools/36.0.0 platforms/android-37.0 system-images/android-37.0/google_apis/arm64-v8a
```

Its repository metadata is schema 2-3 with the Android 37.0 minor level. The
older package syntax also works with the same installed tools:

```bash
"$ANDROID_HOME/cmdline-tools/23.0/bin/sdkmanager" --sdk_root="$ANDROID_HOME" "build-tools;36.0.0" "platforms;android-37.0" "platform-tools" "emulator" "system-images;android-37.0;google_apis;arm64-v8a"
```

Boot the existing local AVD with `"$ANDROID_HOME/emulator/emulator" -avd
MainCourse_Phone_API37`.

## API Configuration
Gradle validates `BuildConfig.API_BASE_URL` at configuration time:

- Debug default: `http://10.0.2.2:3000/`.
- Debug override: `-Pmaincourse.apiBaseUrl=https://your-tunnel/`.
- Release: fixed to `https://app.getmaincourse.com/`; Gradle properties cannot
  override it.

The URL must use HTTP(S), include a host, end in `/`, and contain no credentials,
query, or fragment. HTTPS may use any valid host. Debug HTTP is restricted to
`10.0.2.2`, `localhost`, or `127.0.0.1`; arbitrary LAN HTTP is rejected. Release
denies all cleartext traffic.

For a USB-connected physical device, run `adb reverse tcp:3000 tcp:3000`, then
use `-Pmaincourse.apiBaseUrl=http://localhost:3000/`. An HTTPS tunnel is the
alternative for devices that cannot use reverse forwarding.

### Run against local Rails

Prepare the repository once, start Rails on loopback, boot the emulator, and
install the debug app:

```bash
bin/setup --skip-server
bin/rails server -b 127.0.0.1 -p 3000
# In another terminal:
"$ANDROID_HOME/emulator/emulator" -avd MainCourse_Phone_API37
bin/android-gradle :app:installDebug
```

The default debug URL maps emulator `10.0.2.2:3000` to the host's
`127.0.0.1:3000`. Use dedicated development accounts only. Do not record local
passwords, bearer tokens, fixture IDs, or downloaded provider configuration in
documentation or source control.

Android 17/API 37 requires the debug-only `ACCESS_LOCAL_NETWORK` runtime
permission for `10.0.2.2`, `localhost`, and `127.0.0.1`. Android presents this as
**Nearby devices**. The app waits for the first permission result before
restoring its view model; denial still permits cached/offline use with retryable
degraded state. If Android suppresses a later prompt, open **Settings → Apps →
MainCourse Dev → Permissions → Nearby devices**, choose **Allow**, return to the
app, and retry.

The permission exists only in the debug manifest and is requested only on API
37+ for those local hosts. A public HTTPS debug override does not prompt for
local-network access:

```bash
bin/android-gradle :app:installDebug \
  -Pmaincourse.apiBaseUrl=https://your-development-host.example/
```

No secrets belong in `BuildConfig`, committed Gradle properties, resources,
manifests, or logs. Google Services/provider plugins stay disabled until real
console configuration exists. Release is minified, unsigned in the repository,
uses the fixed public HTTPS API, and contains neither the local-network
permission nor a debug URL override. Signing keys and Play App Signing are
user-owned.

The manifest sets `allowBackup="false"` and `fullBackupContent="false"`. `data_extraction_rules.xml` also excludes every storage
domain, including device-protected storage, from cloud backup and device transfer on API 31+.

## Tests
The Android suites include:

- `MainCourseThemeTest.kt`: token identity and required text/surface contrast.
- `MainCourseAppTest.kt`: authentication validation, session recovery, cookbook
  switching, recipe list/detail cache states, logout, four destinations, Back,
  gallery restoration, and adaptive navigation.
- `SessionImagesTest.kt`: session loader reuse, cleanup, and unauthenticated
  image requests.

Use the Compose JUnit `v2` test rules. Keep Espresso explicitly pinned in the
catalog: Compose's older transitive version uses an input API removed in API 37.

Room, secure-store, API, coordinator, image URL, and theme behavior also have
JVM or device coverage at their appropriate boundary.

Compose and image device tests run through `MainCourseTestActivity`, a
non-exported host that exists only in the debug source set and is absent from
release. It injects production composables and actions for deterministic tests;
it is not an authentication bypass. Live Rails acceptance must install and use
the real `MainActivity`, encrypted store, Room database, API, and image owner.

## CI
`.github/workflows/android.yml` defines, but does not by itself prove execution
of, two Linux jobs:

- Build: JDK 25, clean SDK 37/Build Tools 36.0.0 installation, JVM tests, lint, and `assembleDebug`.
- Instrumentation: `ReactiveCircus/android-emulator-runner` on API 29 and 36 with Google APIs x86_64 Pixel 7 images.

`android-actions/setup-android@v4` receives command-line tools package ID
`16111833`, the current 23.0 package. Keep the API 29/36 matrix and compile SDK
37 independently reviewable; workflow presence is not evidence that either job
or the Milestone 0 gate passed.

## Development Without Play Console
Play Console registration and a physical Android phone are not prerequisites for
local builds, emulator testing, email authentication, or the core product
workflows. Install debug APKs directly on the emulator. Google Cloud OAuth and
Firebase setup can also proceed independently of Play Console; Google Play
services on a device/emulator are distinct from a Play developer account.

### Google OAuth signing fingerprint
Read the certificate used by this machine's debug builds:

```bash
bin/android-gradle :app:signingReport --console=plain
```

The owner-recorded **Android** OAuth client in #92 is intended to use
**`com.getmaincourse.app.debug`** and the **SHA1** from the report's `debug`
variant. The keystore normally lives at `~/.android/debug.keystore` and is
generated by the first debug build. The certificate fingerprint is public; the
keystore itself must stay outside source control. Another machine or a
regenerated keystore may require another OAuth client registration.

Keep the existing web/server client ID: Android will request Google ID tokens
for that audience so Rails can verify them. The Android client ID is a separate
package/certificate registration, not a replacement for Rails' web client.

The Android OAuth client ID is public configuration and is recorded in #92
alongside its package and signing identity. Keep Google Cloud's downloaded OAuth
JSON outside the repository as a setup reference; this client does not currently
load that file. It is not Firebase's `google-services.json`. No Android client
secret is needed, and the web/server client secret stays in Rails credentials.

Release is currently unsigned, so the report correctly shows no release
certificate. Later register `com.getmaincourse.app` with the certificate that
actually signs each installed release build. For Play-distributed builds, use
the **Play App Signing certificate**, not the upload certificate. A locally
signed release APK can be tested before Play access with its own certificate
registration once the owner establishes that signing key.

OAuth uses SHA-1 here; Android Digital Asset Links uses **SHA-256**. Production
links must use the production package/signing identity, never trust the debug
certificate. Test debug app-link verification on a separate development host.

See Google's [client authentication guide](https://developers.google.com/android/guides/client-auth).

## External Setup Tracks
External setup is tracked in
[issue #92](https://github.com/SzymonNastaly/maincourse/issues/92). These are
independent tracks, not a sequence that starts with Play registration:

- **Google Cloud now:** retain the debug OAuth client recorded in #92; add
  release signing identities when available. Provider integration requires the
  real Cloud configuration and a suitable Google-enabled emulator/device.
- **Apple now:** keep Apple's existing primary App ID grouping and Services ID
  so Apple subjects remain shared. Android uses the secure web handoff in the
  roadmap and requires the account-identity fix in #86 and a registered HTTPS
  callback. Play access is not a prerequisite.
- **Firebase now:** register the exact package for each build being tested
  (including `.debug`), configure FCM, and test on a Google APIs/Google Play
  emulator. Server credentials belong in Rails deployment secrets. Physical
  device/background-delivery validation remains a later gate.
- **Play when accessible:** create the app for `com.getmaincourse.app`, establish
  Play App Signing, and retain ownership of upload/signing keys. Record upload
  and Play signing fingerprints separately; configure license/internal testers.
- **RevenueCat/Play billing afterward:** add Android to the existing project,
  identify with the Rails user ID, retain `Hauptgang Pro`, and configure Play
  products. Real purchase/restore validation requires the Play track.
- **App links:** implement routing and use a development host for debug
  verification; publish production `assetlinks.json` for the actual production
  signing certificate once known.

Provider sign-in, billing, FCM, and app-link behavior are not verified until
their real consoles, signing identities, backend configuration, and device tests
are in place. The owner has recorded the Android OAuth client ID in #92, but no
provider sign-in is integrated into the app yet.
