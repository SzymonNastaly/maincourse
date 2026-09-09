package com.getmaincourse.app.data.images

import kotlinx.serialization.Serializable

@Serializable
data class PreparedRecipeImage(
    val path: String,
    val userId: Long,
    val key: String,
)

class PreparedRecipeImageUnavailable(message: String) : Exception(message)
