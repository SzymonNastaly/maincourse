# Task 1 report — Rails HTTP contract and model boundary

## Status

DONE

Implemented the Task 1 Android model and OkHttp boundary only. No storage,
controller, repository, wiring, or UI work was added. Existing unrelated skill
changes were left untouched.

## Rails contract reviewed

- `RegistrationsController`, `SessionsController`, and `BaseController` establish
  the auth request fields and the `token`/`expires_at`/`user` response.
- `CookbooksController#index` returns the full accessible cookbook array with
  `recipe_count` and members, and deliberately ignores `X-Cookbook-Id`.
- `RecipesController#index/show` establish summary/detail fields and cookbook
  scoping. Recipe model/schema nullability was retained for optional times,
  servings, images, notes, source URL, error message, and structured ingredient
  values.
- `Recipe#cover_image_urls` returns `thumb`, `card`, and `hero` as relative Rails
  paths. DTOs keep these as unmodified nullable strings; URL resolution remains
  later UI/wiring work.
- Rails currently returns HTTP 200 with JSON for logout; the client accepts any
  successful 2xx response, including the brief's required empty 204 case.

## TDD evidence

### RED

Command:

```text
bin/android-gradle testDebugUnitTest --tests '*OkHttpMainCourseApiTest' --console=plain
```

Observed output before production classes existed:

```text
> Task :app:compileDebugUnitTestKotlin FAILED
Unresolved reference 'model'.
Unresolved reference 'MainCourseApi'.
Unresolved reference 'OkHttpMainCourseApi'.
Unresolved reference 'ApiFailure'.
BUILD FAILED in 10s
```

This was the expected missing-implementation failure. The first implementation
run then exposed a test scheduling defect in the cancellation case (the child
coroutine had not started before blocking on `takeRequest`); starting that test
coroutine undispatched made it exercise the real in-flight call.

### GREEN

Focused command after implementation/test correction:

```text
bin/android-gradle testDebugUnitTest --tests '*OkHttpMainCourseApiTest' --console=plain
```

Observed output:

```text
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 1s
26 actionable tasks: 2 executed, 24 up-to-date
```

## Final verification

Command (run once after focused red/green and self-review):

```text
bin/android-test
```

Observed output:

```text
> Task :app:testDebugUnitTest
> Task :app:lintDebug
BUILD SUCCESSFUL in 5s
35 actionable tasks: 13 executed, 22 up-to-date
```

The JVM result XML reports 14 tests, 0 failures, 0 errors: 12 HTTP boundary
tests and 2 existing theme tests. `git diff --check` also passed.

Resolved dependency checks confirmed:

- `org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0`
- `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2`
- `org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2`
- `com.squareup.okhttp3:okhttp:4.12.0`
- `com.squareup.okhttp3:mockwebserver:4.12.0`

## Changed files

- `maincourse-android/gradle/libs.versions.toml`
- `maincourse-android/app/build.gradle.kts`
- `maincourse-android/app/src/main/java/com/getmaincourse/app/data/model/ApiModels.kt`
- `maincourse-android/app/src/main/java/com/getmaincourse/app/data/network/MainCourseApi.kt`
- `maincourse-android/app/src/main/java/com/getmaincourse/app/data/network/OkHttpMainCourseApi.kt`
- `maincourse-android/app/src/main/java/com/getmaincourse/app/data/network/ApiFailure.kt`
- `maincourse-android/app/src/test/java/com/getmaincourse/app/data/network/OkHttpMainCourseApiTest.kt`
- `.superpowers/sdd/android-milestone-1-plan/task-1-report.md`

## Downstream signatures and wire choices

```kotlin
interface MainCourseApi {
    suspend fun signIn(request: SignInRequest): SessionResponse
    suspend fun signUp(request: SignUpRequest): SessionResponse
    suspend fun signOut(token: String)
    suspend fun cookbooks(token: String): List<Cookbook>
    suspend fun recipes(token: String, cookbookId: Long): List<RecipeSummary>
    suspend fun recipe(token: String, cookbookId: Long, recipeId: Long): RecipeDetail
}

class ApiFailure(val status: Int?, message: String) : Exception(message)

class OkHttpMainCourseApi(
    baseUrl: HttpUrl,
    client: OkHttpClient = OkHttpClient(),
    json: Json = Json { ignoreUnknownKeys = true },
) : MainCourseApi
```

