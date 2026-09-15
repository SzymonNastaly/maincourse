package com.getmaincourse.app.features.recipes

import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.ShoppingItem
import java.io.IOException
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeActionsViewModelTest {
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
    fun deleteSuccessRemovesCachedRecipe() = runTest(dispatcher) {
        val deletedRows = mutableListOf<Triple<Long, Long, Long>>()
        val viewModel = viewModel(
            deleteRecipe = { deletedRows += Triple(USER_ID, COOKBOOK_ID, RECIPE_ID) },
        )

        viewModel.delete().join()

        assertEquals(listOf(Triple(USER_ID, COOKBOOK_ID, RECIPE_ID)), deletedRows)
        assertEquals(RecipeActionUiState.Succeeded("Recipe deleted"), viewModel.action.value)
    }

    @Test
    fun moveSuccessRefreshesTargetAndConfirms() = runTest(dispatcher) {
        val targets = mutableListOf<Long>()
        val viewModel = viewModel(moveRecipe = targets::add)

        viewModel.moveTo(22).join()

        assertEquals(listOf(22L), targets)
        assertEquals(RecipeActionUiState.Succeeded("Recipe moved"), viewModel.action.value)
    }

    @Test
    fun shoppingSubmissionSendsReviewedRowsOnceAndConfirms() = runTest(dispatcher) {
        val submitted = mutableListOf<List<ShoppingItemInput>>()
        val rows = listOf(ShoppingItemInput("stable", "Salt", null, null, RECIPE_ID))
        val viewModel = viewModel(addIngredients = { items, _ -> submitted += items })

        viewModel.addIngredients(rows).join()

        assertEquals(listOf(rows), submitted)
        assertEquals(RecipeActionUiState.Succeeded("Ingredients added"), viewModel.action.value)
    }

    @Test
    fun shoppingSubmissionCanClearExistingItems() = runTest(dispatcher) {
        var submission: Pair<List<ShoppingItemInput>, Boolean>? = null
        val rows = listOf(ShoppingItemInput("stable", "Salt", null, null, RECIPE_ID))
        val viewModel = viewModel(addIngredients = { items, clearExisting ->
            submission = items to clearExisting
        })

        viewModel.addIngredients(rows, clearExisting = true).join()

        assertEquals(rows to true, submission)
        assertEquals(RecipeActionUiState.Succeeded("Ingredients added"), viewModel.action.value)
    }

    @Test
    fun recipeAdditionNeedsReviewOnlyAfterAnItemIsOlderThan36Hours() = runTest(dispatcher) {
        var now = Instant.parse("2026-09-15T10:00:00Z")
        val shoppingItems = MutableStateFlow(
            listOf(shoppingItem("2026-09-13T22:00:00Z")),
        )
        val viewModel = viewModel(
            shoppingItems = shoppingItems,
            refreshShoppingItems = { shoppingItems.value },
            now = { now },
        )
        val collection = backgroundScope.launch { viewModel.state.collect() }
        advanceUntilIdle()

        assertFalse(viewModel.state.value.shoppingListNeedsReview)

        now = now.plusSeconds(1)

        assertTrue(viewModel.shoppingListNeedsReview())
        collection.cancel()
    }

    @Test
    fun shoppingListReviewWaitsForTheRefreshAttemptToFinish() = runTest(dispatcher) {
        val refreshStarted = CompletableDeferred<Unit>()
        val finishRefresh = CompletableDeferred<Unit>()
        val viewModel = viewModel(
            refreshShoppingItems = {
                refreshStarted.complete(Unit)
                finishRefresh.await()
                emptyList()
            },
        )
        val collection = backgroundScope.launch { viewModel.state.collect() }
        runCurrent()
        refreshStarted.await()

        assertFalse(viewModel.state.value.shoppingListReviewReady)

        finishRefresh.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.shoppingListReviewReady)
        collection.cancel()
    }

    @Test
    fun duplicateTapIsIgnoredWhileAnActionIsRunning() = runTest(dispatcher) {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var deletes = 0
        var moves = 0
        val viewModel = viewModel(
            deleteRecipe = {
                deletes++
                started.complete(Unit)
                finish.await()
            },
            moveRecipe = { moves++ },
        )

        val delete = viewModel.delete()
        runCurrent()
        started.await()
        val duplicate = viewModel.moveTo(22)

        assertSame(delete, duplicate)
        assertEquals(RecipeActionUiState.Running(RecipeAction.DELETE), viewModel.action.value)
        finish.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, deletes)
        assertEquals(0, moves)
    }

    @Test
    fun failureReportsAnErrorAndAllowsADeliberateRetry() = runTest(dispatcher) {
        var attempts = 0
        val viewModel = viewModel(
            deleteRecipe = {
                attempts++
                if (attempts == 1) throw IOException("offline")
            },
        )

        viewModel.delete().join()
        assertEquals(RecipeActionUiState.Failed("You're offline"), viewModel.action.value)

        viewModel.delete().join()
        assertEquals(2, attempts)
        assertEquals(RecipeActionUiState.Succeeded("Recipe deleted"), viewModel.action.value)
    }

    @Test
    fun cancellationReturnsToIdleWithoutAnError() = runTest(dispatcher) {
        val viewModel = viewModel(deleteRecipe = { throw CancellationException("cancelled") })

        viewModel.delete().join()

        assertEquals(RecipeActionUiState.Idle, viewModel.action.value)
    }

    private fun viewModel(
        moveRecipe: suspend (Long) -> Unit = {},
        deleteRecipe: suspend () -> Unit = {},
        addIngredients: suspend (List<ShoppingItemInput>, Boolean) -> Unit = { _, _ -> },
        shoppingItems: MutableStateFlow<List<ShoppingItem>> = MutableStateFlow(emptyList()),
        refreshShoppingItems: suspend () -> List<ShoppingItem> = { emptyList() },
        now: () -> Instant = Instant::now,
    ) = RecipeDetailViewModel(
        observeDetail = { MutableStateFlow<RecipeDetail?>(null) },
        refreshDetail = {},
        moveRecipe = moveRecipe,
        deleteRecipe = deleteRecipe,
        addReviewedIngredients = addIngredients,
        observeShoppingItems = { shoppingItems },
        refreshShoppingItems = refreshShoppingItems,
        now = now,
    )

    private fun shoppingItem(createdAt: String) = ShoppingItem(
        id = 1,
        clientId = "shopping-item",
        name = "Milk",
        details = null,
        checkedAt = null,
        sourceRecipeId = null,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private companion object {
        const val USER_ID = 1L
        const val COOKBOOK_ID = 10L
        const val RECIPE_ID = 7L
    }
}
