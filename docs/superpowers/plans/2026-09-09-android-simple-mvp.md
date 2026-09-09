# Android Simple MVP Rewrite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current Android coordination stack with a small email-authenticated MVP providing cached cookbook and recipe browsing, recipe actions, and account settings.

**Architecture:** Keep the existing project and proven data/UI pieces, but build a new path around a small application container, an in-memory session provider, Retrofit, Room `Flow`s, and focused view models. Room is the source of truth for cookbook and recipe screens; normal `viewModelScope` cancellation replaces request generations, job registries, mutex orchestration, and reconciliation state machines.

**Tech Stack:** Kotlin 2.2.10, Jetpack Compose/Material 3, Navigation 3, Android Lifecycle ViewModel, Retrofit 3 with kotlinx.serialization, OkHttp 4.12, Room 2.8.4, Coil 3.3, coroutines 1.10.2, JUnit 4, MockWebServer.

**Spec:** `docs/superpowers/specs/2026-09-09-android-simple-mvp-design.md`

## Global Constraints

- Keep one `:app` module and package code under `com.getmaincourse.app` by feature.
- Keep `minSdk 29`, `compileSdk 37`, `targetSdk 37`, Java 17, and the pinned dependency set unless a task removes an unused dependency.
- Keep release `applicationId` `com.getmaincourse.app`, debug `.debug`, fixed release API `https://app.getmaincourse.com/`, and current debug URL validation.
- Keep light-only `MainCourseTheme`, semantic colors, Material behavior, bottom navigation, and the existing launcher artwork.
- Keep tokens only in the encrypted Keystore-backed session file and in-memory provider; never put them in Room, preferences, routes, logs, or image requests.
- Keep user/cookbook scoping on all cached data and explicit `X-Cookbook-Id` request headers.
- Do not add DI, use-case, coordinator, job-registry, request-generation, durable-outbox, or custom concurrency frameworks.
- Do not restore onboarding, provider sign-in, search, recipe editing/photo upload, design gallery, detail sweeps, or adaptive navigation in this plan.
- Follow TDD task by task and commit only files belonging to the current task; preserve unrelated working-tree changes.

---

### Task 1: Build the Small Network and Session Boundary

**Files:**
- Create: `maincourse-android/app/src/main/java/com/getmaincourse/app/data/session/SessionProvider.kt`
- Create: `maincourse-android/app/src/main/java/com/getmaincourse/app/data/network/AuthInterceptor.kt`
- Create: `maincourse-android/app/src/main/java/com/getmaincourse/app/data/network/MainCourseService.kt`
- Create: `maincourse-android/app/src/main/java/com/getmaincourse/app/data/network/ApiErrorMessage.kt`
- Create: `maincourse-android/app/src/test/java/com/getmaincourse/app/data/network/AuthInterceptorTest.kt`
- Create: `maincourse-android/app/src/test/java/com/getmaincourse/app/data/network/MainCourseServiceTest.kt`
- Keep temporarily: `MainCourseApi.kt`, `RetrofitMainCourseApi.kt`, and `RetrofitMainCourseService.kt` for the old app path.

**Interfaces:**
- Produces: `SessionProvider.session: StateFlow<SessionResponse?>`, `set(SessionResponse)`, and `clear()`.
- Produces: `SessionEvents.expired: SharedFlow<Unit>` and `notifyExpired()`.
- Produces: `MainCourseService` methods without token parameters; scoped methods take `cookbookId: Long` as an explicit header argument.
- Produces: `Throwable.userMessage(fallback: String): String`.

- [ ] **Step 1: Write failing interceptor tests**

Cover: protected requests receive `Authorization: Bearer token`; anonymous requests marked `X-MainCourse-Anonymous: true` send neither marker nor authorization; explicit cookbook headers survive; a protected `401` emits expiry; an anonymous `401` does not.

```kotlin
@Test fun protectedRequestAddsBearer() = runTest {
    val provider = SessionProvider().apply { set(session(token = "token")) }
    val client = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(provider, SessionEvents()))
        .build()

    client.newCall(Request.Builder().url(server.url("/recipes")).build()).execute().close()

    assertEquals("Bearer token", server.takeRequest().getHeader("Authorization"))
}
```

- [ ] **Step 2: Run the interceptor tests and confirm failure**

Run: `bin/android-gradle :app:testDebugUnitTest --tests '*AuthInterceptorTest'`

Expected: compilation fails because the new session/network types do not exist.

- [ ] **Step 3: Implement the session provider and interceptor**

Use these public shapes:

