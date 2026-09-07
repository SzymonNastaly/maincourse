# Task 3 report — Android session coordinator and scoped reconciliation

## Outcome

Implemented the Milestone 1 non-UI session/catalog layer:

- `CatalogRepository` serializes cache writes, checks coroutine cancellation before writes, and keeps full-list replacement separate from partial detail updates.
- `SessionController` owns restore/auth/logout, expiry/origin validation, user/cookbook generations, cookbook selection, cache-first state, reconciliation, error recovery, and bounded cleanup/revocation.
- `MainCourseViewModel` creates the controller with `viewModelScope` and delegates its actions.
- Added `androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4`; no Activity, Compose, image-loader, or application-container wiring was added.

The coordinator deliberately remains the one orchestration boundary rather than introducing a generic state-machine framework. Persistence/network behavior stays in the existing interfaces and `CatalogRepository`; observable UI data stays in `SessionState`.

## Public contract for Task 4

### Construction

```kotlin
CatalogRepository(api: MainCourseApi, store: CatalogStore)

SessionController(
    api: MainCourseApi,
    sessionStore: SessionStore,
    catalogRepository: CatalogRepository,
    baseUrl: String,
    clock: Clock,
    scope: CoroutineScope,
    imageCleanup: suspend () -> Unit,
    revokeTimeoutMillis: Long = 5_000,
)

MainCourseViewModel(
    api: MainCourseApi,
    sessionStore: SessionStore,
    catalogRepository: CatalogRepository,
    baseUrl: String,
    clock: Clock,
    imageCleanup: suspend () -> Unit,
)
```

Task 4 should construct the stores/API/repository as application singletons, inject the real session-owned image cleanup callback, and obtain `MainCourseViewModel` through its factory. `MainCourseViewModel` supplies `viewModelScope`; callers do not supply a scope to it.

### State

`StateFlow<SessionState>` exposes no bearer credential. `SessionState` contains:

- `phase`: `RESTORING`, `SIGNED_OUT`, `LOADING_COOKBOOKS`, `READY`, `SIGNING_OUT`, `RESTORE_FAILED`, or `CLEANUP_FAILED`
- public `user`, `cookbooks`, `activeCookbookId`, and `recipes`
- `recipesFetched`, `catalogStatus`, and `recipeStatus`
- optional `RecipeDetailState` with `LOADING`, `FRESH`, `SAVED_OFFLINE`, `ERROR`, `UNAVAILABLE`, or `NOT_READY`
- `authError`, general `message`, `canRetry`, and `canReset`

`LoadStatus` is `IDLE`, `LOADING`, `FRESH`, `DEGRADED`, or `ERROR`.

### Actions

All actions return `Job` so tests/callers may await them; UI can ignore the return value:

```kotlin
restore()
signIn(SignInRequest)
signUp(SignUpRequest)
switchCookbook(id: Long)
refresh()
openRecipe(id: Long)
closeRecipe()
logout()
reset()
checkExpiry()
```

- Retry `RESTORE_FAILED` with `restore()`; `reset()` is the explicit protected-data escape hatch.
- Retry cookbook/list startup with `refresh()`.
- Retry an uncached/cached detail with `openRecipe(id)`.
- Retry `CLEANUP_FAILED` with `logout()` or `reset()`; authentication remains blocked until cleanup succeeds.
- Task 4 must call `checkExpiry()` from the app-resume lifecycle hook.

## Behavioral coverage

The pure JVM fake store enforces Room's membership foreign-key behavior before selection and recipe replacement, including empty replacement. The 28 coordinator/repository tests cover:

- cache-first and empty-cache startup, explicit retry/reset, origin mismatch, malformed/expired sessions, and transient session-read failure
- sign-in/sign-up persistence, auth-form 401, duplicate submit suppression, persistence failure, and authenticated 401 cleanup
- personal-first fallback, authoritative membership removal, switch races, immediate old-scope clearing, stale/cancelled response suppression, and membership write ordering
- preserved cache on transport/5xx failure, authoritative successful empty lists, complete-list versus partial-detail writes, cached/uncached offline detail, and pending import handling
- immediate 403 purge, one bounded rediscovery, alternate-membership recovery, failed rediscovery without forbidden-cache exposure, and 404 removal
- expiry on resume, bounded logout revoke, guaranteed best-effort local cleanup, visible/retryable cleanup failure, and cancellation checks before late repository writes

