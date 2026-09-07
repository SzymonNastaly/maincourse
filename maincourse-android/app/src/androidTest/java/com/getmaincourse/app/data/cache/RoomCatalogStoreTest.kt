package com.getmaincourse.app.data.cache

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.CookbookMember
import com.getmaincourse.app.data.model.CoverImages
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeSummary
import com.getmaincourse.app.data.model.RecipeTag
import com.getmaincourse.app.data.model.StructuredIngredient
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RoomCatalogStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var databaseName: String
    private lateinit var database: MainCourseDatabase
    private lateinit var store: CatalogStore

    @Before
    fun setUp() {
        databaseName = "catalog-${UUID.randomUUID()}.db"
        database = MainCourseDatabase.open(context, databaseName)
        store = RoomCatalogStore(database)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun scopesSameIdentifiersByUserAndCookbookWithoutRewritingImagePaths() = runBlocking {
        store.replaceCookbooks(1, listOf(cookbook(10), cookbook(20)))
        store.replaceCookbooks(2, listOf(cookbook(10, "Shared for user two")))

        val first = summary(7, "User one", coverPath = "/rails/active_storage/user-one")
        val secondCookbook = summary(7, "Other cookbook", coverPath = "images/other.jpg")
        val secondUser = summary(7, "User two", coverPath = "/rails/active_storage/user-two")
        store.replaceRecipes(RecipeScope(1, 10), listOf(first))
        store.replaceRecipes(RecipeScope(1, 20), listOf(secondCookbook))
        store.replaceRecipes(RecipeScope(2, 10), listOf(secondUser))

        assertEquals(listOf(first), store.recipes(RecipeScope(1, 10)).items)
        assertEquals(listOf(secondCookbook), store.recipes(RecipeScope(1, 20)).items)
        assertEquals(listOf(secondUser), store.recipes(RecipeScope(2, 10)).items)
    }

    @Test
    fun authoritativeEmptyReplacementPrunesOnlyItsScopeAndRecordsThatItWasFetched() = runBlocking {
        val firstScope = RecipeScope(1, 10)
        val otherUserScope = RecipeScope(2, 10)
        store.replaceCookbooks(1, listOf(cookbook(10)))
        store.replaceCookbooks(2, listOf(cookbook(10)))
        assertFalse(store.recipes(firstScope).fetched)

        val recipeA = summary(1, "A")
        val recipeB = summary(2, "B")
        val otherUserRecipe = summary(1, "Other user")
        store.replaceRecipes(firstScope, listOf(recipeA, recipeB))
        store.saveDetail(firstScope, detail(1, "A", "2026-01-01T00:00:00Z"))
        store.replaceRecipes(otherUserScope, listOf(otherUserRecipe))
        store.saveDetail(otherUserScope, detail(1, "Other user", "2026-02-01T00:00:00Z"))

        store.replaceRecipes(firstScope, emptyList())

        assertEquals(CachedRecipes(emptyList(), fetched = true), store.recipes(firstScope))
        assertNull(store.detail(firstScope, recipeA.id))
        assertEquals(listOf(otherUserRecipe), store.recipes(otherUserScope).items)
        assertEquals("Other user", store.detail(otherUserScope, otherUserRecipe.id)?.name)
    }

    @Test
    fun summaryReplacementPreservesRetainedDetailFreshnessAndDetailCannotInventSummary() = runBlocking {
        val scope = RecipeScope(1, 10)
        store.replaceCookbooks(1, listOf(cookbook(10)))
        val oldSummary = summary(1, "Old summary", updatedAt = "2026-04-01T00:00:00Z")
        val peer = summary(2, "Peer")
        val savedDetail = detail(1, "Detailed name", "2026-03-01T00:00:00Z")
        store.replaceRecipes(scope, listOf(oldSummary, peer))
        store.saveDetail(scope, savedDetail)

        val freshSummary = summary(1, "Fresh summary", updatedAt = "2026-05-01T00:00:00Z")
        store.replaceRecipes(scope, listOf(freshSummary, peer))

        assertEquals(listOf(freshSummary, peer), store.recipes(scope).items)
        assertEquals(savedDetail, store.detail(scope, 1))
        store.saveDetail(scope, detail(99, "Missing", "2026-06-01T00:00:00Z"))
        assertNull(store.detail(scope, 99))
        assertEquals(listOf(freshSummary, peer), store.recipes(scope).items)
    }

    @Test
    fun cookbookReplacementPrunesLostMembershipDataAndSelection() = runBlocking {
        val removedScope = RecipeScope(1, 10)
        val retainedScope = RecipeScope(1, 20)
        store.replaceCookbooks(1, listOf(cookbook(10), cookbook(20)))
        store.selectCookbook(1, 10)
        store.replaceRecipes(removedScope, listOf(summary(1, "Removed")))
        store.saveDetail(removedScope, detail(1, "Removed", "2026-01-01T00:00:00Z"))
        store.replaceRecipes(retainedScope, listOf(summary(1, "Retained")))

        store.replaceCookbooks(1, listOf(cookbook(20)))

        assertEquals(listOf(cookbook(20)), store.cookbooks(1))
        assertNull(store.selectedCookbookId(1))
        assertEquals(CachedRecipes(emptyList(), fetched = false), store.recipes(removedScope))
        assertNull(store.detail(removedScope, 1))
        assertEquals(listOf(summary(1, "Retained")), store.recipes(retainedScope).items)
    }

    @Test
    fun directRemovalPrunesRecipeDetailAndCookbookSelectionSafely() = runBlocking {
        val firstScope = RecipeScope(1, 10)
        val secondScope = RecipeScope(1, 20)
        store.replaceCookbooks(1, listOf(cookbook(10), cookbook(20)))
        store.selectCookbook(1, 10)
        store.replaceRecipes(firstScope, listOf(summary(1, "One"), summary(2, "Two")))
        store.saveDetail(firstScope, detail(1, "One", "2026-01-01T00:00:00Z"))
        store.replaceRecipes(secondScope, listOf(summary(1, "Other cookbook")))

        store.removeRecipe(firstScope, 1)

        assertEquals(listOf(summary(2, "Two")), store.recipes(firstScope).items)
        assertNull(store.detail(firstScope, 1))
        assertEquals(listOf(summary(1, "Other cookbook")), store.recipes(secondScope).items)

        store.removeCookbook(firstScope)

        assertNull(store.selectedCookbookId(1))
        assertEquals(listOf(cookbook(20)), store.cookbooks(1))
        assertEquals(CachedRecipes(emptyList(), fetched = false), store.recipes(firstScope))
    }

    @Test
    fun selectionAndCachedContentSurviveDatabaseReopen() = runBlocking {
        val scope = RecipeScope(1, 10)
        val savedSummary = summary(3, "Persistent")
        val savedDetail = detail(3, "Persistent", "2026-07-01T00:00:00Z")
        store.replaceCookbooks(1, listOf(cookbook(10)))
        store.selectCookbook(1, 10)
        store.replaceRecipes(scope, listOf(savedSummary))
        store.saveDetail(scope, savedDetail)

        database.close()
        database = MainCourseDatabase.open(context, databaseName)
        store = RoomCatalogStore(database)

        assertEquals(10L, store.selectedCookbookId(1))
        assertEquals(CachedRecipes(listOf(savedSummary), fetched = true), store.recipes(scope))
        assertEquals(savedDetail, store.detail(scope, 3))
    }

    @Test
    fun clearRemovesAllUsersAndFetchMetadata() = runBlocking {
        store.replaceCookbooks(1, listOf(cookbook(10)))
        store.replaceCookbooks(2, listOf(cookbook(10)))
        store.replaceRecipes(RecipeScope(1, 10), emptyList())
        store.selectCookbook(2, 10)

        store.clear()

        assertTrue(store.cookbooks(1).isEmpty())
        assertTrue(store.cookbooks(2).isEmpty())
        assertFalse(store.recipes(RecipeScope(1, 10)).fetched)
        assertNull(store.selectedCookbookId(2))
    }

    private fun cookbook(id: Long, name: String = "Cookbook $id") = Cookbook(
        id = id,
        name = name,
        personal = id == 10L,
        recipeCount = 2,
        members = listOf(CookbookMember(id = 4, email = "member@example.com", role = "member")),
    )

    private fun summary(
        id: Long,
        name: String,
        updatedAt: String = "2026-01-01T00:00:00Z",
        coverPath: String? = null,
    ) = RecipeSummary(
        id = id,
        name = name,
        prepTime = 10,
        cookTime = 20,
        favorite = false,
        coverImageUrl = coverPath,
        coverImages = coverPath?.let { CoverImages(thumb = "$it/thumb", card = it, hero = null) },
        importStatus = "completed",
        errorMessage = null,
        updatedAt = updatedAt,
    )

    private fun detail(id: Long, name: String, updatedAt: String) = RecipeDetail(
        id = id,
        name = name,
        prepTime = 10,
        cookTime = 20,
        servings = 4,
        favorite = false,
        ingredients = listOf("1 egg"),
        structuredIngredients = listOf(
            StructuredIngredient(
                id = 8,
                position = 1,
                amount = "1",
                amountMax = null,
                unit = null,
                name = "egg",
                note = null,
                raw = "1 egg",
            ),
        ),
        instructions = listOf("Cook it"),
        notes = "Saved",
        sourceUrl = null,
        tags = listOf(RecipeTag(id = 5, name = "Dinner")),
        coverImageUrl = "/rails/active_storage/detail",
        coverImages = CoverImages(thumb = "images/thumb.jpg", card = null, hero = "images/hero.jpg"),
        createdAt = "2025-01-01T00:00:00Z",
        updatedAt = updatedAt,
    )
}
