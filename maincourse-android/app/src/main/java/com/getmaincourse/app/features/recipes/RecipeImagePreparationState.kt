package com.getmaincourse.app.features.recipes

import com.getmaincourse.app.data.cache.RecipeScope
import com.getmaincourse.app.data.images.PreparedRecipeImage

enum class RecipeImagePreparationStatus { IDLE, PREPARING, READY, ERROR }

data class RecipeImagePreparationState(
    val status: RecipeImagePreparationStatus = RecipeImagePreparationStatus.IDLE,
    val scope: RecipeScope? = null,
    val image: PreparedRecipeImage? = null,
    val message: String? = null,
)