```kotlin
class SessionProvider {
    private val mutableSession = MutableStateFlow<SessionResponse?>(null)
    val session: StateFlow<SessionResponse?> = mutableSession.asStateFlow()
    fun set(value: SessionResponse) { mutableSession.value = value }
    fun clear() { mutableSession.value = null }
}

class SessionEvents {
    private val mutableExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val expired: SharedFlow<Unit> = mutableExpired.asSharedFlow()
    fun notifyExpired() { mutableExpired.tryEmit(Unit) }
}
```

`AuthInterceptor` removes the internal anonymous marker before sending, adds the current bearer only to protected calls, and notifies only when a request carrying that bearer receives `401`.

- [ ] **Step 4: Run interceptor tests**

Run: `bin/android-gradle :app:testDebugUnitTest --tests '*AuthInterceptorTest'`

Expected: PASS.

- [ ] **Step 5: Write failing Retrofit contract tests**

Test JSON and paths for session, registration, cookbooks, recipe list/detail, move, delete, shopping-item creation, account patch/delete, and logout. Assert auth endpoints carry the anonymous marker and recipe endpoints send the exact cookbook header.

```kotlin
@Test fun recipeListUsesExplicitCookbookHeader() = runTest {
    server.enqueue(MockResponse().setBody("[]").setHeader("Content-Type", "application/json"))
    service.recipes(cookbookId = 42)
    val request = server.takeRequest()
    assertEquals("/api/v1/recipes", request.path)
    assertEquals("42", request.getHeader("X-Cookbook-Id"))
}
```

- [ ] **Step 6: Implement `MainCourseService` and error mapping**

Define only MVP endpoints. Use `@Headers("X-MainCourse-Anonymous: true")` on `signIn` and `signUp`; rely on the interceptor for bearer headers; retain explicit `@Header("X-Cookbook-Id") cookbookId: Long` on recipe/shopping methods. Retain `AccountResponse` unwrapping in callers and `MoveRecipeRequest`. Exclude batch loading, onboarding, OAuth, recipe editing, and multipart upload.

Map `HttpException` and `IOException` to concise existing user messages. Always rethrow `CancellationException`.

- [ ] **Step 7: Run network tests and commit**

Run: `bin/android-gradle :app:testDebugUnitTest --tests '*AuthInterceptorTest' --tests '*MainCourseServiceTest'`

Expected: PASS.

```bash
git add maincourse-android/app/src/main/java/com/getmaincourse/app/data/session/SessionProvider.kt \
  maincourse-android/app/src/main/java/com/getmaincourse/app/data/network/AuthInterceptor.kt \
  maincourse-android/app/src/main/java/com/getmaincourse/app/data/network/MainCourseService.kt \
  maincourse-android/app/src/main/java/com/getmaincourse/app/data/network/ApiErrorMessage.kt \
  maincourse-android/app/src/test/java/com/getmaincourse/app/data/network/AuthInterceptorTest.kt \
  maincourse-android/app/src/test/java/com/getmaincourse/app/data/network/MainCourseServiceTest.kt
git commit -m "Add simple Android network boundary"
```

### Task 2: Make Room the Observable Source of Truth

**Files:**
- Modify: `maincourse-android/app/src/main/java/com/getmaincourse/app/data/cache/CatalogDao.kt`
- Create: `maincourse-android/app/src/main/java/com/getmaincourse/app/data/cache/CatalogJson.kt`
- Create: `maincourse-android/app/src/main/java/com/getmaincourse/app/data/CookbookRepository.kt`
- Create: `maincourse-android/app/src/main/java/com/getmaincourse/app/data/RecipeRepository.kt`
- Create: `maincourse-android/app/src/androidTest/java/com/getmaincourse/app/data/SimpleRepositoriesTest.kt`
- Modify: `maincourse-android/app/src/androidTest/java/com/getmaincourse/app/data/cache/RoomCatalogStoreTest.kt`

**Interfaces:**
- Consumes: `MainCourseService` from Task 1.
- Produces: `CookbookSelection(cookbooks: List<Cookbook>, selectedId: Long?)`.
- Produces: `CookbookRepository.observe(userId): Flow<CookbookSelection>`, `refresh(userId)`, and `select(userId, cookbookId)`.
- Produces: `RecipeRepository.observeSummaries(userId, cookbookId): Flow<List<RecipeSummary>>`, `observeDetail(...): Flow<RecipeDetail?>`, `refreshList(...)`, `refreshDetail(...)`, `move(...)`, `delete(...)`, and `addIngredients(...)`.

- [ ] **Step 1: Write failing Room Flow tests**

Use `Room.inMemoryDatabaseBuilder`. Verify observers emit inserted values; replacing recipes retains detail JSON for retained IDs and prunes removed IDs; users/cookbooks never mix; selecting a cookbook emits its ID.

