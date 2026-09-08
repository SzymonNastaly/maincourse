# Android Development
`maincourse-android/` is the native Kotlin/Jetpack Compose client. It provides
first-run onboarding, native email, Google, and Apple sign-in, cookbook-scoped recipe
browsing with offline cache support, account settings/deletion, and an
interactive development gallery. It uses the same Rails API and accounts as the
web and iOS clients. See
`docs/superpowers/plans/2026-09-07-native-android.md` for the product roadmap and
[issue #92](https://github.com/SzymonNastaly/maincourse/issues/92) for Android
external-service setup.

Milestone 1 behavior follows its
[approved design](superpowers/specs/2026-09-07-android-milestone-1-design.md) and
is tracked in [issue #93](https://github.com/SzymonNastaly/maincourse/issues/93).
Milestone 2's onboarding/account core follows its
[approved design](superpowers/specs/2026-09-08-android-milestone-2-core-design.md)
and is tracked in [issue #97](https://github.com/SzymonNastaly/maincourse/issues/97).
Its Google slice follows a separate
[approved design](superpowers/specs/2026-09-08-android-milestone-2-google-design.md)
and is tracked in [issue #98](https://github.com/SzymonNastaly/maincourse/issues/98).
The core, Google, and Apple implementation gates have passed locally. Real
Google and Apple accounts, release-signed provider verification, production App
Link association, and the full Milestone 2 review remain outstanding.

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
| Credential Manager / Play-services adapter | 1.6.0 / 1.6.0 |
| Google ID library | 1.2.0 |
| Fragment | Direct stable compatibility pin 1.9.0 |

The version catalog and Gradle wrapper are the source of truth. This is a pinned
compatible set: update reviews are deliberate and must validate the set
together. Lint treats warnings as errors and disables only the time-dependent
`NewerVersionAvailable`, `AndroidGradlePluginVersion`, and `GradleDependency`
freshness checks; it does not suppress a blanket baseline.

Credential Manager's Play-services adapter reaches an old Fragment version
through Biometric. Keep the direct stable Fragment 1.9.0 pin: without it, the
resolved Fragment 1.5.7 runtime fails the current
`InvalidFragmentVersionForActivityResult` lint check. The explicit
`NoCredentialException` handling in the provider adapter is likewise required
by `CredentialManagerMisuse`; it maps to the same safe unavailable-provider
message rather than exposing SDK details.

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
  boundary; `data/session` owns protected credentials; `data/onboarding` owns
  the nonsecret onboarding draft; `data/cache` owns Room; and `data/images`
  owns image URL and cache policy.
- `features/auth`, `features/onboarding`, `features/session`,
  `features/recipes`, and `features/settings` contain the implemented product
  slice.
- `features/preview` contains the explicit Shopping/Search placeholders;
  `features/designsystem` is the development gallery; theme code is under
  `ui/theme`.

`MainCourseApplication` manually constructs one application-level API, encrypted
session store, onboarding store, Room database/store, repository, and image
owner. Do not add a DI framework for this app's current scale. `MainActivity`
obtains the lifecycle-retained `MainCourseViewModel`; the view model owns the
session and onboarding coordinators and starts each restore once. `onResume`
rechecks session expiry.

Explicit Google sign-in uses Credential Manager's
`GetSignInWithGoogleOption`; it never opens automatically. `MainActivity` owns
the foreground chooser and cancels an unresolved attempt when that Activity is
destroyed. After a valid Google credential returns, the retained view model owns
the Rails exchange, so that phase may finish across Activity recreation. A new
Activity or process never restores or relaunches a chooser. Google and email
share one admission/busy gate, and cancellation restores the existing form and
its volatile field values.

Explicit Apple sign-in uses a Rails-hosted browser handoff and the system
browser. The retained view model creates an in-memory PKCE verifier, starts an
anonymous five-minute transaction, and launches the returned same-origin URL
once in a separate browser task. The verifier, handle, and relative five-minute
wait deadline are never persisted. Rotation retains the attempt without
relaunching it; process death loses the proof, so a later callback is discarded
and the user starts again. Cancel, timeout, provider failure, or a failed single
exchange releases the shared auth gate. There is no automatic retry.

`MainActivity` accepts Apple returns in `onCreate` and `onNewIntent`. Release
parses only `https://app.getmaincourse.com/android/auth/apple`; debug additionally
accepts `com.getmaincourse.app.debug:/oauth/apple`. Parsing rejects duplicate,
unknown, conflicting, oversized, or malformed parameters and requires the
returned transaction handle to match the active in-memory attempt. The debug
custom scheme is a local-development convenience, not Digital Asset Links
proof. The release intent filter requests App Link verification, but production
association remains unverified until the owner publishes `assetlinks.json` for
the certificate that actually signs `com.getmaincourse.app`.

The protected shell preserves four destinations: Recipes, Shopping, Search, and
Settings. Recipes and account/preferences Settings are real; Shopping and Search
remain explicitly labeled previews. The design gallery remains reachable from
Settings during development.

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

Profile updates and deletion are online-only account operations and never send
`X-Cookbook-Id`. Profile request ownership uses the user generation, user ID,
and unchanged bearer identity rather than object identity, so replacing the
server-returned user does not invalidate legitimate recipe work. The returned
user is authoritative in memory, but the app reports a completed save only
after serializing it through the encrypted session store. If Rails accepted the
change and that local write failed, **Retry save** writes the accepted session
locally without repeating the PATCH. Definite server/network rejection keeps
the prior acknowledged value and requires a deliberate retry; account errors do
not overwrite recipe errors.

Account deletion sends one captured-bearer `DELETE /api/v1/account`, without a
cookbook header or automatic retry. A `204` immediately hides protected content
and runs the established cancellation-safe credential, Room, and image cleanup;
it does not send a redundant logout. A `401` invalidates the session but is not
reported as confirmed deletion. HTTP errors preserve the session, while any
transport-level failure without an HTTP status is explicitly ambiguous and
offers deliberate retry or sign out. Shared-cookbook ownership transfer is a
Rails account contract; Android clears all data owned by the deleted account
after success.

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

Logout, account deletion, and authenticated-session invalidation also request a
bounded, best-effort Credential Manager `clearCredentialState`. This clears the
provider's selection state; it does not revoke Google permissions or remove a
device account. Its failure never blocks the authoritative Keystore, Room, and
image cleanup or a later email login.

That same limitation applies after a successful account deletion: ordinary
in-process cleanup is non-cancellable and tested, but Android does not yet have
a durable marker that guarantees cleanup resumes if the OS kills the process or
storage itself is broken. This exact cross-process cleanup/purge protocol gap is
tracked in [issue #94](https://github.com/SzymonNastaly/maincourse/issues/94).

## Onboarding

Onboarding restores alongside session startup, but routing waits for secure
session restoration: a valid session always opens protected content and marks
onboarding complete. A signed-out first run shows welcome, household, saving
habits, diet, then embedded signup. Back retains answers. Skip and the existing-
account shortcut discard the questionnaire and open standalone authentication;
sign out or account deletion does not make onboarding repeat.

`AtomicOnboardingStore` writes a versioned JSON record under
`noBackupFilesDir` with `AtomicFile`. It stores only the API origin, a random
draft-local UUID, step, validated answer values, and completion state—never a
password, bearer, hardware identifier, or RevenueCat identifier. The origin
prevents a debug draft crossing API environments. Writes are serialized so an
older answer cannot recreate a skipped or consumed draft. Unknown JSON fields
are tolerated; malformed, wrong-origin, or invalid records reset onboarding
only. Read/write errors expose Retry and Continue without saving and never
discard a valid encrypted session or recipe cache.

Entering embedded authentication starts one best-effort unauthenticated
`POST /api/v1/onboarding_response`. An explicit email-auth action starts the
first attempt after an AUTH restore, joins an owned running attempt within its
single five-second budget, or retries a previously failed attempt, then sends the
draft UUID as `onboarding_device_id` without making analytics submission an
authentication prerequisite. Restoring directly at the AUTH step does not
eagerly post again. Failed authentication retains the
draft and UUID for an explicit retry; a securely stored successful session
consumes them even if cookbook refresh later fails. There is no background retry
loop or durable mutation outbox, and a late timeout can leave an anonymous,
unlinked analytics response by design.

## Theme Contract
`MainCourseTheme.kt` maps semantic roles from `app/assets/tailwind/application.css` and
`hauptgang-ios/Hauptgang/Utilities/MainCourseTheme.swift` into Material 3; extra roles live in `MainCourseColors`.

- Light mode only; ignore system dark mode and disable dynamic color.
- Use Material's native default sans for text.
- Bundle IBM Plex Mono regular/medium and use it only for times, servings, quantities, counts, and identifiers.
- Use mobile radii of 8dp for controls, 10dp for cards, and 12dp for panels.
- Keep surfaces flat with hairline borders; reserve elevation for native transient/floating UI.
- Keep native Material navigation, dialogs, sheets, fields, touch feedback, accessibility, and adaptive behavior.
- Provider controls are the only color/type exceptions: the Google button keeps
  its full-color G and Google Sans Medium, while the Apple button keeps its
  official white mark and black/white treatment.
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
manifests, or logs. The Google Credential Manager integration needs neither the
Google Services Gradle plugin nor `google-services.json`; both remain absent.
Release is minified, unsigned in the repository,
uses the fixed public HTTPS API, and contains neither the local-network
permission nor a debug URL override. Signing keys and Play App Signing are
user-owned.

The local HTTP server can create an Apple transaction and render its browser
landing, but it cannot complete real Apple authorization because Apple requires
a registered HTTPS return URL. The landing says that HTTPS setup is required
and retains a CSRF-protected, non-Turbo Cancel action that returns to the app.
Browser pages containing handoff forms use `Referrer-Policy: strict-origin` so
normal Rails/OmniAuth origin checks work without disclosing the handle-bearing
path or query; API, callback, failure, and static fallback responses remain
`no-referrer`. Use a real registered HTTPS development origin for provider
testing; never add a fake consent route or weaken production redirects to make
localhost appear valid.

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

Google tests cover nonce generation, exact anonymous OAuth payloads, no replay,
SDK credential parsing/error mapping, shared email/provider admission, chooser
and exchange ownership across recreation, cleanup notification, and both auth
surfaces. They do not substitute for a real Google account and registered
package/certificate.

Apple tests cover RFC 7636, exact anonymous start/exchange payloads, strict
callback parsing, transaction binding, one-shot browser launch, rotation,
process-death rejection, relative timeout behavior, cancellation, and reuse of
the existing secure session path. Rails tests cover strict browser state/nonce,
digest-only and single-use transaction state, callback failure mapping, and the
explicit new-account decision. Synthetic credentials and a controlled local
fixture can prove the handoff boundaries, but neither proves Apple credential
validation, Hide My Email, a registered HTTPS callback, or a verified release
App Link.

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
The public audience currently used by Android is
`1048887933015-tga3ld77ufb2jfgugh5b5ddgo854uoto.apps.googleusercontent.com`,
matching `GOOGLE_SERVER_CLIENT_ID` in the iOS project and Rails' Google client
configuration.

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

For live verification, install with `adb install -r` so existing MainCourse app
data survives. Use the real `MainActivity`, tap **Continue with Google**, and
confirm the real chooser (or Google add-account flow) opens only from that tap.
Cancel and retry, rotate while the chooser owns the attempt, and confirm the
email form remains usable. A zero-account emulator verifies only SDK admission,
cancellation, and recovery. Do not claim provider success until an owner adds a
test account interactively, authorizes its use and consent, and the returned
credential completes the Rails exchange. Never automate or record a Google
password, ID token, nonce, or account identity.

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
  so Apple subjects remain shared. Android's PKCE-bound web handoff reuses the
  existing `/auth/apple/callback`; real acceptance still requires #86, a
  registered HTTPS callback, an owner-controlled Apple/Hide My Email identity,
  and the fresh-confirmation name fallback check. Play access is not a
  prerequisite.
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

Google and Apple sign-in are integrated and their local code/device gates are
complete. The owner-recorded debug Android Google registration in #92 remains
live-unverified until a real credential is returned for this package and
certificate. Apple credential validation, registered-HTTPS return, Hide My
Email, release-signed provider behavior, production App Link association,
billing, and FCM retain their respective real-service gates. Milestone 3 may
proceed while those owner-controlled gates and the final Milestone 2 review stay
open.
