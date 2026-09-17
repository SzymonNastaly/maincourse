# Android Release Workflow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add one local command that qualifies, signs, verifies, and uploads a versioned MainCourse Android App Bundle to Google Play closed testing.

**Architecture:** Gradle reads committed version and release configuration properties and exposes a release-verification task. A testable Bash orchestrator performs preflight, version mutation with rollback before upload, qualification, bundle verification, and a pinned Fastlane lane performs the transactional Play upload.

**Tech Stack:** Bash, Gradle Kotlin DSL, Android Gradle Plugin 9.4, Kotlin/JUnit, Ruby/Bundler, Fastlane `supply`, Google Play Developer Publishing API

**Spec:** `docs/superpowers/specs/2026-09-17-android-release-workflow-design.md`

## Global Constraints

- The release application ID is exactly `com.getmaincourse.app`.
- The release API base URL is exactly `https://app.getmaincourse.com/`.
- Debug remains `com.getmaincourse.app.debug` and defaults to `http://10.0.2.2:3000/`.
- A successful `bin/android-release VERSION` always uploads an AAB to the configured closed-testing track; it does not produce or distribute an APK.
- Release builds retain minification and resource shrinking.
- Signing keys, passwords, Google service-account JSON, and generated release artifacts remain untracked and must never be printed.
- The upload service account receives testing-release access only, not production permissions.
- Runtime network error reporting and production promotion remain outside this implementation.

---

### Task 1: Version Source and Gradle Release Guard

**Files:**
- Create: `maincourse-android/version.properties`
- Create: `maincourse-android/app/src/test/java/com/getmaincourse/app/ReleaseConfigurationTest.kt`
- Modify: `maincourse-android/app/build.gradle.kts:1-92`

**Interfaces:**
- Consumes: Java-properties keys `versionName` and `versionCode`.
- Produces: Gradle task `:app:verifyReleaseConfiguration`; release metadata values in `defaultConfig`; constants `releaseApplicationId` and `releaseApiBaseUrl` local to the build script.

- [ ] **Step 1: Add a failing configuration test**

Create `ReleaseConfigurationTest.kt` that locates the Android root in the same manner as `SimpleArchitectureTest`, loads `version.properties`, and asserts:

```kotlin
assertTrue(properties.getProperty("versionName").matches(Regex("\\d+\\.\\d+\\.\\d+")))
assertTrue(properties.getProperty("versionCode").toInt() > 0)
assertTrue(buildScript.contains("https://app.getmaincourse.com/"))
assertTrue(buildScript.contains("com.getmaincourse.app"))
```

It must also reject a missing trailing slash and assert the debug localhost URL remains present.

- [ ] **Step 2: Run the focused test and confirm the missing version file fails**

Run:

```bash
bin/android-gradle :app:testDebugUnitTest --tests com.getmaincourse.app.ReleaseConfigurationTest
```

Expected: FAIL because `maincourse-android/version.properties` does not exist.

- [ ] **Step 3: Add the committed version file and load it from Gradle**

Create:

```properties
versionName=0.1.0
versionCode=1
```

In `app/build.gradle.kts`, load the file with `Properties`, fail configuration for a missing/malformed name or non-positive integer code, and replace the inline `versionName` and `versionCode` literals with the parsed values. Name the release constants:

```kotlin
val releaseApplicationId = "com.getmaincourse.app"
val releaseApiBaseUrl = "https://app.getmaincourse.com/"
```

- [ ] **Step 4: Add `verifyReleaseConfiguration`**

Register a Gradle task that fails unless the application ID and endpoint equal the constants above, the release build is non-debuggable, minification and resource shrinking are enabled, the URL is HTTPS with a trailing slash, and release signing is configured. Print only non-secret package, endpoint, and version metadata.

- [ ] **Step 5: Run focused and release checks**

Run:

```bash
bin/android-gradle :app:testDebugUnitTest --tests com.getmaincourse.app.ReleaseConfigurationTest :app:verifyReleaseConfiguration
```

Expected: PASS and output identifying `com.getmaincourse.app`, production HTTPS, version `0.1.0 (1)`, and configured signing.

- [ ] **Step 6: Commit**

```bash
git add maincourse-android/version.properties maincourse-android/app/build.gradle.kts maincourse-android/app/src/test/java/com/getmaincourse/app/ReleaseConfigurationTest.kt
git commit -m "Guard Android release configuration"
```

### Task 2: Pinned Google Play Uploader

**Files:**
- Create: `maincourse-android/fastlane/Appfile`
- Create: `maincourse-android/fastlane/Fastfile`
- Create: `test/lib/android_play_uploader_test.rb`
- Modify: `Gemfile`
- Modify: `Gemfile.lock`
- Modify: `maincourse-android/.gitignore`

