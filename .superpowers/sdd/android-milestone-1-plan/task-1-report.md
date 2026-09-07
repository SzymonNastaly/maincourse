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
