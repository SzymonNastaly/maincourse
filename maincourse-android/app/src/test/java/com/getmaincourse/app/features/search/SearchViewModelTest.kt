package com.getmaincourse.app.features.search

import com.getmaincourse.app.data.CookbookSelection
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.RecipeSummary
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
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
    fun preparesSelectedCookbookAndSearchesCurrentQuery() = runTest(dispatcher) {
        val selection = MutableStateFlow(CookbookSelection(listOf(cookbook(10)), 10))
        val prepared = mutableListOf<Long>()
        val searches = mutableListOf<Pair<Long, String>>()
        val results = MutableStateFlow(listOf(recipe(7, "Tomato soup")))
        val viewModel = SearchViewModel(
            observeCookbooks = { selection },
            prepareSearchIndex = { prepared += it },
            searchRecipes = { cookbookId, query ->
                searches += cookbookId to query
                results
            },
        )
        val collection = backgroundScope.launch { viewModel.state.collect() }
        advanceUntilIdle()

        assertEquals(listOf(10L), prepared)
        assertEquals(emptyList<Pair<Long, String>>(), searches)
        assertFalse(viewModel.state.value.preparing)

        viewModel.updateQuery(" tomato ")
        advanceUntilIdle()

        assertEquals(listOf(10L to "tomato"), searches)
        assertEquals(listOf(7L), viewModel.state.value.results.map(RecipeSummary::id))
        assertNull(viewModel.state.value.error)
        collection.cancel()
    }

    @Test
    fun cookbookChangeRebuildsScopeAndKeepsTheQuery() = runTest(dispatcher) {
        val selection = MutableStateFlow(
            CookbookSelection(listOf(cookbook(10), cookbook(20)), 10),
        )
        val prepared = mutableListOf<Long>()
        val searches = mutableListOf<Pair<Long, String>>()
        val viewModel = SearchViewModel(
            observeCookbooks = { selection },
            prepareSearchIndex = { prepared += it },
            searchRecipes = { cookbookId, query ->
                searches += cookbookId to query
                MutableStateFlow(listOf(recipe(cookbookId, "Result")))
            },
        )
        val collection = backgroundScope.launch { viewModel.state.collect() }
        viewModel.updateQuery("salt")
        advanceUntilIdle()

        selection.value = selection.value.copy(selectedId = 20)
        advanceUntilIdle()

        assertEquals(listOf(10L, 20L), prepared)
        assertEquals(listOf(10L to "salt", 20L to "salt"), searches)
        assertEquals(20L, viewModel.state.value.selectedCookbookId)
        assertEquals("Cookbook 20", viewModel.state.value.selectedCookbookName)
        assertEquals(listOf(20L), viewModel.state.value.results.map(RecipeSummary::id))
        collection.cancel()
    }

    @Test
    fun preparationFailureCanBeRetried() = runTest(dispatcher) {
        val selection = MutableStateFlow(CookbookSelection(listOf(cookbook(10)), 10))
        var failure: Throwable? = IOException("offline")
        var attempts = 0
        val viewModel = SearchViewModel(
            observeCookbooks = { selection },
            prepareSearchIndex = {
                attempts += 1
                failure?.let { throw it }
            },
            searchRecipes = { _, _ -> MutableStateFlow(emptyList()) },
        )
        val collection = backgroundScope.launch { viewModel.state.collect() }
        advanceUntilIdle()

        assertEquals("Could not prepare recipe search", viewModel.state.value.error)
        failure = null
        viewModel.retry()
        advanceUntilIdle()

        assertEquals(2, attempts)
        assertNull(viewModel.state.value.error)
        assertFalse(viewModel.state.value.preparing)
        collection.cancel()
    }

    private companion object {
        fun cookbook(id: Long) = Cookbook(id, "Cookbook $id", true, 1, emptyList())

        fun recipe(id: Long, name: String) = RecipeSummary(
            id = id,
            name = name,
            prepTime = null,
            cookTime = null,
            favorite = false,
            coverImageUrl = null,
            coverImages = null,
            importStatus = "completed",
            errorMessage = null,
            updatedAt = "2026-09-11T00:00:00Z",
        )
    }
}