```kotlin
@Test fun replacingListRetainsCachedDetail() = runTest {
    dao.replaceCookbooks(1, listOf(cookbookEntity(userId = 1, cookbookId = 10)))
    dao.replaceRecipes(1, 10, listOf(recipeEntity(1, 10, 7)))
    dao.updateDetail(1, 10, 7, detailJson(recipeId = 7))
    dao.replaceRecipes(1, 10, listOf(recipeEntity(1, 10, 7)))
    assertNotNull(dao.recipe(1, 10, 7)?.detailJson)
}
```

- [ ] **Step 2: Run the device test and confirm failure**

Run: `bin/android-gradle :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.getmaincourse.app.data.SimpleRepositoriesTest`

Expected: compilation fails because observable DAO methods and repositories do not exist.

- [ ] **Step 3: Add observable DAO queries and JSON mapping**

```kotlin
@Query("SELECT * FROM cookbooks WHERE userId = :userId ORDER BY listPosition")
fun observeCookbooks(userId: Long): Flow<List<CookbookEntity>>

@Query("SELECT cookbookId FROM selected_cookbooks WHERE userId = :userId")
fun observeSelectedCookbookId(userId: Long): Flow<Long?>

@Query("SELECT * FROM recipes WHERE userId = :userId AND cookbookId = :cookbookId ORDER BY listPosition")
fun observeRecipes(userId: Long, cookbookId: Long): Flow<List<RecipeEntity>>

@Query("SELECT * FROM recipes WHERE userId = :userId AND cookbookId = :cookbookId AND recipeId = :recipeId")
fun observeRecipe(userId: Long, cookbookId: Long, recipeId: Long): Flow<RecipeEntity?>
```

Move entity/domain JSON conversion into focused internal functions in `CatalogJson.kt`. Malformed JSON maps to absence and is replaced by the next refresh.

- [ ] **Step 4: Implement repositories**

`CookbookRepository.refresh(userId)` calls `service.cookbooks()` and transactionally replaces rows. Keep a valid prior selection, otherwise select the first response, or no selection for an empty response.

`RecipeRepository.refreshList` calls `service.recipes(cookbookId)` and delegates to `replaceRecipes`. `refreshDetail` fetches one detail and updates only that row. Repositories own no coroutine scope, loading state, retry loop, or session state.

- [ ] **Step 5: Run repository device tests and commit**

Run: `bin/android-gradle :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.getmaincourse.app.data.SimpleRepositoriesTest`

Expected: PASS.

```bash
git add maincourse-android/app/src/main/java/com/getmaincourse/app/data/cache/CatalogDao.kt \
  maincourse-android/app/src/main/java/com/getmaincourse/app/data/cache/CatalogJson.kt \
  maincourse-android/app/src/main/java/com/getmaincourse/app/data/CookbookRepository.kt \
  maincourse-android/app/src/main/java/com/getmaincourse/app/data/RecipeRepository.kt \
  maincourse-android/app/src/androidTest/java/com/getmaincourse/app/data/SimpleRepositoriesTest.kt \
  maincourse-android/app/src/androidTest/java/com/getmaincourse/app/data/cache/RoomCatalogStoreTest.kt
git commit -m "Make Room the Android catalog source of truth"
```

### Task 3: Replace Session and Email Authentication Orchestration

**Files:**
- Create: `maincourse-android/app/src/main/java/com/getmaincourse/app/features/session/SessionViewModel.kt`
- Create: `maincourse-android/app/src/main/java/com/getmaincourse/app/features/session/SessionUiState.kt`
- Modify: `maincourse-android/app/src/main/java/com/getmaincourse/app/features/auth/AuthScreen.kt`
- Modify: `maincourse-android/app/src/main/java/com/getmaincourse/app/MainCourseApplication.kt`
- Create: `maincourse-android/app/src/main/java/com/getmaincourse/app/ViewModelFactory.kt`
- Create: `maincourse-android/app/src/test/java/com/getmaincourse/app/features/session/SessionViewModelTest.kt`
- Keep temporarily: `SessionState.kt` and `MainCourseViewModel.kt` so the old UI compiles until Task 4 switches entry points.

**Interfaces:**
- Consumes: Task 1 network/session types, `SessionStore`, `MainCourseDatabase`, and `SessionImages`.
- Produces: `SessionUiState.Restoring`, `SignedOut(authError)`, `SignedIn(session)`, and `RestoreError(message)`.
- Produces: `restore()`, `signIn(email, password)`, `signUp(name, email, password, confirmation)`, `signOut()`, and `deleteAccount()` returning their launched `Job` for tests.
- Produces: `simpleViewModelFactory { ... }`.

- [ ] **Step 1: Write failing session tests**

Test valid restore, missing/expired/wrong-origin restore, email auth, bad login, authenticated `401`, best-effort logout despite network failure, and confirmed account-deletion cleanup.

