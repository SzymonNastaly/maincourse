# Android Release Workflow Design

## Goal

Provide one reliable local command that builds, verifies, signs, and uploads a
MainCourse Android App Bundle to Google Play closed testing. The workflow should
remove ambiguity about build type, API endpoint, signing identity, and version
while keeping releases deliberately initiated by a developer.

The command is:

```bash
bin/android-release 0.2.0
```

A successful invocation always uploads the resulting AAB to the configured
closed-testing track. The workflow does not produce or distribute an APK.

## Scope

The workflow covers:

- Android release version management
- local preflight checks
- unit tests, lint, and release compilation
- signed AAB creation and validation
- upload to Google Play closed testing
- release provenance and operator documentation

It does not automate production promotion, tester management, store listing
metadata, screenshots, or release notes. It also does not change the app's
runtime network error reporting. Diagnosing the current generic "You're
offline" failure remains a separate debugging task.

## Current State

Debug and release builds intentionally differ:

- Debug uses application ID `com.getmaincourse.app.debug` and defaults to
  `http://10.0.2.2:3000/` for local Rails development.
- Release uses application ID `com.getmaincourse.app` and the fixed API base URL
  `https://app.getmaincourse.com/`.

`bin/android-build` creates only a debug APK. A local ignored upload keystore and
`keystore.properties` already exist, but there is no versioning or publishing
workflow. The existing CI workflow builds and archives only the debug APK.

A debug AAB cannot replace the Play app because its application ID differs. The
current Play-installed app therefore should not be assumed to target localhost;
the broad `IOException` mapping must be investigated with the installed version
and device logs.

## Approach

Use a small shell entry point for orchestration and Fastlane `supply` as the
established client for the Google Play Developer Publishing API.

Fastlane is preferred over Gradle Play Publisher because the latter is in
maintenance mode and the project uses a recent Android Gradle Plugin. A custom
Publishing API client would create unnecessary authentication, edit-transaction,
and track-management code. Fastlane will be pinned through an Android-specific
Bundler definition for reproducibility without constraining the Rails bundle.

## Version Management

Move `versionCode` and `versionName` from inline Gradle literals to a small,
committed Android version properties file. Gradle remains authoritative by
loading this file for every build.

`bin/android-release VERSION` will:

1. Validate `VERSION` as a release semantic version.
2. Require a clean Git working tree before making changes, except for a
   recognized retry containing only the version change produced by the prior
   failed upload.
3. Refuse reuse of the current version name unless it matches that recognized
   retry state and its verified AAB.
4. Increment the committed integer `versionCode` by one.
5. Write the requested `versionName` and incremented code before building.

If a pre-upload step fails, the script restores the version file to its original
contents. Once upload begins, it retains the new version file and verified AAB
so an uncertain or failed upload can be inspected and retried without silently
creating another version code. After a successful release, the operator commits
the version-file change.

## Release Flow

The release command performs these stages in order:

1. **Preflight**
   - Verify the Git working tree is clean.
   - Validate the requested version.
   - Verify the upload keystore, signing properties, and Play credential file
     exist with restrictive local handling.
   - Read only the upload certificate's public fingerprint and compare it with
     the expected committed fingerprint.
   - Verify the configured Play track is the intended closed-testing track.

2. **Version**
   - Increment `versionCode` and set the requested `versionName` in the committed
     version file.

3. **Qualify**
   - Run Android unit tests and lint through the existing repository wrappers.
   - Run release-specific Gradle checks needed to catch configuration and
     shrinking failures.

4. **Build and verify**
   - Build `bundleRelease` with minification and resource shrinking enabled.
   - Verify the AAB is signed by the expected upload certificate.
   - Verify package ID, version name, version code, release build type, and the
     production API base URL from authoritative build outputs.
   - Compute and display the AAB's SHA-256 checksum.

5. **Upload**
   - Upload only the verified AAB through Fastlane `supply`.
   - Skip store listing metadata, images, screenshots, and changelogs.
   - Commit the Google Play edit as a completed release on the configured closed
     testing track.
   - Use the semantic version and short Git commit as the developer-facing
     release name.

