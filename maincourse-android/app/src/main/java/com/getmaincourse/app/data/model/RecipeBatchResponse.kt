package com.getmaincourse.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RecipeBatchResponse(
    val recipes: List<RecipeDetail>,
    @SerialName("next_cursor")
    val nextCursor: String?,
)
