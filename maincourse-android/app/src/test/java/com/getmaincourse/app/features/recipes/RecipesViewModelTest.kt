package com.getmaincourse.app.features.recipes

import com.getmaincourse.app.data.CookbookSelection
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.RecipeDetail
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecipesViewModelTest {
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
    fun cachedRecipesAreEmittedBeforeRefreshCompletes() = runTest(dispatcher) {
        val fixture = RecipesFixture().apply {
            selection.value = CookbookSelection(listOf(cookbook(10)), 10)
            summaries(10).value = listOf(recipeSummary(7))
            suspendRecipeRefresh = true
        }
        val viewModel = fixture.viewModel()
        val collection = backgroundScope.launch { viewModel.state.collect() }

        advanceUntilIdle()

        assertEquals(listOf(7L), viewModel.state.value.recipes.map { it.id })
        assertEquals(true, viewModel.state.value.refreshing)
        collection.cancel()
    }

    @Test
    fun successfulRefreshPublishesRepositoryRows() = runTest(dispatcher) {
        val fixture = RecipesFixture().apply {
            selection.value = CookbookSelection(listOf(cookbook(10)), 10)
            refreshedRecipes = listOf(recipeSummary(8))
        }
        val viewModel = fixture.viewModel()
        val collection = backgroundScope.launch { viewModel.state.collect() }

        advanceUntilIdle()

        assertEquals(listOf(8L), viewModel.state.value.recipes.map { it.id })
        assertFalse(viewModel.state.value.initialLoading)
        assertFalse(viewModel.state.value.refreshing)
        assertNull(viewModel.state.value.error)
        collection.cancel()
    }

    @Test
    fun refreshFailureKeepsCachedRecipesVisible() = runTest(dispatcher) {
        val fixture = RecipesFixture().apply {
            selection.value = CookbookSelection(listOf(cookbook(10)), 10)
            summaries(10).value = listOf(recipeSummary(7))
            refreshFailure = IOException("offline")
        }
        val viewModel = fixture.viewModel()
        val collection = backgroundScope.launch { viewModel.state.collect() }

        viewModel.refresh().join()
        advanceUntilIdle()

        assertEquals(listOf(7L), viewModel.state.value.recipes.map { it.id })
        assertEquals("You're offline", viewModel.state.value.error)
        collection.cancel()
    }

    @Test
    fun cookbookSelectionChangesObservedRecipeScope() = runTest(dispatcher) {
        val fixture = RecipesFixture().apply {
            selection.value = CookbookSelection(listOf(cookbook(10), cookbook(20)), 10)
            summaries(10).value = listOf(recipeSummary(7))
            summaries(20).value = listOf(recipeSummary(9))
        }
        val viewModel = fixture.viewModel()
        val collection = backgroundScope.launch { viewModel.state.collect() }
        advanceUntilIdle()

        viewModel.selectCookbook(20).join()
        advanceUntilIdle()

        assertEquals(20L, viewModel.state.value.selectedCookbookId)
        assertEquals(listOf(9L), viewModel.state.value.recipes.map { it.id })
        collection.cancel()
    }

    @Test
    fun cookbookSelectionFailureIsShownWithoutChangingScope() = runTest(dispatcher) {
        val fixture = RecipesFixture().apply {
            selection.value = CookbookSelection(listOf(cookbook(10), cookbook(20)), 10)
            summaries(10).value = listOf(recipeSummary(7))
            selectionFailure = IOException("offline")
        }
        val viewModel = fixture.viewModel()
        val collection = backgroundScope.launch { viewModel.state.collect() }
        advanceUntilIdle()

        viewModel.selectCookbook(20).join()
        advanceUntilIdle()

        assertEquals(10L, viewModel.state.value.selectedCookbookId)
        assertEquals(listOf(7L), viewModel.state.value.recipes.map { it.id })
        assertEquals("You're offline", viewModel.state.value.error)
        collection.cancel()
    }

    @Test
    fun cookbookSelectionCancellationIsNotReportedAsAnError() = runTest(dispatcher) {
        val fixture = RecipesFixture().apply {
            selection.value = CookbookSelection(listOf(cookbook(10), cookbook(20)), 10)
            selectionFailure = kotlinx.coroutines.CancellationException("cancelled")
        }
        val viewModel = fixture.viewModel()
        val collection = backgroundScope.launch { viewModel.state.collect() }
        advanceUntilIdle()

        val job = viewModel.selectCookbook(20)
        job.join()
        advanceUntilIdle()

        assertTrue(job.isCancelled)
        assertNull(viewModel.state.value.error)
        collection.cancel()
    }

    @Test
    fun emptyCookbookResponseEndsInitialLoading() = runTest(dispatcher) {
        val fixture = RecipesFixture()
        val viewModel = fixture.viewModel()
        val collection = backgroundScope.launch { viewModel.state.collect() }

        advanceUntilIdle()

        assertEquals(emptyList<Cookbook>(), viewModel.state.value.cookbooks)
        assertNull(viewModel.state.value.selectedCookbookId)
        assertFalse(viewModel.state.value.initialLoading)
        collection.cancel()
    }

    @Test
    fun cachedDetailIsShownWithoutFetching() = runTest(dispatcher) {
        val detail = recipeDetail(7)
        val cached = MutableStateFlow<RecipeDetail?>(detail)
        var refreshes = 0
        val viewModel = RecipeDetailViewModel(
            observeDetail = { cached },
            refreshDetail = { refreshes++ },
        )
        val collection = backgroundScope.launch { viewModel.state.collect() }

        advanceUntilIdle()

        assertEquals(detail, viewModel.state.value.recipe)
        assertEquals(0, refreshes)
        assertFalse(viewModel.state.value.loading)
        collection.cancel()
    }

    @Test
    fun missingDetailFetchesOnceAndExplicitRefreshFetchesAgain() = runTest(dispatcher) {
        val cached = MutableStateFlow<RecipeDetail?>(null)
        var refreshes = 0
        val viewModel = RecipeDetailViewModel(
            observeDetail = { cached },
            refreshDetail = {
                refreshes++
                cached.value = recipeDetail(7)
            },
        )
        val collection = backgroundScope.launch { viewModel.state.collect() }

        advanceUntilIdle()
        viewModel.refresh().join()
        advanceUntilIdle()

        assertEquals(2, refreshes)
        assertEquals(7L, viewModel.state.value.recipe?.id)
        collection.cancel()
    }

    private class RecipesFixture {
        val selection = MutableStateFlow(CookbookSelection(emptyList(), null))
        private val recipeFlows = mutableMapOf<Long, MutableStateFlow<List<RecipeSummary>>>()
        var refreshedRecipes: List<RecipeSummary>? = null
        var refreshFailure: Throwable? = null
        var selectionFailure: Throwable? = null
        var suspendRecipeRefresh = false

        fun summaries(cookbookId: Long): MutableStateFlow<List<RecipeSummary>> =
            recipeFlows.getOrPut(cookbookId) { MutableStateFlow(emptyList()) }

        fun viewModel() = RecipesViewModel(
            observeCookbooks = { selection },
            observeRecipes = ::summaries,
            refreshCookbooks = {},
            refreshRecipes = { cookbookId ->
                if (suspendRecipeRefresh) kotlinx.coroutines.awaitCancellation()
                refreshFailure?.let { throw it }
                refreshedRecipes?.let { summaries(cookbookId).value = it }
            },
            selectCookbook = { cookbookId ->
                selectionFailure?.let { throw it }
                selection.value = selection.value.copy(selectedId = cookbookId)
            },
        )
    }

    private companion object {
        fun cookbook(id: Long) = Cookbook(id, "Cookbook $id", true, 1, emptyList())

        fun recipeSummary(id: Long) = RecipeSummary(
            id = id,
            name = "Recipe $id",
            prepTime = null,
            cookTime = null,
            favorite = false,
            coverImageUrl = null,
            coverImages = null,
            importStatus = "completed",
            errorMessage = null,
            updatedAt = "2026-09-09T00:00:00Z",
        )

        fun recipeDetail(id: Long) = RecipeDetail(
            id = id,
            name = "Recipe $id",
            prepTime = null,
            cookTime = null,
            servings = 2,
            favorite = false,
            ingredients = listOf("Salt"),
            structuredIngredients = emptyList(),
            instructions = listOf("Cook"),
            notes = null,
            sourceUrl = null,
            tags = emptyList(),
            coverImageUrl = null,
            coverImages = null,
            createdAt = "2026-09-09T00:00:00Z",
            updatedAt = "2026-09-09T00:00:00Z",
        )
    }
}
