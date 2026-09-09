package com.getmaincourse.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MoveRecipeRequest(
    @SerialName("cookbook_id")
    val cookbookId: Long,
)