**Interfaces:**
- Consumes environment variables `MAINCOURSE_AAB`, `MAINCOURSE_VERSION_NAME`, `MAINCOURSE_VERSION_CODE`, `MAINCOURSE_GIT_SHA`, `MAINCOURSE_PLAY_TRACK`, and `SUPPLY_JSON_KEY`.
- Produces Fastlane lane `android closed_test`; exits successfully when the exact version code already exists on the target track and otherwise uploads one completed AAB release.

- [ ] **Step 1: Write a failing Fastlane configuration test**

Create a standalone Minitest that reads the Fastlane files and asserts the package is fixed to `com.getmaincourse.app`, all six environment variables are required, existing track version codes are queried before upload, `upload_to_play_store` receives `aab`, `track`, `release_status: "completed"`, and all listing/media/changelog upload skip flags.

- [ ] **Step 2: Run the focused test and verify failure**

Run:

```bash
bin/rails test test/lib/android_play_uploader_test.rb
```

Expected: FAIL because the Fastlane files are absent.

- [ ] **Step 3: Pin Fastlane and create minimal configuration**

Add `gem "fastlane", "~> 2.240", require: false` to the development group and run:

```bash
bundle install
```

Set the package in `Appfile`. In `Fastfile`, validate all required environment variables, call `google_play_track_version_codes`, return success when the requested integer code already exists, and otherwise call `upload_to_play_store` with the release name `VERSION (GIT_SHA)`. Skip APK, metadata, images, screenshots, and changelogs.

- [ ] **Step 4: Ignore Play credentials and release output**

Add these exact entries to `maincourse-android/.gitignore`:

```gitignore
/play-service-account.json
/releases/
```

- [ ] **Step 5: Verify syntax and focused tests**

Run:

```bash
bundle exec ruby -c maincourse-android/fastlane/Fastfile
bin/rails test test/lib/android_play_uploader_test.rb
```

Expected: both commands PASS without contacting Google Play.

- [ ] **Step 6: Commit**

```bash
git add Gemfile Gemfile.lock maincourse-android/.gitignore maincourse-android/fastlane test/lib/android_play_uploader_test.rb
git commit -m "Configure Android closed-test uploads"
```

### Task 3: Tested Release Orchestrator

**Files:**
- Create: `bin/android-release`
- Create: `maincourse-android/release.properties`
- Create: `test/lib/android_release_script_test.rb`

**Interfaces:**
- Consumes one semantic `VERSION` argument plus files `version.properties`, `release.properties`, `keystore.properties`, `upload-keystore.jks`, and `play-service-account.json`.
- Produces `maincourse-android/releases/maincourse-VERSION-CODE.aab`, its `.sha256` file, an updated `version.properties`, and a completed closed-test upload through `bundle exec fastlane android closed_test`.

- [ ] **Step 1: Build a hermetic release-script test fixture**

In `android_release_script_test.rb`, create a temporary Git repository with the expected Android paths and copy the release script under test into `bin/`. Put fake `android-test`, `android-gradle`, `keytool`, `jarsigner`, and `bundle` executables first on `PATH`; each records invocations, while fake Gradle creates `app/build/outputs/bundle/release/app-release.aab`.

The fixture's committed `release.properties` must contain:

```properties
playTrack=alpha
uploadCertificateSha256=04:2C:E7:F1:37:6B:01:4F:2C:F2:A1:ED:47:73:97:2C:C3:F3:A6:BC:83:51:F4:1D:4F:41:F1:E4:23:A5:33:FA
```

- [ ] **Step 2: Add failing validation and rollback tests**

Cover malformed/missing version arguments, dirty Git state, missing credential/signing files, mismatched certificate fingerprint, qualification failure, and bundle verification failure. Assert no fake `bundle exec fastlane` invocation occurs and `version.properties` is restored byte-for-byte for every pre-upload failure.

- [ ] **Step 3: Run focused tests and confirm the script is absent**

Run:

```bash
bin/rails test test/lib/android_release_script_test.rb
```

Expected: FAIL because `bin/android-release` does not exist.

- [ ] **Step 4: Implement preflight and reversible versioning**

Create an executable Bash script with `set -euo pipefail`, strict `X.Y.Z` validation, clean-tree checks, Java-properties readers that never echo passwords, integer version-code incrementing, expected certificate comparison, and a trap that restores the original version file until the upload stage begins.

The script must read these committed properties:

```properties
playTrack=alpha
uploadCertificateSha256=04:2C:E7:F1:37:6B:01:4F:2C:F2:A1:ED:47:73:97:2C:C3:F3:A6:BC:83:51:F4:1D:4F:41:F1:E4:23:A5:33:FA
```

