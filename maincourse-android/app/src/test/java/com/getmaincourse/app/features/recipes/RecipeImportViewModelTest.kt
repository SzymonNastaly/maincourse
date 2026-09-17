package com.getmaincourse.app.features.recipes

import com.getmaincourse.app.data.CookbookSelection
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.RecipeImportResponse
import java.io.IOException
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeImportViewModelTest {
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
    fun sharedUrlAndRecipeTextSelectTheMatchingImportMode() = runTest(dispatcher) {
        val fixture = ImportFixture()
        val viewModel = fixture.viewModel()
        val collection = backgroundScope.launch { viewModel.state.collect() }
        advanceUntilIdle()

        viewModel.acceptSharedInput("https://example.com/recipe")
        advanceUntilIdle()
        assertEquals(RecipeImportMode.URL, viewModel.state.value.mode)
        assertEquals("https://example.com/recipe", viewModel.state.value.url)

        viewModel.acceptSharedInput("Tomato soup\n\nIngredients\n2 tomatoes")
        advanceUntilIdle()
        assertEquals(RecipeImportMode.TEXT, viewModel.state.value.mode)
        assertEquals("Tomato soup\n\nIngredients\n2 tomatoes", viewModel.state.value.text)
        collection.cancel()
    }

    @Test
    fun urlImportUsesSelectedCookbookAndPublishesCompletion() = runTest(dispatcher) {
        val fixture = ImportFixture()
        val viewModel = fixture.viewModel()
        val collection = backgroundScope.launch { viewModel.state.collect() }
        advanceUntilIdle()
        viewModel.updateUrl(" https://example.com/soup ")

        viewModel.submit().join()
        advanceUntilIdle()

        assertEquals(listOf(10L to "https://example.com/soup"), fixture.urlImports)
        assertEquals(8L, viewModel.state.value.importedRecipeId)
        assertFalse(viewModel.state.value.importing)
        assertNull(viewModel.state.value.error)
        collection.cancel()
    }

    @Test
    fun textImportRejectsOversizedInputBeforeNetworking() = runTest(dispatcher) {
        val fixture = ImportFixture()
        val viewModel = fixture.viewModel()
        val collection = backgroundScope.launch { viewModel.state.collect() }
        advanceUntilIdle()
        viewModel.setMode(RecipeImportMode.TEXT)
        viewModel.updateText("a".repeat(MAX_RECIPE_IMPORT_TEXT_LENGTH + 1))

        viewModel.submit().join()
        advanceUntilIdle()

        assertTrue(fixture.textImports.isEmpty())
        assertEquals("Recipe text must be 50,000 characters or fewer.", viewModel.state.value.error)
        collection.cancel()
    }

    @Test
    fun networkFailureKeepsDraftAndShowsUsefulError() = runTest(dispatcher) {
        val fixture = ImportFixture().apply { importFailure = IOException("offline") }
        val viewModel = fixture.viewModel()
        val collection = backgroundScope.launch { viewModel.state.collect() }
        advanceUntilIdle()
        viewModel.setMode(RecipeImportMode.TEXT)
        viewModel.updateText("Soup\n1 onion")

        viewModel.submit().join()
        advanceUntilIdle()

        assertEquals("Could not import recipe", viewModel.state.value.error)
        assertEquals("Soup\n1 onion", viewModel.state.value.text)
        assertNull(viewModel.state.value.importedRecipeId)
        collection.cancel()
    }

    @Test
    fun repeatedSubmitWhileImportingStartsOnlyOneRequest() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val fixture = ImportFixture().apply {
            urlImporter = { cookbookId, url ->
                calls += 1
                gate.await()
                urlImports += cookbookId to url
                RecipeImportResponse(8, "pending")
            }
        }
        val viewModel = fixture.viewModel()
        val collection = backgroundScope.launch { viewModel.state.collect() }
        advanceUntilIdle()
        viewModel.updateUrl("https://example.com/soup")

        viewModel.submit()
        viewModel.submit()
        runCurrent()

        assertEquals(1, calls)
        gate.complete(Unit)
        advanceUntilIdle()
        collection.cancel()
    }

    @Test
    fun noCookbookDisablesImportAndRefreshFailureIsVisible() = runTest(dispatcher) {
        val fixture = ImportFixture().apply {
            selection.value = CookbookSelection(emptyList(), null)
            refreshFailure = IOException("offline")
        }
        val viewModel = fixture.viewModel()
        val collection = backgroundScope.launch { viewModel.state.collect() }
        advanceUntilIdle()

        assertFalse(viewModel.state.value.canSubmit)
        assertEquals("Could not load cookbooks", viewModel.state.value.error)
        collection.cancel()
    }

    private class ImportFixture {
        val selection = MutableStateFlow(CookbookSelection(listOf(COOKBOOK), COOKBOOK.id))
        val urlImports = mutableListOf<Pair<Long, String>>()
        val textImports = mutableListOf<Pair<Long, String>>()
        var refreshFailure: Throwable? = null
        var importFailure: Throwable? = null
        var urlImporter: (suspend (Long, String) -> RecipeImportResponse)? = null

        fun viewModel() = RecipeImportViewModel(
            observeCookbooks = { selection },
            refreshCookbooks = { refreshFailure?.let { throw it } },
            selectCookbook = { cookbookId -> selection.value = selection.value.copy(selectedId = cookbookId) },
            importUrl = { cookbookId, url ->
                val importer = urlImporter
                if (importer != null) {
                    importer(cookbookId, url)
                } else {
                    importFailure?.let { throw it }
                    urlImports += cookbookId to url
                    RecipeImportResponse(8, "pending")
                }
            },
            importText = { cookbookId, text ->
                importFailure?.let { throw it }
                textImports += cookbookId to text
                RecipeImportResponse(9, "pending")
            },
        )
    }

    private companion object {
        val COOKBOOK = Cookbook(10, "Home", true, 0, emptyList())
    }
}
