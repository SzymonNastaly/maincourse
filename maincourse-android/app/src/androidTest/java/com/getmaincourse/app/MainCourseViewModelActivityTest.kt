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
import com.getmaincourse.app.features.auth.GoogleAuthenticationLauncher
import com.getmaincourse.app.features.auth.GoogleCredentialProvider
import com.getmaincourse.app.features.auth.GoogleSignInCancelledException
import com.getmaincourse.app.features.auth.GoogleSignInException
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
        var googleResult: CompletableDeferred<SessionResponse>? = null
        val googleRequests = mutableListOf<GoogleSignInRequest>()
        val appleExchangeRequests = mutableListOf<AppleAuthenticationExchangeRequest>()

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
        const val HANDLE = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        const val CODE = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"
        const val APPLE_BROWSER_URL = "https://example.test/android/apple/sign_in?transaction_id=$HANDLE"
        const val APPLE_CALLBACK_URL =
            "com.getmaincourse.app.debug:/oauth/apple?transaction_id=$HANDLE&exchange_code=$CODE"
        val CLOCK: Clock = Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC)
        val USER = User(7, "Reader", "reader@example.test", false)
        val SESSION = SessionResponse("fixture-token", "2026-12-08T00:00:00Z", USER)
    }
}
