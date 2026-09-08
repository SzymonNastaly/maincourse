package com.getmaincourse.app

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.getmaincourse.app.data.cache.CachedRecipes
import com.getmaincourse.app.data.cache.CatalogStore
import com.getmaincourse.app.data.cache.RecipeScope
import com.getmaincourse.app.data.model.AccountUpdateRequest
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.OnboardingRequest
import com.getmaincourse.app.data.model.OnboardingResponse
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeSummary
import com.getmaincourse.app.data.model.SessionResponse
import com.getmaincourse.app.data.model.SignInRequest
import com.getmaincourse.app.data.model.SignUpRequest
import com.getmaincourse.app.data.model.User
import com.getmaincourse.app.data.network.MainCourseApi
import com.getmaincourse.app.data.onboarding.OnboardingRecord
import com.getmaincourse.app.data.onboarding.OnboardingStep
import com.getmaincourse.app.data.onboarding.OnboardingStore
import com.getmaincourse.app.data.session.SessionStore
import com.getmaincourse.app.data.session.StoredSession
import com.getmaincourse.app.features.session.CatalogRepository
import com.getmaincourse.app.features.session.MainCourseViewModel
import com.getmaincourse.app.features.session.SessionPhase
import com.getmaincourse.app.features.settings.AccountOperation
import com.getmaincourse.app.ui.theme.MainCourseTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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

    @Before
    fun setUp() {
        api = FakeApi()
        sessionStore = FakeSessionStore(StoredSession(BASE_URL, SESSION))
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = MainCourseViewModel(
                api = api,
                sessionStore = sessionStore,
                catalogRepository = CatalogRepository(api, FakeCatalogStore()),
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

    private fun openNameEditorAndSave(name: String) {
        compose.onNodeWithTag("nav_Settings").performClick()
        compose.onNodeWithText(text(R.string.edit_name)).performClick()
        compose.onNodeWithTag("edit_name_input").performTextClearance()
        compose.onNodeWithTag("edit_name_input").performTextInput(name)
        compose.onNodeWithText(text(R.string.save)).performClick()
    }

    @androidx.compose.runtime.Composable
    private fun ViewModelContent(viewModel: MainCourseViewModel) {
        val state by viewModel.state.collectAsStateWithLifecycle()
        val onboarding by viewModel.onboardingState.collectAsStateWithLifecycle()
        val account by viewModel.accountState.collectAsStateWithLifecycle()
        val preparing by viewModel.isPreparingAuthentication.collectAsStateWithLifecycle()
        MainCourseTheme {
            MainCourseApp(
                state = state,
                onboardingState = onboarding,
                accountState = account,
                isPreparingAuthentication = preparing,
                actions = MainCourseActions(
                    updateName = { viewModel.updateName(it) },
                    updateLifecycleNotifications = { viewModel.updateLifecycleNotifications(it) },
                    retryAccountPersistence = { viewModel.retryAccountPersistence() },
                    deleteAccount = { viewModel.deleteAccount() },
                    clearAccountError = { viewModel.clearAccountError() },
                    logout = { viewModel.logout() },
                ),
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

        override suspend fun signIn(request: SignInRequest): SessionResponse = error("unused")

        override suspend fun signInWithGoogle(request: com.getmaincourse.app.data.model.GoogleSignInRequest): SessionResponse = error("unused")
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
        override suspend fun cookbooks(token: String) = emptyList<Cookbook>()
        override suspend fun recipes(token: String, cookbookId: Long) = emptyList<RecipeSummary>()
        override suspend fun recipe(token: String, cookbookId: Long, recipeId: Long): RecipeDetail = error("unused")
    }

    private class FakeCatalogStore : CatalogStore {
        override suspend fun cookbooks(userId: Long) = emptyList<Cookbook>()
        override suspend fun replaceCookbooks(userId: Long, items: List<Cookbook>) = Unit
        override suspend fun selectedCookbookId(userId: Long): Long? = null
        override suspend fun selectCookbook(userId: Long, cookbookId: Long) = Unit
        override suspend fun recipes(scope: RecipeScope) = CachedRecipes(emptyList(), false)
        override suspend fun replaceRecipes(scope: RecipeScope, items: List<RecipeSummary>) = Unit
        override suspend fun detail(scope: RecipeScope, recipeId: Long): RecipeDetail? = null
        override suspend fun saveDetail(scope: RecipeScope, detail: RecipeDetail) = Unit
        override suspend fun removeRecipe(scope: RecipeScope, recipeId: Long) = Unit
        override suspend fun removeCookbook(scope: RecipeScope) = Unit
        override suspend fun clear() = Unit
    }

    private companion object {
        const val BASE_URL = "https://example.test/"
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC)
        val USER = User(7, "Reader", "reader@example.test", false)
        val SESSION = SessionResponse("fixture-token", "2026-12-08T00:00:00Z", USER)
    }
}
