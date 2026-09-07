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