```kotlin
@Test fun validStoredSessionRestores() = runTest {
    store.value = StoredSession(BASE_URL, session(expiresAt = futureInstant))
    val viewModel = buildViewModel()
    viewModel.restore().join()
    assertEquals(SessionUiState.SignedIn(store.value!!.response), viewModel.state.value)
    assertEquals(store.value!!.response, provider.session.value)
}
```

- [ ] **Step 2: Run session tests and confirm failure**

Run: `bin/android-gradle :app:testDebugUnitTest --tests '*SessionViewModelTest'`

Expected: compilation fails because the new state/view model do not exist.

- [ ] **Step 3: Implement the minimal session view model**

Use one `MutableStateFlow<SessionUiState>`, one optional foreground action `Job`, and `viewModelScope`. `restore()` reads once; auth methods validate, call the service, persist `StoredSession`, set the provider, and publish signed-in state. Collect `SessionEvents.expired` once in `init` and run local cleanup.

After restore or authentication succeeds, call `images.prepare(session.user.id)` and expose that loader to the protected shell. After hiding protected UI, cleanup directly clears provider, encrypted store, `database.clearAllTables()`, and `images.clear()`. If local cleanup fails, publish `RestoreError` and do not admit another account. Do not add job sets, mutexes, generations, admissions, or pending operations.

- [ ] **Step 4: Reduce `AuthScreen` to email-only state/events**

```kotlin
@Composable
fun AuthScreen(
    busy: Boolean,
    error: String?,
    onSignIn: (email: String, password: String) -> Unit,
    onSignUp: (name: String?, email: String, password: String, confirmation: String) -> Unit,
)
```

Use local field state and `AuthMode { SIGN_IN, SIGN_UP }`. Remove Google/Apple and onboarding parameters. Keep passwords only in composable memory.

- [ ] **Step 5: Replace the application container**

Add one database, encrypted store, `SessionProvider`, `SessionEvents`, authenticated OkHttp client, Retrofit `MainCourseService`, cookbook repository, recipe repository, and `SessionImages` to the container. Keep the old factory and dependencies temporarily so the old activity path compiles. Task 4 replaces the old factory after switching the UI entry point.

- [ ] **Step 6: Run tests and commit**

Run: `bin/android-gradle :app:testDebugUnitTest --tests '*SessionViewModelTest' --tests '*AuthInterceptorTest' --tests '*MainCourseServiceTest'`

Expected: PASS.

```bash
git add maincourse-android/app/src/main/java/com/getmaincourse/app/MainCourseApplication.kt \
  maincourse-android/app/src/main/java/com/getmaincourse/app/ViewModelFactory.kt \
  maincourse-android/app/src/main/java/com/getmaincourse/app/features/auth/AuthScreen.kt \
  maincourse-android/app/src/main/java/com/getmaincourse/app/features/session/SessionUiState.kt \
  maincourse-android/app/src/main/java/com/getmaincourse/app/features/session/SessionViewModel.kt \
  maincourse-android/app/src/test/java/com/getmaincourse/app/features/session/SessionViewModelTest.kt
git commit -m "Replace Android session orchestration"
```

### Task 4: Wire Cookbook and Read-Only Recipe Navigation

**Files:**
- Create: `maincourse-android/app/src/main/java/com/getmaincourse/app/features/recipes/RecipesViewModel.kt`
- Create: `maincourse-android/app/src/main/java/com/getmaincourse/app/features/recipes/RecipeDetailViewModel.kt`
- Modify: `maincourse-android/app/src/main/java/com/getmaincourse/app/features/recipes/RecipesScreen.kt`
- Modify: `maincourse-android/app/src/main/java/com/getmaincourse/app/features/recipes/RecipeDetailScreen.kt`
- Replace: `maincourse-android/app/src/main/java/com/getmaincourse/app/MainCourseApp.kt`
- Modify: `maincourse-android/app/src/main/java/com/getmaincourse/app/MainActivity.kt`
- Modify: `maincourse-android/app/src/main/java/com/getmaincourse/app/MainCourseApplication.kt`
- Delete: `maincourse-android/app/src/main/java/com/getmaincourse/app/features/session/MainCourseViewModel.kt`
- Replace: `maincourse-android/app/src/androidTest/java/com/getmaincourse/app/MainCourseAppTest.kt`
- Create: `maincourse-android/app/src/test/java/com/getmaincourse/app/features/recipes/RecipesViewModelTest.kt`

**Interfaces:**
- Consumes: Task 2 repositories and Task 3 session state.
- Produces: `RecipesUiState(cookbooks, selectedCookbookId, recipes, initialLoading, refreshing, error)`.
- Produces: `RecipeDetailUiState(recipe, loading, refreshing, error)`.
- Produces typed keys `RecipesRoute`, `RecipeDetailRoute(recipeId, cookbookId)`, `ShoppingRoute`, `SearchRoute`, and `SettingsRoute`.

