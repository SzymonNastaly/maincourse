package com.getmaincourse.app.features.recipes

import com.getmaincourse.app.data.model.StructuredIngredient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IngredientReviewTest {
    @Test
    fun unknownBaseServingsUsesRawRowsAtOneToOneWithoutInventingDetails() {
        val review = IngredientReview.create(
            recipeId = 9,
            ingredients = listOf(StructuredIngredient(1, 0, "2", null, null, "onions", null, "about two onions")),
            portions = 1,
            baseServings = null,
            newId = { "stable" },
        )

        assertEquals("about two onions", review.items.single().name)
        assertNull(review.items.single().details)
        assertEquals("stable", review.items.single().clientId)
    }
    @Test
    fun reviewStartsIncludedAndBuildsScaledParsedAndRawPayloadsWithStableIds() {
        val review = IngredientReview.create(
            recipeId = 10,
            ingredients = listOf(
                StructuredIngredient(1, 0, "0.5", null, "cup", "cream", "cold", "cream"),
                StructuredIngredient(2, 1, null, null, null, null, null, "salt to taste"),
            ),
            portions = 8,
            baseServings = 4,
            newId = sequenceOf("stable-a", "stable-b").iterator()::next,
        )

        assertTrue(review.items.all(IngredientReviewItem::included))
        assertEquals(
            listOf(
                ShoppingItemInput("stable-a", "cream", "1 cup, cold", null, 10),
                ShoppingItemInput("stable-b", "salt to taste", null, null, 10),
            ),
            review.includedPayload(),
        )

        val excluded = review.withIncluded("stable-a", false)
        assertFalse(excluded.items.first().included)
        assertEquals(listOf("stable-b"), excluded.includedPayload().map(ShoppingItemInput::clientId))
        assertEquals(review.items.map(IngredientReviewItem::clientId), excluded.items.map(IngredientReviewItem::clientId))
    }

    @Test
    fun unusableStructuredRowUsesRawWithoutFabricatedDetails() {
        val review = IngredientReview.create(
            recipeId = 10,
            ingredients = listOf(StructuredIngredient(1, 0, "many", null, "cups", "berries", "ripe", "berries as needed")),
            portions = 2,
            baseServings = 1,
            newId = { "stable" },
        )

        assertEquals(ShoppingItemInput("stable", "berries as needed", null, null, 10), review.includedPayload().single())
    }

    @Test
    fun stapleDefaultCanBeOverridden() {
        val ingredient = StructuredIngredient(
            id = 1,
            position = 0,
            amount = null,
            amountMax = null,
            unit = null,
            name = "sól",
            note = null,
            raw = "sól do smaku",
            canonicalName = "salt",
            shoppingDefaultIncluded = false,
        )

        val review = IngredientReview.create(9, listOf(ingredient), 1, null) { "salt-id" }

        assertTrue(review.includedPayload().isEmpty())
        assertEquals(
            listOf("salt-id"),
            review.withIncluded("salt-id", true).includedPayload().map(ShoppingItemInput::clientId),
        )
    }
}
