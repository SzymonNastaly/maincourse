package com.getmaincourse.app.features.recipes

import com.getmaincourse.app.data.cache.RecipeScope
import com.getmaincourse.app.data.images.PreparedRecipeImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeUiSavedStateTest {
    @Test
    fun editorPayloadRoundTripsRowsPhotoAndInterruptedMarkerOnlyForItsExactScope() {
        val payload = RecipeEditorSavedState(
            userId = 7,
            cookbookId = 2,
            recipeId = 9,
            editorId = "editor-a",
            original = values("Soup"),
            values = values("Changed soup"),
            stagedImage = PreparedRecipeImage("/private/staged.jpg", 7, "staged"),
            operationInterrupted = true,
        )

        val encoded = RecipeUiSavedStateCodec.encodeEditor(payload)

        assertEquals(payload, RecipeUiSavedStateCodec.decodeEditor(encoded, RecipeScope(7, 2), 9))
        assertNull(RecipeUiSavedStateCodec.decodeEditor(encoded, RecipeScope(8, 2), 9))
        assertNull(RecipeUiSavedStateCodec.decodeEditor(encoded, RecipeScope(7, 3), 9))
        assertNull(RecipeUiSavedStateCodec.decodeEditor(encoded, RecipeScope(7, 2), 10))
        assertNull(RecipeUiSavedStateCodec.decodeEditor(encoded, RecipeScope(7, 2), 9, "editor-b"))
        assertTrue(payload.operationInterrupted)
    }

    @Test
    fun detailPayloadRoundTripsPortionsCookingAndStableReviewIdsOnlyForItsExactScope() {
        val review = IngredientReview(
            recipeId = 9,
            items = listOf(IngredientReviewItem("stable-id", "onion", "2", 9, included = false)),
        )
        val payload = RecipeDetailSavedState(
            7, 2, 9, portions = 6, cooking = true, review = review, reviewSubmissionInterrupted = true,
        )

        val encoded = RecipeUiSavedStateCodec.encodeDetail(payload)

        assertEquals(payload, RecipeUiSavedStateCodec.decodeDetail(encoded, RecipeScope(7, 2), 9))
        assertNull(RecipeUiSavedStateCodec.decodeDetail(encoded, RecipeScope(7, 3), 9))
    }

    private fun values(name: String) = RecipeEditValues(
        name = name,
        prepMinutes = "10",
        cookMinutes = "20",
        servings = "4",
        ingredients = listOf(RecipeEditRow("ingredient-id", "2 onions")),
        instructions = listOf(RecipeEditRow("step-id", "Cook")),
        notes = "note",
        sourceUrl = "https://example.test/recipe",
    )
}