- [ ] **Step 1: Write failing view-model tests**

Test cached emission before refresh, success, cached rows surviving refresh failure, cookbook selection changing scope, empty cookbooks, cached detail emission, and detail fetch only when absent or explicitly refreshed.

```kotlin
@Test fun refreshFailureKeepsCachedRecipesVisible() = runTest {
    recipeRepository.emitSummaries(10, listOf(recipeSummary(7)))
    recipeRepository.refreshFailure = IOException("offline")
    val viewModel = buildRecipesViewModel()
    viewModel.refresh().join()
    assertEquals(listOf(7L), viewModel.state.value.recipes.map { it.id })
    assertEquals("You're offline", viewModel.state.value.error)
}
```

- [ ] **Step 2: Run tests and confirm failure**

Run: `bin/android-gradle :app:testDebugUnitTest --tests '*RecipesViewModelTest'`

Expected: compilation fails because focused view models do not exist.

- [ ] **Step 3: Implement focused view models**

Use `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)` over repository flows plus small local loading/error flows. A refresh method launches one replaceable `Job`; cookbook selection only calls `CookbookRepository.select`. Do not duplicate cookbook/recipe lists in mutable state.

- [ ] **Step 4: Reduce recipe screens to read-only inputs**

Remove editor/photo/search state. Keep cookbook picker, recipe cards, refresh, cached-content error banner, list empty state, detail ingredients/instructions/notes/source, Back, Move, Delete, and Add ingredients actions. Action callbacks are disabled parameters until Task 5.

- [ ] **Step 5: Replace app/navigation shell**

`MainCourseApp` observes session state and renders restoration, auth, restore error, or protected shell. The shell owns one Navigation 3 back stack and four bottom destinations. Shopping and Search use `PreviewScreen`; Settings is wired in Task 6. Use bottom navigation at every width.

`MainActivity` keeps splash/local-network permission setup but removes Google chooser and Apple callback ownership. It creates `SessionViewModel` and calls `restore()` once. Remove the old container dependencies/factory and `MainCourseViewModel` now that no entry point references them; leave `SessionController` and its supporting files for Task 7's consolidated deletion.

- [ ] **Step 6: Write and run navigation smoke tests**

Cover signed-out auth, signed-in Recipes, recipe-card navigation to detail and Back, Shopping placeholder, Search placeholder, and Settings tab selection.

Run: `bin/android-gradle :app:testDebugUnitTest --tests '*RecipesViewModelTest' && bin/android-gradle :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.getmaincourse.app.MainCourseAppTest`

Expected: PASS.

- [ ] **Step 7: Commit browsing slice**

```bash
git add maincourse-android/app/src/main/java/com/getmaincourse/app/MainActivity.kt \
  maincourse-android/app/src/main/java/com/getmaincourse/app/MainCourseApp.kt \
  maincourse-android/app/src/main/java/com/getmaincourse/app/MainCourseApplication.kt \
  maincourse-android/app/src/main/java/com/getmaincourse/app/features/recipes/RecipesViewModel.kt \
  maincourse-android/app/src/main/java/com/getmaincourse/app/features/recipes/RecipeDetailViewModel.kt \
  maincourse-android/app/src/main/java/com/getmaincourse/app/features/recipes/RecipesScreen.kt \
  maincourse-android/app/src/main/java/com/getmaincourse/app/features/recipes/RecipeDetailScreen.kt \
  maincourse-android/app/src/test/java/com/getmaincourse/app/features/recipes/RecipesViewModelTest.kt \
  maincourse-android/app/src/androidTest/java/com/getmaincourse/app/MainCourseAppTest.kt
git rm maincourse-android/app/src/main/java/com/getmaincourse/app/features/session/MainCourseViewModel.kt
git commit -m "Add simple cached Android recipe browsing"
```

### Task 5: Add the Three MVP Recipe Actions

**Files:**
- Modify: `maincourse-android/app/src/main/java/com/getmaincourse/app/data/RecipeRepository.kt`
- Modify: `maincourse-android/app/src/main/java/com/getmaincourse/app/features/recipes/RecipeDetailViewModel.kt`
- Modify: `maincourse-android/app/src/main/java/com/getmaincourse/app/features/recipes/RecipeDetailScreen.kt`
- Modify: `maincourse-android/app/src/main/java/com/getmaincourse/app/features/recipes/IngredientReview.kt`
- Modify: `maincourse-android/app/src/main/java/com/getmaincourse/app/features/recipes/IngredientReviewScreen.kt`
- Create: `maincourse-android/app/src/test/java/com/getmaincourse/app/features/recipes/RecipeActionsViewModelTest.kt`
- Modify: `maincourse-android/app/src/androidTest/java/com/getmaincourse/app/features/recipes/RecipeWorkflowScreenTest.kt`

