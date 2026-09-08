package com.getmaincourse.app.features.onboarding

import com.getmaincourse.app.data.model.AccountUpdateRequest
import com.getmaincourse.app.data.model.Cookbook
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
import com.getmaincourse.app.data.onboarding.OnboardingStore
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingControllerTest {
    @Test
    fun requiredAnswersGateAdvancementWhileDietMayBeEmpty() = runTest {
        val controller = controller()
        controller.restore().join()
        controller.start().join()

        controller.advance().join()
        assertEquals(OnboardingStep.HOUSEHOLD, controller.state.value.step)
        controller.updateHousehold(3).join()
        controller.advance().join()
        assertEquals(OnboardingStep.SAVING, controller.state.value.step)
        controller.advance().join()
        assertEquals(OnboardingStep.SAVING, controller.state.value.step)
        controller.updateSaving("screenshots").join()
        controller.advance().join()
        assertEquals(OnboardingStep.DIET, controller.state.value.step)

        controller.advance().join()

        assertEquals(OnboardingStep.AUTH, controller.state.value.step)
        assertTrue(controller.state.value.diet.isEmpty())
    }

    @Test
    fun onlyServerValuesAreAcceptedAndSelectionsAreSortedUniqueAcrossBack() = runTest {
        val controller = controller()
        controller.restore().join()
        controller.start().join()
        controller.updateHousehold(4).join()
        assertNull(controller.state.value.householdSize)
        controller.updateHousehold(5).join()
        controller.advance().join()
        controller.updateSaving("notes").join()
        controller.updateSaving("screenshots").join()
        controller.updateSaving("unknown").join()
        controller.advance().join()
        controller.updateDiet("vegan").join()
        controller.updateDiet("glutenFree").join()
        controller.updateDiet("unknown").join()
        controller.back().join()

        assertEquals(OnboardingStep.SAVING, controller.state.value.step)
        assertEquals(5, controller.state.value.householdSize)
        assertEquals(listOf("notes", "screenshots"), controller.state.value.saveToday)
        assertEquals(listOf("glutenFree", "vegan"), controller.state.value.diet)
    }

    @Test
    fun acceptsEveryExactServerApprovedChoiceValue() = runTest {
        val savingValues = listOf(
            "screenshots",
            "browser_bookmarks",
            "notes",
            "recipe_apps",
            "cookbooks",
            "dont_save",
        )
        val dietValues = listOf(
            "vegetarian",
            "vegan",
            "glutenFree",
            "pescatarian",
            "halal",
            "kosher",
            "lactoseFree",
        )
        for (householdSize in listOf(1, 2, 3, 5)) {
            val controller = controller()
            controller.restore().join()
            controller.start().join()
            controller.updateHousehold(householdSize).join()
            controller.advance().join()
            savingValues.reversed().forEach { controller.updateSaving(it).join() }
            controller.advance().join()
            dietValues.reversed().forEach { controller.updateDiet(it).join() }

            assertEquals(householdSize, controller.state.value.householdSize)
            assertEquals(savingValues.sorted(), controller.state.value.saveToday)
            assertEquals(dietValues.sorted(), controller.state.value.diet)
        }
    }

    @Test
    fun everyDraftStepAndAuthResumeAfterColdRestart() = runTest {
        val steps = listOf(
            OnboardingStep.WELCOME,
            OnboardingStep.HOUSEHOLD,
            OnboardingStep.SAVING,
            OnboardingStep.DIET,
            OnboardingStep.AUTH,
        )
        steps.forEach { step ->
            val record = draft(step = step)
            val controller = controller(store = FakeStore(record))

            controller.restore().join()

            assertFalse(controller.state.value.isLoading)
            assertEquals(step, controller.state.value.step)
            assertEquals(3, controller.state.value.householdSize)
            assertEquals(listOf("screenshots"), controller.state.value.saveToday)
            assertEquals(listOf("vegan"), controller.state.value.diet)
        }
    }

    @Test
    fun completedRecordRestoresWithoutChoicesOrIdentifier() = runTest {
        val store = FakeStore(completed())
        val controller = controller(store = store)

        controller.restore().join()

        assertEquals(OnboardingStep.COMPLETE, controller.state.value.step)
        assertTrue(controller.state.value.saveToday.isEmpty())
        assertTrue(controller.state.value.diet.isEmpty())
        assertNull(store.value?.deviceId)
    }

    @Test
    fun originMismatchResetsToWelcome() = runTest {
        val controller = controller(store = FakeStore(draft(origin = "https://other.example/")))

        controller.restore().join()

        assertEquals(OnboardingStep.WELCOME, controller.state.value.step)
        assertNull(controller.state.value.householdSize)
    }

    @Test
    fun persistenceFailureCanBeRetriedWithoutLosingTheLatestState() = runTest {
        val store = FakeStore().apply { writeFailure = IOException("private disk detail") }
        val controller = controller(store = store)
        controller.restore().join()

        controller.start().join()
        controller.updateHousehold(3).join()

        assertEquals(3, controller.state.value.householdSize)
        assertEquals("Could not save onboarding progress", controller.state.value.persistenceError)
        assertTrue(controller.state.value.canRetryPersistence)
        store.writeFailure = null
        controller.retryPersistence().join()
        assertNull(controller.state.value.persistenceError)
        assertEquals(3, store.value?.householdSize)
    }

    @Test
    fun retryAfterFailedReadPersistsTheNewLiveDraftInsteadOfReadingDiskAgain() = runTest {
        val store = FakeStore().apply {
            readFailure = IOException("read unavailable")
            writeFailure = IOException("write unavailable")
        }
        val controller = controller(store = store)
        controller.restore().join()
        controller.start().join()
        controller.updateHousehold(3).join()
        store.writeFailure = null

        controller.retryPersistence().join()

        assertEquals(1, store.readCalls)
        assertEquals(3, store.value?.householdSize)
        assertNull(controller.state.value.persistenceError)
    }

    @Test
    fun retryAfterFailedReadPersistsLiveCompletionInsteadOfReopeningOldAuthDraft() = runTest {
        val store = FakeStore(draft(step = OnboardingStep.AUTH)).apply {
            readFailure = IOException("read unavailable")
            writeFailure = IOException("write unavailable")
        }
        val controller = controller(store = store)
        controller.restore().join()
        controller.complete().join()
        store.readFailure = null
        store.writeFailure = null

        controller.retryPersistence().join()

        assertEquals(1, store.readCalls)
        assertEquals(completed(), store.value)
        assertEquals(OnboardingStep.COMPLETE, controller.state.value.step)
    }

    @Test
    fun continueWithoutSavingAllowsProgressAndStopsFurtherWrites() = runTest {
        val store = FakeStore().apply { writeFailure = IOException("disk") }
        val controller = controller(store = store)
        controller.restore().join()
        controller.start().join()
        val failedWrites = store.writeCalls

        controller.continueWithoutSaving().join()
        controller.updateHousehold(2).join()
        controller.advance().join()

        assertEquals(OnboardingStep.SAVING, controller.state.value.step)
        assertNull(controller.state.value.persistenceError)
        assertFalse(controller.state.value.canRetryPersistence)
        assertEquals(failedWrites, store.writeCalls)
    }

    @Test
    fun delayedWritesAreCommittedInActionOrder() = runTest {
        val firstWriteStarted = CompletableDeferred<Unit>()
        val releaseFirstWrite = CompletableDeferred<Unit>()
        val store = FakeStore(draft(step = OnboardingStep.HOUSEHOLD)).apply {
            beforeWrite = { record ->
                if (record.householdSize == 2) {
                    firstWriteStarted.complete(Unit)
                    withContext(NonCancellable) { releaseFirstWrite.await() }
                }
            }
        }
        val controller = controller(store = store)
        controller.restore().join()

        val first = controller.updateHousehold(2)
        firstWriteStarted.await()
        val second = controller.updateHousehold(5)
        runCurrent()
        releaseFirstWrite.complete(Unit)
        first.join()
        second.join()

        assertEquals(listOf(2, 5), store.writes.takeLast(2).map { it.householdSize })
        assertEquals(5, store.value?.householdSize)
    }

    @Test
    fun skipAndExistingAccountPersistCleanCompletion() = runTest {
        listOf<(OnboardingController) -> Unit>(
            { it.skip() },
            { it.existingAccount() },
        ).forEach { action ->
            val store = FakeStore(draft(step = OnboardingStep.SAVING))
            val controller = controller(store = store)
            controller.restore().join()

            action(controller)
            advanceUntilIdle()

            assertEquals(OnboardingStep.COMPLETE, controller.state.value.step)
            assertEquals(completed(), store.value)
        }
    }

    @Test
    fun enteringAuthSubmitsSortedSnapshotAndPreparationReturnsItsIdentifier() = runTest {
        val response = CompletableDeferred<OnboardingResponse>()
        val api = FakeApi().apply { submitBlock = { response.await() } }
        val controller = controller(api = api)
        reachDiet(controller)
        controller.updateDiet("vegan").join()
        controller.updateDiet("glutenFree").join()

        controller.advance().join()
        runCurrent()
        val preparing = async { controller.prepareAuthentication() }
        runCurrent()

        assertEquals(1, api.requests.size)
        assertEquals(
            OnboardingRequest(
                deviceId = DEVICE_ID,
                answers = com.getmaincourse.app.data.model.OnboardingAnswers(
                    householdSize = 3,
                    saveToday = listOf("screenshots"),
                    diet = listOf("glutenFree", "vegan"),
                ),
            ),
            api.requests.single(),
        )
        response.complete(OnboardingResponse(1, DEVICE_ID, api.requests.single().answers))
        assertEquals(DEVICE_ID, preparing.await())
    }

    @Test
    fun restoredAuthDraftWaitsForExplicitAuthenticationPreparationBeforeSubmitting() = runTest {
        val api = FakeApi()
        val controller = controller(
            api = api,
            store = FakeStore(draft(step = OnboardingStep.AUTH)),
        )

        controller.restore().join()
        runCurrent()

        assertTrue(api.requests.isEmpty())
        assertEquals(DEVICE_ID, controller.prepareAuthentication())
        assertEquals(1, api.requests.size)
    }

    @Test
    fun submissionUsesOneFiveSecondBudgetFromEntryToAuthPreparation() = runTest {
        val api = FakeApi().apply { submitBlock = { awaitCancellation() } }
        val controller = controller(api = api)
        reachDiet(controller)
        controller.advance().join()
        runCurrent()
        advanceTimeBy(4_999)

        val preparing = async { controller.prepareAuthentication() }
        runCurrent()
        assertFalse(preparing.isCompleted)
        advanceTimeBy(1)
        runCurrent()

        assertEquals(DEVICE_ID, preparing.await())
        assertEquals(1, api.requests.size)
    }

    @Test
    fun cancellingAuthenticationPreparationCancelsTheCallerButNotOwnedSubmission() = runTest {
        val release = CompletableDeferred<OnboardingResponse>()
        val api = FakeApi().apply { submitBlock = { release.await() } }
        val controller = controller(api = api)
        reachDiet(controller)
        controller.advance().join()
        runCurrent()
        val preparing = async { controller.prepareAuthentication() }
        runCurrent()

        preparing.cancel()
        runCurrent()

        assertTrue(preparing.isCancelled)
        assertEquals(1, api.requests.size)
        release.complete(OnboardingResponse(1, DEVICE_ID, api.requests.single().answers))
        advanceUntilIdle()
        assertEquals(1, api.completedSubmissions)
        assertEquals(DEVICE_ID, controller.prepareAuthentication())
        assertEquals(1, api.requests.size)
    }

    @Test
    fun failedSubmissionRetriesOnlyForANewExplicitAuthenticationAttempt() = runTest {
        var calls = 0
        val api = FakeApi().apply {
            submitBlock = { request ->
                calls++
                if (calls == 1) throw IOException("offline")
                OnboardingResponse(1, request.deviceId, request.answers)
            }
        }
        val controller = controller(api = api)
        reachDiet(controller)
        controller.advance().join()
        advanceUntilIdle()
        assertEquals(1, calls)

        assertEquals(DEVICE_ID, controller.prepareAuthentication())
        assertEquals(2, calls)
        advanceUntilIdle()
        assertEquals(2, calls)
    }

    @Test
    fun failedAuthenticationRetainsIdWhileSuccessConsumesItAndCancelsOldCallback() = runTest {
        val response = CompletableDeferred<OnboardingResponse>()
        val api = FakeApi().apply {
            submitBlock = { request ->
                withContext(NonCancellable) { response.await() }
                OnboardingResponse(1, request.deviceId, request.answers)
            }
        }
        val store = FakeStore()
        val controller = controller(api = api, store = store)
        reachDiet(controller)
        controller.advance().join()
        runCurrent()

        val prepare = async { controller.prepareAuthentication() }
        runCurrent()
        prepare.cancel()
        assertEquals(DEVICE_ID, store.value?.deviceId)

        controller.authenticationSucceeded()
        assertEquals(completed(), store.value)
        response.complete(OnboardingResponse(1, DEVICE_ID, api.requests.single().answers))
        advanceUntilIdle()
        assertEquals(completed(), store.value)
        assertEquals(OnboardingStep.COMPLETE, controller.state.value.step)
    }

    @Test
    fun skipInvalidatesSubmissionSoItsLateCallbackCannotRestoreTheDraft() = runTest {
        val response = CompletableDeferred<OnboardingResponse>()
        val api = FakeApi().apply {
            submitBlock = { request ->
                withContext(NonCancellable) { response.await() }
                OnboardingResponse(1, request.deviceId, request.answers)
            }
        }
        val store = FakeStore()
        val controller = controller(api = api, store = store)
        reachDiet(controller)
        controller.advance().join()
        runCurrent()

        controller.skip().join()
        response.complete(OnboardingResponse(1, DEVICE_ID, api.requests.single().answers))
        advanceUntilIdle()

        assertEquals(completed(), store.value)
        assertEquals(OnboardingStep.COMPLETE, controller.state.value.step)
    }

    @Test
    fun authenticationPreparationDoesNotReturnAnIdentifierConsumedWhileJoining() = runTest {
        val api = FakeApi().apply { submitBlock = { awaitCancellation() } }
        val controller = controller(api = api)
        reachDiet(controller)
        controller.advance().join()
        runCurrent()
        val preparing = async { controller.prepareAuthentication() }
        runCurrent()

        controller.skip().join()

        assertNull(preparing.await())
        assertEquals(OnboardingStep.COMPLETE, controller.state.value.step)
    }

    private suspend fun reachDiet(controller: OnboardingController) {
        controller.restore().join()
        controller.start().join()
        controller.updateHousehold(3).join()
        controller.advance().join()
        controller.updateSaving("screenshots").join()
        controller.advance().join()
    }

    private fun CoroutineScope.controller(
        api: FakeApi = FakeApi(),
        store: FakeStore = FakeStore(),
    ) = OnboardingController(
        store = store,
        api = api,
        origin = ORIGIN,
        scope = this,
        uuidFactory = { DEVICE_ID },
    )

    private class FakeStore(initial: OnboardingRecord? = null) : OnboardingStore {
        var value = initial
        var readFailure: Throwable? = null
        var writeFailure: Throwable? = null
        var readCalls = 0
        var writeCalls = 0
        var beforeWrite: suspend (OnboardingRecord) -> Unit = {}
        val writes = mutableListOf<OnboardingRecord>()

        override suspend fun read(): OnboardingRecord? {
            readCalls++
            readFailure?.let { throw it }
            return value
        }

        override suspend fun write(record: OnboardingRecord) {
            writeCalls++
            beforeWrite(record)
            writeFailure?.let { throw it }
            value = record
            writes += record
        }
    }

    private class FakeApi : MainCourseApi {
        val requests = mutableListOf<OnboardingRequest>()
        var completedSubmissions = 0
        var submitBlock: suspend (OnboardingRequest) -> OnboardingResponse = {
            OnboardingResponse(1, it.deviceId, it.answers)
        }

        override suspend fun submitOnboarding(request: OnboardingRequest): OnboardingResponse {
            requests += request
            return submitBlock(request).also { completedSubmissions++ }
        }

        override suspend fun signIn(request: SignInRequest): SessionResponse = unused()

        override suspend fun signInWithGoogle(request: com.getmaincourse.app.data.model.GoogleSignInRequest): SessionResponse = unused()
        override suspend fun startAppleAuthentication(
            request: com.getmaincourse.app.data.model.AppleAuthenticationStartRequest,
        ): com.getmaincourse.app.data.model.AppleAuthenticationStartResponse = unused()
        override suspend fun exchangeAppleAuthentication(
            request: com.getmaincourse.app.data.model.AppleAuthenticationExchangeRequest,
        ): SessionResponse = unused()
        override suspend fun signUp(request: SignUpRequest): SessionResponse = unused()
        override suspend fun signOut(token: String) = unused<Unit>()
        override suspend fun updateAccount(token: String, request: AccountUpdateRequest): User = unused()
        override suspend fun deleteAccount(token: String) = unused<Unit>()
        override suspend fun cookbooks(token: String): List<Cookbook> = unused()
        override suspend fun recipes(token: String, cookbookId: Long): List<RecipeSummary> = unused()
        override suspend fun recipe(token: String, cookbookId: Long, recipeId: Long): RecipeDetail = unused()
        override suspend fun recipeBatch(token: String, cookbookId: Long, cursor: String?): RecipeBatchResponse = unused()
        override suspend fun updateRecipe(
            token: String,
            cookbookId: Long,
            recipeId: Long,
            request: RecipeUpdateRequest,
        ): RecipeDetail = unused()
        override suspend fun updateRecipeCover(
            token: String,
            cookbookId: Long,
            recipeId: Long,
            image: File,
        ): RecipeDetail = unused()
        override suspend fun moveRecipe(
            token: String,
            sourceCookbookId: Long,
            recipeId: Long,
            targetCookbookId: Long,
        ): RecipeDetail = unused()
        override suspend fun deleteRecipe(token: String, cookbookId: Long, recipeId: Long) = unused<Unit>()
        override suspend fun addRecipeIngredients(
            token: String,
            cookbookId: Long,
            request: ShoppingItemsRequest,
        ): List<ShoppingItem> = unused()

        private fun <T> unused(): T = error("Unused in onboarding controller tests")
    }

    private fun draft(
        origin: String = ORIGIN,
        step: OnboardingStep = OnboardingStep.DIET,
    ) = OnboardingRecord(
        origin = origin,
        deviceId = DEVICE_ID,
        step = step,
        householdSize = 3,
        saveToday = listOf("screenshots"),
        diet = listOf("vegan"),
    )

    private fun completed() = OnboardingRecord(
        origin = ORIGIN,
        deviceId = null,
        step = OnboardingStep.COMPLETE,
        completed = true,
    )

    private companion object {
        const val ORIGIN = "https://app.getmaincourse.com/"
        const val DEVICE_ID = "11111111-2222-3333-4444-555555555555"
    }
}
