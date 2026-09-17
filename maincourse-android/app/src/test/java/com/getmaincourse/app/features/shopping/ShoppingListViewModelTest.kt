package com.getmaincourse.app.features.shopping

import com.getmaincourse.app.data.CookbookSelection
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.ShoppingItem
import com.getmaincourse.app.data.model.ShoppingItemRequest
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingListViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val selection = MutableStateFlow(CookbookSelection(listOf(COOKBOOK), COOKBOOK.id))
    private val items = MutableStateFlow(emptyList<ShoppingItem>())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun refreshPublishesCachedItemsThenReplacesThemFromNetwork() = runTest(dispatcher) {
        items.value = listOf(item(1, "Cached"))
        val viewModel = viewModel(
            refreshItems = {
                items.value = listOf(item(2, "Fresh"))
            },
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect() }

        advanceUntilIdle()

        assertEquals(listOf("Fresh"), viewModel.state.value.items.map { it.name })
        assertFalse(viewModel.state.value.refreshing)
    }

    @Test
    fun failedRefreshKeepsCachedItemsAndReportsError() = runTest(dispatcher) {
        items.value = listOf(item(1, "Cached"))
        val viewModel = viewModel(refreshItems = { throw IOException("offline") })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect() }

        advanceUntilIdle()

        assertEquals(listOf("Cached"), viewModel.state.value.items.map { it.name })
        assertEquals("Could not refresh shopping list", viewModel.state.value.error)
    }

    @Test
    fun cookbookChangeRefreshesTheNewShoppingListScope() = runTest(dispatcher) {
        val refreshedCookbooks = mutableListOf<Long>()
        val viewModel = viewModel(refreshItems = { refreshedCookbooks += it })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect() }
        advanceUntilIdle()

        selection.value = CookbookSelection(listOf(COOKBOOK, SHARED_COOKBOOK), SHARED_COOKBOOK.id)
        advanceUntilIdle()

        assertEquals(listOf(COOKBOOK.id, SHARED_COOKBOOK.id), refreshedCookbooks)
    }

    @Test
    fun cookbookConnectionFailureStillRefreshesTheCachedSelectedShoppingList() = runTest(dispatcher) {
        val viewModel = viewModel(
            refreshCookbooks = { throw IOException("connection reset") },
            refreshItems = {
                assertEquals(COOKBOOK.id, it)
                items.value = listOf(item(2, "Fresh"))
            },
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect() }

        advanceUntilIdle()

        assertEquals(listOf("Fresh"), viewModel.state.value.items.map { it.name })
        assertEquals(null, viewModel.state.value.error)
        assertFalse(viewModel.state.value.refreshing)
    }

    @Test
    fun cookbookConnectionFailureWithoutCachedSelectionIsReported() = runTest(dispatcher) {
        selection.value = CookbookSelection(emptyList(), null)
        val viewModel = viewModel(
            refreshCookbooks = { throw java.net.SocketTimeoutException() },
            refreshItems = { error("Must not request an unknown cookbook") },
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect() }

        advanceUntilIdle()

        assertEquals("Connection timed out. Please try again.", viewModel.state.value.error)
        assertFalse(viewModel.state.value.initialLoading)
    }

    @Test
    fun cookbookAuthFailureDoesNotContinueWithCachedSelection() = runTest(dispatcher) {
        val viewModel = viewModel(
            refreshCookbooks = {
                throw retrofit2.HttpException(retrofit2.Response.error<Unit>(
                    401,
                    "{}".toResponseBody(),
                ))
            },
            refreshItems = { error("Must not continue after an authentication failure") },
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect() }

        advanceUntilIdle()

        assertEquals("Could not refresh shopping list", viewModel.state.value.error)
        assertFalse(viewModel.state.value.refreshing)
    }

    @Test
    fun addWaitsForAcknowledgementAndReusesClientIdForAnExplicitRetry() = runTest(dispatcher) {
        val requests = mutableListOf<ShoppingItemRequest>()
        var attempts = 0
        val viewModel = viewModel(
            createItem = { request ->
                requests += request
                attempts += 1
                if (attempts == 1) throw IOException("offline")
                items.value = listOf(item(3, request.name, clientId = request.clientId))
            },
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect() }
        advanceUntilIdle()
        viewModel.updateDraft("  Milk  ")

        viewModel.addItem()!!.join()
        assertTrue(items.value.isEmpty())
        assertEquals("  Milk  ", viewModel.state.value.draft)

        viewModel.addItem()!!.join()
        advanceUntilIdle()

        assertEquals(2, requests.size)
        assertEquals(requests[0].clientId, requests[1].clientId)
        assertEquals("Milk", requests[1].name)
        assertEquals("", viewModel.state.value.draft)
        assertEquals(listOf("Milk"), viewModel.state.value.items.map { it.name })
    }

    @Test
    fun toggleDeleteAndClearOnlyPublishRepositoryAcknowledgements() = runTest(dispatcher) {
        val original = item(1, "Milk")
        val checked = original.copy(checkedAt = "2026-09-11T10:00:00Z")
        items.value = listOf(original, item(2, "Bread"))
        val viewModel = viewModel(
            setChecked = { id, value ->
                assertEquals(1L, id)
                assertTrue(value)
                items.value = listOf(checked, item(2, "Bread"))
            },
            deleteItem = { id -> items.value = items.value.filterNot { it.id == id } },
            clearItems = { items.value = emptyList() },
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect() }
        advanceUntilIdle()

        viewModel.toggleItem(original)!!.join()
        advanceUntilIdle()
        assertEquals(listOf(checked), viewModel.state.value.checkedItems)

        viewModel.deleteItem(checked)!!.join()
        advanceUntilIdle()
        assertEquals(listOf("Bread"), viewModel.state.value.items.map { it.name })

        viewModel.clearItems()!!.join()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.items.isEmpty())
    }

    private fun viewModel(
        refreshCookbooks: suspend () -> Unit = {},
        refreshItems: suspend (Long) -> Unit = {},
        createItem: suspend (ShoppingItemRequest) -> Unit = {},
        setChecked: suspend (Long, Boolean) -> Unit = { _, _ -> },
        deleteItem: suspend (Long) -> Unit = {},
        clearItems: suspend () -> Unit = {},
    ) = ShoppingListViewModel(
        observeCookbooks = { selection },
        observeItems = { items },
        refreshCookbooks = refreshCookbooks,
        refreshItems = refreshItems,
        createItem = { _, item -> createItem(item) },
        setItemChecked = { _, id, checked -> setChecked(id, checked) },
        deleteItem = { _, id -> deleteItem(id) },
        clearItems = { clearItems() },
        newClientId = { "stable-id" },
    )

    private fun item(
        id: Long,
        name: String,
        clientId: String = "client-$id",
    ) = ShoppingItem(
        id = id,
        clientId = clientId,
        name = name,
        details = null,
        checkedAt = null,
        sourceRecipeId = null,
        createdAt = "2026-09-11T09:00:00Z",
        updatedAt = "2026-09-11T09:00:00Z",
    )

    private companion object {
        val COOKBOOK = Cookbook(10, "Home", true, 0, emptyList())
        val SHARED_COOKBOOK = Cookbook(20, "Family", false, 0, emptyList())
    }
}
