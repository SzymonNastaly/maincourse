# Android onboarding and authentication

The first-run introduction mirrors the iOS interactive onboarding using native
Compose and Material controls. `features/auth/PreAuthViewModel.kt` owns four steps:

1. Welcome, with a recipe-source illustration and a direct login action.
2. An offline, playable Share → Share to… → MainCourse example. A brief processing
   state reveals the sample recipe with working portion scaling.
3. A recap of portions, shopping lists, and shared cookbooks. Keep and Continue
   without it both lead to signup; only Keep records a save request.
4. The existing email authentication screen. Back returns to the recap after the
   exercise has completed, so it need not be replayed.

The quiz is retired. Older installations can still link a previously submitted
quiz ID at their next authentication, but new onboarding creates no questionnaire
responses or anonymous research IDs. Existing completed installations go directly
to login. Successful authentication also marks restored sessions as completed.

## Local example and presentation

`ImportDemoScreen` has no account or network dependency. The sharing panels are
simulated Material bottom sheets with fictional contacts and inert placeholders;
they never send an intent to another app. Real imports still use the existing
Android share receiver, photo picker, and link/text import flows.

Hints appear after 1.2 foreground seconds and reset when leaving a stage or
backgrounding. Actions use a gentle size pulse, without spring bounce or animated
border thickness. The destinations row demonstrates scrolling once per visit;
touching the row cancels it. Disabled system animations suppress motion, and
TalkBack suppresses automatic scrolling. Content scrolls independently of pinned
actions, including at enlarged font scales.

Gradle bundles `config/starter_recipes/` directly as assets. Rails, iOS, and Android
therefore preview and save the same versioned JSON/JPEG. Keep source keys immutable;
a persisted retry must continue to mean the same recipe. App-owned text lives in
native Android string resources; sample recipe content remains source data.

After a successful empty cookbook load, the library offers this same example.
Completing it or dismissing its invitation hides the suggestion per user. Completing
the anonymous exercise records a pending dismissal for the next authenticated user,
including when they choose not to keep the recipe. Replay goes directly from the
recipe preview to Keep, without repeating the signup recap.

## Saving across authentication

`SampleSaveViewModel` owns one explicit Keep request. `OnboardingPreferences`
persists its UUID and source before networking, binds it to the first authenticated
user, and binds the personal cookbook before sending
`POST /api/v1/cookbooks/:id/recipe_saves`. The request ID, source, and destination
are retained on failure and reused on retry or process restoration. This is a
single user-requested sample save, not a general offline import/mutation queue.

Normal library startup continues while a preview/status banner shows saving or
offers Retry and Continue. Success refreshes the library and opens the server's
real recipe ID. Invitations, notifications, incoming shares, and an already-open
destination retain priority; the banner offers Open instead of replacing them.
The preview never supplies a synthetic recipe ID to the API.

Session changes cancel outstanding work and clear account-bound intent. Late
responses cannot publish into another account. Preferences contain no credentials
and are excluded from backup/transfer. The optional `starter_recipe_key` travels
through recipe JSON in Room without a schema migration; starter recipes alone do
not trigger the library notification-permission prompt.

## Verification

Run `bin/android-build`, `bin/android-test`, and `bin/android-test --device` with an
emulator. Unit tests cover navigation, request serialization, durable retries, and
session isolation. Device tests play the sharing exercise, scale an ingredient,
reach signup, navigate back, and check enlarged text and direct login.