- [ ] **Step 5: Implement qualification, artifact verification, and upload**

Run `bin/android-test`, then `bin/android-gradle :app:verifyReleaseConfiguration :app:bundleRelease`. Verify the AAB exists, verify its JAR signature, copy it to the ignored releases directory, write a SHA-256 sidecar, disable rollback, and invoke Fastlane from `maincourse-android/` with the six required environment variables. Print package, endpoint, version, code, track, commit, artifact, and checksum; never print credential contents or signing passwords.

- [ ] **Step 6: Add success, upload-failure, and retry tests**

Assert success increments `1` to `2`, creates `maincourse-0.2.0-2.aab`, records the `alpha` track and expected environment, and leaves the version change. Assert upload failure also leaves the version/artifact. Assert rerunning `0.2.0` accepts only that exact version-only dirty state and checksum-matching artifact, does not increment again, and delegates duplicate detection to the Fastlane lane. Any additional dirty path must fail.

- [ ] **Step 7: Run focused tests and shell lint checks**

Run:

```bash
bin/rails test test/lib/android_release_script_test.rb
bash -n bin/android-release
bin/android-release --help
```

Expected: tests PASS, Bash syntax is valid, and help documents the live closed-testing upload.

- [ ] **Step 8: Commit**

```bash
git add bin/android-release test/lib/android_release_script_test.rb maincourse-android/release.properties
git commit -m "Add verified Android release command"
```

### Task 4: Release Documentation and End-to-End Verification

**Files:**
- Create: `docs/android-release.md`
- Modify: `README.md:68-87`
- Modify: `maincourse-android/AGENTS.md:34-45`
- Modify: `.github/workflows/android.yml:5-15`

**Interfaces:**
- Consumes the completed `bin/android-release VERSION` workflow.
- Produces operator instructions for Play API setup, signing-key confirmation and backup, release execution, retry handling, Play verification, closed-test installation, and `adb logcat` diagnosis.

- [ ] **Step 1: Add documentation assertions**

Extend `android_release_script_test.rb` to assert the guide includes the exact command, closed-testing requirement, service-account least privilege, ignored credential path, upload-key fingerprint comparison, backup/recovery check, version commit step, Play processing check, device smoke test, and `adb logcat` instructions.

- [ ] **Step 2: Run the documentation test and verify failure**

Run:

```bash
bin/rails test test/lib/android_release_script_test.rb
```

Expected: FAIL because `docs/android-release.md` is absent.

- [ ] **Step 3: Write the operator guide and link it**

Document:

- enabling the Google Play Developer API and creating the dedicated service account
- granting only MainCourse testing-release permissions
- placing JSON at `maincourse-android/play-service-account.json`
- verifying `playTrack=alpha` against the actual closed-track ID
- comparing the committed SHA-256 upload fingerprint with Play Console
- backing up and restoring the key and password
- running `bin/android-release VERSION`
- retry behavior before and after upload begins
- confirming processing, installing from the closed-test link, and smoke-testing sign-in/data
- capturing filtered `adb logcat` output for the current generic network failure without including credentials

Link the guide from README and Android guidance. Add the release files and guide to the Android workflow path filters so later CI changes are not skipped.

- [ ] **Step 4: Run all Android and release-workflow checks**

Run:

```bash
bin/rails test test/lib/android_play_uploader_test.rb test/lib/android_release_script_test.rb
bin/android-test
bin/android-gradle :app:verifyReleaseConfiguration :app:bundleRelease
bundle exec ruby -c maincourse-android/fastlane/Fastfile
bash -n bin/android-release
git diff --check
```

Expected: all commands PASS. Do not invoke the live release command during verification because it always uploads.

- [ ] **Step 5: Inspect the release artifact without uploading**

Run `jarsigner -verify -verbose -certs maincourse-android/app/build/outputs/bundle/release/app-release.aab` and calculate its SHA-256. Confirm the certificate fingerprint, package/URL output from `verifyReleaseConfiguration`, and version match the committed configuration. Do not expose passwords or service-account JSON.

- [ ] **Step 6: Commit**

```bash
git add docs/android-release.md README.md maincourse-android/AGENTS.md .github/workflows/android.yml test/lib/android_release_script_test.rb
git commit -m "Document Android release operations"
```

- [ ] **Step 7: Prepare the one-time live setup handoff**

Report the exact Play Console steps still requiring the account owner: compare the upload certificate fingerprint, create/restrict/download the service account, confirm the closed-track API ID, and back up the signing files. Do not execute `bin/android-release` until those checks are complete and the user supplies the ignored credential locally.
