package com.getmaincourse.app.features.recipes

import com.getmaincourse.app.data.model.StructuredIngredient
import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class IngredientFormatterTest {
    @Test
    fun commonFractionsMatchIosForTheEntireScaledAmount() {
        val cases = listOf(
            "0.25" to "¼",
            "0.5" to "½",
            "0.75" to "¾",
            "0.3333" to "⅓",
            "0.6667" to "⅔",
            "0.125" to "⅛",
            "0.375" to "⅜",
            "0.625" to "⅝",
            "0.875" to "⅞",
        )

        cases.forEach { (amount, expected) ->
            assertEquals(expected, IngredientFormatter.formatAmount(BigDecimal(amount)))
        }
        assertEquals("1.5", IngredientFormatter.formatAmount(BigDecimal("1.5")))
        assertEquals("0.26", IngredientFormatter.formatAmount(BigDecimal("0.255")))
        assertEquals("0.25", IngredientFormatter.formatAmount(BigDecimal("0.245")))
    }

    @Test
    fun fractionToleranceIsStrictlyLessThanPointZeroZeroFive() {
        assertEquals("¼", IngredientFormatter.formatAmount(BigDecimal("0.2549")))
        assertEquals("0.26", IngredientFormatter.formatAmount(BigDecimal("0.255")))
    }

    @Test
    fun rangesUnitsNamesAndNotesUseExactBigDecimalServingRatios() {
        val ingredient = StructuredIngredient(
            id = 1,
            position = 0,
            amount = "0.3333",
            amountMax = "0.5",
            unit = "cup",
            name = "cream",
            note = "cold",
            raw = "cream to taste",
        )

        assertEquals(
            "⅔–1 cup cream, cold",
            IngredientFormatter.formatIngredient(ingredient, portions = 8, baseServings = 4),
        )
        assertEquals("200–250 g", IngredientFormatter.formatQuantity("200", "250", "g"))
    }

    @Test
    fun everyAllowedPortionUsesAnExactRatioRatherThanDoubleArithmetic() {
        (1..64).forEach { portions ->
            assertEquals(
                BigDecimal(portions).divide(BigDecimal(64)),
                IngredientFormatter.servingRatio(portions, 64),
            )
        }
    }

    @Test
    fun absentOrUnusableStructureFallsBackToRawWithoutInventingQuantity() {
        val unparsed = StructuredIngredient(1, 0, null, null, null, null, null, "salt to taste")
        val malformed = StructuredIngredient(2, 1, "one", null, "cup", "flour", null, "one cup flour")

        assertEquals("salt to taste", IngredientFormatter.formatIngredient(unparsed, 8, 4))
        assertEquals("one cup flour", IngredientFormatter.formatIngredient(malformed, 8, 4))
    }
}
