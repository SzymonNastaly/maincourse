package com.getmaincourse.app.features.settings

import com.getmaincourse.app.data.model.AccountAttributes
import com.getmaincourse.app.data.model.AccountResponse
import com.getmaincourse.app.data.model.AccountUpdateRequest
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.CookbookInvitation
import com.getmaincourse.app.data.model.CookbookInvitationAcceptance
import com.getmaincourse.app.data.model.CookbookInvitationPreview
import com.getmaincourse.app.data.model.CreateCookbookRequest
import com.getmaincourse.app.data.model.MoveRecipeRequest
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeDetailBatchResponse
import com.getmaincourse.app.data.model.RecipeImportResponse
import com.getmaincourse.app.data.model.RecipeContentImportRequest
import com.getmaincourse.app.data.model.RecipeSummary
import com.getmaincourse.app.data.model.RecipeTextImportRequest
import com.getmaincourse.app.data.model.RecipeUpdateRequest
import com.getmaincourse.app.data.model.RecipeUrlImportRequest
import okhttp3.MultipartBody
import com.getmaincourse.app.data.model.SessionResponse
import com.getmaincourse.app.data.model.ShoppingItem
import com.getmaincourse.app.data.model.ShoppingItemsRequest
import com.getmaincourse.app.data.model.ShoppingItemUpdateRequest
import com.getmaincourse.app.data.model.SignInRequest
import com.getmaincourse.app.data.model.SignUpRequest
import com.getmaincourse.app.data.model.User
import com.getmaincourse.app.data.network.ApiFailure
import com.getmaincourse.app.data.network.MainCourseService
import com.getmaincourse.app.data.session.SessionProvider
import com.getmaincourse.app.data.session.SessionStore
import com.getmaincourse.app.data.session.StoredSession
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var service: FakeService
    private lateinit var store: FakeSessionStore
    private lateinit var provider: SessionProvider

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        service = FakeService()
        store = FakeSessionStore(StoredSession(BASE_URL, SESSION))
        provider = SessionProvider().apply { set(SESSION) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun profileSaveSendsNameAndReminderValues() = runTest(dispatcher) {
        val viewModel = buildViewModel()

        viewModel.saveProfile(" New name ", remindersEnabled = false).join()

        assertEquals(
            AccountUpdateRequest(AccountAttributes(name = "New name", lifecycleNotificationsEnabled = false)),
            service.updateRequest,
        )
    }

    @Test
    fun profileSavePublishesServerUserOnlyAfterPersistence() = runTest(dispatcher) {
        val updated = USER.copy(name = "Server name", lifecycleNotificationsEnabled = false)
        service.updatedUser = updated
        var publishedWhileWriting: SessionResponse? = SESSION
        store.onWrite = { publishedWhileWriting = provider.session.value }
        val viewModel = buildViewModel()

        viewModel.saveProfile("New name", remindersEnabled = false).join()
        runCurrent()

        assertEquals(SESSION, publishedWhileWriting)
        assertEquals(updated, store.value?.response?.user)
        assertEquals(updated, provider.session.value?.user)
        assertEquals(updated, viewModel.state.value.user)
        assertFalse(viewModel.state.value.saving)
    }

    @Test
    fun httpFailurePreservesAcknowledgedUser() = runTest(dispatcher) {
        service.updateFailure = ApiFailure(422, "Name is invalid")
        val viewModel = buildViewModel()

        viewModel.saveProfile("New name", remindersEnabled = false).join()
        runCurrent()

        assertEquals(SESSION, provider.session.value)
        assertEquals(SESSION, store.value?.response)
        assertEquals(USER, viewModel.state.value.user)
        assertEquals("Name is invalid", viewModel.state.value.error)
    }

    @Test
    fun persistenceFailureKeepsPriorPublishedSession() = runTest(dispatcher) {
        val updated = USER.copy(name = "New name", lifecycleNotificationsEnabled = false)
        service.updatedUser = updated
        store.writeFailure = IOException("disk full")
        val viewModel = buildViewModel()

        viewModel.saveProfile("New name", remindersEnabled = false).join()
        runCurrent()

        assertEquals(1, service.updateCalls)
        assertEquals(SESSION, provider.session.value)
        assertEquals(SESSION, store.value?.response)
        assertEquals(SESSION.copy(user = updated), provider.pendingAcceptedSession.value)
        assertTrue(viewModel.state.value.pendingPersistence)
        assertEquals("Could not save account changes", viewModel.state.value.error)
    }

    @Test
    fun recreatedViewModelRetriesMatchingPendingPersistenceWithoutPatch() = runTest(dispatcher) {
        val updated = USER.copy(name = "New name", lifecycleNotificationsEnabled = false)
        service.updatedUser = updated
        store.writeFailure = IOException("disk full")
        val viewModel = buildViewModel()
        viewModel.saveProfile("New name", remindersEnabled = false).join()

        store.writeFailure = null
        val recreatedViewModel = buildViewModel()
        runCurrent()
        assertTrue(recreatedViewModel.state.value.pendingPersistence)
        recreatedViewModel.saveProfile("New name", remindersEnabled = false).join()
        runCurrent()

        assertEquals(1, service.updateCalls)
        assertEquals(updated, store.value?.response?.user)
        assertEquals(updated, provider.session.value?.user)
        assertEquals(null, provider.pendingAcceptedSession.value)
    }

    @Test
    fun revertingToDisplayedValuesAfterRecreationSendsFreshPatch() = runTest(dispatcher) {
        service.updatedUser = USER.copy(name = "First accepted", lifecycleNotificationsEnabled = false)
        store.writeFailure = IOException("disk full")
        val viewModel = buildViewModel()
        viewModel.saveProfile("First accepted", remindersEnabled = false).join()

        store.writeFailure = null
        service.updatedUser = USER
        val recreatedViewModel = buildViewModel()
        recreatedViewModel.saveProfile(USER.name!!, remindersEnabled = true).join()
        runCurrent()

        assertEquals(2, service.updateCalls)
        assertEquals(
            AccountUpdateRequest(AccountAttributes(name = USER.name, lifecycleNotificationsEnabled = true)),
            service.updateRequest,
        )
        assertEquals(USER, store.value?.response?.user)
        assertEquals(USER, provider.session.value?.user)
        assertEquals(null, provider.pendingAcceptedSession.value)
    }

    @Test
    fun deletionAndSignOutDelegateToTheSessionOwner() = runTest(dispatcher) {
        var deleteCalls = 0
        var signOutCalls = 0
        val viewModel = buildViewModel(
            deleteAccount = { completedJob().also { deleteCalls += 1 } },
            signOut = { completedJob().also { signOutCalls += 1 } },
        )

        viewModel.deleteAccount().join()
        viewModel.signOut().join()

        assertEquals(1, deleteCalls)
        assertEquals(1, signOutCalls)
    }

    private fun buildViewModel(
        deleteAccount: () -> Job = ::completedJob,
        signOut: () -> Job = ::completedJob,
    ) = SettingsViewModel(
        service = service,
        sessionStore = store,
        sessionProvider = provider,
        deleteAccount = deleteAccount,
        signOut = signOut,
    )

    private fun completedJob() = Job().apply { complete() }

    private class FakeSessionStore(var value: StoredSession?) : SessionStore {
        var writeFailure: Throwable? = null
        var onWrite: () -> Unit = {}

        override suspend fun read(): StoredSession? = value

        override suspend fun write(session: StoredSession) {
            onWrite()
            writeFailure?.let { throw it }
            value = session
        }

        override suspend fun clear() {
            value = null
        }
    }

    private class FakeService : MainCourseService {
        var updatedUser: User = USER
        var updateRequest: AccountUpdateRequest? = null
        var updateFailure: Throwable? = null
        var updateCalls = 0

        override suspend fun updateAccount(request: AccountUpdateRequest): AccountResponse {
            updateCalls += 1
            updateRequest = request
            updateFailure?.let { throw it }
            return AccountResponse(updatedUser)
        }

        override suspend fun signIn(request: SignInRequest): SessionResponse = error("Not used")
        override suspend fun signUp(request: SignUpRequest): SessionResponse = error("Not used")
        override suspend fun signOut() = error("Not used")
        override suspend fun cookbooks(): List<Cookbook> = error("Not used")
        override suspend fun createCookbook(request: CreateCookbookRequest): Cookbook = error("Not used")
        override suspend fun deleteCookbook(cookbookId: Long) = error("Not used")
        override suspend fun leaveCookbook(cookbookId: Long) = error("Not used")
        override suspend fun createCookbookInvitation(cookbookId: Long): CookbookInvitation = error("Not used")
        override suspend fun cookbookInvitation(token: String): CookbookInvitationPreview = error("Not used")
        override suspend fun acceptCookbookInvitation(token: String): CookbookInvitationAcceptance = error("Not used")
        override suspend fun rejectCookbookInvitation(token: String) = error("Not used")
        override suspend fun recipes(cookbookId: Long): List<RecipeSummary> = error("Not used")
        override suspend fun recipe(cookbookId: Long, recipeId: Long): RecipeDetail = error("Not used")
        override suspend fun recipeDetails(
            cookbookId: Long,
            cursor: String?,
            limit: Int,
        ): RecipeDetailBatchResponse = error("Not used")
        override suspend fun importRecipe(
            cookbookId: Long,
            request: RecipeUrlImportRequest,
        ): RecipeImportResponse = error("Not used")
        override suspend fun importRecipeContent(
            cookbookId: Long,
            request: RecipeContentImportRequest,
        ): RecipeImportResponse = error("Not used")
        override suspend fun importRecipeText(
            cookbookId: Long,
            request: RecipeTextImportRequest,
        ): RecipeImportResponse = error("Not used")
        override suspend fun importRecipeImage(
            cookbookId: Long,
            image: MultipartBody.Part,
        ): RecipeImportResponse = error("Not used")
        override suspend fun moveRecipe(
            cookbookId: Long,
            recipeId: Long,
            request: MoveRecipeRequest,
        ): RecipeDetail = error("Not used")
        override suspend fun updateRecipe(
            cookbookId: Long,
            recipeId: Long,
            request: RecipeUpdateRequest,
        ): RecipeDetail = error("Not used")
        override suspend fun updateRecipeCoverImage(
            cookbookId: Long,
            recipeId: Long,
            coverImage: MultipartBody.Part,
        ): RecipeDetail = error("Not used")
        override suspend fun deleteRecipe(cookbookId: Long, recipeId: Long) = error("Not used")
        override suspend fun shoppingListItems(cookbookId: Long): List<ShoppingItem> = error("Not used")
        override suspend fun createShoppingItems(
            cookbookId: Long,
            request: ShoppingItemsRequest,
        ): List<ShoppingItem> = error("Not used")
        override suspend fun updateShoppingItem(
            cookbookId: Long,
            itemId: Long,
            request: ShoppingItemUpdateRequest,
        ): ShoppingItem = error("Not used")
        override suspend fun deleteShoppingItem(cookbookId: Long, itemId: Long) = error("Not used")
        override suspend fun clearShoppingItems(cookbookId: Long) = error("Not used")
        override suspend fun deleteAccount() = error("Not used")
    }

    private companion object {
        const val BASE_URL = "https://app.getmaincourse.com/"
        val USER = User(7, "Cook", "cook@example.com", true)
        val SESSION = SessionResponse("token", "2026-12-09T12:00:00Z", USER)
    }
}
