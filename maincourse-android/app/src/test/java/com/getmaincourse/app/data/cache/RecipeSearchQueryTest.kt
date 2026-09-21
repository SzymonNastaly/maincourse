package com.getmaincourse.app.data.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecipeSearchQueryTest {
    @Test
    fun buildsSafePrefixTermsFromWordsAndNumbers() {
        assertEquals("tomato* AND soup* AND 2*", RecipeSearchQuery.build(" Tomato-soup 2 "))
        assertEquals(
            "ingredients:tom* AND ingredients:soup*",
            RecipeSearchQuery.build("Tom soup", "ingredients"),
        )
    }

    @Test
    fun removesDiacriticsAndFtsPunctuation() {
        assertEquals("creme* AND brulee*", RecipeSearchQuery.build("Crème brûlée!"))
        assertEquals("quoted*", RecipeSearchQuery.build("\"quoted\"*"))
    }

    @Test
    fun normalizesIndexedTextForThePlatformFtsTokenizer() {
        assertEquals("creme brulee!", RecipeSearchQuery.normalizeIndexedText("Crème BRÛLÉE!"))
    }

    @Test
    fun rejectsQueriesWithoutSearchableTokens() {
        assertNull(RecipeSearchQuery.build("  -!?  "))
    }
}
