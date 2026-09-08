package com.getmaincourse.app.features.recipes

import com.getmaincourse.app.data.cache.RecipeScope

enum class RecipeActionOperation { IDLE, SAVING, UPLOADING_PHOTO, MOVING, DELETING, ADDING_INGREDIENTS, RECONCILING }

enum class RecipeActionOutcome { IDLE, RUNNING, SUCCEEDED, PARTIAL, FAILED, AMBIGUOUS, RECONCILIATION_REQUIRED }

data class RecipeActionState(
    val operation: RecipeActionOperation = RecipeActionOperation.IDLE,
    val outcome: RecipeActionOutcome = RecipeActionOutcome.IDLE,
    val scope: RecipeScope? = null,
    val recipeId: Long? = null,
    val message: String? = null,
    val acknowledgedCount: Int? = null,
    val canRetryPhoto: Boolean = false,
    val needsPhotoSelection: Boolean = false,
    val canRetryReconciliation: Boolean = false,
    val frozenShoppingItems: List<ShoppingItemInput> = emptyList(),
)