6. **Report**
   - Print the uploaded track, version name, version code, Git commit, artifact
     path, and checksum.
   - Remind the operator to commit the version-file change.

## Google Play Authentication

The workflow uses a dedicated Google Cloud service account connected to the
Google Play Developer account. It receives access only to MainCourse and only
the permissions required to manage testing-track releases. It receives no
production release or unrelated app permissions.

The downloaded JSON credential is stored at a documented ignored path under
`maincourse-android/`. It must never be committed, copied into generated output,
or printed. The script passes its path to Fastlane rather than parsing or
rewriting the credential.

The closed-testing API track identifier is explicit configuration. Setup must
confirm it against Play Console rather than assuming that a UI label maps to a
particular API track name.

## Signing Safety

The upload keystore and passwords remain in the existing ignored files. A
committed public SHA-256 certificate fingerprint allows the release command to
reject an accidental replacement key without exposing private material.

Before enabling upload, setup requires:

1. Comparing the local certificate fingerprint with Play Console's upload-key
   certificate.
2. Backing up both the keystore and its credentials outside the repository.
3. Confirming that the backup can be recovered.

The workflow does not generate or rotate a key automatically.

## Failure Handling and Retry Semantics

- A validation, test, lint, or build failure prevents any Play API call and
  restores the original version file.
- A signing or bundle-verification failure prevents upload.
- Once upload starts, the version file and AAB remain available even if the
  command exits unsuccessfully.
- A retry is recognized only when the requested version, version-file change,
  artifact metadata, and checksum all match the prior attempt. Before retrying,
  the workflow checks whether Play already contains the version code. If it
  does, the command reports that state instead of uploading a duplicate or
  incrementing again.
- Fastlane's Publishing API edit is committed only after the bundle and track
  update have succeeded.
- Secrets and signing passwords are redacted from normal and error output.

## Files and Responsibilities

- `bin/android-release`: orchestration, preflight, version update, verification,
  upload, rollback before upload, and final report.
- `maincourse-android/version.properties`: committed version name and monotonically
  increasing version code.
- `maincourse-android/app/build.gradle.kts`: loads version properties, retains the
  production-only release endpoint, and exposes focused verification tasks or
  metadata needed by the script.
- `maincourse-android/Gemfile` and `maincourse-android/Gemfile.lock`: pin the
  Fastlane dependency independently from the Rails deployment dependencies.
- `maincourse-android/fastlane/`: minimal Fastlane configuration for binary-only
  upload to the configured closed-testing track.
- `docs/android-release.md`: one-time Play API/signing setup, normal release
  procedure, recovery steps, and verification in Play Console.
- ignore rules: exclude the Play credential and copied release artifacts.

The existing `bin/android-build` and debug behavior remain unchanged.

## Testing

Automated coverage will verify:

- Gradle loads version values from the committed version file.
- Release configuration retains the production application ID and HTTPS API
  base URL while debug retains its separate ID and local default.
- Release preflight rejects malformed versions, dirty repositories, missing
  credentials, missing signing material, and certificate mismatches.
- No upload command runs after qualification or verification failure.
- Pre-upload failures restore the version file.
- Upload-stage failures preserve the version and artifact for retry.
- The final AAB passes Gradle release checks and signature verification.

Tests must stub the Play uploader; automated tests never contact or modify the
real Play Console. A final manual setup check will use the service account to
inspect the configured track before the first real upload.

## Operational Checklist

The release guide will keep the human steps short:

1. Confirm the desired code is committed and the working tree is clean.
2. Run `bin/android-release VERSION`.
3. Confirm the reported upload in Play Console and wait for processing.
4. Install/update from the closed-testing Play link and perform a production API
   smoke test.
5. Commit the version-file change.

Closed testing, rather than internal testing, is the track required for the
12-tester production-access process. Tester recruitment and the required testing
period remain Play Console concerns outside this build workflow.
