# Task 4 report — Android product UI, lifecycle wiring, and session images

## Outcome

Implemented the Milestone 1 Android product surface from the approved design:

- Real launch is authentication-first and backed by application-singleton API,
  encrypted session store, Room catalog store/repository, and image ownership.
- Email sign-in and signup use scrollable native forms, volatile password state,
  autofill/password semantics, local validation, duplicate-submit prevention,
  useful server errors, and the existing iOS signup convention (name plus a
  12-character password sent as its own confirmation). Sign-in accepts any
  nonempty existing password.
- The protected shell retains Recipes as its Back-stack start, four adaptive
  bar/rail destinations, and the design gallery from Settings.
- Recipes now provide a cookbook picker, adaptive cards, pull-to-refresh,
  loading/empty/degraded/error states, pending/failed import status, and
  semantic no-photo placeholders.
- Typed `RecipeDestination(cookbookId, recipeId)` navigation shows cached/fresh
  detail sections for time, servings, ingredients, numbered steps, and notes.
  Missing optional sections are omitted; uncached offline detail asks the user
  to connect rather than constructing incomplete detail.
- Restored detail routes are validated after authentication, remain in place
  through same-cookbook rediscovery, and are removed on cookbook scope changes.
- Settings shows account identity, logout, app version, and the development
  gallery.
- `MainCourseViewModel` starts restoration once in `init`; `MainActivity` checks
  expiry from `onResume` and never stores credentials in saved state or routes.
- Restore and cleanup failures have explicit retry/reset surfaces. Internal
  exception text is no longer forwarded as user-facing copy; API validation
  messages remain available.

## Application and image wiring

`MainCourseApplication` owns `AppContainer`, which constructs one instance each
of the API, encrypted session store, Room database/store, catalog repository,
and `SessionImages`. Its `ViewModelProvider.Factory` creates the production
`MainCourseViewModel` with the image cleanup callback.

`SessionImages` owns at most one loader at a time:

```kotlin
SessionImages(context: Context, apiBaseUrl: String)
fun resolve(path: String?): String?
fun loaderFor(userId: Long): ImageLoader
suspend fun clear()
```

The loader uses a private `cacheDir/session-images/user-<id>` directory and an
independent `OkHttpClient` with no bearer interceptor. Relative image paths are
resolved from the API origin (never `/api/v1`); absolute URLs remain absolute.
Card fallback order is card → legacy → hero → thumb, while detail uses hero →
legacy → card → thumb. Cleanup cancels calls, clears memory/disk data, shuts
down the loader/client, removes the directory, and must complete before a new
loader can share that path.

## Public UI signatures

```kotlin
@Serializable
data class RecipeDestination(val cookbookId: Long, val recipeId: Long) : NavKey

data class MainCourseActions(
    val restore: () -> Unit,
    val signIn: (SignInRequest) -> Unit,
    val signUp: (SignUpRequest) -> Unit,
    val switchCookbook: (Long) -> Unit,
    val refresh: () -> Unit,
    val openRecipe: (Long) -> Unit,
    val closeRecipe: () -> Unit,
    val logout: () -> Unit,
    val reset: () -> Unit,
)

@Composable
fun MainCourseApp(
    state: SessionState,
    actions: MainCourseActions,
    imageLoader: ImageLoader? = null,
    resolveImage: (String?) -> String? = { it },
)
```

The Task 3 `MainCourseViewModel` constructor, `StateFlow<SessionState>`, and
action signatures are unchanged; construction now automatically calls
`restore()` once.

## Test-only host

`MainCourseTestActivity` and `MainCourseTestContent` exist only in `src/debug`.
The merged debug manifest declares the activity `exported="false"`; release
manifests contain no test host. Instrumentation injects production composables
and actions there. `MainActivity` has no intent extra, login flag, or credential
bypass.

## Task 3 integration amendments

Two handoff concerns were exposed by product navigation and fixed with focused
coordinator regressions:

- If one operation clears a pending purge while a peer waits on the serialized
  repository write, the peer now continues instead of dropping a cookbook
  switch.
- A failed retry of a stale recipe purge preserves an unrelated open recipe
  detail instead of replacing it with the stale recipe's unavailable state.

