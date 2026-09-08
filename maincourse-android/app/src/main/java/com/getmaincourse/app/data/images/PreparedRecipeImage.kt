package com.getmaincourse.app.data.images

data class PreparedRecipeImage(
    val path: String,
    val userId: Long,
    val key: String,
)

class PreparedRecipeImageUnavailable(message: String) : Exception(message)
