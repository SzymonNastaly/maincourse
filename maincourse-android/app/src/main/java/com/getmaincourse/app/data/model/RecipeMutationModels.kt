package com.getmaincourse.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MoveRecipeRequest(
    @SerialName("cookbook_id")
    val cookbookId: Long,
)

@Serializable
data class RecipeUpdateRequest(
    val name: String,
    @SerialName("prep_time")
    val prepTime: Int?,
    @SerialName("cook_time")
    val cookTime: Int?,
    val servings: Int?,
    val ingredients: List<String>,
    val instructions: List<String>,
    val notes: String?,
    @SerialName("source_url")
    val sourceUrl: String?,
)