- All IDs are `Long`; Rails timestamps are retained as wire-format `String`
  values for later persistence/mapping.
- `SessionResponse` contains only the Rails response. Task 2 should persist the
  API base URL alongside it rather than adding a field to this DTO.
- `Cookbook` includes `List<CookbookMember>`.
- `RecipeSummary` and `RecipeDetail` have independent `updatedAt` properties.
  Detail also includes `List<StructuredIngredient>` and `List<RecipeTag>` from
  the existing full Rails response. Rails decimal ingredient amounts remain
  nullable wire strings.
- `SignUpRequest.name` is nullable because the Rails user column and endpoint
  permit omission. `deviceName` is required by the Android request constructors.
- Task 4 can pass `BuildConfig.API_BASE_URL.toHttpUrl()` to the API constructor.

## Behavior covered

- Sign-in/sign-up JSON payloads, snake-case mappings, 201 responses, and both
  `error` and `errors` bodies.
- Per-request bearer and cookbook headers, discovery/header isolation, and no
  credential leakage into later authentication calls.
- Complete cookbook/recipe arrays, full recipe detail, nullable fields, and
  relative cover image paths.
- Empty 204 logout, malformed JSON, transport failures, redirects, cancellation,
  and disabled implicit POST retry.
- The client uses a 30-second call timeout, disables retries and redirects, and
  installs no logging or global bearer interceptor.

## Concerns

None. Live Rails integration remains part of the milestone-level verification,
not Task 1; this boundary is covered with MockWebServer fixtures matching the
current controllers.

## Review round 1 — main-safe response consumption

### Finding and root cause

The original callback resumed with an open OkHttp `Response`. Consequently,
`ResponseBody.string()`, successful JSON decoding, and API-error decoding ran
after resumption on the caller dispatcher. A `viewModelScope` caller could
therefore block Main while a body streamed. The cancellable continuation also
stopped owning the active call as soon as response headers were handed off, so
cancelling while `string()` was blocked did not reliably invoke `Call.cancel()`.

### Fix

- The OkHttp callback now consumes the complete body into a private
  `BufferedResponse(status, body)` while the cancellable continuation still owns
  the call, and closes the `Response` with `use` on success or read failure.
- Cancellation invokes `Call.cancel()` throughout streaming body consumption;
  an already-cancelled callback closes a received response immediately.
- Successful JSON decoding and HTTP error-body parsing run on
  `Dispatchers.Default`, never the caller dispatcher.
- `ApiFailure` and public `MainCourseApi` signatures remain unchanged.
- The retry regression now uses two DNS routes: the first cannot connect and
  the second reaches MockWebServer. With retries disabled the POST fails before
  reaching the server, rather than relying on ambiguous disconnect behavior.

### Review RED

Command:

```text
bin/android-gradle testDebugUnitTest --tests '*OkHttpMainCourseApiTest' --console=plain
```

Observed against the prior response-handoff implementation:

```text
OkHttpMainCourseApiTest > streamingResponseDoesNotBlockTheCallerDispatcher FAILED
OkHttpMainCourseApiTest > cancellingStreamingResponseCancelsCallAndPreservesCancellation FAILED
13 tests completed, 2 failed
BUILD FAILED
```

The first test showed the dedicated caller thread unavailable during body
consumption. The second observed no OkHttp `EventListener.canceled` event after
coroutine cancellation. The throttled-body fixture was then adjusted to keep
bytes actively streaming after `responseBodyStart`, rather than merely delaying
the first byte.

### Review GREEN

Focused command:

```text
bin/android-gradle testDebugUnitTest --tests '*OkHttpMainCourseApiTest' --console=plain
```

Observed output:

```text
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 3s
26 actionable tasks: 5 executed, 21 up-to-date
```

Final unit/lint command, run once after the focused cycle and self-review:

```text
bin/android-test
```

Observed output:

```text
> Task :app:testDebugUnitTest
> Task :app:lintDebug
BUILD SUCCESSFUL in 2s
35 actionable tasks: 9 executed, 26 up-to-date
```

Final JVM result XML reports 15 tests, 0 failures, 0 errors: 13 HTTP boundary
tests and 2 existing theme tests. No new concerns remain.
