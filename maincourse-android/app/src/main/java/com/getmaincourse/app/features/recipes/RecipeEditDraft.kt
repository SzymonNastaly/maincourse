package com.getmaincourse.app.features.recipes

import com.getmaincourse.app.data.cache.RecipeScope
import com.getmaincourse.app.data.model.RecipeUpdateRequest
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Serializable
data class RecipeEditRow(
    val id: String,
    val text: String,
)

@Serializable
data class RecipeEditValues(
    val name: String,
    val prepMinutes: String,
    val cookMinutes: String,
    val servings: String,
    val ingredients: List<RecipeEditRow>,
    val instructions: List<RecipeEditRow>,
    val notes: String,
    val sourceUrl: String,
)

data class RecipeEditDraft(
    val scope: RecipeScope,
    val recipeId: Long,
    val original: RecipeEditValues,
    val values: RecipeEditValues,
    val requestKey: String? = null,
) {
    fun hasChanges(): Boolean = normalized(original) != normalized(values)

    fun validate(): RecipeDraftValidation {
        val name = values.name.trim()
        if (name.isEmpty()) return RecipeDraftValidation.Invalid(RecipeDraftError.NAME_REQUIRED)
        val prep = parseOptionalInteger(values.prepMinutes, allowZero = true)
            ?: return RecipeDraftValidation.Invalid(RecipeDraftError.PREP_MINUTES_INVALID)
        val cook = parseOptionalInteger(values.cookMinutes, allowZero = true)
            ?: return RecipeDraftValidation.Invalid(RecipeDraftError.COOK_MINUTES_INVALID)
        val servings = parseOptionalInteger(values.servings, allowZero = false)
            ?: return RecipeDraftValidation.Invalid(RecipeDraftError.SERVINGS_INVALID)
        val sourceUrl = values.sourceUrl.trim().takeIf(String::isNotEmpty)
        if (sourceUrl != null && !isSafeRecipeSourceUrl(sourceUrl)) {
            return RecipeDraftValidation.Invalid(RecipeDraftError.SOURCE_URL_INVALID)
        }
        return RecipeDraftValidation.Valid(
            RecipeUpdateRequest(
                name = name,
                prepTime = prep.value,
                cookTime = cook.value,
                servings = servings.value,
                ingredients = values.ingredients.mapNotNull { it.text.trim().takeIf(String::isNotEmpty) },
                instructions = values.instructions.mapNotNull { it.text.trim().takeIf(String::isNotEmpty) },
                notes = values.notes.trim().takeIf(String::isNotEmpty),
                sourceUrl = sourceUrl,
            ),
        )
    }

    private fun normalized(input: RecipeEditValues) = NormalizedValues(
        name = input.name.trim(),
        prepMinutes = input.prepMinutes.trim(),
        cookMinutes = input.cookMinutes.trim(),
        servings = input.servings.trim(),
        ingredients = input.ingredients.mapNotNull { it.text.trim().takeIf(String::isNotEmpty) },
        instructions = input.instructions.mapNotNull { it.text.trim().takeIf(String::isNotEmpty) },
        notes = input.notes.trim(),
        sourceUrl = input.sourceUrl.trim(),
    )

    private fun parseOptionalInteger(value: String, allowZero: Boolean): ParsedInteger? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return ParsedInteger(null)
        val parsed = trimmed.toIntOrNull() ?: return null
        if (parsed < 0 || (!allowZero && parsed == 0)) return null
        return ParsedInteger(parsed)
    }

    private data class ParsedInteger(val value: Int?)

    private data class NormalizedValues(
        val name: String,
        val prepMinutes: String,
        val cookMinutes: String,
        val servings: String,
        val ingredients: List<String>,
        val instructions: List<String>,
        val notes: String,
        val sourceUrl: String,
    )
}

internal fun isSafeRecipeSourceUrl(value: String): Boolean {
    val url = value.toHttpUrlOrNull() ?: return false
    return url.scheme in setOf("http", "https") && url.host.isNotBlank() &&
        url.username.isEmpty() && url.password.isEmpty()
}

enum class RecipeDraftError {
    NAME_REQUIRED,
    PREP_MINUTES_INVALID,
    COOK_MINUTES_INVALID,
    SERVINGS_INVALID,
    SOURCE_URL_INVALID,
}

sealed interface RecipeDraftValidation {
    data class Valid(val request: RecipeUpdateRequest) : RecipeDraftValidation
    data class Invalid(val error: RecipeDraftError) : RecipeDraftValidation
}
