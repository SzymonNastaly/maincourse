package com.getmaincourse.app.features.search

import com.getmaincourse.app.data.model.CoverImages
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeSummary
import com.getmaincourse.app.data.model.StructuredIngredient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeSearchEngineTest {
    private val engine = RecipeSearchEngine()

    @Test
    fun ranksNamesBeforeIngredientsBeforeInstructionsAndRequiresEveryTerm() {
        val documents = listOf(
            document(1, "Tomato soup", ingredients = listOf("basil")),
            document(2, "Garden bowl", ingredients = listOf("tomato basil")),
            document(3, "Weeknight dinner", instructions = listOf("Add tomato and basil")),
            document(4, "Tomato toast", ingredients = listOf("bread")),
        )

        assertEquals(listOf(1L, 2L, 3L), engine.search(documents, "tomato basil").map { it.id })
    }

    @Test
    fun normalizesDiacriticsAndCommonLatinLetters() {
        val documents = listOf(
            document(1, "Łódź æbleskiver", ingredients = listOf("crème fraîche")),
            document(2, "Smørrebrød", instructions = listOf("Straße style")),
        )

        assertEquals(listOf(1L), engine.search(documents, "lodz aebleskiver creme fraiche").map { it.id })
        assertEquals(listOf(2L), engine.search(documents, "smorrebrod strasse").map { it.id })
    }

    @Test
    fun usesPrefixAndBoundedTypoMatchingOnlyWhenDirectMatchesAreAbsent() {
        val documents = listOf(
            document(1, "Tomato soup"),
            document(2, "Tomatillo salad"),
            document(3, "Potato soup"),
        )

        assertEquals(listOf(1L), engine.search(documents, "tomato").map { it.id })
        assertEquals(listOf(2L, 1L), engine.search(documents, "toma").map { it.id })
        assertEquals(listOf(1L), engine.search(documents, "tomto").map { it.id })
        assertTrue(engine.search(documents, "tm").isEmpty())
    }

    @Test
    fun searchesStructuredNamesAndRawFallbackAndExcludesFailedImports() {
        val parsed = document(
            1,
            "Dinner",
            structuredIngredients = listOf(ingredient(name = "aubergine", raw = "1 eggplant")),
        )
        val rawFallback = document(
            2,
            "Lunch",
            structuredIngredients = listOf(ingredient(name = null, raw = "2 scallions")),
        )
        val failed = document(3, "Aubergine failure", importStatus = "failed")
        val pending = document(4, "Aubergine pending", importStatus = "pending", withDetail = false)

        assertEquals(listOf(1L), engine.search(listOf(parsed), "aubergine").map { it.id })
        assertEquals(listOf(2L), engine.search(listOf(rawFallback), "scallions").map { it.id })
        assertEquals(listOf(4L, 1L), engine.search(listOf(parsed, failed, pending), "aubergine").map { it.id })
    }

    @Test
    fun returnsAtMostFiftyResultsWithStableOrdering() {
        val documents = (1L..70L).map { document(it, "Soup ${it.toString().padStart(2, '0')}") }

        assertTrue(engine.search(documents, "   ").isEmpty())
        val results = engine.search(documents.reversed(), "soup")

        assertEquals(50, results.size)
        assertEquals((1L..50L).toList(), results.map { it.id })
    }

    private fun document(
        id: Long,
        name: String,
        ingredients: List<String> = emptyList(),
        structuredIngredients: List<StructuredIngredient> = emptyList(),
        instructions: List<String> = emptyList(),
        importStatus: String = "completed",
        withDetail: Boolean = true,
    ): RecipeSearchDocument {
        val summary = RecipeSummary(
            id = id,
            name = name,
            prepTime = null,
            cookTime = null,
            favorite = false,
            coverImageUrl = null,
            coverImages = null,
            importStatus = importStatus,
            errorMessage = null,
            updatedAt = "2026-09-08T00:00:00Z",
        )
        val detail = if (withDetail) RecipeDetail(
            id = id,
            name = name,
            prepTime = null,
            cookTime = null,
            servings = null,
            favorite = false,
            ingredients = ingredients,
            structuredIngredients = structuredIngredients,
            instructions = instructions,
            notes = null,
            sourceUrl = null,
            tags = emptyList(),
            coverImageUrl = null,
            coverImages = null,
            createdAt = "2026-09-08T00:00:00Z",
            updatedAt = "2026-09-08T00:00:00Z",
        ) else null
        return RecipeSearchDocument(summary, detail)
    }

    private fun ingredient(name: String?, raw: String) = StructuredIngredient(
        id = 1,
        position = 1,
        amount = null,
        amountMax = null,
        unit = null,
        name = name,
        note = null,
        raw = raw,
    )
}
