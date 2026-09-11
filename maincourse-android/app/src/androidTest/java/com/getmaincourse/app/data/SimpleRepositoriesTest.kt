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
import com.getmaincourse.app.data.model.ShoppingItem
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
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
    private lateinit var shopping: ShoppingListRepository

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
        shopping = ShoppingListRepository(database, service, json)
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
        assertEquals("Detailed", recipes.observeSummaries(USER_ID, 10).first().single().name)
        assertNull(recipes.observeDetail(OTHER_USER_ID, 10, 7).first())
    }

    @Test
    fun recipeListRefreshDoesNotAdvanceDetailCursor() = runBlocking {
        seedCookbook(USER_ID, 10)
        server.enqueue(jsonResponse("[${summaryJson(7, "Fresh")}]"))

        recipes.refreshList(USER_ID, 10)

        assertNull(database.catalogDao().recipeDetailSyncCursor(USER_ID, 10))
    }

    @Test
    fun recipeSearchUsesNameIngredientAndInstructionPrefixesWithinItsScope() = runBlocking {
        seedCookbook(USER_ID, 10)
        seedCookbook(USER_ID, 20)
        seedCookbook(OTHER_USER_ID, 10)
        server.enqueue(
            jsonResponse(
                "[" +
                    summaryJson(7, "Café tomato pasta") + "," +
                    summaryJson(8, "Weeknight bowl") + "," +
                    summaryJson(9, "Broken tomato", "failed") +
                    "]",
            ),
        )
        recipes.refreshList(USER_ID, 10)
        val enrichedResults = async(Dispatchers.IO) {
            recipes.searchSummaries(USER_ID, 10, "cori").first { it.isNotEmpty() }
        }
        server.enqueue(
            jsonResponse(
                batchJson(
                    listOf(
                        detailJson(
                            7,
                            "Café tomato pasta",
                            structuredIngredients =
                                "[{\"id\":1,\"position\":0,\"amount\":null," +
                                    "\"amount_max\":null,\"unit\":null,\"name\":\"Coriander\"," +
                                    "\"note\":null,\"raw\":\"fresh coriander\"}]",
                            instructions = "[\"Simmer gently\"]",
                        ),
                        detailJson(
                            8,
                            "Weeknight bowl",
                            ingredients = "[\"tomatoes\"]",
                            instructions = "[\"Bake until golden\"]",
                        ),
                    ).joinToString(","),
                    "page-1",
                ),
            ),
        )
        server.enqueue(jsonResponse(batchJson()))

        recipes.syncDetails(USER_ID, 10)

        assertEquals(listOf(7L), enrichedResults.await().map { it.id })
        assertEquals(listOf(7L), recipes.searchSummaries(USER_ID, 10, "cafe").first().map { it.id })
        assertEquals(listOf(7L), recipes.searchSummaries(USER_ID, 10, "cori").first().map { it.id })
        assertEquals(listOf(7L), recipes.searchSummaries(USER_ID, 10, "simm").first().map { it.id })
        assertEquals(
            listOf(7L, 8L),
            recipes.searchSummaries(USER_ID, 10, "tom").first().map { it.id },
        )
        assertTrue(recipes.searchSummaries(USER_ID, 10, "broken").first().isEmpty())
        assertTrue(recipes.searchSummaries(USER_ID, 20, "tom").first().isEmpty())
        assertTrue(recipes.searchSummaries(OTHER_USER_ID, 10, "tom").first().isEmpty())
    }

    @Test
    fun searchIndexRebuildsFromRoomAndRecipeDeletionCleansItUp() = runBlocking {
        seedCookbook(USER_ID, 10)
        seedRecipe(USER_ID, 10, summary(7, "Cached noodles"))

        assertTrue(recipes.searchSummaries(USER_ID, 10, "nood").first().isEmpty())

        recipes.rebuildSearchIndex(USER_ID, 10)

        assertEquals(listOf(7L), recipes.searchSummaries(USER_ID, 10, "nood").first().map { it.id })

        database.catalogDao().removeRecipe(USER_ID, 10, 7)

        assertTrue(recipes.searchSummaries(USER_ID, 10, "nood").first().isEmpty())
        assertTrue(database.catalogDao().recipeSearchDocuments(USER_ID, 10).isEmpty())
    }

    @Test
    fun searchDocumentFailureRollsBackRecipeAndIndexReplacementTogether() = runBlocking {
        seedCookbook(USER_ID, 10)
        server.enqueue(jsonResponse("[${summaryJson(7, "Cached")}]"))
        recipes.refreshList(USER_ID, 10)
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_search_document
            BEFORE INSERT ON recipe_search_documents
            WHEN NEW.name = 'Fresh'
            BEGIN
                SELECT RAISE(FAIL, 'forced search document failure');
            END
            """.trimIndent(),
        )
        server.enqueue(jsonResponse("[${summaryJson(8, "Fresh")}]"))

        assertTrue(runCatching { recipes.refreshList(USER_ID, 10) }.isFailure)

        assertEquals(listOf("Cached"), recipes.observeSummaries(USER_ID, 10).first().map { it.name })
        assertEquals(listOf(7L), recipes.searchSummaries(USER_ID, 10, "cach").first().map { it.id })
        assertTrue(recipes.searchSummaries(USER_ID, 10, "fresh").first().isEmpty())
    }

    @Test
    fun detailSyncPagesFromRoomCursorAndUpdatesSummaryWithDetail() = runBlocking {
        seedCookbook(USER_ID, 10)
        seedRecipe(USER_ID, 10, summary(7, "Summary"))
        database.catalogDao().upsertRecipes(
            listOf(RecipeEntity(USER_ID, 10, 8, 1, Json.encodeToString(summary(8, "Peer")))),
        )
        server.enqueue(jsonResponse(batchJson(detailJson(7, "Detailed"), "page-1")))
        server.enqueue(jsonResponse(batchJson(detailJson(8, "Peer detail"), "page-2")))
        server.enqueue(jsonResponse(batchJson()))

        recipes.syncDetails(USER_ID, 10)

        assertEquals("Detailed", recipes.observeDetail(USER_ID, 10, 7).first()?.name)
        assertEquals("Peer detail", recipes.observeDetail(USER_ID, 10, 8).first()?.name)
        assertEquals(
            listOf("Detailed", "Peer detail"),
            recipes.observeSummaries(USER_ID, 10).first().map(RecipeSummary::name),
        )
        assertEquals("page-2", database.catalogDao().recipeDetailSyncCursor(USER_ID, 10))
        val first = server.takeRequest()
        assertEquals("/api/v1/recipes/batch?limit=100", first.path)
        assertEquals("10", first.getHeader("X-Cookbook-Id"))
        assertEquals("/api/v1/recipes/batch?cursor=page-1&limit=100", server.takeRequest().path)
        assertEquals("/api/v1/recipes/batch?cursor=page-2&limit=100", server.takeRequest().path)
    }

    @Test
    fun failedDetailPageKeepsCommittedCursorAndLaterRunResumes() = runBlocking {
        seedCookbook(USER_ID, 10)
        seedRecipe(USER_ID, 10, summary(7, "First"))
        database.catalogDao().upsertRecipes(
            listOf(RecipeEntity(USER_ID, 10, 8, 1, Json.encodeToString(summary(8, "Second")))),
        )
        server.enqueue(jsonResponse(batchJson(detailJson(7, "First detail"), "page-1")))
        server.enqueue(MockResponse().setResponseCode(500))

        assertTrue(runCatching { recipes.syncDetails(USER_ID, 10) }.isFailure)
        assertEquals("page-1", database.catalogDao().recipeDetailSyncCursor(USER_ID, 10))
        assertEquals("First detail", recipes.observeDetail(USER_ID, 10, 7).first()?.name)
        assertNull(recipes.observeDetail(USER_ID, 10, 8).first())
        assertEquals(
            listOf("First detail", "Second"),
            recipes.observeSummaries(USER_ID, 10).first().map(RecipeSummary::name),
        )
        assertEquals("/api/v1/recipes/batch?limit=100", server.takeRequest().path)
        assertEquals("/api/v1/recipes/batch?cursor=page-1&limit=100", server.takeRequest().path)

        server.enqueue(jsonResponse(batchJson(detailJson(8, "Second detail"), "page-2")))
        server.enqueue(jsonResponse(batchJson()))
        recipes.syncDetails(USER_ID, 10)

        assertEquals("Second detail", recipes.observeDetail(USER_ID, 10, 8).first()?.name)
        assertEquals("page-2", database.catalogDao().recipeDetailSyncCursor(USER_ID, 10))
        assertEquals("/api/v1/recipes/batch?cursor=page-1&limit=100", server.takeRequest().path)
        assertEquals("/api/v1/recipes/batch?cursor=page-2&limit=100", server.takeRequest().path)
    }

    @Test
    fun detailSyncSkipsUnknownIncompleteAndStaleRowsButAdvancesCursor() = runBlocking {
        seedCookbook(USER_ID, 10)
        seedRecipe(USER_ID, 10, summary(7, "Current"))
        database.catalogDao().upsertRecipes(
            listOf(
                RecipeEntity(
                    USER_ID,
                    10,
                    8,
                    1,
                    Json.encodeToString(summary(8, "Pending").copy(importStatus = "pending")),
                ),
                RecipeEntity(
                    USER_ID,
                    10,
                    9,
                    2,
                    Json.encodeToString(summary(9, "Failed").copy(importStatus = "failed")),
                ),
                RecipeEntity(
                    USER_ID,
                    10,
                    10,
                    3,
                    Json.encodeToString(summary(10, "Fresh detail")),
                    detailJson(10, "Fresh detail", updatedAt = "2026-09-11T08:00:00Z"),
                ),
            ),
        )
        server.enqueue(
            jsonResponse(
                batchJson(
                    listOf(
                        detailJson(7, "Stale", updatedAt = "2026-09-08T08:00:00Z"),
                        detailJson(8, "Pending detail", updatedAt = "2026-09-10T08:00:00Z"),
                        detailJson(9, "Failed detail", updatedAt = "2026-09-10T08:00:00Z"),
                        detailJson(10, "Older detail", updatedAt = "2026-09-10T08:00:00Z"),
                        detailJson(99, "Unknown", updatedAt = "2026-09-10T08:00:00Z"),
                    ).joinToString(","),
                    "page-1",
                ),
            ),
        )
        server.enqueue(jsonResponse(batchJson()))

        recipes.syncDetails(USER_ID, 10)

        assertNull(recipes.observeDetail(USER_ID, 10, 7).first())
        assertNull(recipes.observeDetail(USER_ID, 10, 8).first())
        assertNull(recipes.observeDetail(USER_ID, 10, 9).first())
        assertEquals("Fresh detail", recipes.observeDetail(USER_ID, 10, 10).first()?.name)
        assertNull(recipes.observeDetail(USER_ID, 10, 99).first())
        assertEquals(
            listOf("Current", "Pending", "Failed", "Fresh detail"),
            recipes.observeSummaries(USER_ID, 10).first().map(RecipeSummary::name),
        )
        assertEquals("page-1", database.catalogDao().recipeDetailSyncCursor(USER_ID, 10))
    }

    @Test
    fun failedDetailPageWriteDoesNotAdvanceItsCursor() = runBlocking {
        seedCookbook(USER_ID, 10)
        seedRecipe(USER_ID, 10, summary(7, "Summary"))
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_detail_sync
            BEFORE UPDATE ON recipes
            WHEN OLD.recipeId = 7
            BEGIN
                SELECT RAISE(FAIL, 'forced detail write failure');
            END
            """.trimIndent(),
        )
        server.enqueue(jsonResponse(batchJson(detailJson(7, "Detailed"), "page-1")))

        assertTrue(runCatching { recipes.syncDetails(USER_ID, 10) }.isFailure)

        assertNull(recipes.observeDetail(USER_ID, 10, 7).first())
        assertEquals("Summary", recipes.observeSummaries(USER_ID, 10).first().single().name)
        assertNull(database.catalogDao().recipeDetailSyncCursor(USER_ID, 10))
    }

    @Test
    fun detailSyncRejectsRepeatedCursorWithoutSavingItsPage() = runBlocking {
        seedCookbook(USER_ID, 10)
        seedRecipe(USER_ID, 10, summary(7, "Summary"))
        server.enqueue(jsonResponse(batchJson(detailJson(7, "Detailed"), "same")))
        server.enqueue(jsonResponse(batchJson(detailJson(7, "Must not save"), "same")))

        assertTrue(runCatching { recipes.syncDetails(USER_ID, 10) }.isFailure)

        assertEquals("Detailed", recipes.observeDetail(USER_ID, 10, 7).first()?.name)
        assertEquals("same", database.catalogDao().recipeDetailSyncCursor(USER_ID, 10))
    }

    @Test
    fun authoritativeListDeletionRemovesItsCachedDetail() = runBlocking {
        seedCookbook(USER_ID, 10)
        database.catalogDao().upsertRecipes(
            listOf(
                RecipeEntity(
                    USER_ID,
                    10,
                    7,
                    0,
                    Json.encodeToString(summary(7, "Summary")),
                    detailJson(7, "Detailed"),
                ),
            ),
        )
        server.enqueue(jsonResponse("[]"))

        recipes.refreshList(USER_ID, 10)

        assertEquals(emptyList<RecipeSummary>(), recipes.observeSummaries(USER_ID, 10).first())
        assertNull(recipes.observeDetail(USER_ID, 10, 7).first())
    }

    @Test
    fun acceptedUrlImportRefreshesTheScopedRecipeCache() = runBlocking {
        seedCookbook(USER_ID, 10)
        server.enqueue(jsonResponse("""{"id":8,"import_status":"pending"}""", 202))
        server.enqueue(jsonResponse("[${summaryJson(8, "Importing...", "pending")}]"))

        val response = recipes.importUrl(USER_ID, 10, "https://example.com/soup")

        assertEquals(8L, response.id)
        assertEquals(listOf(8L), recipes.observeSummaries(USER_ID, 10).first().map { it.id })
        assertTrue(recipes.hasUnsettledImport(10))
        recipes.markImportSettled(10)
        assertFalse(recipes.hasUnsettledImport(10))
        val importRequest = server.takeRequest()
        assertEquals("/api/v1/recipes/import", importRequest.path)
        assertEquals("10", importRequest.getHeader("X-Cookbook-Id"))
        assertEquals("""{"url":"https://example.com/soup"}""", importRequest.body.readUtf8())
    }

    @Test
    fun acceptedTextImportSucceedsWhenItsBestEffortRefreshFails() = runBlocking {
        seedCookbook(USER_ID, 10)
        seedRecipe(USER_ID, 10, summary(7, "Cached"))
        server.enqueue(jsonResponse("""{"id":9,"import_status":"pending"}""", 202))
        server.enqueue(MockResponse().setResponseCode(500))

        val response = recipes.importText(USER_ID, 10, "Soup\n1 onion")

        assertEquals(9L, response.id)
        assertEquals(listOf("Cached"), recipes.observeSummaries(USER_ID, 10).first().map { it.name })
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
    fun confirmedMoveRefreshesTargetAndPreservesPatchDetail() = runBlocking {
        seedCookbook(USER_ID, 10)
        seedCookbook(USER_ID, 20)
        seedRecipe(USER_ID, 10, summary(7, "Source"))
        server.enqueue(jsonResponse(detailJson(7, "Moved")))
        server.enqueue(jsonResponse("[${summaryJson(7, "Refreshed")}]"))

        recipes.move(USER_ID, 10, 7, 20)

        assertEquals(2, server.requestCount)
        assertEquals(emptyList<RecipeSummary>(), recipes.observeSummaries(USER_ID, 10).first())
        assertEquals(listOf("Refreshed"), recipes.observeSummaries(USER_ID, 20).first().map { it.name })
        assertEquals("Moved", recipes.observeDetail(USER_ID, 20, 7).first()?.name)
    }

    @Test
    fun failedTargetRefreshKeepsConfirmedMoveAndPatchDetail() = runBlocking {
        seedCookbook(USER_ID, 10)
        seedCookbook(USER_ID, 20)
        seedRecipe(USER_ID, 10, summary(7, "Source"))
        server.enqueue(jsonResponse(detailJson(7, "Moved")))
        server.enqueue(MockResponse().setResponseCode(500))

        recipes.move(USER_ID, 10, 7, 20)

        assertEquals(2, server.requestCount)
        assertEquals(emptyList<RecipeSummary>(), recipes.observeSummaries(USER_ID, 10).first())
        assertEquals(listOf("Moved"), recipes.observeSummaries(USER_ID, 20).first().map { it.name })
        assertEquals("Moved", recipes.observeDetail(USER_ID, 20, 7).first()?.name)
    }

    @Test
    fun disconnectedTargetRefreshKeepsConfirmedMoveAndPatchDetail() = runBlocking {
        seedCookbook(USER_ID, 10)
        seedCookbook(USER_ID, 20)
        seedRecipe(USER_ID, 10, summary(7, "Source"))
        server.enqueue(jsonResponse(detailJson(7, "Moved")))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))

        recipes.move(USER_ID, 10, 7, 20)

        assertEquals(emptyList<RecipeSummary>(), recipes.observeSummaries(USER_ID, 10).first())
        assertEquals(listOf("Moved"), recipes.observeSummaries(USER_ID, 20).first().map { it.name })
        assertEquals("Moved", recipes.observeDetail(USER_ID, 20, 7).first()?.name)
    }

    @Test
    fun malformedTargetRefreshKeepsConfirmedMoveAndPatchDetail() = runBlocking {
        seedCookbook(USER_ID, 10)
        seedCookbook(USER_ID, 20)
        seedRecipe(USER_ID, 10, summary(7, "Source"))
        server.enqueue(jsonResponse(detailJson(7, "Moved")))
        server.enqueue(jsonResponse("{"))

        recipes.move(USER_ID, 10, 7, 20)

        assertEquals(emptyList<RecipeSummary>(), recipes.observeSummaries(USER_ID, 10).first())
        assertEquals(listOf("Moved"), recipes.observeSummaries(USER_ID, 20).first().map { it.name })
        assertEquals("Moved", recipes.observeDetail(USER_ID, 20, 7).first()?.name)
    }

    @Test
    fun failedTargetCacheWriteKeepsConfirmedMoveAndPatchDetail() = runBlocking {
        seedCookbook(USER_ID, 10)
        seedCookbook(USER_ID, 20)
        seedRecipe(USER_ID, 10, summary(7, "Source"))
        seedRecipe(USER_ID, 20, summary(8, "Existing"))
        database.openHelper.writableDatabase.execSQL(
            """
            CREATE TRIGGER fail_target_refresh
            BEFORE DELETE ON recipes
            WHEN OLD.cookbookId = 20
            BEGIN
                SELECT RAISE(FAIL, 'forced target write failure');
            END
            """.trimIndent(),
        )
        server.enqueue(jsonResponse(detailJson(7, "Moved")))
        server.enqueue(jsonResponse("[${summaryJson(7, "Refreshed")}]"))

        recipes.move(USER_ID, 10, 7, 20)

        assertEquals(emptyList<RecipeSummary>(), recipes.observeSummaries(USER_ID, 10).first())
        assertEquals(
            listOf("Existing", "Moved"),
            recipes.observeSummaries(USER_ID, 20).first().map { it.name },
        )
        assertEquals("Moved", recipes.observeDetail(USER_ID, 20, 7).first()?.name)
    }

    @Test
    fun cancelledTargetRefreshKeepsReconciledMoveAndPropagatesCancellation() = runBlocking {
        seedCookbook(USER_ID, 10)
        seedCookbook(USER_ID, 20)
        seedRecipe(USER_ID, 10, summary(7, "Source"))
        server.enqueue(jsonResponse(detailJson(7, "Moved")))
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        val move = async(Dispatchers.IO) { recipes.move(USER_ID, 10, 7, 20) }
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
        move.cancel()
        runCatching { move.await() }

        assertTrue(move.isCancelled)
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
        seedCookbook(USER_ID, 10)
        server.enqueue(jsonResponse("[${shoppingItemJson(9, "stable-id", "Salt", null)}]", 201))
        val rows = listOf(ShoppingItemRequest("stable-id", "Salt", "to taste", null, 7))

        shopping.create(USER_ID, 10, rows)

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("10", request.getHeader("X-Cookbook-Id"))
        assertEquals(
            """{"items":[{"client_id":"stable-id","name":"Salt","details":"to taste","checked_at":null,"source_recipe_id":7}]}""",
            request.body.readUtf8(),
        )
        assertEquals(1, server.requestCount)
        assertEquals(listOf("Salt"), shopping.observeItems(USER_ID, 10).first().map { it.name })
    }

    @Test
    fun shoppingRefreshIsScopedAndOrdersUncheckedBeforeChecked() = runBlocking {
        seedCookbook(USER_ID, 10)
        seedCookbook(USER_ID, 20)
        seedCookbook(OTHER_USER_ID, 10)
        server.enqueue(
            jsonResponse(
                "[" +
                    shoppingItemJson(2, "bread", "Bread", null) + "," +
                    shoppingItemJson(1, "milk", "Milk", "2026-09-11T10:00:00Z") +
                    "]",
            ),
        )

        shopping.refresh(USER_ID, 10)

        assertEquals(listOf("Bread", "Milk"), shopping.observeItems(USER_ID, 10).first().map { it.name })
        assertEquals(emptyList<ShoppingItem>(), shopping.observeItems(USER_ID, 20).first())
        assertEquals(emptyList<ShoppingItem>(), shopping.observeItems(OTHER_USER_ID, 10).first())
    }

    @Test
    fun failedShoppingMutationsLeaveAcknowledgedCacheUnchanged() = runBlocking {
        seedCookbook(USER_ID, 10)
        server.enqueue(jsonResponse("[${shoppingItemJson(1, "milk", "Milk", null)}]"))
        shopping.refresh(USER_ID, 10)
        server.enqueue(MockResponse().setResponseCode(500))

        assertTrue(runCatching { shopping.setChecked(USER_ID, 10, 1, true) }.isFailure)

        val cached = shopping.observeItems(USER_ID, 10).first().single()
        assertNull(cached.checkedAt)
    }

    @Test
    fun confirmedShoppingMutationsUpdateAndRemoveCachedRows() = runBlocking {
        seedCookbook(USER_ID, 10)
        server.enqueue(jsonResponse("[${shoppingItemJson(1, "milk", "Milk", null)}]"))
        shopping.refresh(USER_ID, 10)
        server.enqueue(jsonResponse(shoppingItemJson(1, "milk", "Milk", "2026-09-11T10:00:00Z")))
        server.enqueue(MockResponse().setResponseCode(204))

        shopping.setChecked(USER_ID, 10, 1, true)
        assertNotNull(shopping.observeItems(USER_ID, 10).first().single().checkedAt)

        shopping.delete(USER_ID, 10, 1)
        assertTrue(shopping.observeItems(USER_ID, 10).first().isEmpty())
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

    private fun summaryJson(id: Long, name: String, importStatus: String = "completed") =
        """{"id":$id,"name":"$name","prep_time":10,"cook_time":20,"favorite":false,"cover_image_url":null,"cover_images":null,"import_status":"$importStatus","error_message":null,"updated_at":"2026-09-09T08:00:00Z"}"""

    private fun detailJson(
        id: Long,
        name: String,
        updatedAt: String = "2026-09-09T08:00:00Z",
        ingredients: String = "[]",
        structuredIngredients: String = "[]",
        instructions: String = "[]",
    ) = """{"id":$id,"name":"$name","prep_time":10,"cook_time":20,"servings":2,"favorite":false,"ingredients":$ingredients,"structured_ingredients":$structuredIngredients,"instructions":$instructions,"notes":null,"source_url":null,"tags":[],"cover_image_url":null,"cover_images":null,"created_at":"2026-09-01T08:00:00Z","updated_at":"$updatedAt"}"""

    private fun batchJson(recipes: String = "", nextCursor: String? = null) =
        """{"recipes":[$recipes],"next_cursor":${nextCursor?.let { "\"$it\"" } ?: "null"}}"""

    private fun shoppingItemJson(id: Long, clientId: String, name: String, checkedAt: String?) =
        """{"id":$id,"client_id":"$clientId","name":"$name","details":null,"checked_at":${checkedAt?.let { "\"$it\"" } ?: "null"},"source_recipe_id":null,"created_at":"2026-09-11T09:00:00Z","updated_at":"2026-09-11T10:00:00Z"}"""

    private fun jsonResponse(body: String, status: Int = 200) = MockResponse()
        .setResponseCode(status)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    private companion object {
        const val USER_ID = 1L
        const val OTHER_USER_ID = 2L
    }
}
