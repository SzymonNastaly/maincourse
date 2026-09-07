package com.getmaincourse.app.features.session

import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeSummary
import com.getmaincourse.app.data.model.User

enum class SessionPhase {
    RESTORING,
    SIGNED_OUT,
    LOADING_COOKBOOKS,
    READY,
    SIGNING_OUT,
    RESTORE_FAILED,
    CLEANUP_FAILED,
}

enum class LoadStatus { IDLE, LOADING, FRESH, DEGRADED, ERROR }

enum class DetailStatus { LOADING, FRESH, SAVED_OFFLINE, ERROR, UNAVAILABLE, NOT_READY }

data class RecipeDetailState(
    val recipeId: Long,
    val status: DetailStatus,
    val recipe: RecipeDetail? = null,
    val message: String? = null,
)

data class SessionState(
    val phase: SessionPhase = SessionPhase.RESTORING,
    val user: User? = null,
    val cookbooks: List<Cookbook> = emptyList(),
    val activeCookbookId: Long? = null,
    val recipes: List<RecipeSummary> = emptyList(),
    val recipesFetched: Boolean = false,
    val catalogStatus: LoadStatus = LoadStatus.IDLE,
    val recipeStatus: LoadStatus = LoadStatus.IDLE,
    val detail: RecipeDetailState? = null,
    val authError: String? = null,
    val message: String? = null,
    val canRetry: Boolean = false,
    val canReset: Boolean = false,
)