The UI also keeps a same-cookbook typed detail route during transient catalog
rediscovery and reloads its detail when the list is restored.

## TDD evidence

### Initial RED

```text
$ bin/android-gradle :app:testDebugUnitTest --tests '*ImageUrlResolverTest' :app:compileDebugAndroidTestKotlin --console=plain
ImageUrlResolverTest.kt: Unresolved reference 'resolveImageUrl'
ImageUrlResolverTest.kt: Unresolved reference 'cardImagePath'
ImageUrlResolverTest.kt: Unresolved reference 'heroImagePath'
MainCourseAppTest.kt: Unresolved reference 'MainCourseTestActivity'
MainCourseAppTest.kt: unresolved product resources and actions
BUILD FAILED
exit 1
```

### Focused RED/GREEN

```text
$ bin/android-gradle :app:testDebugUnitTest \
    --tests '*SessionControllerTest.restoreStorageFailureKeepsProtectedDataUntilRetryOrExplicitReset' \
    --tests '*SessionControllerTest.failedRecipePurgeRetryDoesNotReplaceAnUnrelatedOpenDetail' \
    --console=plain
2 tests failed
BUILD FAILED
exit 1

$ bin/android-gradle :app:testDebugUnitTest \
    --tests '*SessionControllerTest.peerCompletingTheSamePendingPurgeDoesNotDropACookbookSwitch' \
    --console=plain
1 test failed
BUILD FAILED
exit 1

$ bin/android-gradle :app:testDebugUnitTest \
    --tests '*SessionControllerTest.restoreStorageFailureKeepsProtectedDataUntilRetryOrExplicitReset' \
    --tests '*SessionControllerTest.failedRecipePurgeRetryDoesNotReplaceAnUnrelatedOpenDetail' \
    --tests '*SessionControllerTest.peerCompletingTheSamePendingPurgeDoesNotDropACookbookSwitch' \
    --console=plain
BUILD SUCCESSFUL
exit 0
```

The initial focused Compose run had two test-harness mistakes (a nested
`runOnIdle` and an incorrect text matcher); after correcting only the tests, all
ten then-current product UI tests passed. The cookbook-startup recovery test was
subsequently observed failing before its recovery surface was implemented.

### Focused GREEN

```text
$ bin/android-gradle :app:testDebugUnitTest --tests '*ImageUrlResolverTest' --console=plain
BUILD SUCCESSFUL
exit 0

$ bin/android-gradle :app:connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.class=com.getmaincourse.app.MainCourseAppTest,com.getmaincourse.app.data.images.SessionImagesTest \
    --console=plain
BUILD SUCCESSFUL
exit 0
```

## Final verification

```text
$ bin/android-test --device
BUILD SUCCESSFUL in 35s
exit 0

JUnit XML: 61 JVM tests + 38 device tests = 99 tests,
0 failures, 0 errors, 0 skipped.

$ bin/android-gradle :app:assembleDebug :app:assembleRelease :app:lintRelease --console=plain
BUILD SUCCESSFUL in 27s
exit 0
```

The real debug app was installed and launched on the API 37 emulator against
the configured local Rails URL. The authentication screen was visually checked
at normal and 200% font scale; content remained readable, bounded, scrollable,
and clear of system bars. Screenshots were captured outside the repository at
`task4-auth.png` and `task4-auth-200.png`.

## Dependency resolution

- Coil Compose and network-OkHttp are declared at and resolve to `3.3.0`.
- Lifecycle ViewModel and runtime-Compose are declared at the requested `2.9.4`
  baseline. The existing pinned Activity 1.13.0 / Navigation 3 dependency graph
  aligns the AndroidX Lifecycle atomic group to 2.10.0 at runtime. Strictly
  forcing only runtime-Compose to 2.9.4 produces an unsatisfied atomic-group
  resolution, so no base toolchain or existing UI dependency was downgraded or
  upgraded in this task.

## Remaining boundaries

- Real Rails multi-user/offline acceptance and fixture setup remain Task 5.
- Provider auth, imports/deletes, search, shopping, and other later workflows
  were not added.
- Cross-process incomplete-cleanup durability remains tracked in issue #94 and
  was not duplicated here.