**Interfaces:**
- Produces: `RecipeActionUiState.Idle`, `Running(action)`, `Succeeded(message)`, and `Failed(message)`.
- Produces: `moveTo(targetCookbookId)`, `delete()`, and `addIngredients(rows)`.
- Uses existing shopping request wire values, separated from editor dependencies.

- [ ] **Step 1: Write failing action tests**

Test duplicate taps ignored while running; successful move removes source and refreshes target; delete removes row; shopping submission confirms; failures leave Room unchanged; cancellation shows no error.

```kotlin
@Test fun deleteSuccessRemovesCachedRecipe() = runTest {
    val viewModel = buildDetailViewModel(recipeId = 7)
    viewModel.delete().join()
    assertEquals(listOf(Triple(USER_ID, COOKBOOK_ID, 7L)), repository.deletedRows)
    assertEquals(RecipeActionUiState.Succeeded("Recipe deleted"), viewModel.action.value)
}
```

- [ ] **Step 2: Run tests and confirm failure**

Run: `bin/android-gradle :app:testDebugUnitTest --tests '*RecipeActionsViewModelTest'`

Expected: compilation fails because action state/methods do not exist.

- [ ] **Step 3: Implement direct repository mutations**

- `move`: call `service.moveRecipe(sourceCookbookId, recipeId, MoveRecipeRequest(targetId))`, remove source, refresh target.
- `delete`: call `service.deleteRecipe(cookbookId, recipeId)`, remove the row.
- `addIngredients`: call `service.addRecipeIngredients(cookbookId, ShoppingItemsRequest(rows))` once.

Do not retry automatically or persist pending operations.

- [ ] **Step 4: Implement action state and UI**

Hold one foreground action job and one action-state flow. Disable mutations while running. Show move in a cookbook dialog, delete behind confirmation, and ingredient review with serving scaling and stable in-memory row IDs. Success returns appropriately; failure leaves UI intact and Retry requires a new tap.

- [ ] **Step 5: Run unit and Compose tests**

Run: `bin/android-gradle :app:testDebugUnitTest --tests '*RecipeActionsViewModelTest' --tests '*IngredientReviewTest' --tests '*IngredientFormatterTest' && bin/android-gradle :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.getmaincourse.app.features.recipes.RecipeWorkflowScreenTest`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add maincourse-android/app/src/main/java/com/getmaincourse/app/data/RecipeRepository.kt \
  maincourse-android/app/src/main/java/com/getmaincourse/app/features/recipes/RecipeDetailViewModel.kt \
  maincourse-android/app/src/main/java/com/getmaincourse/app/features/recipes/RecipeDetailScreen.kt \
  maincourse-android/app/src/main/java/com/getmaincourse/app/features/recipes/IngredientReview.kt \
  maincourse-android/app/src/main/java/com/getmaincourse/app/features/recipes/IngredientReviewScreen.kt \
  maincourse-android/app/src/test/java/com/getmaincourse/app/features/recipes/RecipeActionsViewModelTest.kt \
  maincourse-android/app/src/androidTest/java/com/getmaincourse/app/features/recipes/RecipeWorkflowScreenTest.kt
