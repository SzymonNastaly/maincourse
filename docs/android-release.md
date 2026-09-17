# Android releases

MainCourse Android releases are built locally and uploaded to Google Play closed
testing with one command:

```bash
bin/android-release 0.2.0
```

This is a live command. If its checks pass, it uploads the signed AAB and
completes the release on the configured testing track. It does not create an APK
and has no build-only mode.

## One-time setup

### Install the uploader

Fastlane is isolated from the Rails bundle because its dependency constraints
conflict with the application's deployment gems.

```bash
BUNDLE_GEMFILE=maincourse-android/Gemfile bundle install
```

### Confirm and back up the upload key

The local files are ignored by Git:

- `maincourse-android/upload-keystore.jks`
- `maincourse-android/keystore.properties`

Run `keytool` against the keystore and compare its SHA-256 certificate
fingerprint with **Play Console → App integrity → Upload key certificate**. It
must also match `uploadCertificateSha256` in
`maincourse-android/release.properties`.

```bash
keytool -list -v \
  -keystore maincourse-android/upload-keystore.jks \
  -alias maincourse-upload
```

Back up both ignored signing files in a password manager or another encrypted,
recoverable location. Verify that the backup can be downloaded and opened before
depending on it. Do not regenerate the key for an existing Play app.

### Configure Google Play API access

1. In the Google Cloud project connected to Play Console, enable the **Google
   Play Android Developer API**.
2. Create a dedicated service account and download its JSON key.
3. In **Play Console → Users and permissions**, invite the service-account email.
4. Limit access to MainCourse and grant only permissions needed to view app
   information and create/manage testing-track releases. Do not grant production
   release or unrelated-app access.
5. Save the downloaded key as
   `maincourse-android/play-service-account.json`. This path is ignored by Git.
6. Confirm that `playTrack` in `maincourse-android/release.properties` is the API
   identifier shown for the intended closed-testing track. `alpha` is the
   standard default closed track; replace it if Play Console uses a custom track.

Never paste the service-account JSON, keystore password, or key password into an
issue, commit, terminal transcript, or release note.

## Publishing a closed-test release

1. Commit the code to release and ensure `git status --short` is empty.
2. Choose the next semantic version. The command increments Android's integer
   `versionCode` automatically.
3. Run:

   ```bash
   bin/android-release VERSION
   ```

4. The command runs Android unit tests and lint, checks the release variant,
   builds and verifies the signed AAB, and uploads it with Fastlane.
5. In Play Console, confirm that the reported version code is processing on the
   closed-testing track.
6. Commit the resulting `maincourse-android/version.properties` change.
7. Install or update MainCourse from the closed-test Play link. Smoke-test sign
   in and load recipes over both Wi-Fi and mobile data.

Closed testing—not internal testing—is the track used for the 12-tester
production-access requirement. Keep the required testers opted in for the period
shown by Play Console.

## Failures and retries

Failures before upload restore `version.properties` and make no Play API call.
Fix the reported problem and run the command again.

Once upload starts, the command keeps the incremented version and the verified
AAB under the ignored `maincourse-android/releases/` directory. Retry with the
exact same version:

```bash
bin/android-release 0.2.0
```

The retry is accepted only when the version-file change is the sole Git change
and the saved AAB matches its checksum. Fastlane checks the target track first;
if Play already contains that version code, it does not upload a duplicate.

Do not manually edit `versionCode` to work around a failed upload. If the state
does not meet the safe retry rules, inspect Play Console before preparing another
release.

## Diagnosing an installed test build

The app currently maps every network `IOException` to the generic message
“You're offline.” That message alone does not identify the failing host.

Confirm the installed package and version from a USB-connected phone:

```bash
adb shell dumpsys package com.getmaincourse.app | grep -E 'versionCode|versionName'
adb shell dumpsys package com.getmaincourse.app.debug | grep -E 'versionCode|versionName'
```

The Play app is `com.getmaincourse.app`. A locally installed debug build is
`com.getmaincourse.app.debug` and defaults to the emulator-only
`http://10.0.2.2:3000/` endpoint.

Compare the installed version code with **Play Console → Latest releases and
bundles**. To gather connection evidence, clear logs, reproduce the failure, and
save relevant networking/process messages:

```bash
adb logcat -c
# Reproduce the failure on the phone, then:
adb logcat -d | grep -E 'com.getmaincourse.app|UnknownHost|ConnectException|SSLHandshake|SSLPeer'
```

Review logs before sharing them and remove email addresses, tokens, or other
personal data. Also test `https://app.getmaincourse.com/` in the phone's browser
and compare Wi-Fi with mobile data to distinguish server, DNS/TLS, and
device-network failures.
