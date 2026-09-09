package com.getmaincourse.app.features.recipes

import com.getmaincourse.app.data.cache.RecipeScope
import com.getmaincourse.app.data.images.PreparedRecipeImage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class RecipeEditorSavedState(
    val userId: Long,
    val cookbookId: Long,
    val recipeId: Long,
    val editorId: String,
    val original: RecipeEditValues,
    val values: RecipeEditValues,
    val stagedImage: PreparedRecipeImage? = null,
    val imageRequestKey: String? = null,
    val operationInterrupted: Boolean = false,
)

data class RecipeEditorImageSelection(
    val editorId: String,
    val image: PreparedRecipeImage?,
    val requestKey: String?,
)

@Serializable
data class RecipeDetailSavedState(
    val userId: Long,
    val cookbookId: Long,
    val recipeId: Long,
    val portions: Int,
    val cooking: Boolean,
    val review: IngredientReview? = null,
    val reviewSubmissionInterrupted: Boolean = false,
)

object RecipeUiSavedStateCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encodeEditor(value: RecipeEditorSavedState): String = json.encodeToString(value)

    fun decodeEditor(
        value: String?,
        scope: RecipeScope,
        recipeId: Long,
        editorId: String? = null,
    ): RecipeEditorSavedState? =
        decode<RecipeEditorSavedState>(value)?.takeIf {
            it.userId == scope.userId && it.cookbookId == scope.cookbookId && it.recipeId == recipeId &&
                (editorId == null || it.editorId == editorId)
        }?.let { restored ->
            restored.copy(stagedImage = restored.stagedImage?.takeIf { it.userId == scope.userId })
        }

    fun encodeDetail(value: RecipeDetailSavedState): String = json.encodeToString(value)

    fun decodeDetail(value: String?, scope: RecipeScope, recipeId: Long): RecipeDetailSavedState? =
        decode<RecipeDetailSavedState>(value)?.takeIf {
            it.userId == scope.userId && it.cookbookId == scope.cookbookId && it.recipeId == recipeId
        }

    private inline fun <reified T> decode(value: String?): T? = try {
        value?.let(json::decodeFromString)
    } catch (_: IllegalArgumentException) {
        null
    }
}
