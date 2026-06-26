---
name: deploying-maincourse
description: Use when releasing a new version of the MainCourse iOS app to the App Store (public release) or TestFlight via the asc CLI — covers version bumps, building, uploading, and submitting for review.
---

# Deploying MainCourse

Ship a new public App Store version of the MainCourse iOS app (bundle `app.hauptgang.ios`, App Store Connect app ID **6758990872**, store name "MainCourse"). Uses the `asc` CLI (see `asc-cli-usage` skill).

## Key facts

- App ID: `6758990872`. Locales in use: `en-US`, `en-GB`, `de-DE`.
- Version lives in `hauptgang-ios/project.yml`: `MARKETING_VERSION` (public) + `CURRENT_PROJECT_VERSION` (build number). XcodeGen generates the project — never edit `.pbxproj`.
- TestFlight only: use `bin/ios-release` (`--external` for external testers). Public App Store: follow the steps below.

## Steps (public App Store release)

1. **Check what's already taken** so you don't collide:
   ```bash
   asc versions list --app 6758990872 --output table
   asc builds info --latest --app 6758990872 --pretty
   ```
   Both the new marketing version AND build number must be **higher** than anything already on ASC.

2. **Bump both** in `hauptgang-ios/project.yml`, then regenerate:
   ```bash
   cd hauptgang-ios && xcodegen generate
   ```

3. **Stage the build** (archive, upload, create draft version, attach — NO submit yet). All reversible.
   ```bash
   asc publish appstore --app 6758990872 \
     --project Hauptgang.xcodeproj --scheme Hauptgang \
     --export-options ExportOptions.plist \
     --version <VERSION> --build-number <BUILD> --wait
   ```
   ⚠️ **Always pass `--build-number` explicitly.** In local-build mode `asc` auto-resolves it to `1`, which ASC rejects. Note the returned `versionId` and `buildId`.

4. **Set "What's New"** on every locale (a new version starts with empty notes; description/keywords carry over):
   ```bash
   asc localizations update --version <VERSION_ID> --locale en-US --whats-new "Bug fixes and improvements."
   asc localizations update --version <VERSION_ID> --locale en-GB --whats-new "Bug fixes and improvements."
   asc localizations update --version <VERSION_ID> --locale de-DE --whats-new "Fehlerbehebungen und Verbesserungen."
   ```

5. **Validate** — must be `0 blocking` (subscription-image warnings + privacy-state info are non-blocking):
   ```bash
   asc validate --app 6758990872 --version <VERSION>
   ```

6. **Submit for review.** Use `asc review submit` (build is already attached). Do **NOT** re-run `asc publish appstore --submit` — it tries to re-upload and fails with "bundle version must be higher".
   ```bash
   asc review submit --app 6758990872 --version-id <VERSION_ID> --build <BUILD_ID> --dry-run   # preview
   asc review submit --app 6758990872 --version-id <VERSION_ID> --build <BUILD_ID> --confirm
   ```

7. **Confirm / monitor:**
   ```bash
   asc review status --app 6758990872          # expect WAITING_FOR_REVIEW
   asc submit cancel --version-id <VERSION_ID> --confirm   # to pull back before review starts
   ```

8. **Commit** the `project.yml` version bump.

## Common mistakes

| Mistake | Fix |
|---------|-----|
| Reusing an existing version/build number | Step 1 — both must exceed what's on ASC |
| Build uploads as number `1` | Pass `--build-number` explicitly (step 3) |
| Submit fails: "bundle version must be higher" | Build already uploaded — submit via `asc review submit`, not `publish --submit` |
| Submission blocked on missing release notes | Set `whatsNew` for all locales (step 4) |
| App Store name updated but home screen wasn't | `CFBundleDisplayName` in `Hauptgang/Resources/Info.plist` controls the home-screen label |