git commit -m "Add simple Android recipe actions"
```

### Task 6: Wire Account Settings and Local Cleanup

**Files:**
- Create: `maincourse-android/app/src/main/java/com/getmaincourse/app/features/settings/SettingsViewModel.kt`
- Modify: `maincourse-android/app/src/main/java/com/getmaincourse/app/features/settings/SettingsScreen.kt`
- Modify: `maincourse-android/app/src/main/java/com/getmaincourse/app/features/settings/AccountScreens.kt`
- Modify: `maincourse-android/app/src/main/java/com/getmaincourse/app/features/session/SessionViewModel.kt`
- Create: `maincourse-android/app/src/test/java/com/getmaincourse/app/features/settings/SettingsViewModelTest.kt`
- Modify: `maincourse-android/app/src/androidTest/java/com/getmaincourse/app/MainCourseAppTest.kt`

**Interfaces:**
- Consumes: `MainCourseService`, `SessionStore`, `SessionProvider`, plus `SessionViewModel.deleteAccount()` and `signOut()` callbacks.
- Produces: `SettingsUiState(user, saving, deleting, error)`; email remains read-only.

- [ ] **Step 1: Write failing settings tests**

Verify profile save sends name/reminder values, persists/publishes the returned user, HTTP failure preserves the acknowledged user, and local persistence failure does not publish an unpersisted user. Account deletion and logout remain covered by `SessionViewModelTest`.

```kotlin
@Test fun profileSavePublishesServerUserAfterPersistence() = runTest {
    service.updatedUser = user(name = "New name")
    viewModel.saveProfile("New name", remindersEnabled = false).join()
    assertEquals("New name", provider.session.value?.user?.name)
    assertEquals("New name", store.value?.response?.user?.name)
}
```

- [ ] **Step 2: Run tests and confirm failure**

Run: `bin/android-gradle :app:testDebugUnitTest --tests '*SettingsViewModelTest'`

Expected: compilation fails because `SettingsViewModel` does not exist.

- [ ] **Step 3: Implement account state without a second session controller**

Derive the displayed user from `SessionProvider.session`. `saveProfile` calls `MainCourseService.updateAccount`, writes the returned user into the stored session, and only then publishes it through `SessionProvider`. Delegate account deletion and logout to `SessionViewModel` callbacks supplied by the host. Retain only `saving`, `deleting`, and `error` locally.

After `PATCH /account`, write the returned user into the stored session before publishing it through `SessionProvider`. On storage failure, report failure and retain the prior published session; never repeat the PATCH automatically.

- [ ] **Step 4: Simplify settings screens**

Keep account name, read-only email, recipe-reminder switch, Save, Delete account confirmation, and Sign out. Remove design-gallery and provider credential-state UI.

- [ ] **Step 5: Run settings/navigation tests**

Run: `bin/android-gradle :app:testDebugUnitTest --tests '*SettingsViewModelTest' --tests '*SessionViewModelTest' && bin/android-gradle :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.getmaincourse.app.MainCourseAppTest`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add maincourse-android/app/src/main/java/com/getmaincourse/app/features/settings/SettingsViewModel.kt \
  maincourse-android/app/src/main/java/com/getmaincourse/app/features/settings/SettingsScreen.kt \
  maincourse-android/app/src/main/java/com/getmaincourse/app/features/settings/AccountScreens.kt \
  maincourse-android/app/src/main/java/com/getmaincourse/app/features/session/SessionViewModel.kt \
  maincourse-android/app/src/test/java/com/getmaincourse/app/features/settings/SettingsViewModelTest.kt \
  maincourse-android/app/src/androidTest/java/com/getmaincourse/app/MainCourseAppTest.kt
git commit -m "Add simple Android account settings"
```

### Task 7: Delete Superseded Architecture and Deferred Features

**Files:**
- Delete: `features/session/SessionController.kt`, `SessionState.kt`, `CatalogRepository.kt`, `RecipeHydrator.kt`, and `RecipeReadMutationBarrier.kt`.
- Delete: `features/recipes/RecipeActionController.kt`, `RecipeActionState.kt`, `RecipeEditDraft.kt`, `RecipeEditScreen.kt`, `RecipeImagePreparationState.kt`, and `RecipeUiSavedState.kt`.
- Delete: `features/onboarding/OnboardingController.kt`, `OnboardingScreen.kt`, and `OnboardingState.kt`.
- Delete: `features/search/RecipeSearchCoordinator` declarations in `RecipeSearchState.kt`, plus `RecipeSearchScreen.kt`, `RecipeSearchEngine.kt`, and `RecipeSearchDocument.kt`.
- Delete: `features/designsystem/DesignSystemScreen.kt` and obsolete `features/settings/AccountState.kt`.
- Delete: every file under `features/auth/` except `AuthScreen.kt`: `GoogleCredentialProvider.kt`, `GoogleAuthenticationLauncher.kt`, `GoogleNonce.kt`, `GoogleCredentialSessionCleaner.kt`, `AndroidGoogleCredentialProvider.kt`, `GoogleSignInConfiguration.kt`, `GoogleSignInButton.kt`, `AppleAuthenticationCallback.kt`, `ApplePkce.kt`, and `AppleSignInButton.kt`.
- Delete: `data/onboarding/OnboardingStore.kt`, `AtomicOnboardingStore.kt`, `data/images/PreparedRecipeImage.kt`, `RecipeImageOwner.kt`, and `RecipeImagePreparer.kt`.
- Delete: `data/model/OnboardingModels.kt`, `AppleAuthenticationModels.kt`, `GoogleSignInRequest.kt`, and `RecipeBatchResponse.kt`.
- Delete: `data/network/MainCourseApi.kt`, `RetrofitMainCourseApi.kt`, `RetrofitMainCourseService.kt`, `data/cache/CatalogStore.kt`, and `RoomCatalogStore.kt`.
- Modify: `maincourse-android/app/src/main/java/com/getmaincourse/app/data/model/RecipeMutationModels.kt` to retain only `MoveRecipeRequest`.
- Modify: `maincourse-android/app/build.gradle.kts`, `gradle/libs.versions.toml`, main/debug manifests, strings, and orphaned resources.
- Delete: tests dedicated to removed production files.
- Create: `maincourse-android/app/src/test/java/com/getmaincourse/app/SimpleArchitectureTest.kt`.

