package com.getmaincourse.app.features.session

import com.getmaincourse.app.data.cache.CachedRecipes
import com.getmaincourse.app.data.cache.CatalogStore
import com.getmaincourse.app.data.cache.RecipeScope
import com.getmaincourse.app.data.model.AccountUpdateRequest
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
import com.getmaincourse.app.data.network.ApiFailure
import com.getmaincourse.app.data.onboarding.OnboardingRecord
import com.getmaincourse.app.data.onboarding.OnboardingStep
import com.getmaincourse.app.data.onboarding.OnboardingStore
import com.getmaincourse.app.data.session.SessionStore
import com.getmaincourse.app.data.session.StoredSession
import com.getmaincourse.app.features.auth.AuthenticationMethod
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainCourseViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun restoredAuthDraftWaitsForPreparationThenAttachesItsIdAndCompletesOnSecureSession() = runTest(dispatcher) {
        val api = FakeApi()
        val onboardingStore = FakeOnboardingStore(authDraft())
        val submission = CompletableDeferred<Unit>()
        api.onSubmitOnboarding = { submission.await() }
        val viewModel = viewModel(api = api, onboardingStore = onboardingStore)
        runCurrent()

        assertEquals(0, api.onboardingCalls)
        val auth = viewModel.signUp(signUpRequest())
        assertEquals(AuthenticationMethod.EMAIL, viewModel.authenticationMethod.value)
        runCurrent()
        assertEquals(1, api.onboardingCalls)
        assertEquals(0, api.signUpCalls)

        submission.complete(Unit)
        auth.join()
        advanceUntilIdle()

        assertEquals("draft-id", api.lastSignUp?.onboardingDeviceId)
        assertEquals(1, api.signUpCalls)
        assertEquals(USER, viewModel.state.value.user)
        assertEquals(OnboardingStep.COMPLETE, viewModel.onboardingState.value.step)
        assertNull(onboardingStore.record?.deviceId)
    }

    @Test
    fun changedDraftReturningNullAbortsTheAuthenticationIntent() = runTest(dispatcher) {
        val api = FakeApi()
        val onboardingStore = FakeOnboardingStore(authDraft())
        val submission = CompletableDeferred<Unit>()
        api.onSubmitOnboarding = { submission.await() }
        val viewModel = viewModel(api = api, onboardingStore = onboardingStore)
        runCurrent()

        val auth = viewModel.signUp(signUpRequest())
        runCurrent()
        viewModel.skipOnboarding().join()
        submission.complete(Unit)
        auth.join()

        assertEquals(0, api.signUpCalls)
        assertNull(viewModel.authenticationMethod.value)
        assertEquals(OnboardingStep.COMPLETE, viewModel.onboardingState.value.step)
    }

    @Test
    fun incompleteRestoredAuthDraftFallsBackToOrdinaryAuthenticationWithoutAnId() = runTest(dispatcher) {
        val api = FakeApi()
        val onboardingStore = FakeOnboardingStore(
            OnboardingRecord(
                origin = BASE_URL,
                deviceId = "123e4567-e89b-12d3-a456-426614174000",
                step = OnboardingStep.AUTH,
            ),
        )
        val viewModel = viewModel(api = api, onboardingStore = onboardingStore)
        runCurrent()

        viewModel.signUp(signUpRequest()).join()
        advanceUntilIdle()

        assertEquals(1, api.signUpCalls)
        assertNull(api.lastSignUp?.onboardingDeviceId)
        assertEquals(USER, viewModel.state.value.user)
    }

    @Test
    fun restoredSecureSessionConsumesOnboardingEvenWhenCatalogRefreshFails() = runTest(dispatcher) {
        val api = FakeApi().apply { catalogFailure = true }
        val onboardingStore = FakeOnboardingStore(authDraft())
        val sessionStore = FakeSessionStore(StoredSession(BASE_URL, SESSION))
        val viewModel = viewModel(api, onboardingStore, sessionStore)

        advanceUntilIdle()

        assertEquals(USER, viewModel.state.value.user)
        assertEquals(OnboardingStep.COMPLETE, viewModel.onboardingState.value.step)
        assertEquals(0, api.onboardingCalls)
    }

    @Test
    fun googleChooserAdmissionIsImmediateAndBlocksDuplicateGoogleAndEmail() = runTest(dispatcher) {
        val api = FakeApi()
        val viewModel = viewModel(api, FakeOnboardingStore(null))
        advanceUntilIdle()

        val attempt = checkNotNull(viewModel.beginGoogleAuthentication())

        assertEquals(AuthenticationMethod.GOOGLE, viewModel.authenticationMethod.value)
        assertNull(viewModel.beginGoogleAuthentication())
        viewModel.signIn(SignInRequest("reader@example.test", "password", "Android")).join()
        assertEquals(0, api.signInCalls)
        assertTrue(viewModel.cancelGoogleAuthentication(attempt))
        assertNull(viewModel.authenticationMethod.value)
    }

    @Test
    fun pendingEmailAuthenticationRejectsGoogleAdmission() = runTest(dispatcher) {
        val api = FakeApi()
        val onboardingSubmission = CompletableDeferred<Unit>()
        api.onSubmitOnboarding = { onboardingSubmission.await() }
        val viewModel = viewModel(api, FakeOnboardingStore(authDraft()))
        runCurrent()

        val email = viewModel.signUp(signUpRequest())
        runCurrent()

        assertEquals(AuthenticationMethod.EMAIL, viewModel.authenticationMethod.value)
        assertNull(viewModel.beginGoogleAuthentication())
        onboardingSubmission.complete(Unit)
        email.join()
    }

    @Test
    fun acceptedGoogleResultPreparesOnboardingThenExchangesAndSecuresOnce() = runTest(dispatcher) {
        val api = FakeApi()
        val onboardingSubmission = CompletableDeferred<Unit>()
        api.onSubmitOnboarding = { onboardingSubmission.await() }
        val sessionStore = FakeSessionStore()
        val viewModel = viewModel(api, FakeOnboardingStore(authDraft()), sessionStore)
        runCurrent()
        val attempt = checkNotNull(viewModel.beginGoogleAuthentication())

        assertEquals(0, api.onboardingCalls)
        assertTrue(viewModel.finishGoogleAuthentication(attempt, "provider-token"))
        runCurrent()
        assertEquals(1, api.onboardingCalls)
        assertEquals(0, api.googleRequests.size)

        onboardingSubmission.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            listOf(GoogleSignInRequest("provider-token", attempt.nonce, "Android", "draft-id")),
            api.googleRequests,
        )
        assertEquals(SESSION, sessionStore.stored?.response)
        assertEquals(USER, viewModel.state.value.user)
        assertEquals(OnboardingStep.COMPLETE, viewModel.onboardingState.value.step)
    }

    @Test
    fun cancelledAndStaleGoogleResultsKeepDraftAndCannotAuthenticate() = runTest(dispatcher) {
        val store = FakeOnboardingStore(authDraft())
        val api = FakeApi()
        val viewModel = viewModel(api, store)
        runCurrent()
        val cancelled = checkNotNull(viewModel.beginGoogleAuthentication())

        assertTrue(viewModel.cancelGoogleAuthentication(cancelled))
        assertFalse(viewModel.finishGoogleAuthentication(cancelled, "stale-token"))
        advanceUntilIdle()

        assertTrue(api.googleRequests.isEmpty())
        assertEquals("draft-id", store.record?.deviceId)
        assertNull(viewModel.authenticationMethod.value)

        val replacement = checkNotNull(viewModel.beginGoogleAuthentication())
        assertFalse(viewModel.cancelGoogleAuthentication(cancelled))
        assertEquals(AuthenticationMethod.GOOGLE, viewModel.authenticationMethod.value)
        assertTrue(viewModel.cancelGoogleAuthentication(replacement))

        val loggedOut = checkNotNull(viewModel.beginGoogleAuthentication())
        viewModel.logout().join()
        assertFalse(viewModel.finishGoogleAuthentication(loggedOut, "late-token"))
        assertTrue(api.googleRequests.isEmpty())
    }

    @Test
    fun googleFailureNeedsAFreshManualAttemptAndPreservesPasswordFormDraft() = runTest(dispatcher) {
        val api = FakeApi().apply {
            googleFailure = ApiFailure(409, "Sign in with your password to link this account")
        }
        val onboardingStore = FakeOnboardingStore(authDraft())
        val viewModel = viewModel(api, onboardingStore)
        advanceUntilIdle()
        val first = checkNotNull(viewModel.beginGoogleAuthentication())

        assertTrue(viewModel.finishGoogleAuthentication(first, "first-token"))
        advanceUntilIdle()

        assertEquals(1, api.googleRequests.size)
        assertEquals("Sign in with your password to link this account", viewModel.state.value.authError)
        assertEquals("draft-id", onboardingStore.record?.deviceId)
        assertNull(viewModel.authenticationMethod.value)
        advanceUntilIdle()
        assertEquals(1, api.googleRequests.size)

        api.googleFailure = ApiFailure(503, "Try again later")
        val second = checkNotNull(viewModel.beginGoogleAuthentication())
        assertTrue(viewModel.finishGoogleAuthentication(second, "second-token"))
        advanceUntilIdle()
        assertEquals(2, api.googleRequests.size)
    }

    @Test
    fun aNewViewModelNeverReplaysAnUnfinishedGoogleAttempt() = runTest(dispatcher) {
        val firstApi = FakeApi()
        val first = viewModel(firstApi, FakeOnboardingStore(null))
        advanceUntilIdle()
        checkNotNull(first.beginGoogleAuthentication())

        val replacementApi = FakeApi()
        val replacement = viewModel(replacementApi, FakeOnboardingStore(null))
        advanceUntilIdle()

        assertNull(replacement.authenticationMethod.value)
        assertTrue(replacementApi.googleRequests.isEmpty())
    }

    private fun viewModel(
        api: FakeApi,
        onboardingStore: FakeOnboardingStore,
        sessionStore: FakeSessionStore = FakeSessionStore(),
    ) = MainCourseViewModel(
        api = api,
        sessionStore = sessionStore,
        catalogRepository = CatalogRepository(api, FakeCatalogStore()),
        onboardingStore = onboardingStore,
        baseUrl = BASE_URL,
        clock = CLOCK,
        imageCleanup = {},
    )

    private class FakeOnboardingStore(var record: OnboardingRecord?) : OnboardingStore {
        override suspend fun read() = record
        override suspend fun write(record: OnboardingRecord) {
            this.record = record
        }
    }

    private class FakeSessionStore(var stored: StoredSession? = null) : SessionStore {
        override suspend fun read() = stored
        override suspend fun write(session: StoredSession) {
            stored = session
        }
        override suspend fun clear() {
            stored = null
        }
    }

    private class FakeApi : MainCourseApi {
        var onboardingCalls = 0
        var signInCalls = 0
        var signUpCalls = 0
        val googleRequests = mutableListOf<GoogleSignInRequest>()
        var lastSignUp: SignUpRequest? = null
        var catalogFailure = false
        var googleFailure: Throwable? = null
        var onSubmitOnboarding: suspend () -> Unit = {}

        override suspend fun signIn(request: SignInRequest): SessionResponse {
            signInCalls++
            return SESSION
        }

        override suspend fun signInWithGoogle(request: GoogleSignInRequest): SessionResponse {
            googleRequests += request
            googleFailure?.let { throw it }
            return SESSION
        }
        override suspend fun signUp(request: SignUpRequest): SessionResponse {
            signUpCalls++
            lastSignUp = request
            return SESSION
        }
        override suspend fun signOut(token: String) = Unit
        override suspend fun updateAccount(token: String, request: AccountUpdateRequest) = USER
        override suspend fun deleteAccount(token: String) = Unit
        override suspend fun submitOnboarding(request: OnboardingRequest): OnboardingResponse {
            onboardingCalls++
            onSubmitOnboarding()
            return OnboardingResponse(1, request.deviceId, request.answers)
        }
        override suspend fun cookbooks(token: String): List<Cookbook> {
            if (catalogFailure) error("offline")
            return emptyList()
        }
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
        val SESSION = SessionResponse("secret", "2026-12-08T00:00:00Z", USER)

        fun authDraft() = OnboardingRecord(
            origin = BASE_URL,
            deviceId = "draft-id",
            step = OnboardingStep.AUTH,
            householdSize = 2,
            saveToday = listOf("screenshots"),
        )

        fun signUpRequest() = SignUpRequest(
            name = "Reader",
            email = "reader@example.test",
            password = "long-password",
            passwordConfirmation = "long-password",
            deviceName = "Android",
        )
    }
}
