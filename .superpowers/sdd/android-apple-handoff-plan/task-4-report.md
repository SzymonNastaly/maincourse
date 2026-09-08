# Task 4 report: cross-platform gates and documentation

## Outcome

The Android Apple handoff passed its local implementation gate and is ready for
the still-pending final whole-branch review. This does **not** complete Milestone
2: real Google and Apple identities, registered HTTPS Apple acceptance, Hide My
Email/name fallback, release signing, production Digital Asset Links, and #86
owner proof remain open in #86/#92/#100. Per the approved sequence, Milestone 3
is next after final review without waiting for those external gates.

Scoped runtime/documentation commit: `e14768d` (`Document and verify Android
Apple handoff`). Existing skill changes and `config/credentials.yml.enc` were
not read, staged, reverted, or modified.

## Rails gate

Development migration `20260908140000 Create apple auth transactions` was applied
additively by setup. The stale local Rails process was restarted on
`127.0.0.1:3000`; `/up` then returned 200 with current initializers/routes.

Final captured command:

```text
bin/ci > tmp/android-apple-ci.log 2>&1
exit recorded in tmp/android-apple-ci.exit: 0
984 runs, 2913 assertions, 0 failures, 0 errors, 2 expected corpus skips
9 system runs, 41 assertions, 0 failures, 0 errors, 0 skips
Continuous Integration passed in 24.29s
```

An earlier full attempt exposed the existing intermittent recipe system-login
input race (1 failure); its isolated test immediately passed and the fresh full
gate above passed. The first capture wrapper also used zsh's reserved `status`
variable and therefore did not write a reliable exit file; the final capture
uses `rc` and replaces both evidence files.

## Acceptance fix discovered by the real browser

The first real Chrome run showed the correct HTTP-only “HTTPS setup required”
message but no browser Cancel action. TDD added the missing action. A second run
showed that a normal non-Turbo POST from the required `no-referrer` page carries
`Origin: null`; Rails rejected it with 422 before following the custom scheme.
The final implementation narrowly accepts null origin only for `cancel`, while
still requiring the session authenticity token. A missing token remains 422.

```text
RED: bin/rails test test/controllers/android/apple_authentications_controller_test.rb:25
     1 run, 6 assertions, 1 failure (missing browser Cancel)
RED: bin/rails test test/controllers/android/apple_authentications_controller_test.rb -n '/null origin/'
     1 run, 1 assertion, 1 failure (422 instead of redirect)
GREEN: bin/rails test test/controllers/android/apple_authentications_controller_test.rb
       9 runs, 49 assertions, 0 failures/errors/skips
GREEN focused browser/callback/strategy suite:
       39 runs, 282 assertions, 0 failures/errors/skips
RuboCop: 2 files inspected, no offenses
```

## Android gate

Captured command and evidence:

```text
bin/android-gradle clean testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease connectedDebugAndroidTest --no-build-cache --continue --console=plain
tmp/android-apple-final.log
tmp/android-apple-final.exit: 0
BUILD SUCCESSFUL in 2m 22s
```

- JVM XML: 165 tests, 0 failures, 0 errors, 0 skipped under
  `maincourse-android/app/build/test-results/testDebugUnitTest/`.
- API 37 device XML: 109 tests, 0 failures, 0 errors, 0 skipped at
  `maincourse-android/app/build/outputs/androidTest-results/connected/debug/TEST-MainCourse_Phone_API37(AVD) - 17.xml`.
- Both lint variants and debug/minified unsigned release assembly passed.
- Release remains unsigned; no release owner key or association was available.

## Shared iOS regression

`hauptgang-ios/` is unchanged since prerequisite commit `820e7d8`. With the
restarted healthy Rails server, full `bin/ios-test` passed and compiled the app;
logs are `tmp/android-apple-ios.log` and `.exit` (0). Result bundle:

```text
/Users/szymonnastaly/Library/Developer/Xcode/DerivedData/Hauptgang-blzfsytyznjkdifvlygadtmsdplg/Logs/Test/Test-Hauptgang-2026.09.08_16-02-31-+0200.xcresult
261 total: 259 passed, 0 failed, 2 skipped
```

Both skips are `APIIntegrationTests` fixture skips: the API returned no recipes,
so recipe persistence and search-index assertions could not run. They are not
OAuth failures.

## Real `MainActivity` and local Rails evidence

All interaction used the installed debug `MainActivity`, Chrome, the real local
Rails API, Android Keystore store, and Room database—not
`MainCourseTestActivity` or a production auth bypass.

- Apple tap opened the exact same-origin Rails landing in a separate browser
  task. `tmp/android-apple-http-landing-cancel.png` shows the actionable local
  HTTPS requirement and Cancel; no fake provider consent was shown.
- Chrome's CSRF-protected Cancel posted to `/android/apple/cancel`; Rails returned
  the real 302 debug custom scheme, the existing `MainActivity` received it, and
  Apple busy state cleared while email/Google/Apple remained available.
- After force-stopping the app during a fresh browser attempt, a complete
  well-formed implicit callback restarted `MainActivity`, was discarded because
  the verifier was gone, issued no exchange request, and left usable signed-out
  auth. Explicit retry remained available.
- A **controlled local fixture proof** used an ignored mode-0600 Rails runner
  helper to lock and authorize the active transaction to the existing dedicated
  fixture. The actual implicit callback then completed one real PKCE exchange,
  marked the transaction consumed, created an API token, wrote `session.enc`,
  opened the Room-backed protected shell, and kept the app process stable. This
  proves local handoff/session integration only; it does not validate an Apple
  credential or live provider callback.
- The controlled session was signed out, then the dedicated surviving fixture
  completed real email login. The app was left running in the protected shell.
- A locally forwarded-HTTPS request to `/android/auth/apple` returned 200,
  no-store/no-referrer static return guidance and did not echo any query value.
  This is local response-shape proof, not production proxy or App Link proof.
- Final emulator state: airplane mode off, Wi-Fi/data on, font scale 1.0, night
  mode off, physical 1080x2424 at 420 dpi.

Raw transaction handles/codes and fixture credentials were never written to
tracked files. Relevant ignored `tmp/android-apple-*` artifacts are mode 0600;
provider secrets and owner accounts were not used.

## Documentation and remaining gates

Updated `docs/android.md`, `docs/oauth-sign-in.md`, the Android roadmap, and
`maincourse-android/AGENTS.md` for API/schema/PKCE, one-use expiry, strict
Android-only state/nonce, fixed request parameters and identity behavior,
credential lifetimes, browser task/process-death/relative timeout/no-retry
rules, custom-scheme versus verified release links, and both Google and Apple
branding exceptions.

No real Apple sign-in, Hide My Email, registered HTTPS callback, production
proxy/origin, release-signed provider login, or production `assetlinks.json` was
claimed. No owner Google/Apple account, consent flow, production data, deploy,
push, signing key, Firebase credential, or provider JSON was used.
