package com.getmaincourse.app.features.recipes

import com.getmaincourse.app.data.CookbookSelection
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.RecipeImportResponse
import com.getmaincourse.app.data.model.RecipePageContent
import com.getmaincourse.app.data.network.ApiFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeShareViewModelTest {
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
    fun publicUrlWaitsForRenderedContentAndImportsIt() = runTest(dispatcher) {
        val fixture = Fixture(RecipeShareContent.Url("https://example.com/soup"))
        val viewModel = fixture.viewModel()
        advanceUntilIdle()
        val reading = viewModel.state.value.status as RecipeShareStatus.ReadingPage
        val content = RecipePageContent(
            url = "https://example.com/soup",
            jsonLd = listOf("{\"@type\":\"Recipe\"}"),
            html = "<body>Soup</body>",
        )

        viewModel.pageExtractionFinished(reading.requestId, content)
        advanceUntilIdle()

        assertEquals(listOf(10L to content), fixture.contentImports)
        assertTrue(fixture.urlImports.isEmpty())
        assertEquals(RecipeShareStatus.Success, viewModel.state.value.status)
        assertEquals("Home", viewModel.state.value.destinationName)
    }

    @Test
    fun failedRenderingFallsBackToUrlImport() = runTest(dispatcher) {
        val fixture = Fixture(RecipeShareContent.Url("https://example.com/soup"))
        val viewModel = fixture.viewModel()
        advanceUntilIdle()
        val reading = viewModel.state.value.status as RecipeShareStatus.ReadingPage

        viewModel.pageExtractionFinished(reading.requestId, null)
        advanceUntilIdle()

        assertEquals(listOf(10L to "https://example.com/soup"), fixture.urlImports)
        assertEquals(RecipeShareStatus.Success, viewModel.state.value.status)
    }

    @Test
    fun oversizedRenderedPayloadFallsBackToUrlImport() = runTest(dispatcher) {
        val fixture = Fixture(RecipeShareContent.Url("https://example.com/soup")).apply {
            contentFailure = ApiFailure(413, "Payload too large")
        }
        val viewModel = fixture.viewModel()
        advanceUntilIdle()
        val reading = viewModel.state.value.status as RecipeShareStatus.ReadingPage

        viewModel.pageExtractionFinished(
            reading.requestId,
            RecipePageContent(url = reading.url, html = "<body>Soup</body>"),
        )
        advanceUntilIdle()

        assertEquals(listOf(10L to "https://example.com/soup"), fixture.urlImports)
        assertEquals(RecipeShareStatus.Success, viewModel.state.value.status)
    }

    @Test
    fun backendDrivenSocialUrlSkipsWebViewRendering() = runTest(dispatcher) {
        val fixture = Fixture(RecipeShareContent.Url("https://www.instagram.com/reel/abc"))
        val viewModel = fixture.viewModel()

        advanceUntilIdle()

        assertEquals(listOf(10L to "https://www.instagram.com/reel/abc"), fixture.urlImports)
        assertEquals(RecipeShareStatus.Success, viewModel.state.value.status)
    }

    @Test
    fun sharedTextAndImageUseTheirNativeImportPaths() = runTest(dispatcher) {
        val textFixture = Fixture(RecipeShareContent.Text("Soup\n1 onion"))
        val imageFixture = Fixture(RecipeShareContent.Image("content://images/1", "image/png"))

        textFixture.viewModel()
        imageFixture.viewModel()
        advanceUntilIdle()

        assertEquals(listOf(10L to "Soup\n1 onion"), textFixture.textImports)
        assertEquals(listOf(Triple(10L, "content://images/1", "image/png")), imageFixture.imageImports)
    }

    @Test
    fun parsesTheJsonStringReturnedByEvaluateJavascript() {
        val pageJson =
            """{"url":"https://example.com/soup","jsonLd":["{}"],"metaTags":{},"coverImageCandidates":[],"html":"<body>Soup</body>"}"""

        val content = parseRecipePageEvaluation(Json.encodeToString(pageJson))

        assertEquals("https://example.com/soup", content?.url)
        assertEquals(listOf("{}"), content?.jsonLd)
    }

    private class Fixture(private val content: RecipeShareContent) {
        val selection = MutableStateFlow(CookbookSelection(listOf(COOKBOOK), COOKBOOK.id))
        val urlImports = mutableListOf<Pair<Long, String>>()
        val contentImports = mutableListOf<Pair<Long, RecipePageContent>>()
        val textImports = mutableListOf<Pair<Long, String>>()
        val imageImports = mutableListOf<Triple<Long, String, String>>()
        var contentFailure: Throwable? = null

        fun viewModel() = RecipeShareViewModel(
            content = content,
            observeCookbooks = { selection },
            refreshCookbooks = {},
            importUrl = { cookbookId, url ->
                urlImports += cookbookId to url
                RESPONSE
            },
            importContent = { cookbookId, pageContent ->
                contentFailure?.let { throw it }
                contentImports += cookbookId to pageContent
                RESPONSE
            },
            importText = { cookbookId, text ->
                textImports += cookbookId to text
                RESPONSE
            },
            importImage = { cookbookId, uri, mimeType ->
                imageImports += Triple(cookbookId, uri, mimeType)
                RESPONSE
            },
        )
    }

    private companion object {
        val COOKBOOK = Cookbook(10, "Home", true, 0, emptyList())
        val RESPONSE = RecipeImportResponse(8, "pending")
    }
}
