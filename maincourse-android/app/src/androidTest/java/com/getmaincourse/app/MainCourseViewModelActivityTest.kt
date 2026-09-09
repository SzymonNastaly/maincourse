package com.getmaincourse.app

import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.getmaincourse.app.data.cache.CachedRecipes
import com.getmaincourse.app.data.cache.CatalogStore
import com.getmaincourse.app.data.cache.RecipeScope
import com.getmaincourse.app.data.model.AccountUpdateRequest
import com.getmaincourse.app.data.model.AppleAuthenticationExchangeRequest
import com.getmaincourse.app.data.model.AppleAuthenticationStartRequest
import com.getmaincourse.app.data.model.AppleAuthenticationStartResponse
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.GoogleSignInRequest
import com.getmaincourse.app.data.model.OnboardingRequest
import com.getmaincourse.app.data.model.OnboardingResponse
import com.getmaincourse.app.data.model.RecipeBatchResponse
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeUpdateRequest
import com.getmaincourse.app.data.model.RecipeSummary
import com.getmaincourse.app.data.model.SessionResponse
import com.getmaincourse.app.data.model.ShoppingItem
import com.getmaincourse.app.data.model.ShoppingItemsRequest
import com.getmaincourse.app.data.model.SignInRequest
import com.getmaincourse.app.data.model.SignUpRequest
import com.getmaincourse.app.data.model.User
import com.getmaincourse.app.data.network.MainCourseApi
import com.getmaincourse.app.data.onboarding.OnboardingRecord
import com.getmaincourse.app.data.onboarding.OnboardingStep
import com.getmaincourse.app.data.onboarding.OnboardingStore
import java.io.File
import com.getmaincourse.app.data.session.SessionStore
import com.getmaincourse.app.data.session.StoredSession
import com.getmaincourse.app.features.session.CatalogRepository
import com.getmaincourse.app.features.session.MainCourseViewModel
import com.getmaincourse.app.features.session.SessionPhase
import com.getmaincourse.app.features.auth.GoogleAuthenticationLauncher
import com.getmaincourse.app.features.auth.GoogleCredentialProvider
import com.getmaincourse.app.features.auth.GoogleSignInCancelledException
import com.getmaincourse.app.features.auth.GoogleSignInException
import com.getmaincourse.app.features.search.RecipeSearchDocument
import com.getmaincourse.app.features.recipes.RecipeEditScreen
import com.getmaincourse.app.features.recipes.RecipeImagePreparationState
import com.getmaincourse.app.features.settings.AccountOperation
import com.getmaincourse.app.ui.theme.MainCourseTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainCourseViewModelActivityTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainCourseTestActivity>()

    private lateinit var api: FakeApi
    private lateinit var sessionStore: FakeSessionStore
    private lateinit var viewModel: MainCourseViewModel
    private lateinit var factory: ViewModelProvider.Factory
    private lateinit var catalogStore: FakeCatalogStore

    @Before
    fun setUp() {
        api = FakeApi()
        sessionStore = FakeSessionStore(StoredSession(BASE_URL, SESSION))
        catalogStore = FakeCatalogStore()
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MainCourseViewModel(
                api = api,
                sessionStore = sessionStore,
                catalogRepository = CatalogRepository(api, catalogStore),
                onboardingStore = FakeOnboardingStore(),
                baseUrl = BASE_URL,
                clock = CLOCK,
                imageCleanup = {},
            ) as T
        }
        compose.runOnIdle {
            viewModel = ViewModelProvider(compose.activity, factory)[MainCourseViewModel::class.java]
            MainCourseTestContent.content = { ViewModelContent(viewModel) }
        }
        compose.waitUntil(5_000) { viewModel.state.value.phase == SessionPhase.READY }
    }

    @After
    fun tearDown() {
        compose.runOnIdle { MainCourseTestContent.content = {} }
    }

    @Test
    fun pendingNameMutationSurvivesRecreationAndPersistenceRetryDoesNotRepeatPatch() {
        val updateResult = CompletableDeferred<User>()
        api.updateResult = updateResult
        sessionStore.failWrites = true
        openNameEditorAndSave("Ada")
        compose.waitUntil(5_000) { viewModel.accountState.value.operation == AccountOperation.SAVING }

        compose.activityRule.scenario.recreate()
        val retained = ViewModelProvider(compose.activity, factory)[MainCourseViewModel::class.java]
        assertSame(viewModel, retained)
        compose.onNodeWithTag("edit_name_input").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.save)).assertIsNotEnabled()

        updateResult.complete(USER.copy(name = "Ada"))
        compose.waitUntil(5_000) { viewModel.accountState.value.canRetryPersistence }
        compose.onNodeWithTag("edit_name_retry").assertIsDisplayed()
        assertEquals(1, api.updateCalls)

        sessionStore.failWrites = false
        compose.onNodeWithTag("edit_name_retry").performClick()
        compose.waitUntil(5_000) { !viewModel.accountState.value.canRetryPersistence }

        assertEquals(1, api.updateCalls)
        assertEquals(2, sessionStore.writeCalls)
        compose.onNodeWithTag("edit_name_input").assertDoesNotExist()
    }

    @Test
    fun pendingDeletionSurvivesRecreationWithoutDispatchingAgain() {
        val deletion = CompletableDeferred<Unit>()
        api.deleteResult = deletion
        compose.onNodeWithTag("nav_Settings").performClick()
        compose.onNodeWithText(text(R.string.manage_account)).performClick()
        compose.onNodeWithText(text(R.string.delete_account)).performClick()
        compose.onNodeWithTag("delete_confirmation").performTextInput("DELETE")
        compose.onNodeWithTag("delete_account_button").performScrollTo().performClick()
        compose.waitUntil(5_000) { viewModel.accountState.value.operation == AccountOperation.DELETING }

        compose.activityRule.scenario.recreate()
        val retained = ViewModelProvider(compose.activity, factory)[MainCourseViewModel::class.java]
        assertSame(viewModel, retained)
        compose.onNodeWithTag("delete_account_button").assertIsNotEnabled()
        assertEquals(1, api.deleteCalls)

        deletion.complete(Unit)
        compose.waitUntil(5_000) { viewModel.state.value.phase == SessionPhase.SIGNED_OUT }
        compose.onNodeWithTag("auth_email").assertIsDisplayed()
        assertEquals(1, api.deleteCalls)
        assertEquals(0, api.signOutCalls)
    }

    @Test
    fun rotationDuringChooserCancelsOnlyTheUnresolvedChooserWithoutAutoLaunch() {
        compose.runOnIdle { viewModel.logout() }
        compose.waitUntil(5_000) { viewModel.state.value.phase == SessionPhase.SIGNED_OUT }
        val chooserStarted = CompletableDeferred<Unit>()
        var requests = 0
        val provider = object : GoogleCredentialProvider {
            override suspend fun credential(nonce: String): String {
                requests++
                chooserStarted.complete(Unit)
                awaitCancellation()
            }
        }
        compose.runOnIdle {
            val launcher = GoogleAuthenticationLauncher(compose.activity, viewModel, provider)
            launcher.launch()
            launcher.launch()
        }
        compose.waitUntil(5_000) { chooserStarted.isCompleted }

        compose.activityRule.scenario.recreate()
        val retained = ViewModelProvider(compose.activity, factory)[MainCourseViewModel::class.java]
        compose.waitUntil(5_000) { viewModel.authenticationMethod.value == null }

        assertSame(viewModel, retained)
        assertEquals(1, requests)
        assertEquals(0, api.googleRequests.size)
        assertEquals(SessionPhase.SIGNED_OUT, viewModel.state.value.phase)
    }

    @Test
    fun chooserDismissalReturnsToIdleFormWithoutClearingTypedEmail() {
        compose.runOnIdle { viewModel.logout() }
        compose.waitUntil(5_000) { viewModel.state.value.phase == SessionPhase.SIGNED_OUT }
        compose.onNodeWithTag("auth_email").performTextInput("reader@example.test")
        val provider = object : GoogleCredentialProvider {
            override suspend fun credential(nonce: String): String = throw GoogleSignInCancelledException()
        }

        compose.runOnIdle {
            GoogleAuthenticationLauncher(compose.activity, viewModel, provider).launch()
        }
        compose.waitUntil(5_000) { viewModel.authenticationMethod.value == null }

        compose.onNodeWithTag("auth_email").assertTextContains("reader@example.test")
        assertEquals(SessionPhase.SIGNED_OUT, viewModel.state.value.phase)
        assertEquals(null, viewModel.state.value.authError)
        assertEquals(0, api.googleRequests.size)
    }

    @Test
    fun providerFailureReturnsSafeFeedbackWithoutCallingTheApi() {
        showSignedOut()
        val provider = object : GoogleCredentialProvider {
            override suspend fun credential(nonce: String): String = throw GoogleSignInException()
        }

        compose.runOnIdle {
            GoogleAuthenticationLauncher(compose.activity, viewModel, provider).launch()
        }
        compose.waitUntil(5_000) { viewModel.authenticationMethod.value == null }

        assertEquals("Google sign-in is unavailable. Please try again.", viewModel.state.value.authError)
        assertEquals(0, api.googleRequests.size)
        assertEquals(SessionPhase.SIGNED_OUT, viewModel.state.value.phase)
    }

    @Test
    fun unexpectedProviderFailureReturnsSafeFeedbackWithoutLeakingDetailsOrCallingTheApi() {
        showSignedOut()
        val provider = object : GoogleCredentialProvider {
            override suspend fun credential(nonce: String): String = error("secret provider detail")
        }

        compose.runOnIdle {
            GoogleAuthenticationLauncher(compose.activity, viewModel, provider).launch()
        }
        compose.waitUntil(5_000) { viewModel.authenticationMethod.value == null }

        assertEquals("Google sign-in is unavailable. Please try again.", viewModel.state.value.authError)
        assertEquals(0, api.googleRequests.size)
        assertEquals(SessionPhase.SIGNED_OUT, viewModel.state.value.phase)
    }

    @Test
    fun rotationAfterChooserResultRetainsOneRailsExchangeAndSecureOutcome() {
        compose.runOnIdle { viewModel.logout() }
        compose.waitUntil(5_000) { viewModel.state.value.phase == SessionPhase.SIGNED_OUT }
        val response = CompletableDeferred<SessionResponse>()
        api.googleResult = response
        val provider = object : GoogleCredentialProvider {
            override suspend fun credential(nonce: String) = "provider-token"
        }
        compose.runOnIdle {
            GoogleAuthenticationLauncher(compose.activity, viewModel, provider).launch()
        }
        compose.waitUntil(5_000) { api.googleRequests.size == 1 }

        compose.activityRule.scenario.recreate()
        val retained = ViewModelProvider(compose.activity, factory)[MainCourseViewModel::class.java]
        response.complete(SESSION)
        compose.waitUntil(5_000) { viewModel.state.value.user == USER }

        assertSame(viewModel, retained)
        assertEquals(1, api.googleRequests.size)
        assertEquals(SESSION, sessionStore.stored?.response)
        assertEquals(USER, viewModel.state.value.user)
    }

    @Test
    fun appleWaitingAndConsumedBrowserCommandSurviveRecreationThenNewIntentExchangesOnce() {
        showSignedOut()
        compose.runOnIdle { assertTrue(viewModel.beginAppleAuthentication()) }
        compose.waitUntil(5_000) { viewModel.appleBrowserLaunch.value != null }
        compose.runOnIdle {
            val command = checkNotNull(viewModel.appleBrowserLaunch.value)
            assertEquals(APPLE_BROWSER_URL, viewModel.consumeAppleBrowserLaunch(command))
            assertEquals(null, viewModel.consumeAppleBrowserLaunch(command))
        }

        compose.activityRule.scenario.recreate()
        val retained = ViewModelProvider(compose.activity, factory)[MainCourseViewModel::class.java]
        assertSame(viewModel, retained)
        assertEquals(null, viewModel.appleBrowserLaunch.value)
        assertTrue(viewModel.appleCanCancel.value)

        val callbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(APPLE_CALLBACK_URL))
        compose.runOnIdle {
            assertTrue(consumeAppleCallbackIntent(callbackIntent, true, viewModel::handleAppleAuthenticationCallback))
            assertFalse(consumeAppleCallbackIntent(callbackIntent, true, viewModel::handleAppleAuthenticationCallback))
        }
        compose.waitUntil(5_000) { viewModel.state.value.user == USER }

        assertEquals(1, api.appleExchangeRequests.size)
        assertEquals(null, callbackIntent.data)
        assertFalse(viewModel.appleCanCancel.value)
    }

    @Test
    fun editorSaveableStateSurvivesAFreshMainCourseViewModelWithoutReplayingSave() {
        val detail = RecipeDetail(
            id = 20,
            name = "Soup",
            prepTime = 10,
            cookTime = 20,
            servings = 4,
            favorite = false,
            ingredients = listOf("onion"),
            structuredIngredients = emptyList(),
            instructions = listOf("Cook"),
            notes = null,
            sourceUrl = null,
            tags = emptyList(),
            coverImageUrl = null,
            coverImages = null,
            createdAt = "then",
            updatedAt = "now",
        )
        var activeViewModel = viewModel
        var submissions = 0
        compose.runOnIdle {
            MainCourseTestContent.content = {
                val action by activeViewModel.recipeActionState.collectAsStateWithLifecycle()
                MainCourseTheme {
                    RecipeEditScreen(
                        scope = RecipeScope(USER.id, 1),
                        recipe = detail,
                        actionState = action,
                        imageState = RecipeImagePreparationState(),
                        onSave = { draft, image ->
                            submissions++
                            activeViewModel.saveRecipe(draft, image)
                        },
                        editorId = "fresh-view-model-editor",
                    )
                }
            }
        }
        compose.onNodeWithTag("editor_name").performTextInput(" changed")
        compose.onNodeWithTag("editor_list").performScrollToNode(hasTestTag("editor_save"))
        compose.onNodeWithTag("editor_save").performClick()
        assertEquals(1, submissions)

        compose.runOnIdle {
            compose.activity.viewModelStore.clear()
            activeViewModel = factory.create(MainCourseViewModel::class.java)
            viewModel = activeViewModel
        }
        compose.activityRule.scenario.recreate()

        compose.onNodeWithTag("editor_list").performScrollToNode(hasTestTag("editor_unconfirmed"))
        compose.onNodeWithTag("editor_unconfirmed").assertIsDisplayed()
        assertEquals(1, submissions)
        assertEquals(0, api.updateCalls)
    }

    @Test
    fun fastRealControllerSaveReturnsToDetailAndOldSuccessDoesNotCloseReopenedEditor() {
        api.cookbookItems = listOf(COOKBOOK)
        api.recipeItems = listOf(RECIPE)
        api.recipeDetail = DETAIL
        compose.runOnIdle { viewModel.refresh() }
        compose.waitUntil(5_000) { viewModel.state.value.recipes == listOf(RECIPE) }

        compose.onNodeWithText(RECIPE.name).performClick()
        compose.waitUntil(5_000) { viewModel.state.value.detail?.recipe == DETAIL }
        compose.onNodeWithTag("detail_actions").performScrollTo().performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.recipe_edit)).performClick()
        compose.onNodeWithTag("editor_name").performTextInput(" updated")
        compose.onNodeWithTag("editor_list").performScrollToNode(hasTestTag("editor_save"))
        compose.onNodeWithTag("editor_save").performClick()

        compose.waitUntil(5_000) { viewModel.recipeActionState.value.outcome == com.getmaincourse.app.features.recipes.RecipeActionOutcome.SUCCEEDED }
        compose.onNodeWithTag("recipe_detail").assertIsDisplayed()
        compose.onNodeWithTag("detail_actions").performScrollTo().performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.recipe_edit)).performClick()
        compose.onNodeWithTag("editor_name").assertIsDisplayed()
        assertEquals(1, api.updateCalls)
    }

    private fun openNameEditorAndSave(name: String) {
        compose.onNodeWithTag("nav_Settings").performClick()
        compose.onNodeWithText(text(R.string.edit_name)).performClick()
        compose.onNodeWithTag("edit_name_input").performTextClearance()
        compose.onNodeWithTag("edit_name_input").performTextInput(name)
        compose.onNodeWithText(text(R.string.save)).performClick()
    }

    private fun showSignedOut() {
        compose.runOnIdle { viewModel.logout() }
        compose.waitUntil(5_000) { viewModel.state.value.phase == SessionPhase.SIGNED_OUT }
    }

    @androidx.compose.runtime.Composable
    private fun ViewModelContent(viewModel: MainCourseViewModel) {
        val state by viewModel.state.collectAsStateWithLifecycle()
        val onboarding by viewModel.onboardingState.collectAsStateWithLifecycle()
        val account by viewModel.accountState.collectAsStateWithLifecycle()
        val authenticationMethod by viewModel.authenticationMethod.collectAsStateWithLifecycle()
        val search by viewModel.searchState.collectAsStateWithLifecycle()
        val recipeAction by viewModel.recipeActionState.collectAsStateWithLifecycle()
        val recipeImage by viewModel.recipeImagePreparationState.collectAsStateWithLifecycle()
        MainCourseTheme {
            MainCourseApp(
                state = state,
                onboardingState = onboarding,
                accountState = account,
                authenticationMethod = authenticationMethod,
                actions = MainCourseActions(
                    updateName = { viewModel.updateName(it) },
                    updateLifecycleNotifications = { viewModel.updateLifecycleNotifications(it) },
                    retryAccountPersistence = { viewModel.retryAccountPersistence() },
                    deleteAccount = { viewModel.deleteAccount() },
                    clearAccountError = { viewModel.clearAccountError() },
                    refresh = { viewModel.refresh() },
                    openRecipe = { viewModel.openRecipe(it) },
                    closeRecipe = { viewModel.closeRecipe() },
                    saveRecipe = { draft, image -> viewModel.saveRecipe(draft, image) },
                    logout = { viewModel.logout() },
                ),
                searchState = search,
                recipeActionState = recipeAction,
                recipeImagePreparationState = recipeImage,
            )
        }
    }

    private fun text(id: Int) = compose.activity.getString(id)

    private class FakeSessionStore(var stored: StoredSession?) : SessionStore {
        var failWrites = false
        var writeCalls = 0

        override suspend fun read() = stored
        override suspend fun write(session: StoredSession) {
            writeCalls++
            if (failWrites) error("disk full")
            stored = session
        }
        override suspend fun clear() {
            stored = null
        }
    }

    private class FakeOnboardingStore : OnboardingStore {
        private var record: OnboardingRecord? = OnboardingRecord(
            origin = BASE_URL,
            deviceId = null,
            step = OnboardingStep.COMPLETE,
            completed = true,
        )
        override suspend fun read() = record
        override suspend fun write(record: OnboardingRecord) {
            this.record = record
        }
    }

    private class FakeApi : MainCourseApi {
        var updateCalls = 0
        var deleteCalls = 0
        var signOutCalls = 0
        var updateResult: CompletableDeferred<User>? = null
        var deleteResult: CompletableDeferred<Unit>? = null
        var googleResult: CompletableDeferred<SessionResponse>? = null
        val googleRequests = mutableListOf<GoogleSignInRequest>()
        val appleExchangeRequests = mutableListOf<AppleAuthenticationExchangeRequest>()
        var cookbookItems = emptyList<Cookbook>()
        var recipeItems = emptyList<RecipeSummary>()
        var recipeDetail: RecipeDetail? = null

        override suspend fun signIn(request: SignInRequest): SessionResponse = error("unused")

        override suspend fun signInWithGoogle(request: GoogleSignInRequest): SessionResponse {
            googleRequests += request
            return googleResult?.await() ?: SESSION
        }
        override suspend fun startAppleAuthentication(
            request: AppleAuthenticationStartRequest,
        ): AppleAuthenticationStartResponse =
            AppleAuthenticationStartResponse(HANDLE, APPLE_BROWSER_URL, "2026-09-08T00:05:00Z")
        override suspend fun exchangeAppleAuthentication(
            request: AppleAuthenticationExchangeRequest,
        ): SessionResponse {
            appleExchangeRequests += request
            return SESSION
        }
        override suspend fun signUp(request: SignUpRequest): SessionResponse = error("unused")
        override suspend fun signOut(token: String) {
            signOutCalls++
        }
        override suspend fun updateAccount(token: String, request: AccountUpdateRequest): User {
            updateCalls++
            return updateResult?.await() ?: USER
        }
        override suspend fun deleteAccount(token: String) {
            deleteCalls++
            deleteResult?.await()
        }
        override suspend fun submitOnboarding(request: OnboardingRequest) =
            OnboardingResponse(1, request.deviceId, request.answers)
        override suspend fun cookbooks(token: String) = cookbookItems
        override suspend fun recipes(token: String, cookbookId: Long) = recipeItems
        override suspend fun recipe(token: String, cookbookId: Long, recipeId: Long): RecipeDetail =
            this.recipeDetail ?: error("unused")
        override suspend fun recipeBatch(token: String, cookbookId: Long, cursor: String?): RecipeBatchResponse =
            error("unused")
        override suspend fun updateRecipe(
            token: String,
            cookbookId: Long,
            recipeId: Long,
            request: RecipeUpdateRequest,
        ): RecipeDetail {
            updateCalls++
            return checkNotNull(recipeDetail).copy(name = request.name, updatedAt = "later")
        }
        override suspend fun updateRecipeCover(
            token: String,
            cookbookId: Long,
            recipeId: Long,
            image: File,
        ): RecipeDetail = error("unused")
        override suspend fun moveRecipe(
            token: String,
            sourceCookbookId: Long,
            recipeId: Long,
            targetCookbookId: Long,
        ): RecipeDetail = error("unused")
        override suspend fun deleteRecipe(token: String, cookbookId: Long, recipeId: Long) = error("unused")
        override suspend fun addRecipeIngredients(
            token: String,
            cookbookId: Long,
            request: ShoppingItemsRequest,
        ): List<ShoppingItem> = error("unused")
    }

    private class FakeCatalogStore : CatalogStore {
        private val memberships = mutableMapOf<Long, List<Cookbook>>()
        private val recipeLists = mutableMapOf<RecipeScope, List<RecipeSummary>>()
        private val details = mutableMapOf<Pair<RecipeScope, Long>, RecipeDetail>()
        private val selected = mutableMapOf<Long, Long>()
        override suspend fun cookbooks(userId: Long) = memberships[userId].orEmpty()
        override suspend fun replaceCookbooks(userId: Long, items: List<Cookbook>) {
            memberships[userId] = items
        }
        override suspend fun selectedCookbookId(userId: Long): Long? = selected[userId]
        override suspend fun selectCookbook(userId: Long, cookbookId: Long) {
            require(memberships[userId].orEmpty().any { it.id == cookbookId })
            selected[userId] = cookbookId
        }
        override suspend fun recipes(scope: RecipeScope) = CachedRecipes(recipeLists[scope].orEmpty(), recipeLists.containsKey(scope))
        override suspend fun replaceRecipes(scope: RecipeScope, items: List<RecipeSummary>) {
            require(memberships[scope.userId].orEmpty().any { it.id == scope.cookbookId })
            recipeLists[scope] = items
        }
        override suspend fun detail(scope: RecipeScope, recipeId: Long): RecipeDetail? = details[scope to recipeId]
        override suspend fun saveRecipeDetails(scope: RecipeScope, details: List<RecipeDetail>) {
            require(memberships[scope.userId].orEmpty().any { it.id == scope.cookbookId })
            details.forEach { detail -> this.details[scope to detail.id] = detail }
        }
        override suspend fun searchDocuments(scope: RecipeScope) = emptyList<RecipeSearchDocument>()
        override suspend fun upsertPartialRecipe(
            scope: RecipeScope,
            knownSummary: RecipeSummary,
            detail: RecipeDetail,
        ) {
            require(memberships[scope.userId].orEmpty().any { it.id == scope.cookbookId })
        }
        override suspend fun removeRecipe(scope: RecipeScope, recipeId: Long) = Unit
        override suspend fun removeCookbook(scope: RecipeScope) = Unit
        override suspend fun clear() = Unit
    }

    private companion object {
        const val BASE_URL = "https://example.test/"
        const val HANDLE = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        const val CODE = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"
        const val APPLE_BROWSER_URL = "https://example.test/android/apple/sign_in?transaction_id=$HANDLE"
        const val APPLE_CALLBACK_URL =
            "com.getmaincourse.app.debug:/oauth/apple?transaction_id=$HANDLE&exchange_code=$CODE"
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC)
        val USER = User(7, "Reader", "reader@example.test", false)
        val SESSION = SessionResponse("fixture-token", "2026-12-08T00:00:00Z", USER)
        val COOKBOOK = Cookbook(1, "My cookbook", true, 1, emptyList())
        val RECIPE = RecipeSummary(20, "Soup", 10, 20, false, null, null, "completed", null, "now")
        val DETAIL = RecipeDetail(
            20, "Soup", 10, 20, 4, false, listOf("onion"), emptyList(), listOf("Cook"),
            null, null, emptyList(), null, null, "then", "now",
        )
    }
}
