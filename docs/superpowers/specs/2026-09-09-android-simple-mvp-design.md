# Android Simple MVP Rewrite

## Intent

Replace the Android client's over-engineered coordination layer with a small,
idiomatic Compose application. Preserve the useful platform and library choices,
salvage focused code where it remains clear, and remove functionality that is
not needed for the first Android MVP.

This is a fresh rewrite within the existing `maincourse-android/` project, not an
incremental refactor of `SessionController`, `RecipeActionController`, or the
current navigation/state graph. Existing models, Retrofit declarations, Room
entities, secure session storage, theme code, and stateless composables may be
copied or adapted when doing so produces simpler code than rewriting them.

## MVP Scope

The MVP includes:

- Email sign in and sign up.
- Secure session restoration and sign out.
- Cookbook listing and switching.
- Cached recipe lists and recipe details.
- Recipe detail loading on demand.
- Moving and deleting recipes.
- Reviewing recipe ingredients and adding them to the shopping list.
- Editing account name and email.
- Deleting the account.
- Bottom navigation with Recipes, Shopping, Search, and Settings.

Shopping and Search remain clearly labeled placeholders. The MVP deliberately
does not include:

- The onboarding questionnaire or onboarding-response submission.
- Google sign in.
- Apple sign in or browser callback handling.
- Recipe editing or photo upload.
- The development design-system gallery.
- Tablet navigation rails or a tablet-specific layout.
- Background recipe-detail hydration.

Deferred functionality is removed from the rewritten production path rather
than retained as dormant coordination code. It can return as independently
designed features after the MVP works end to end.

## Technical Shape

Keep one `:app` module and the existing package-by-feature convention. Continue
using Kotlin, Compose, Material 3, Navigation 3, Retrofit with kotlinx
serialization, Room, Coil, coroutines, and Android Keystore. Do not add a DI
framework, modular architecture, use-case layer, or custom concurrency
framework.

`MainCourseApplication` creates a small application container containing the
Retrofit service, Room database, encrypted session store, repositories, and
Coil image loader. View models receive these dependencies through one plain
factory.

The intended production layout is:

```text
MainCourseApplication.kt       application container
MainActivity.kt                activity setup only
MainCourseApp.kt               session routing and bottom navigation
data/
  model/                       API/domain records
  network/                     Retrofit service and API error mapping
  cache/                       Room database, entities, and DAOs
  session/                     encrypted session persistence
  CookbookRepository.kt
  RecipeRepository.kt
features/
  auth/                        AuthViewModel and email auth UI
  session/                     SessionViewModel
  recipes/                     list, detail, ingredient review, move/delete
  settings/                    profile, deletion, and logout
  preview/                     Shopping and Search placeholders
ui/theme/                      shared visual tokens
```

Packages may contain more than one focused file, but abstractions should only be
introduced for current behavior. Prefer direct repository calls from view
models and explicit screen state/event parameters.

## Session and Request Handling

`SessionViewModel` owns only application authentication state: restoring,
signed out, signed in, and a recoverable restore error. On launch it reads the
encrypted store once. A missing, expired, corrupt, or wrong-origin session is
cleared and routes to authentication. A valid session routes to the protected
shell.

Keep the encrypted, Android-Keystore-backed `SessionStore`; tokens never enter
Room, preferences, navigation arguments, logs, or image requests. An in-memory
session provider exposes the current token to a small OkHttp interceptor.
Anonymous endpoints are explicitly marked so the interceptor omits the bearer.
Cookbook-scoped service methods continue to receive `X-Cookbook-Id` explicitly,
because the repository already knows the target cookbook and no mutable global
cookbook header is needed.

A `401` from an authenticated endpoint emits a session-expired event. The
session view model clears the encrypted session and Room, then shows the email
authentication screen. A `401` from sign in or sign up remains a form error.
There is no token refresh flow.

Sign out makes one best-effort server request, then clears the local session,
Room data, and session image cache. Local sign out does not wait indefinitely
for the network. Account deletion clears the same local state after confirmed
server success. Failures are shown as ordinary retryable errors; the MVP does
not implement durable cleanup markers or cross-process recovery protocols.

## Room as the UI Source of Truth

Room is the only source read by cookbook, recipe-list, and recipe-detail
screens. DAOs expose `Flow` queries scoped by user and cookbook.

