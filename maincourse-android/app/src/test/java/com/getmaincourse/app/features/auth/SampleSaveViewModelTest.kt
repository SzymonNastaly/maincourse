package com.getmaincourse.app.features.auth

import com.getmaincourse.app.data.model.RecipeSaveResponse
import com.getmaincourse.app.data.onboarding.SampleSaveIntent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SampleSaveViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val users = MutableStateFlow<Long?>(null)
    private var intent: SampleSaveIntent? = null
    private val requests = mutableListOf<SampleSaveIntent>()
    private val dismissed = mutableSetOf<Long>()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun keepIsLocalUntilAuthenticationAndRetryUsesOriginalRequestAndDestination() = runTest(dispatcher) {
        var destination = 10L
        var fail = true
        val model = model(personal = { destination }) {
            requests += it
            if (fail) error("offline")
            RecipeSaveResponse(42, checkNotNull(it.cookbookId))
        }
        runCurrent()
        model.keep()
        val requestId = intent!!.requestId
        assertNull(intent!!.userId)
        assertTrue(requests.isEmpty())
        users.value = 1
        runCurrent()
        assertTrue(model.state.value.failed)
        assertEquals(1L, intent!!.userId)
        assertEquals(10L, intent!!.cookbookId)
        destination = 20
        fail = false
        model.retry()
        runCurrent()
        assertEquals(listOf(requestId, requestId), requests.map { it.requestId })
        assertEquals(listOf(10L, 10L), requests.map { it.cookbookId })
        assertNull(intent)
        assertEquals(RecipeSaveResponse(42, 10), model.state.value.saved)
    }

    @Test
    fun continuingWithoutRecipeClearsAnonymousIntentAndNeverSaves() = runTest(dispatcher) {
        val model = model()
        runCurrent()
        model.keep()
        model.continueWithoutRecipe()
        users.value = 1
        runCurrent()
        assertNull(intent)
        assertTrue(requests.isEmpty())
        assertNull(model.state.value.saved)
    }

    @Test
    fun processRestorationRetainsIdempotencyKeyAndBoundOwner() = runTest(dispatcher) {
        intent = SampleSaveIntent("original", userId = 7, cookbookId = 99)
        model()
        runCurrent()
        assertNotNull(intent)
        users.value = 7
        runCurrent()
        assertEquals("original", requests.single().requestId)
        assertEquals(99L, requests.single().cookbookId)
    }

    @Test
    fun anotherAccountCannotInheritBoundIntentOrLateResult() = runTest(dispatcher) {
        val response = CompletableDeferred<RecipeSaveResponse>()
        val model = model { withContext(NonCancellable) { response.await() } }
        runCurrent()
        model.keep()
        users.value = 1
        runCurrent()
        assertTrue(model.state.value.saving)
        users.value = 2
        runCurrent()
        response.complete(RecipeSaveResponse(42, 10))
        runCurrent()
        assertNull(intent)
        assertNull(model.state.value.saved)
        assertFalse(model.state.value.saving)
    }

    @Test
    fun restoringDifferentAccountOrConfirmedSignOutClearsOldIntent() = runTest(dispatcher) {
        intent = SampleSaveIntent("old", userId = 1, cookbookId = 10)
        val model = model()
        runCurrent()
        users.value = 2
        runCurrent()
        assertNull(intent)
        assertTrue(requests.isEmpty())
        intent = SampleSaveIntent("old", userId = 1)
        model.signedOut()
        assertNull(intent)
    }

    @Test
    fun dismissalsAreAccountScopedAndNewExplicitKeepUsesNewKey() = runTest(dispatcher) {
        val model = model()
        users.value = 1
        runCurrent()
        model.dismissDemo()
        assertTrue(model.state.value.demoDismissed)
        model.keep()
        runCurrent()
        model.keep()
        runCurrent()
        assertNotEquals(requests[0].requestId, requests[1].requestId)
        users.value = 2
        runCurrent()
        assertFalse(model.state.value.demoDismissed)
        assertEquals(setOf(1L), dismissed)
    }

    private fun model(
        personal: suspend () -> Long = { 10 },
        save: suspend (SampleSaveIntent) -> RecipeSaveResponse = {
            requests += it
            RecipeSaveResponse(42, checkNotNull(it.cookbookId))
        },
    ) = SampleSaveViewModel(users, { intent }, { intent = it }, personal, save, {}, { it in dismissed }, { dismissed += it })
}
