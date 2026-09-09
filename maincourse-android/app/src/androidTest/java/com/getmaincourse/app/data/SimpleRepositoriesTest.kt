package com.getmaincourse.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.getmaincourse.app.data.cache.CookbookEntity
import com.getmaincourse.app.data.cache.MainCourseDatabase
import com.getmaincourse.app.data.cache.RecipeEntity
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeSummary
import com.getmaincourse.app.data.model.ShoppingItemRequest
import com.getmaincourse.app.data.network.MainCourseService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.Dispatcher as MockDispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@RunWith(AndroidJUnit4::class)
class SimpleRepositoriesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: MainCourseDatabase
    private lateinit var server: MockWebServer
    private lateinit var cookbooks: CookbookRepository
    private lateinit var recipes: RecipeRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, MainCourseDatabase::class.java).build()
        server = MockWebServer().apply { start() }
        val json = Json { ignoreUnknownKeys = true }
        val service = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MainCourseService::class.java)
        cookbooks = CookbookRepository(database, service, json)
        recipes = RecipeRepository(database, service, json)
    }

    @After
    fun tearDown() {
        database.close()
        server.shutdown()
    }

    @Test
    fun cookbookRefreshPublishesRowsAndKeepsAValidSelection() = runBlocking {
        server.enqueue(jsonResponse("[${cookbookJson(10)},${cookbookJson(20)}]"))
        val firstEmission = async(Dispatchers.IO) {
            cookbooks.observe(USER_ID).first { it.cookbooks.size == 2 && it.selectedId == 10L }
        }

        cookbooks.refresh(USER_ID)

        assertEquals(CookbookSelection(listOf(cookbook(10), cookbook(20)), 10), firstEmission.await())
        cookbooks.select(USER_ID, 20)
        assertEquals(20L, cookbooks.observe(USER_ID).first().selectedId)

        server.enqueue(jsonResponse("[${cookbookJson(20)},${cookbookJson(30)}]"))
        cookbooks.refresh(USER_ID)
        assertEquals(20L, cookbooks.observe(USER_ID).first().selectedId)

        server.enqueue(jsonResponse("[${cookbookJson(30)}]"))
        cookbooks.refresh(USER_ID)
        assertEquals(30L, cookbooks.observe(USER_ID).first().selectedId)

        server.enqueue(jsonResponse("[]"))
        cookbooks.refresh(USER_ID)
        assertEquals(CookbookSelection(emptyList(), null), cookbooks.observe(USER_ID).first())
    }

    @Test
    fun recipeRefreshesPublishOnlyTheirUserAndCookbookScope() = runBlocking {
        seedCookbook(USER_ID, 10)
        seedCookbook(USER_ID, 20)
        seedCookbook(OTHER_USER_ID, 10)
        server.enqueue(jsonResponse("[${summaryJson(7, "First")}]"))

        recipes.refreshList(USER_ID, 10)

        assertEquals(listOf(summary(7, "First")), recipes.observeSummaries(USER_ID, 10).first())
        assertEquals(emptyList<RecipeSummary>(), recipes.observeSummaries(USER_ID, 20).first())
        assertEquals(emptyList<RecipeSummary>(), recipes.observeSummaries(OTHER_USER_ID, 10).first())

        server.enqueue(jsonResponse(detailJson(7, "Detailed")))
        recipes.refreshDetail(USER_ID, 10, 7)

        assertEquals("Detailed", recipes.observeDetail(USER_ID, 10, 7).first()?.name)
        assertNull(recipes.observeDetail(OTHER_USER_ID, 10, 7).first())
    }

    @Test
    fun malformedCachedJsonMapsToAbsenceUntilRefreshReplacesIt() = runBlocking {
        seedCookbook(USER_ID, 10)
        database.catalogDao().upsertRecipes(
            listOf(RecipeEntity(USER_ID, 10, 7, 0, "{", "{")),
        )

        assertEquals(emptyList<RecipeSummary>(), recipes.observeSummaries(USER_ID, 10).first())
        assertNull(recipes.observeDetail(USER_ID, 10, 7).first())

        server.enqueue(jsonResponse("[${summaryJson(7, "Fresh")}]"))
        recipes.refreshList(USER_ID, 10)
        assertEquals(listOf(summary(7, "Fresh")), recipes.observeSummaries(USER_ID, 10).first())

        server.enqueue(jsonResponse(detailJson(7, "Fresh detail")))
        recipes.refreshDetail(USER_ID, 10, 7)
        assertEquals("Fresh detail", recipes.observeDetail(USER_ID, 10, 7).first()?.name)
    }

    @Test
    fun confirmedMoveUsesPatchDetailWithoutRequiringTargetRefresh() = runBlocking {
        seedCookbook(USER_ID, 10)
        seedCookbook(USER_ID, 20)
        seedRecipe(USER_ID, 10, summary(7, "Source"))
        server.enqueue(jsonResponse(detailJson(7, "Moved")))
        server.enqueue(MockResponse().setResponseCode(500))

        recipes.move(USER_ID, 10, 7, 20)

        assertEquals(1, server.requestCount)
        assertEquals(emptyList<RecipeSummary>(), recipes.observeSummaries(USER_ID, 10).first())
        assertEquals(listOf("Moved"), recipes.observeSummaries(USER_ID, 20).first().map { it.name })
        assertEquals("Moved", recipes.observeDetail(USER_ID, 20, 7).first()?.name)
    }

    @Test
    fun failedMoveLeavesCachedRowsUnchanged() = runBlocking {
        seedCookbook(USER_ID, 10)
        seedCookbook(USER_ID, 20)
        seedRecipe(USER_ID, 10, summary(7, "Source"))
        server.enqueue(MockResponse().setResponseCode(500))

        assertTrue(runCatching { recipes.move(USER_ID, 10, 7, 20) }.isFailure)

        assertEquals(listOf("Source"), recipes.observeSummaries(USER_ID, 10).first().map { it.name })
        assertEquals(emptyList<RecipeSummary>(), recipes.observeSummaries(USER_ID, 20).first())
    }

    @Test
    fun failedDeleteLeavesCachedRowUnchanged() = runBlocking {
        seedCookbook(USER_ID, 10)
        seedRecipe(USER_ID, 10, summary(7, "Cached"))
        server.enqueue(MockResponse().setResponseCode(500))

        assertTrue(runCatching { recipes.delete(USER_ID, 10, 7) }.isFailure)

        assertEquals(listOf("Cached"), recipes.observeSummaries(USER_ID, 10).first().map { it.name })
    }

    @Test
    fun addIngredientsSendsReviewedRowsOnce() = runBlocking {
        server.enqueue(jsonResponse("[]"))
        val rows = listOf(ShoppingItemRequest("stable-id", "Salt", "to taste", null, 7))

        recipes.addIngredients(10, rows)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("10", request.getHeader("X-Cookbook-Id"))
        assertEquals(
            """{"items":[{"client_id":"stable-id","name":"Salt","details":"to taste","checked_at":null,"source_recipe_id":7}]}""",
            request.body.readUtf8(),
        )
        assertEquals(1, server.requestCount)
    }

    @Test
    fun delayedListRefreshCannotOverwriteALaterDelete() = runBlocking {
        seedCookbook(USER_ID, 10)
        seedRecipe(USER_ID, 10, summary(7, "Cached"))
        val refreshRequested = CountDownLatch(1)
        val allowRefreshResponse = CountDownLatch(1)
        val deleteRequested = CountDownLatch(1)
        server.dispatcher = object : MockDispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.method) {
                "GET" -> {
                    refreshRequested.countDown()
                    allowRefreshResponse.await(5, TimeUnit.SECONDS)
                    jsonResponse("[${summaryJson(7, "Stale")}]")
                }
                "DELETE" -> {
                    deleteRequested.countDown()
                    MockResponse().setResponseCode(204)
                }
                else -> MockResponse().setResponseCode(404)
            }
        }

        val refresh = async(Dispatchers.IO) { recipes.refreshList(USER_ID, 10) }
        refreshRequested.await(5, TimeUnit.SECONDS)
        val delete = async(Dispatchers.IO) { recipes.delete(USER_ID, 10, 7) }
        if (deleteRequested.await(500, TimeUnit.MILLISECONDS)) delete.await()
        allowRefreshResponse.countDown()
        refresh.await()
        delete.await()

        assertEquals(emptyList<RecipeSummary>(), recipes.observeSummaries(USER_ID, 10).first())
    }

    private suspend fun seedCookbook(userId: Long, cookbookId: Long) {
        database.catalogDao().upsertCookbooks(
            listOf(CookbookEntity(userId, cookbookId, 0, Json.encodeToString(cookbook(cookbookId)))),
        )
    }

    private suspend fun seedRecipe(userId: Long, cookbookId: Long, recipe: RecipeSummary) {
        database.catalogDao().replaceRecipes(
            userId,
            cookbookId,
            listOf(RecipeEntity(userId, cookbookId, recipe.id, 0, Json.encodeToString(recipe))),
        )
    }

    private fun cookbook(id: Long) = Cookbook(id, "Cookbook $id", id == 10L, 1, emptyList())

    private fun summary(id: Long, name: String) = RecipeSummary(
        id = id,
        name = name,
        prepTime = 10,
        cookTime = 20,
        favorite = false,
        coverImageUrl = null,
        coverImages = null,
        importStatus = "completed",
        errorMessage = null,
        updatedAt = "2026-09-09T08:00:00Z",
    )

    private fun cookbookJson(id: Long) =
        """{"id":$id,"name":"Cookbook $id","personal":${id == 10L},"recipe_count":1,"members":[]}"""

    private fun summaryJson(id: Long, name: String) =
        """{"id":$id,"name":"$name","prep_time":10,"cook_time":20,"favorite":false,"cover_image_url":null,"cover_images":null,"import_status":"completed","error_message":null,"updated_at":"2026-09-09T08:00:00Z"}"""

    private fun detailJson(id: Long, name: String) =
        """{"id":$id,"name":"$name","prep_time":10,"cook_time":20,"servings":2,"favorite":false,"ingredients":[],"structured_ingredients":[],"instructions":[],"notes":null,"source_url":null,"tags":[],"cover_image_url":null,"cover_images":null,"created_at":"2026-09-01T08:00:00Z","updated_at":"2026-09-09T08:00:00Z"}"""

    private fun jsonResponse(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private companion object {
        const val USER_ID = 1L
        const val OTHER_USER_ID = 2L
    }
}