## Round 1 review amendments

### UI lifecycle and recovery

- Authentication now has one stable Compose callsite for signed-out and
  authenticating phases. Login email/password and signup mode/name/email/password
  survive request loading and 401/422 responses; Activity recreation still
  removes the volatile password while retaining saveable non-secret fields.
- The authentication form applies its 440dp bound before filling available
  width, and IME Done invokes the same validation/submission path as its button.
- Authenticated loading and ready phases share one `ProtectedApp` callsite.
  A user-keyed `DisposableEffect` clears navigation when that authenticated
  composition is replaced, while Navigation 3 and gallery saveable state remain
  intact across Activity recreation.
- Cold cookbook discovery now shows neutral cookbook/loading UI instead of a
  false empty state. `catalogStatus` is passed to Recipes, cached/degraded
  membership is visible even when recipes are fresh, and true empty/error states
  remain distinct.
- Recovery/startup surfaces scroll in short landscape/200%-font windows.
- Card navigation has one detail-load trigger. A same-scope 404 retains its
  unavailable destination and Back action; restored routes with no fetched list
  show a retryable load failure instead of spinning forever.
- Generic recipe/detail/not-ready failures use resource-backed status copy.
  Backend authentication validation remains visible, but controller/internal
  exception text is not rendered as product copy.
- Recipe card images and placeholders are decorative because the card already
  exposes the recipe name to accessibility services.

### SessionImages ownership change

Image preparation is now asynchronous and disk work is dispatched off Main:

```kotlin
SessionImages(context: Context, apiBaseUrl: String)
fun resolve(path: String?): String?
suspend fun prepare(userId: Long): ImageLoader
suspend fun clear()
```

`MainActivity` keeps a nullable loader while `prepare` runs, so placeholders are
available immediately without `runBlocking` or synchronous cache deletion in
composition. A coroutine `Mutex` serializes preparation, user changes, and
cleanup. First acquisition preserves the same user's existing disk directory
while deleting obsolete users; switching users disposes the old loader before
removal. Cancelled preparation cannot publish after cleanup. Failed recursive
deletion throws and therefore reaches the existing `CLEANUP_FAILED` session
path rather than claiming local cleanup completed.

### Round 1 RED evidence

The expanded Compose suite was observed with 13 behavioral failures before the
UI fixes, including lost auth fields/signup mode, duplicate detail opens, false
cold-start empty UI, missing degraded membership feedback, unreachable 404,
infinite restored-detail loading, unbounded tablet form, non-scrollable recovery,
and IME Done not submitting:

```text
$ bin/android-gradle :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.getmaincourse.app.MainCourseAppTest \
  --console=plain
26 tests, 13 failures
BUILD FAILED
exit 1
```

The image contract tests were added before the async API and failed compilation
on the missing `prepare` signature and injected cleanup/preparation seams:

```text
$ bin/android-gradle :app:compileDebugAndroidTestKotlin --console=plain
SessionImagesTest.kt: Unresolved reference 'prepare'
SessionImagesTest.kt: No parameter with name 'cacheDirectory'
SessionImagesTest.kt: No parameter with name 'deleteDirectory'
SessionImagesTest.kt: No parameter with name 'beforePublish'
BUILD FAILED
exit 1
```

Focused REDs also reproduced raw controller copy on recipe/detail surfaces.
During the full gate, the existing gallery recreation check exposed that adding
saveable/key state above Navigation 3 invalidated nested gallery restoration.
The final user-scoping implementation uses disposal keyed by authenticated user
identity without inserting another saveable owner above the back stack.

### Round 1 focused GREEN

```text
$ bin/android-gradle :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.getmaincourse.app.MainCourseAppTest,com.getmaincourse.app.data.images.SessionImagesTest \
  --console=plain
BUILD SUCCESSFUL in 57s
exit 0
```

New image coverage proves same-user cache reuse by a new `SessionImages`
instance, cross-user removal, no Authorization header, loader construction off
Main, failed-removal propagation, and cancellation/cleanup ordering.

### Round 1 final verification

