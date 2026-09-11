# Android recipe import

The Android client imports recipes from a URL or pasted text. It uses the same
asynchronous Rails import jobs as the web and iOS clients; image and camera input
are intentionally outside the initial Android slice.

## Client flow

`RecipeImportViewModel` owns the draft, cookbook selection, validation, and the
finite online request. URL imports call `POST /api/v1/recipes/import`; text
imports call `POST /api/v1/recipes/extract_from_text`. Both requests carry the
selected `X-Cookbook-Id`. Text is limited to the server's 50,000-character
maximum.

On the recipe library, the active cookbook is the compact top-bar title and
opens a checked cookbook menu when more than one cookbook is available. Manual
entry uses the quiet `Import` top-bar action; the full cookbook field remains on
the import form where choosing the destination is part of the task.

After Rails accepts an import, `RecipeRepository` makes one best-effort recipe
list refresh. The recipes view model then clears any stale refresh error and
polls every three seconds for up to 30 seconds while a recipe is `pending` or
`processing`. Poll failures stay unobtrusive and retain cached content; a later
poll or manual refresh can still reconcile Room. The server-created placeholder
remains visible while the background extraction job runs.

Cached pending imports also start polling when the recipe library is reopened,
so the behavior does not depend solely on the in-memory import completion event.

## Android share target

`MainActivity` is a `text/plain` `ACTION_SEND` target. It keeps incoming shared
text in memory until the authenticated Compose shell accepts it, so sharing into
a signed-out app continues after sign-in. A plain HTTP(S) URL selects link mode;
other shared content selects text mode. Consumed input is cleared and marked in
activity instance state to avoid reopening it after rotation.

New share intents are handled through `singleTop`/`onNewIntent`. The manifest
does not register image MIME types, storage permissions, or camera permissions.

## Verification

Contract and view-model behavior are covered by JVM tests. Compose navigation,
intent parsing/manifest MIME registration, and repository/Room reconciliation
are covered by device tests under `maincourse-android/app/src/androidTest`.
Run the standard gates with `bin/android-build`, `bin/android-test`, and
`bin/android-test --device` while an emulator or device is connected.