## TDD evidence

### Initial RED

```text
$ bin/android-gradle :app:testDebugUnitTest --tests '*SessionControllerTest' --console=plain
> Task :app:compileDebugUnitTestKotlin FAILED
Unresolved reference 'SessionController'
Unresolved reference 'CatalogRepository'
Unresolved reference 'SessionPhase'
Unresolved reference 'LoadStatus'
Unresolved reference 'DetailStatus'
BUILD FAILED
exit 1
```

### Focused regression RED/GREEN cycles

```text
$ bin/android-gradle :app:testDebugUnitTest --tests '*SessionControllerTest.concurrentSwitchesCannotPersistTheOlderSelectionOrStartItsRefreshLate' --console=plain
SessionControllerTest > concurrentSwitchesCannotPersistTheOlderSelectionOrStartItsRefreshLate FAILED
java.lang.AssertionError (a stale shared-cookbook recipe write occurred)
BUILD FAILED
exit 1

$ bin/android-gradle :app:testDebugUnitTest --tests '*SessionControllerTest.concurrentSwitchesCannotPersistTheOlderSelectionOrStartItsRefreshLate' --console=plain
BUILD SUCCESSFUL
exit 0

$ bin/android-gradle :app:testDebugUnitTest --tests '*SessionControllerTest.restoreShowsCachedPersonalCookbookBeforeOfflineRefreshFails' --console=plain
SessionControllerTest > restoreShowsCachedPersonalCookbookBeforeOfflineRefreshFails FAILED
java.lang.AssertionError (recipe refresh ran before membership discovery completed)
BUILD FAILED
exit 1

$ bin/android-gradle :app:testDebugUnitTest --tests '*SessionControllerTest.restoreShowsCachedPersonalCookbookBeforeOfflineRefreshFails' --console=plain
BUILD SUCCESSFUL (as part of the full focused class run)
exit 0

$ bin/android-gradle :app:testDebugUnitTest --tests '*SessionControllerTest.forbiddenCookbookPerformsOneRediscoveryAndFallsBackToAnotherMembership' --console=plain
SessionControllerTest > forbiddenCookbookPerformsOneRediscoveryAndFallsBackToAnotherMembership FAILED
expected:<FRESH> but was:<LOADING>
BUILD FAILED
exit 1

$ bin/android-gradle :app:testDebugUnitTest --tests '*SessionControllerTest.forbiddenCookbookPerformsOneRediscoveryAndFallsBackToAnotherMembership' --console=plain
BUILD SUCCESSFUL
exit 0
```

### Focused GREEN

```text
$ bin/android-gradle :app:testDebugUnitTest --tests '*SessionControllerTest' --console=plain
BUILD SUCCESSFUL
28 tests, 0 failures, 0 errors
exit 0

$ bin/android-gradle :app:testDebugUnitTest --tests '*OkHttpMainCourseApiTest' --console=plain
BUILD SUCCESSFUL
13 tests, 0 failures, 0 errors
exit 0

$ bin/android-gradle :app:lintDebug --console=plain
BUILD SUCCESSFUL
exit 0
```

### Final gate

```text
$ bin/android-test
BUILD SUCCESSFUL in 3s
37 actionable tasks: 6 executed, 31 up-to-date
exit 0

JUnit XML summary: 43 tests, 0 failures, 0 errors, 0 skipped across 3 suites.
```

## Scoped self-review

- Verified no token appears in `SessionState` or the view-model surface.
- Verified repository writes occur only after cancellation checks and under one write mutex; cleanup waits for cancelled work before clearing stores.
- Verified user/cookbook/detail publications require captured generations and immutable request context.
- Verified 403 rediscovery is bounded and excludes the forbidden scope before persistence; 404 cancels sibling cookbook work before removal.
- Verified cached recipes/details survive failed requests and successful empty list replacement remains authoritative.
- Verified no UI, image implementation, `MainActivity`, or application singleton wiring entered this task.

