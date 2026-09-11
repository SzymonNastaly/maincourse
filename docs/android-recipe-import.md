# Android recipe import

The Android client imports recipes from a URL, pasted or shared text, and shared
images. It uses the same asynchronous Rails import jobs and client-extracted page
content contract as the web and iOS clients.

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
list refresh and remembers that cookbook as awaiting reconciliation. The recipes
view model then clears any stale refresh error and polls every three seconds for
up to 30 seconds while a recipe is `pending` or `processing`. After the import
becomes terminal it performs five more refreshes, allowing the cover image that
Rails attaches just after the recipe update to reach Room as well. Poll failures
stay unobtrusive and retain cached content; an unsettled import is retried the
next time the app comes to the foreground.

Whenever `MainActivity` returns to the foreground, the active cookbook refreshes
immediately without showing a pull-to-refresh spinner. Cached pending imports or
an import remembered by the repository restart the bounded polling sequence, so
both the completed recipe and its image appear without a manual refresh.

## Android share target

`RecipeShareActivity` is the `ACTION_SEND` target for `text/plain`, `text/html`,
and `image/*`. It is a translucent, short-lived Compose activity that presents a
compact sheet over the sending browser or app. Imports start automatically in
the active cookbook. After Rails accepts the import, the activity closes and
returns the user to the sender; opening `MainActivity` is an explicit signed-out
or recovery action. The share activity is excluded from Recents.

Plain HTTP(S) text starts a web-page import, other text starts a text import, and
an image content URI is uploaded to `POST /api/v1/recipes/extract_from_image`.
Image access uses the sender's temporary URI grant, so no storage permission is
required. Shared images are limited to the server's 15 MB upload maximum.

## Rendered web-page import

For an ordinary public URL, the share activity loads the page in a temporary,
hidden WebView and runs `app/src/main/assets/recipe_page_extractor.js`. The script
extracts JSON-LD, relevant metadata, cover-image candidates, and cleaned body
HTML, then the client calls `POST /api/v1/recipes/import_with_content`. This
mirrors the iOS Safari preprocessing payload, but the WebView has its own browser
context and cannot see Chrome's cookies or exact tab state.

The WebView exposes no JavaScript bridge, disables file/content access, blocks
mixed content and extra windows, and is destroyed after extraction. Extraction
has a finite timeout and limits the JavaScript result before it crosses the
WebView process boundary. A render failure, unusable content, or server `413`
falls back to `POST /api/v1/recipes/import`.

YouTube, Instagram, and TikTok URLs skip client rendering and keep using their
specialized backend URL extractors.

## Verification

Contract and view-model behavior are covered by JVM tests. Compose navigation,
intent parsing/manifest MIME registration, rendered WebView extraction, and
repository/Room reconciliation are covered by device tests under
`maincourse-android/app/src/androidTest`.
Run the standard gates with `bin/android-build`, `bin/android-test`, and
`bin/android-test --device` while an emulator or device is connected.