**Interfaces:**
- Consumes: Tasks 1–6.
- Produces: one compiling MVP with no old orchestration or deferred-feature references.

- [ ] **Step 1: Add a failing forbidden-symbol architecture test**

Scan production Kotlin files and reject `SessionController`, `RecipeActionController`, `RecipeHydrator`, `RecipeSearchCoordinator`, `OnboardingController`, `GoogleCredentialProvider`, `ApplePkce`, `RecipeImageOwner`, `AtomicLong`, `authenticatedJobs`, `cookbookGeneration`, and `detailGeneration`.

```kotlin
@Test fun removedCoordinationTypesAreAbsent() {
    val source = productionKotlinFiles().joinToString("\n") { it.readText() }
    forbiddenSymbols.forEach { symbol ->
        assertFalse("Found removed symbol: $symbol", source.contains(symbol))
    }
}
```

- [ ] **Step 2: Run architecture test and confirm failure**

Run: `bin/android-gradle :app:testDebugUnitTest --tests '*SimpleArchitectureTest'`

Expected: FAIL listing old production symbols.

- [ ] **Step 3: Delete old production and tests**

Delete the files listed above plus old session/view-model, onboarding, provider, Apple intent, search, editor/action-controller, image-preparer, and activity-retention tests. Preserve theme, image URL, encrypted store, ingredient formatting/review, local-network, new repository/network/session/settings/navigation tests.

- [ ] **Step 4: Remove obsolete platform configuration**

Remove Credential Manager, Google ID, and direct Fragment dependencies/aliases. Remove Apple App Link filters. Remove the debug recipe-image `FileProvider` and test-path resource if unused. Remove strings/drawables used only by deferred features. Keep debug local-network permission and release security settings.

- [ ] **Step 5: Build and fix only dangling references**

Run: `bin/android-build && bin/android-gradle :app:testDebugUnitTest --tests '*SimpleArchitectureTest'`

Expected: PASS. Resolve dangling references by deleting deferred branches or using new MVP types; never add adapter layers around deleted controllers.

- [ ] **Step 6: Commit deletion**

```bash
git add -A maincourse-android
git commit -m "Remove overbuilt Android architecture"
```

### Task 8: Validate the MVP and Replace Android Documentation

**Files:**
- Replace: `docs/android.md`
- Modify: `maincourse-android/AGENTS.md`
- Modify: `docs/superpowers/plans/2026-09-07-native-android.md` only to mark the old roadmap superseded.
- Modify: `.github/workflows/android.yml` only if stale test arguments remain.

**Interfaces:**
- Produces: concise setup/architecture documentation and final verification evidence.

- [ ] **Step 1: Run complete local automated gate**

```bash
bin/android-build
bin/android-test
bin/android-gradle :app:assembleRelease
```

Expected: debug build, JVM tests, lint, and release compilation PASS.

- [ ] **Step 2: Run device tests**

With the established emulator running:

```bash
bin/android-test --device
```

Expected: retained Room, encrypted-session, recipe workflow, and navigation tests PASS; removed feature tests are absent.

- [ ] **Step 3: Exercise real local MVP**

Start Rails with `bin/rails server -b 127.0.0.1 -p 3000`, install using `bin/android-gradle :app:installDebug`, and use real `MainActivity`. Verify email sign-up/sign-in, cookbook switching, recipe list/detail, offline reopening of a previously opened detail, move, delete, add-to-shopping, profile save, failure messaging, account deletion, and logout. Verify Shopping/Search are labeled placeholders.

- [ ] **Step 4: Rewrite Android documentation**

Keep `docs/android.md` limited to MVP features/exclusions, dependency table, small container/repository/view-model structure, session security, Room cache behavior, API configuration, local setup, and commands. Remove old controller, generation, job-group, hydration, provider, onboarding, editor, and cleanup-protocol narratives.

Update `maincourse-android/AGENTS.md` to match. Add a notice near the old roadmap's top that it is superseded by `docs/superpowers/specs/2026-09-09-android-simple-mvp-design.md`; retain it only as historical context.

- [ ] **Step 5: Check documentation and final diff**

```bash
git diff --check
git grep -n 'SessionController\|RecipeActionController\|OnboardingController\|RecipeHydrator' -- maincourse-android docs/android.md maincourse-android/AGENTS.md
git status --short
```

Expected: diff check passes; grep finds no live source/guidance references; status contains intended work plus pre-existing unrelated changes only.

- [ ] **Step 6: Commit documentation**

```bash
git add docs/android.md maincourse-android/AGENTS.md docs/superpowers/plans/2026-09-07-native-android.md
git commit -m "Document the simple Android MVP"
```

If `.github/workflows/android.yml` required changes, include it in the commit.