## Known concerns / next-task boundaries

- Task 4 must wire application-singleton `SessionStore`, `CatalogStore`, API, repository, and the concrete image cleanup callback.
- Task 4 must invoke `restore()` at startup and `checkExpiry()` on resume, then map controller messages/statuses to resource-backed UI copy and accessibility behavior.
- No device/Room instrumentation or Compose tests were added here; those remain with the persistence/UI tasks. This task's new tests are pure JVM tests using contract-faithful in-memory fakes.
- A cleanup-in-progress marker is not persisted across process death; that separate durability enhancement is intentionally outside this task and should remain tracked as follow-up work.

## Review round 1 amendments

### Behavior changes

- Restore admission is now explicit: only initial `RESTORING` and retryable `RESTORE_FAILED` states may read protected storage. Restore is ignored during authentication, cleanup/revoke, authenticated use, and ordinary signed-out form errors.
- A restore job is registered before it starts, so logout/reset can cancel and join a slow protected-store read before cleanup.
- Cleanup admission is coalesced across concurrent logout/reset calls. Authentication remains blocked for the whole cleanup lifetime, and stale expiry/401 paths cannot start another cleanup or publish over a newer account.
- Failed 404/403 cache removals are contained as retryable states. The rejected content is hidden immediately, and a pending purge must succeed before cache reads or rediscovery resume.
- `refresh()` reruns membership discovery whenever catalog state is not `FRESH`, allowing degraded cached startup to reconcile additions/removals once connectivity returns.
- Successful cookbook discovery preserves a failed/degraded recipe status, message, and retry action.
- 404 completion now compares against the generation captured when removal began, rather than the mutable current generation.
- Invalid cookbook IDs are rejected before advancing the cookbook request generation.
- Detail open/close uses a separate request generation and post-suspension checks; invalidated cache reads cannot start API work, and delayed cancellation cannot publish stale `NOT_READY`/close state.
- Cached membership with no fetched list now reaches terminal recipe `ERROR` when discovery fails instead of remaining `LOADING` without work.

The public constructors, state types, and action signatures listed above are unchanged. Task 4 should still call `restore()` once at startup, `checkExpiry()` on resume, and derive resource-backed copy from statuses/errors.

### Review RED

```text
$ bin/android-gradle :app:testDebugUnitTest --tests '*SessionControllerTest' --console=plain
SessionControllerTest > refreshFromDegradedCatalogRediscoveryReconcilesMemberships FAILED
SessionControllerTest > invalidatedDetailLoadDoesNotStartApiAfterItsCacheRead FAILED
SessionControllerTest > failedForbiddenPurgeIsRetriedBeforeDiscoveryOrCachedContentReuse FAILED
SessionControllerTest > invalidSwitchDoesNotInvalidateAnInProgressValidSwitch FAILED
SessionControllerTest > restoreIsIgnoredDuringSlowLogoutRevocation FAILED
SessionControllerTest > restoreIsIgnoredDuringAuthenticationAndAfterSignedOutFormError FAILED
SessionControllerTest > successfulDiscoveryDoesNotEraseDegradedRecipeFailure FAILED
Command timed out with the intentionally blocked pre-fix cleanup regression still pending.
exit 124
```

The same red batch also included deterministic regressions for reset during a slow session read, overlapping cleanup/new-login admission, failed 404 removal, stale detail cancellation/cache reads, and unfetched-cache offline startup.

### Review focused GREEN

```text
$ bin/android-gradle :app:testDebugUnitTest --tests '*SessionControllerTest' --console=plain
BUILD SUCCESSFUL in 2s
28 actionable tasks: 7 executed, 21 up-to-date
41 tests, 0 failures, 0 errors
exit 0
```

### Review final gate

```text
$ bin/android-test
BUILD SUCCESSFUL in 2s
37 actionable tasks: 8 executed, 29 up-to-date
exit 0

JUnit XML summary: 56 tests, 0 failures, 0 errors, 0 skipped across 3 suites.
```
