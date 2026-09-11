# Android recipe editing

Android recipe editing mirrors the iOS feature's data contract while keeping
Android navigation, controls, and media access native. The editor is a
full-screen Navigation 3 destination with a Material 3 top app bar, system Photo
Picker, outlined fields, explicit row controls, and an unsaved-changes dialog.
The bottom navigation is hidden while editing so leaving always goes through
the editor's back handling.

Move and delete are recipe-management actions within the editor, displayed in
the first form section. The detail screen only offers Edit near the title. Both
actions keep their confirmation dialogs; moving warns when it will discard an
unsaved draft, and a successful move or deletion returns to the recipe list.

Recipe detail shows a safe HTTP(S) source link immediately below the title. As
on the web, the label is the source host without a leading `www.`, accompanied
by an external-link icon; the full URL is not used as visible link text.

## Data flow

`RecipeEditViewModel` initializes once from the cookbook-scoped Room detail
flow. It owns the draft, stable IDs for ingredient and instruction rows,
validation, dirty state, and the finite save job. Rows remain raw strings on
both mobile clients; the server reparses ingredients after an update.

Saving sends a complete `RecipeUpdateRequest` to
`PATCH /api/v1/recipes/:id`, including explicit nulls for cleared optional
fields. `RecipeRepository.update` writes the acknowledged detail, summary, and
search document in one Room transaction. It holds the same finite write mutex
as list refresh, move, and delete, so an overlapping refresh cannot overwrite a
later edit. Failed PATCH requests leave the cache unchanged and are never
replayed automatically.

If the user selected a cover photo, the repository uploads it in a second
multipart PATCH after the field update. The field response is cached before
the upload begins, so a failed image upload does not hide an already-confirmed
text edit. Retrying the editor save is idempotent.

## Photo handling

The Compose screen launches `ActivityResultContracts.PickVisualMedia` for one
image. This is Android's permissionless system Photo Picker; the app does not
request storage access. `SharedImageReader` reads the temporary content URI,
validates an image MIME type, and enforces the server's 15 MB limit while the
screen is active. The view model holds the selected bytes until save; no URI,
image bytes, or pending mutation is persisted to Room or saved state.

## iOS relationship

Both clients edit name, prep and cook time, servings, raw ingredients,
instructions, notes, source URL, and cover photo. Android deliberately uses a
full-screen destination rather than an iOS sheet, Material fields rather than
a SwiftUI Form, the Android Photo Picker rather than PhotosUI, and accessible
Up/Down actions instead of iOS list reordering gestures.

## Verification

Run `bin/android-build`, `bin/android-test`, and `bin/android-test --device`.
The JVM tests cover draft behavior and API serialization. Device tests cover
Room/search reconciliation, editor navigation, successful save, and dirty-draft
discard handling.