```text
$ bin/android-test --device
BUILD SUCCESSFUL in 56s
exit 0

JUnit XML: 61 JVM tests + 57 device tests = 118 tests,
0 failures, 0 errors, 0 skipped.

$ bin/android-gradle :app:assembleDebug :app:assembleRelease :app:lintRelease --console=plain
BUILD SUCCESSFUL in 24s
exit 0
```

The actual unauthenticated launcher was inspected on the API 37 emulator at its
phone configuration (1080x2424, density 420), a tablet override (1600x2560,
density 320), and tablet 200% font scale. The 440dp form bound and scroll-safe
layout were visible. Emulator size, density, and font scale were restored to
physical defaults and 1.0 afterward. Screenshots are outside the repository as
`task4-round1-phone.png`, `task4-round1-tablet.png`, and
`task4-round1-tablet-large.png`.

## Round 2 review amendments

- A synthesized restored-detail error with no fetched recipe summary now retries
  through `refresh()` rather than the coordinator-rejected `openRecipe(id)`.
  When refresh returns the summary, the retained typed route's existing effect
  starts the detail load.
- The detail invalidation effect verifies the captured route is still the top
  back-stack entry before removing it. A direct authenticated-user change can
  therefore reset to Recipes without a stale effect popping that destination.
- The cancelled image-preparation regression now checks the serialized holder
  through a `@VisibleForTesting` internal accessor after cleanup, in addition to
  verifying directory removal.

### Round 2 RED/GREEN evidence

```text
$ bin/android-gradle :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.getmaincourse.app.MainCourseAppTest#restoredUnfetchedDetailRetryRefreshesListThenOpensReturnedRecipe \
  --console=plain
expected:<1> but was:<0> (refresh was not invoked)
BUILD FAILED
exit 1

$ bin/android-gradle :app:compileDebugAndroidTestKotlin --console=plain
SessionImagesTest.kt: Unresolved reference 'preparedUserIdForTest'
BUILD FAILED
exit 1

$ bin/android-gradle :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.getmaincourse.app.MainCourseAppTest,com.getmaincourse.app.data.images.SessionImagesTest \
  --console=plain
BUILD SUCCESSFUL in 57s
exit 0
```

### Round 2 final verification

```text
$ bin/android-test --device
BUILD SUCCESSFUL in 59s
exit 0

JUnit XML: 61 JVM tests + 58 device tests = 119 tests,
0 failures, 0 errors, 0 skipped.

$ bin/android-gradle :app:assembleRelease :app:lintRelease --console=plain
BUILD SUCCESSFUL in 19s
exit 0
```

## Round 3 review amendments

- The direct-user-change regression now changes cookbook scope and removes the
  old recipe, so it necessarily exercises the stale effect's pop path.
- The route identity guard is now an early return before both removal and
  `openRecipe`; a stale effect can neither pop the reset Recipes destination nor
  start the old user's detail when identifiers happen to match.
- Re-review confirmed the cancelled image-preparation test verifies the internal
  serialized holder is empty after cleanup through the existing
  `@VisibleForTesting` accessor, rather than inferring that solely from directory
  removal; its focused device test was rerun unchanged.

### Round 3 RED/GREEN evidence

```text
$ bin/android-gradle :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.getmaincourse.app.MainCourseAppTest#changingUserFromDetailDoesNotOpenTheOldRouteWhenIdentifiersMatch \
  --console=plain
expected:<1> but was:<2> (stale detail open)
BUILD FAILED
exit 1

# With the route guard deliberately removed to validate the revised pop test:
$ bin/android-gradle :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.getmaincourse.app.MainCourseAppTest#changingUserFromDetailDoesNotPopTheResetRecipesDestination \
  --console=plain
BUILD FAILED
exit 1

$ bin/android-gradle :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.getmaincourse.app.MainCourseAppTest \
  --console=plain
30 tests, 0 failures
BUILD SUCCESSFUL in 59s
exit 0

$ bin/android-gradle :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.getmaincourse.app.data.images.SessionImagesTest#cancelledOldUserPreparationCannotPublishAfterCleanup \
  --console=plain
BUILD SUCCESSFUL in 3s
exit 0

$ bin/android-gradle :app:assembleDebug :app:lintDebug --console=plain
BUILD SUCCESSFUL in 8s
exit 0
```
