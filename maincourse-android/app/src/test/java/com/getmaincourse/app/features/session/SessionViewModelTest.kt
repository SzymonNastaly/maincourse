package com.getmaincourse.app.features.session

import com.getmaincourse.app.data.model.AccountResponse
import com.getmaincourse.app.data.model.AccountUpdateRequest
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.MoveRecipeRequest
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeSummary
import com.getmaincourse.app.data.model.SessionResponse
import com.getmaincourse.app.data.model.ShoppingItem
import com.getmaincourse.app.data.model.ShoppingItemsRequest
import com.getmaincourse.app.data.model.SignInRequest
import com.getmaincourse.app.data.model.SignUpRequest
import com.getmaincourse.app.data.model.User
import com.getmaincourse.app.data.network.ApiFailure
import com.getmaincourse.app.data.network.MainCourseService
import com.getmaincourse.app.data.network.SessionEvents
import com.getmaincourse.app.data.session.SessionProvider
import com.getmaincourse.app.data.session.SessionStore
import com.getmaincourse.app.data.session.StoredSession
import java.io.IOException
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var store: FakeSessionStore
    private lateinit var provider: SessionProvider
    private lateinit var events: SessionEvents
    private lateinit var service: FakeService
    private lateinit var cleared: MutableList<String>
    private lateinit var preparedUsers: MutableList<Long>

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        store = FakeSessionStore()
        provider = SessionProvider()
        events = SessionEvents()
        service = FakeService()
        cleared = mutableListOf()
        preparedUsers = mutableListOf()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun validStoredSessionRestoresAndPreparesImages() = runTest(dispatcher) {
        store.value = StoredSession(BASE_URL, session())
        val viewModel = buildViewModel()

        viewModel.restore().join()

        assertEquals(SessionUiState.SignedIn(store.value!!.response), viewModel.state.value)
        assertEquals(store.value!!.response, provider.session.value)
        assertEquals(listOf(USER_ID), preparedUsers)
        assertEquals(emptyList<String>(), cleared)
    }

    @Test
    fun missingExpiredAndWrongOriginSessionsAreCleared() = runTest(dispatcher) {
        val storedSessions = listOf(
            null,
            StoredSession(BASE_URL, session(expiresAt = "2026-09-08T12:00:00Z")),
            StoredSession("https://elsewhere.example/", session()),
        )

        storedSessions.forEach { stored ->
            store.value = stored
            cleared.clear()
            val viewModel = buildViewModel()

            viewModel.restore().join()

            assertEquals(SessionUiState.SignedOut(), viewModel.state.value)
            assertNull(provider.session.value)
            assertNull(store.value)
            assertEquals(listOf("database", "images"), cleared)
        }
    }

    @Test
    fun emailAuthenticationPersistsBeforePublishing() = runTest(dispatcher) {
        val response = session()
        service.signInResponse = response
        service.signUpResponse = response
        val viewModel = buildViewModel()
        viewModel.restore().join()
        cleared.clear()
        var publishedWhileWriting: SessionResponse? = response
        store.onWrite = { publishedWhileWriting = provider.session.value }

        viewModel.signIn(" cook@example.com ", "secret").join()

        assertEquals(SignInRequest("cook@example.com", "secret", "Android"), service.signInRequest)
        assertNull(publishedWhileWriting)
        assertEquals(StoredSession(BASE_URL, response), store.value)
        assertEquals(response, provider.session.value)
        assertEquals(SessionUiState.SignedIn(response), viewModel.state.value)

        viewModel.signOut().join()
        viewModel.signUp(" Cook ", " cook@example.com ", "123456789012", "123456789012").join()

        assertEquals(
            SignUpRequest("Cook", "cook@example.com", "123456789012", "123456789012", "Android"),
            service.signUpRequest,
        )
        assertEquals(SessionUiState.SignedIn(response), viewModel.state.value)
    }

    @Test
    fun badLoginRemainsAFormErrorWithoutClearingProtectedStorage() = runTest(dispatcher) {
        service.signInFailure = ApiFailure(401, "Email or password is incorrect")
        val viewModel = buildViewModel()
        viewModel.restore().join()
        cleared.clear()

        viewModel.signIn("cook@example.com", "wrong").join()

        assertEquals(SessionUiState.SignedOut("Email or password is incorrect"), viewModel.state.value)
        assertNull(provider.session.value)
        assertEquals(emptyList<String>(), cleared)
    }

    @Test
    fun authenticatedExpiryClearsLocalState() = runTest(dispatcher) {
        val response = session()
        store.value = StoredSession(BASE_URL, response)
        val viewModel = buildViewModel()
        viewModel.restore().join()
        cleared.clear()

        events.notifyExpired(response.token)
        advanceUntilIdle()

        assertEquals(SessionUiState.SignedOut(), viewModel.state.value)
        assertNull(store.value)
        assertNull(provider.session.value)
        assertEquals(listOf("database", "images"), cleared)
    }

    @Test
    fun staleTokenExpiryDoesNotClearCurrentSession() = runTest(dispatcher) {
        val response = session(token = "current")
        store.value = StoredSession(BASE_URL, response)
        val viewModel = buildViewModel()
        viewModel.restore().join()
        cleared.clear()

        events.notifyExpired("old")
        advanceUntilIdle()

        assertEquals(SessionUiState.SignedIn(response), viewModel.state.value)
        assertEquals(response, provider.session.value)
        assertEquals(emptyList<String>(), cleared)
    }

    @Test
    fun authenticationIsNotAdmittedWhileExpiryCleanupIsSuspended() = runTest(dispatcher) {
        val cleanupStarted = CompletableDeferred<Unit>()
        val finishCleanup = CompletableDeferred<Unit>()
        store.value = StoredSession(BASE_URL, session())
        val viewModel = buildViewModel(databaseCleanup = {
            cleanupStarted.complete(Unit)
            finishCleanup.await()
            cleared += "database"
        })
        viewModel.restore().join()
        cleared.clear()

        events.notifyExpired(session().token)
        cleanupStarted.await()

        assertEquals(SessionUiState.Restoring, viewModel.state.value)
        val signIn = viewModel.signIn("cook@example.com", "secret")
        runCurrent()
        assertNull(service.signInRequest)
        signIn.join()

        finishCleanup.complete(Unit)
        advanceUntilIdle()

        assertEquals(SessionUiState.SignedOut(), viewModel.state.value)
        assertNull(service.signInRequest)
    }

    @Test
    fun logoutIsBestEffortAndAlwaysClearsLocalState() = runTest(dispatcher) {
        val response = session()
        store.value = StoredSession(BASE_URL, response)
        service.signOutFailure = IOException("offline")
        val viewModel = buildViewModel()
        viewModel.restore().join()
        cleared.clear()

        viewModel.signOut().join()

        assertEquals(1, service.signOutCalls)
        assertEquals(SessionUiState.SignedOut(), viewModel.state.value)
        assertNull(store.value)
        assertNull(provider.session.value)
        assertEquals(listOf("database", "images"), cleared)
    }

    @Test
    fun confirmedAccountDeletionClearsLocalStateWithoutLoggingOut() = runTest(dispatcher) {
        val response = session()
        store.value = StoredSession(BASE_URL, response)
        val viewModel = buildViewModel()
        viewModel.restore().join()
        cleared.clear()

        viewModel.deleteAccount().join()

        assertEquals(1, service.deleteAccountCalls)
        assertEquals(0, service.signOutCalls)
        assertEquals(SessionUiState.SignedOut(), viewModel.state.value)
        assertNull(provider.session.value)
        assertEquals(listOf("database", "images"), cleared)
    }

    @Test
    fun storeClearFailureRequiresCleanupRetryAndNeverRestoresCredential() = runTest(dispatcher) {
        store.value = StoredSession(BASE_URL, session(expiresAt = "2026-09-08T12:00:00Z"))
        store.clearFailure = IOException("disk failed")
        val viewModel = buildViewModel()
        viewModel.restore().join()

        assertEquals(SessionUiState.CleanupError("Could not clear local data"), viewModel.state.value)
        assertEquals(1, store.readCalls)
        viewModel.signIn("cook@example.com", "secret").join()
        viewModel.restore().join()
        assertEquals(1, store.readCalls)
        assertNull(service.signInRequest)
        assertEquals(listOf("database", "images"), cleared)

        store.clearFailure = null
        viewModel.retryCleanup().join()

        assertEquals(SessionUiState.SignedOut(), viewModel.state.value)
        assertNull(store.value)
        assertEquals(1, store.readCalls)
    }

    private fun buildViewModel(
        databaseCleanup: suspend () -> Unit = { cleared += "database" },
    ) = SessionViewModel(
        service = service,
        sessionStore = store,
        sessionProvider = provider,
        sessionEvents = events,
        baseUrl = BASE_URL,
        clock = CLOCK,
        prepareImages = {
            preparedUsers += it
            null
        },
        clearDatabase = databaseCleanup,
        clearImages = { cleared += "images" },
    )

    private class FakeSessionStore : SessionStore {
        var value: StoredSession? = null
        var readCalls = 0
        var clearFailure: Throwable? = null
        var onWrite: () -> Unit = {}

        override suspend fun read(): StoredSession? {
            readCalls += 1
            return value
        }

        override suspend fun write(session: StoredSession) {
            onWrite()
            value = session
        }

        override suspend fun clear() {
            clearFailure?.let { throw it }
            value = null
        }
    }

    private class FakeService : MainCourseService {
        var signInResponse = session()
        var signUpResponse = session()
        var signInFailure: Throwable? = null
        var signOutFailure: Throwable? = null
        var signInRequest: SignInRequest? = null
        var signUpRequest: SignUpRequest? = null
        var signOutCalls = 0
        var deleteAccountCalls = 0

        override suspend fun signIn(request: SignInRequest): SessionResponse {
            signInRequest = request
            signInFailure?.let { throw it }
            return signInResponse
        }

        override suspend fun signUp(request: SignUpRequest): SessionResponse {
            signUpRequest = request
            return signUpResponse
        }

        override suspend fun signOut() {
            signOutCalls += 1
            signOutFailure?.let { throw it }
        }

        override suspend fun deleteAccount() {
            deleteAccountCalls += 1
        }

        override suspend fun cookbooks(): List<Cookbook> = error("Not used")
        override suspend fun recipes(cookbookId: Long): List<RecipeSummary> = error("Not used")
        override suspend fun recipe(cookbookId: Long, recipeId: Long): RecipeDetail = error("Not used")
        override suspend fun moveRecipe(
            cookbookId: Long,
            recipeId: Long,
            request: MoveRecipeRequest,
        ): RecipeDetail = error("Not used")
        override suspend fun deleteRecipe(cookbookId: Long, recipeId: Long) = error("Not used")
        override suspend fun addRecipeIngredients(
            cookbookId: Long,
            request: ShoppingItemsRequest,
        ): List<ShoppingItem> = error("Not used")
        override suspend fun updateAccount(request: AccountUpdateRequest): AccountResponse = error("Not used")
    }

    companion object {
        private const val BASE_URL = "https://app.getmaincourse.com/"
        private const val USER_ID = 7L
        private val CLOCK = Clock.fixed(Instant.parse("2026-09-09T12:00:00Z"), ZoneOffset.UTC)

        private fun session(
            expiresAt: String = "2026-10-09T12:00:00Z",
            token: String = "token",
        ) = SessionResponse(
            token = token,
            expiresAt = expiresAt,
            user = User(USER_ID, "Cook", "cook@example.com", true),
        )
    }
}
