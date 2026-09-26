package com.getmaincourse.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RecipeSaveSource(val type: String, val key: String)

@Serializable
data class RecipeSaveRequest(
    val source: RecipeSaveSource,
    @SerialName("request_id") val requestId: String,
)

@Serializable
data class RecipeSaveResponse(
    @SerialName("recipe_id") val recipeId: Long,
    @SerialName("cookbook_id") val cookbookId: Long,
)
