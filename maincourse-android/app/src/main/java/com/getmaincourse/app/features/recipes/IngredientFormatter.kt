package com.getmaincourse.app.features.recipes

import com.getmaincourse.app.data.model.StructuredIngredient
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

object IngredientFormatter {
    private val fractionTolerance = BigDecimal("0.005")
    private val fractionTable = listOf(
        BigDecimal("0.25") to "¼",
        BigDecimal("0.5") to "½",
        BigDecimal("0.75") to "¾",
        BigDecimal("0.3333") to "⅓",
        BigDecimal("0.6667") to "⅔",
        BigDecimal("0.125") to "⅛",
        BigDecimal("0.375") to "⅜",
        BigDecimal("0.625") to "⅝",
        BigDecimal("0.875") to "⅞",
    )

    fun servingRatio(portions: Int, baseServings: Int): BigDecimal {
        require(portions in 1..64) { "Portions must be between 1 and 64" }
        require(baseServings > 0) { "Base servings must be positive" }
        return BigDecimal(portions).divide(BigDecimal(baseServings), MathContext.DECIMAL128).stripTrailingZeros()
    }

    fun formatAmount(value: BigDecimal): String = fractionTable.firstOrNull { (fraction, _) ->
        value.subtract(fraction).abs() < fractionTolerance
    }?.second ?: value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    fun formatQuantity(
        amount: String?,
        amountMax: String?,
        unit: String?,
        scale: BigDecimal = BigDecimal.ONE,
    ): String {
        val minimum = amount.toDecimalOrNull()
        val maximum = amountMax.toDecimalOrNull()
        val quantity = when {
            minimum != null && maximum != null ->
                "${formatAmount(minimum.multiply(scale))}–${formatAmount(maximum.multiply(scale))}"
            minimum != null -> formatAmount(minimum.multiply(scale))
            else -> null
        }
        return listOfNotNull(quantity, unit?.trim()?.takeIf(String::isNotEmpty)).joinToString(" ")
    }

    fun formatIngredient(ingredient: StructuredIngredient, portions: Int, baseServings: Int): String {
        val name = ingredient.name?.trim()?.takeIf(String::isNotEmpty)
        if (!ingredient.hasUsableStructure(name)) return ingredient.raw
        val quantity = formatQuantity(
            ingredient.amount,
            ingredient.amountMax,
            ingredient.unit,
            servingRatio(portions, baseServings),
        )
        return buildIngredientText(quantity, checkNotNull(name), ingredient.note)
    }

    internal fun reviewParts(
        ingredient: StructuredIngredient,
        portions: Int,
        baseServings: Int,
    ): Pair<String, String?> {
        val name = ingredient.name?.trim()?.takeIf(String::isNotEmpty)
        if (!ingredient.hasUsableStructure(name)) return ingredient.raw to null
        val quantity = formatQuantity(
            ingredient.amount,
            ingredient.amountMax,
            ingredient.unit,
            servingRatio(portions, baseServings),
        )
        val details = listOfNotNull(
            quantity.takeIf(String::isNotEmpty),
            ingredient.note?.trim()?.takeIf(String::isNotEmpty),
        ).joinToString(", ").takeIf(String::isNotEmpty)
        return checkNotNull(name) to details
    }

    private fun StructuredIngredient.hasUsableStructure(name: String?): Boolean {
        if (name == null) return false
        val hasQuantity = amount != null || amountMax != null || !unit.isNullOrBlank()
        if (!hasQuantity) return false
        if (amount != null && amount.toDecimalOrNull() == null) return false
        if (amountMax != null && amountMax.toDecimalOrNull() == null) return false
        return true
    }

    private fun buildIngredientText(quantity: String, name: String, note: String?): String {
        val main = listOf(quantity, name).filter(String::isNotEmpty).joinToString(" ")
        val trimmedNote = note?.trim()?.takeIf(String::isNotEmpty)
        return if (trimmedNote == null) main else "$main, $trimmedNote"
    }

    private fun String?.toDecimalOrNull(): BigDecimal? = this?.trim()?.takeIf(String::isNotEmpty)?.toBigDecimalOrNull()
}
