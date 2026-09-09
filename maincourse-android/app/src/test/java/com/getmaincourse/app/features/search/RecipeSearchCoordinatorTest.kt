package com.getmaincourse.app.features.search

import com.getmaincourse.app.data.cache.RecipeScope
import com.getmaincourse.app.data.model.RecipeSummary
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeSearchCoordinatorTest {
    @Test
    fun aNewQueryClearsOldResultsAndRejectsLateScoring() = runTest {
        val firstLoadStarted = CompletableDeferred<Unit>()
        val releaseFirstLoad = CompletableDeferred<Unit>()
        var loads = 0
        val coordinator = RecipeSearchCoordinator(
            scope = this,
            loadDocuments = {
                loads++
                if (loads == 1) {
                    firstLoadStarted.complete(Unit)
                    withContext(NonCancellable) { releaseFirstLoad.await() }
                    listOf(document(1, "Soup"))
                } else {
                    listOf(document(2, "Salad"))
                }
            },
            searchDispatcher = StandardTestDispatcher(testScheduler),
        )
        coordinator.activate(RecipeScope(1, 10))
        coordinator.updateQuery("soup")
        firstLoadStarted.await()

        val newer = coordinator.updateQuery("salad")
        assertTrue(coordinator.state.value.results.isEmpty())
        runCurrent()
        releaseFirstLoad.complete(Unit)
        newer.join()
        runCurrent()

        assertEquals("salad", coordinator.state.value.query)
        assertEquals(listOf(2L), coordinator.state.value.results.map { it.id })
    }

    @Test
    fun scopeChangeClearsImmediatelyAndRestoresOnlyThatScopesQuery() = runTest {
        val first = RecipeScope(1, 10)
        val second = RecipeScope(1, 20)
        val coordinator = RecipeSearchCoordinator(
            scope = this,
            loadDocuments = { scope ->
                if (scope == first) listOf(document(1, "Soup")) else listOf(document(2, "Salad"))
            },
            searchDispatcher = StandardTestDispatcher(testScheduler),
        )
        coordinator.activate(first)
        assertEquals(first, coordinator.state.value.scope)
        coordinator.updateQuery("soup").join()
        assertEquals(listOf(1L), coordinator.state.value.results.map { it.id })

        coordinator.activate(second)
        assertEquals(second, coordinator.state.value.scope)
        assertEquals("", coordinator.state.value.query)
        assertTrue(coordinator.state.value.results.isEmpty())
        coordinator.updateQuery("salad").join()

        val restored = coordinator.activate(first)
        assertEquals(first, coordinator.state.value.scope)
        assertEquals("soup", coordinator.state.value.query)
        assertTrue(coordinator.state.value.results.isEmpty())
        restored.join()
        assertEquals(listOf(1L), coordinator.state.value.results.map { it.id })
    }

    @Test
    fun scoringIsDispatchedOffTheCallingContext() = runTest {
        val dispatched = AtomicBoolean(false)
        val worker = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        val recordingDispatcher = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) {
                worker.dispatch(context) {
                    dispatched.set(true)
                    block.run()
                }
            }
        }
        try {
            val coordinator = RecipeSearchCoordinator(
                scope = this,
                loadDocuments = { listOf(document(1, "Soup")) },
                searchDispatcher = recordingDispatcher,
            )
            coordinator.activate(RecipeScope(1, 10))

            coordinator.updateQuery("soup").join()

            assertTrue(dispatched.get())
            assertEquals(listOf(1L), coordinator.state.value.results.map { it.id })
        } finally {
            worker.close()
        }
    }

    @Test
    fun inFlightAndCacheReadFailureAreDistinctFromACompletedEmptySearch() = runTest {
        val release = CompletableDeferred<Unit>()
        val coordinator = RecipeSearchCoordinator(
            scope = this,
            loadDocuments = {
                release.await()
                error("cache unavailable")
            },
            searchDispatcher = StandardTestDispatcher(testScheduler),
        )
        coordinator.activate(RecipeScope(1, 10))

        val search = coordinator.updateQuery("soup")
        runCurrent()
        assertTrue(coordinator.state.value.isSearching)
        assertEquals(null, coordinator.state.value.searchError)

        release.complete(Unit)
        search.join()
        assertEquals(false, coordinator.state.value.isSearching)
        assertEquals("Search is unavailable. Try again.", coordinator.state.value.searchError)
    }

    private fun document(id: Long, name: String) = RecipeSearchDocument(
        RecipeSummary(id, name, null, null, false, null, null, "completed", null, "2026-09-08T00:00:00Z"),
        null,
    )
}