- Opening Recipes starts observation of the selected cookbook's rows.
- Refreshing fetches the authoritative list and replaces that cookbook's rows
  in one transaction while retaining already cached details for recipes still
  present.
- Opening a recipe observes its cached row and fetches its detail once when the
  detail is absent or when the user requests refresh.
- A successful detail request updates that recipe only.
- Cached content stays visible during refresh and when refresh fails.

There is no background detail sweep, request-generation counter, stale-request
registry, or custom job grouping. Rows are keyed by user and cookbook, so a late
write to one scope cannot appear in a screen observing another scope. Normal
`viewModelScope` cancellation handles screen lifetime.

Selecting a cookbook persists the selected ID in Room and changes the observed
scope immediately. If the saved cookbook is no longer available, select the
first returned cookbook. An empty cookbook list has a dedicated empty state.

## Mutations

Move, delete, add-to-shopping, profile edit, and account deletion are explicit
online actions launched from the relevant view model. Each action has a small
loading/error state and disables duplicate submission while running.

After a successful recipe mutation, update the affected Room rows and refresh
the relevant list if needed. Do not implement automatic request replay,
mutation outboxes, commit predicates, pending purges, or local reconciliation
state machines. A failed or ambiguous network request reports failure and lets
the user deliberately try again.

Ingredient review retains stable row IDs only for the lifetime of the review
screen and sends the reviewed values once. Shopping itself remains a
placeholder; successful submission only needs confirmation and navigation back
to the recipe.

## UI and Navigation

Keep the established MainCourse light theme and semantic colors. Preserve
native Material behavior and the existing brown launcher artwork. Reuse
stateless screen composables when they remain compatible with the reduced
scope; remove callbacks and states belonging to deferred features.

The app has two top-level routes: authentication and the protected shell. The
protected shell uses phone bottom navigation with four destinations:

1. Recipes — real list and detail flow.
2. Shopping — placeholder.
3. Search — placeholder.
4. Settings — account management and sign out.

Recipe detail and ingredient review are nested routes. Use typed Navigation 3
keys but keep the back stack in one obvious owner. Do not build adaptive rail or
tablet-specific navigation for the MVP.

## Errors and Loading

Use ordinary, screen-local states: initial loading, content, empty, and error.
Cached content remains visible with a compact refresh error when available.
Without cached content, show an error surface with Retry. Mutations show their
error near the initiating control or in a simple dialog/snackbar.

Do not create a global error taxonomy beyond the small API error mapper needed
to turn HTTP and transport failures into user-facing messages. Cancellation is
not displayed as an error.

## Testing

Tests should protect behavior rather than reproduce concurrency implementation
details. Keep a compact suite covering:

- Session restore, email authentication, authenticated `401`, and logout.
- Retrofit request serialization and required headers.
- Room replacement, user/cookbook isolation, selected cookbook, and cached
  detail retention.
- Repository refresh and mutation success/failure using `MockWebServer` and an
  in-memory Room database where appropriate.
- View-model state for recipe loading, cookbook switching, and mutation errors.
- A few Compose navigation smoke tests for auth, recipe list/detail, placeholder
  tabs, and settings.
- One device test for the real Room database boundary if the JVM setup cannot
  prove it.

Delete tests for removed features and for the old controller's internal job,
generation, admission, and mutex behavior. The test suite should be materially
smaller than production code and should not require a bespoke fake framework.

## Migration and Completion

Implement the replacement in coherent vertical slices while keeping the branch
buildable. Old and new orchestration may coexist briefly, but no production
route should mix both models. Once the new flow covers the MVP, delete the old
controllers, deferred feature implementations, obsolete dependencies, tests,
resources, manifest entries, and documentation.

The rewrite is complete when:

- The MVP flows work against the local Rails server through the real
  `MainActivity`.
- Cached recipe lists and previously opened details remain readable offline.
- `bin/android-build` and `bin/android-test` pass.
- Relevant device tests pass on an emulator.
- Production code has no request generations, custom job registries, admission
  state machine, recipe-action host interface, background hydration sweep,
  provider-auth code, onboarding code, editor/image-staging code, or adaptive
  navigation code.
- `docs/android.md` describes the resulting application concisely rather than
  preserving the removed architecture.
