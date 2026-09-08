package com.getmaincourse.app.features.session

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
import com.getmaincourse.app.data.network.ApiFailure
import com.getmaincourse.app.data.onboarding.OnboardingRecord
import com.getmaincourse.app.data.onboarding.OnboardingStep
import com.getmaincourse.app.data.onboarding.OnboardingStore
import com.getmaincourse.app.data.session.SessionStore
import com.getmaincourse.app.data.session.StoredSession
import com.getmaincourse.app.features.auth.AuthenticationMethod
import com.getmaincourse.app.features.auth.AppleAuthenticationCallback
import com.getmaincourse.app.features.auth.ApplePkce
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.advanceTimeBy
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

    @Test
    fun appleStartSharesAdmissionAndPublishesExactlyOneConsumableBrowserLaunch() = runTest(dispatcher) {
        val api = FakeApi()
        val viewModel = viewModel(api, FakeOnboardingStore(null))
        advanceUntilIdle()

        assertTrue(viewModel.beginAppleAuthentication())
        assertFalse(viewModel.beginAppleAuthentication())
        assertNull(viewModel.beginGoogleAuthentication())
        viewModel.signIn(SignInRequest("reader@example.test", "password", "Android")).join()
        runCurrent()

        assertEquals(AuthenticationMethod.APPLE, viewModel.authenticationMethod.value)
        assertEquals(0, api.signInCalls)
        assertEquals(1, api.appleStartRequests.size)
        assertEquals("release", api.appleStartRequests.single().callback)
        val command = checkNotNull(viewModel.appleBrowserLaunch.value)
        assertEquals(APPLE_BROWSER_URL, viewModel.consumeAppleBrowserLaunch(command))
        assertNull(viewModel.consumeAppleBrowserLaunch(command))
        assertNull(viewModel.appleBrowserLaunch.value)
        assertTrue(viewModel.appleCanCancel.value)
    }

    @Test
    fun matchingAppleCallbackPreparesOnboardingThenExchangesAndSecuresExactlyOnce() = runTest(dispatcher) {
        val api = FakeApi()
        val submission = CompletableDeferred<Unit>()
        api.onSubmitOnboarding = { submission.await() }
        val sessionStore = FakeSessionStore()
        val viewModel = viewModel(api, FakeOnboardingStore(authDraft()), sessionStore)
        runCurrent()
        assertTrue(viewModel.beginAppleAuthentication())
        runCurrent()
        val launch = checkNotNull(viewModel.appleBrowserLaunch.value)
        viewModel.consumeAppleBrowserLaunch(launch)

        assertTrue(viewModel.handleAppleAuthenticationCallback(AppleAuthenticationCallback.Success(HANDLE, CODE)))
        assertFalse(viewModel.appleCanCancel.value)
        assertFalse(viewModel.cancelAppleAuthentication())
        assertFalse(viewModel.handleAppleAuthenticationCallback(AppleAuthenticationCallback.Success(HANDLE, CODE)))
        runCurrent()
        assertEquals(1, api.onboardingCalls)
        assertTrue(api.appleExchangeRequests.isEmpty())

        submission.complete(Unit)
        advanceUntilIdle()

        val exchange = api.appleExchangeRequests.single()
        assertEquals(HANDLE, exchange.transactionId)
        assertEquals(CODE, exchange.exchangeCode)
        assertEquals("draft-id", exchange.onboardingDeviceId)
        assertEquals(43, exchange.codeVerifier.length)
        assertEquals(api.appleStartRequests.single().codeChallenge, ApplePkce.challenge(exchange.codeVerifier))
        assertEquals(SESSION, sessionStore.stored?.response)
        assertEquals(USER, viewModel.state.value.user)
        assertNull(viewModel.authenticationMethod.value)
    }

    @Test
    fun browserDeadlineAndCancelCannotStopAnAcceptedExchange() = runTest(dispatcher) {
        val api = FakeApi()
        val exchange = CompletableDeferred<SessionResponse>()
        api.appleExchangeResult = exchange
        val viewModel = viewModel(api, FakeOnboardingStore(null), appleWaitingTimeoutMillis = 1_000)
        advanceUntilIdle()
        viewModel.beginAppleAuthentication()
        runCurrent()

        assertTrue(viewModel.handleAppleAuthenticationCallback(AppleAuthenticationCallback.Success(HANDLE, CODE)))
        runCurrent()
        assertEquals(1, api.appleExchangeRequests.size)
        assertFalse(viewModel.cancelAppleAuthentication())
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(AuthenticationMethod.APPLE, viewModel.authenticationMethod.value)
        assertEquals(1, api.appleExchangeRequests.size)

        exchange.complete(SESSION)
        advanceUntilIdle()
        assertEquals(USER, viewModel.state.value.user)
    }

    @Test
    fun cancelExpiryWrongHandleAndLateCallbacksNeverExchange() = runTest(dispatcher) {
        val api = FakeApi()
        val viewModel = viewModel(api, FakeOnboardingStore(null), appleWaitingTimeoutMillis = 1_000)
        advanceUntilIdle()

        assertTrue(viewModel.beginAppleAuthentication())
        runCurrent()
        assertFalse(
            viewModel.handleAppleAuthenticationCallback(
                AppleAuthenticationCallback.Success("ZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ", CODE),
            ),
        )
        assertTrue(viewModel.cancelAppleAuthentication())
        assertFalse(viewModel.handleAppleAuthenticationCallback(AppleAuthenticationCallback.Success(HANDLE, CODE)))

        assertTrue(viewModel.beginAppleAuthentication())
        runCurrent()
        advanceTimeBy(1_001)
        runCurrent()
        assertNull(viewModel.authenticationMethod.value)
        assertFalse(viewModel.handleAppleAuthenticationCallback(AppleAuthenticationCallback.Success(HANDLE, CODE)))
        assertTrue(api.appleExchangeRequests.isEmpty())
        assertEquals(2, api.appleStartRequests.size)
        assertEquals("Apple sign-in expired. Start again.", viewModel.state.value.authError)
    }

    @Test
    fun callbackErrorReleasesAdmissionAndRequiresAFreshAttempt() = runTest(dispatcher) {
        val api = FakeApi()
        val viewModel = viewModel(api, FakeOnboardingStore(null))
        advanceUntilIdle()
        assertTrue(viewModel.beginAppleAuthentication())
        runCurrent()

        assertTrue(
            viewModel.handleAppleAuthenticationCallback(
                AppleAuthenticationCallback.Error(
                    HANDLE,
                    com.getmaincourse.app.features.auth.AppleAuthenticationError.PROVIDER_UNAVAILABLE,
                ),
            ),
        )
        advanceUntilIdle()

        assertNull(viewModel.authenticationMethod.value)
        assertEquals("Apple sign-in is unavailable. Please try again.", viewModel.state.value.authError)
        assertTrue(api.appleExchangeRequests.isEmpty())
        assertTrue(viewModel.beginAppleAuthentication())
    }

    @Test
    fun browserLaunchFailureReleasesTheSharedAdmission() = runTest(dispatcher) {
        val api = FakeApi()
        val viewModel = viewModel(api, FakeOnboardingStore(null))
        advanceUntilIdle()
        viewModel.beginAppleAuthentication()
        runCurrent()
        val command = checkNotNull(viewModel.appleBrowserLaunch.value)
        viewModel.consumeAppleBrowserLaunch(command)

        viewModel.appleBrowserLaunchFailed(command)

        assertNull(viewModel.authenticationMethod.value)
        assertEquals("No browser is available to continue with Apple.", viewModel.state.value.authError)
        val googleAttempt = checkNotNull(viewModel.beginGoogleAuthentication())
        assertTrue(viewModel.cancelGoogleAuthentication(googleAttempt))
    }

    @Test
    fun providerUnavailableStartReturnsToTheExistingAuthForm() = runTest(dispatcher) {
        val api = FakeApi().apply { appleStartFailure = ApiFailure(503, "provider detail") }
        val viewModel = viewModel(api, FakeOnboardingStore(null))
        advanceUntilIdle()

        assertTrue(viewModel.beginAppleAuthentication())
        advanceUntilIdle()

        assertNull(viewModel.authenticationMethod.value)
        assertNull(viewModel.appleBrowserLaunch.value)
        assertEquals("Apple sign-in is unavailable. Please try again.", viewModel.state.value.authError)
        assertEquals(1, api.appleStartRequests.size)
    }

    @Test
    fun processDeathLosesPrivateAppleProofAndStaleCallbackCannotInterruptRestoredAccount() = runTest(dispatcher) {
        val firstApi = FakeApi()
        val first = viewModel(firstApi, FakeOnboardingStore(null))
        advanceUntilIdle()
        assertTrue(first.beginAppleAuthentication())
        runCurrent()

        val restoredApi = FakeApi()
        val restored = viewModel(
            restoredApi,
            FakeOnboardingStore(null),
            FakeSessionStore(StoredSession(BASE_URL, SESSION)),
        )
        advanceUntilIdle()

        assertFalse(restored.handleAppleAuthenticationCallback(AppleAuthenticationCallback.Success(HANDLE, CODE)))
        advanceUntilIdle()
        assertEquals(USER, restored.state.value.user)
        assertTrue(restoredApi.appleExchangeRequests.isEmpty())
    }

    @Test
    fun appleSecureStoreFailureDoesNotExposeOrKeepTheReturnedSession() = runTest(dispatcher) {
        val api = FakeApi()
        val store = FakeSessionStore().apply { failWrites = true }
        val viewModel = viewModel(api, FakeOnboardingStore(null), store)
        advanceUntilIdle()
        viewModel.beginAppleAuthentication()
        runCurrent()
        viewModel.handleAppleAuthenticationCallback(AppleAuthenticationCallback.Success(HANDLE, CODE))
        advanceUntilIdle()

        assertEquals(1, api.appleExchangeRequests.size)
        assertNull(store.stored)
        assertNull(viewModel.state.value.user)
        assertFalse(viewModel.state.value.toString().contains(SESSION.token))
        assertEquals("Could not save the session", viewModel.state.value.authError)
    }

    @Test
    fun debugCallbackIsRequestedOnlyForLoopbackHttpBackends() {
        assertEquals("debug", appleCallbackFor("http://10.0.2.2:3000/", isDebugBuild = true))
        assertEquals("debug", appleCallbackFor("http://localhost:3000/", isDebugBuild = true))
        assertEquals("release", appleCallbackFor("http://10.0.2.2:3000/", isDebugBuild = false))
        assertEquals("release", appleCallbackFor("https://development.example/", isDebugBuild = true))
    }

    @Test
    fun serverExpiryUsesAValidFormatButDeviceClockSkewDoesNotChangeTheLocalWaitBudget() = runTest(dispatcher) {
        listOf(
            Clock.fixed(Instant.parse("2026-09-07T23:00:00Z"), ZoneOffset.UTC),
            Clock.fixed(Instant.parse("2026-09-08T01:00:00Z"), ZoneOffset.UTC),
        ).forEach { skewedClock ->
            val api = FakeApi()
            val viewModel = viewModel(
                api = api,
                onboardingStore = FakeOnboardingStore(null),
                clock = skewedClock,
                appleWaitingTimeoutMillis = 1_000,
            )
            advanceUntilIdle()

            assertTrue(viewModel.beginAppleAuthentication())
            runCurrent()
            assertTrue(viewModel.appleCanCancel.value)
            advanceTimeBy(999)
            runCurrent()
            assertEquals(AuthenticationMethod.APPLE, viewModel.authenticationMethod.value)
            advanceTimeBy(1)
            runCurrent()
            assertNull(viewModel.authenticationMethod.value)
            assertTrue(api.appleExchangeRequests.isEmpty())
        }
    }

    @Test
    fun malformedServerExpiryNeverLaunchesTheBrowser() = runTest(dispatcher) {
        val api = FakeApi().apply { appleExpiresAt = "not-an-instant" }
        val viewModel = viewModel(api, FakeOnboardingStore(null))
        advanceUntilIdle()

        viewModel.beginAppleAuthentication()
        advanceUntilIdle()

        assertNull(viewModel.appleBrowserLaunch.value)
        assertNull(viewModel.authenticationMethod.value)
        assertTrue(api.appleExchangeRequests.isEmpty())
    }

    private fun viewModel(
        api: FakeApi,
        onboardingStore: FakeOnboardingStore,
        sessionStore: FakeSessionStore = FakeSessionStore(),
        clock: Clock = CLOCK,
        appleWaitingTimeoutMillis: Long = 300_000,
    ) = MainCourseViewModel(
        api = api,
        sessionStore = sessionStore,
        catalogRepository = CatalogRepository(api, FakeCatalogStore()),
        onboardingStore = onboardingStore,
        baseUrl = BASE_URL,
        clock = clock,
        imageCleanup = {},
        appleWaitingTimeoutMillis = appleWaitingTimeoutMillis,
    )

    private class FakeOnboardingStore(var record: OnboardingRecord?) : OnboardingStore {
        override suspend fun read() = record
        override suspend fun write(record: OnboardingRecord) {
            this.record = record
        }
    }

    private class FakeSessionStore(var stored: StoredSession? = null) : SessionStore {
        var failWrites = false
        override suspend fun read() = stored
        override suspend fun write(session: StoredSession) {
            if (failWrites) error("disk full")
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
        val appleStartRequests = mutableListOf<AppleAuthenticationStartRequest>()
        val appleExchangeRequests = mutableListOf<AppleAuthenticationExchangeRequest>()
        var lastSignUp: SignUpRequest? = null
        var catalogFailure = false
        var googleFailure: Throwable? = null
        var appleExchangeResult: CompletableDeferred<SessionResponse>? = null
        var appleStartFailure: Throwable? = null
        var appleExpiresAt = "2026-09-08T00:05:00Z"
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
        override suspend fun startAppleAuthentication(
            request: AppleAuthenticationStartRequest,
        ): AppleAuthenticationStartResponse {
            appleStartRequests += request
            appleStartFailure?.let { throw it }
            return AppleAuthenticationStartResponse(HANDLE, APPLE_BROWSER_URL, appleExpiresAt)
        }
        override suspend fun exchangeAppleAuthentication(request: AppleAuthenticationExchangeRequest): SessionResponse {
            appleExchangeRequests += request
            return appleExchangeResult?.await() ?: SESSION
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
        const val HANDLE = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        const val CODE = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"
        const val APPLE_BROWSER_URL = "https://example.test/android/apple/sign_in?transaction_id=$HANDLE"
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
