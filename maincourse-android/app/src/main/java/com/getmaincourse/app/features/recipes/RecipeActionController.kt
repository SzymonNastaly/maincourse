package com.getmaincourse.app.features.recipes

import com.getmaincourse.app.data.cache.RecipeScope
import com.getmaincourse.app.data.images.PreparedRecipeImage
import com.getmaincourse.app.data.images.PreparedRecipeImageUnavailable
import com.getmaincourse.app.data.model.RecipeSummary
import com.getmaincourse.app.data.model.ShoppingItemsRequest
import com.getmaincourse.app.data.network.ApiFailure
import com.getmaincourse.app.data.network.MainCourseApi
import com.getmaincourse.app.features.session.CatalogRepository
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class RecipeActionContext(
    val generation: Long,
    val userId: Long,
    val token: String,
    val scope: RecipeScope,
    val recipes: List<RecipeSummary>,
    val cookbookIds: Set<Long>,
)

internal interface RecipeActionHost {
    fun launch(block: suspend (RecipeActionContext) -> Unit): Job
    suspend fun prepareMutation(context: RecipeActionContext): Boolean
    suspend fun canCommit(context: RecipeActionContext): Boolean
    suspend fun canPublish(context: RecipeActionContext): Boolean
    suspend fun publishDetails(context: RecipeActionContext, scope: RecipeScope, recipeId: Long)
    suspend fun publishRemoval(context: RecipeActionContext, scope: RecipeScope, recipeId: Long)
    suspend fun publishRemovalCount(context: RecipeActionContext, targetCookbookId: Long?)
    suspend fun retainPendingRemoval(
        context: RecipeActionContext,
        scope: RecipeScope,
        recipeId: Long,
        failure: Throwable,
    )
    suspend fun settleRecipeReads(context: RecipeActionContext, recipeId: Long?)
    suspend fun handleAuthorizationFailure(context: RecipeActionContext, failure: ApiFailure): Boolean
}

class RecipeActionController internal constructor(
    private val api: MainCourseApi,
    private val repository: CatalogRepository,
    private val host: RecipeActionHost,
    private val resolvePreparedImage: suspend (PreparedRecipeImage, Long) -> File,
) {
    private val mutableState = MutableStateFlow(RecipeActionState())
    val state: StateFlow<RecipeActionState> = mutableState.asStateFlow()
    private val operations = Mutex()
    private val stateVersion = AtomicLong()
    private var pendingPhoto: PendingPhoto? = null

    fun saveRecipe(draft: RecipeEditDraft, image: PreparedRecipeImage?): Job = launch { context, version ->
        if (draft.scope != context.scope || context.recipes.none { it.id == draft.recipeId && it.importStatus == "completed" }) {
            publishFailure(context, version, draft.recipeId, "This recipe is no longer available")
            return@launch
        }
        val validation = draft.validate()
        if (validation is RecipeDraftValidation.Invalid) {
            publishFailure(context, version, draft.recipeId, validation.error.userMessage())
            return@launch
        }
        if (!draft.hasChanges() && image == null) {
            publishFailure(context, version, draft.recipeId, "Change something before saving")
            return@launch
        }
        if (!host.prepareMutation(context)) return@launch
        val request = (validation as RecipeDraftValidation.Valid).request
        publishRunning(context, version, RecipeActionOperation.SAVING, draft.recipeId)
        mutateAndSettle(context, draft.recipeId) { permit ->
            val acknowledged = try {
                api.updateRecipe(context.token, context.scope.cookbookId, draft.recipeId, request)
            } catch (failure: Throwable) {
                handleMutationFailure(context, version, draft.recipeId, failure, "Could not save recipe")
                return@mutateAndSettle
            }

            var reconciliationFailure = commitDetails(context, permit, context.scope, draft.recipeId, acknowledged)
            if (image == null) {
                if (reconciliationFailure == null) {
                    publishSuccess(context, version, draft.recipeId, "Recipe saved")
                } else {
                    publishReconciliation(context, version, draft.recipeId, "Recipe saved on the server, but local data needs refreshing")
                }
                return@mutateAndSettle
            }

            val file = try {
                resolvePreparedImage(image, context.userId)
            } catch (failure: PreparedRecipeImageUnavailable) {
                pendingPhoto = null
                publishState(
                    context,
                    version,
                    RecipeActionState(
                        outcome = RecipeActionOutcome.PARTIAL,
                        scope = context.scope,
                        recipeId = draft.recipeId,
                        message = "Recipe details were saved. Choose the photo again.",
                        needsPhotoSelection = true,
                        canRetryReconciliation = reconciliationFailure != null,
                    ),
                )
                return@mutateAndSettle
            }
            try {
                publishRunning(context, version, RecipeActionOperation.UPLOADING_PHOTO, draft.recipeId)
                val cover = api.updateRecipeCover(context.token, context.scope.cookbookId, draft.recipeId, file)
                reconciliationFailure = commitDetails(context, permit, context.scope, draft.recipeId, cover)
                    ?: reconciliationFailure
                pendingPhoto = null
                if (reconciliationFailure == null) {
                    publishSuccess(context, version, draft.recipeId, "Recipe and photo saved")
                } else {
                    publishReconciliation(context, version, draft.recipeId, "Recipe and photo were saved on the server, but local data needs refreshing")
                }
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                if (failure.isAuthorizationFailure()) throw DeferredAuthorizationFailure(failure as ApiFailure)
                pendingPhoto = PendingPhoto(context.generation, context.userId, context.scope, draft.recipeId, image)
                publishState(
                    context,
                    version,
                    RecipeActionState(
                        outcome = RecipeActionOutcome.PARTIAL,
                        scope = context.scope,
                        recipeId = draft.recipeId,
                        message = "Recipe details were saved, but the photo was not confirmed.",
                        canRetryPhoto = true,
                        canRetryReconciliation = reconciliationFailure != null,
                    ),
                )
            }
        }
    }

    fun retryRecipePhoto(): Job = launch { context, version ->
        val pending = pendingPhoto?.takeIf {
            it.generation == context.generation && it.userId == context.userId && it.scope == context.scope
        } ?: run {
            publishFailure(context, version, null, "Choose a photo again")
            return@launch
        }
        if (!host.prepareMutation(context)) return@launch
        publishRunning(context, version, RecipeActionOperation.UPLOADING_PHOTO, pending.recipeId)
        mutateAndSettle(context, pending.recipeId) { permit ->
            val file = try {
                resolvePreparedImage(pending.image, context.userId)
            } catch (_: PreparedRecipeImageUnavailable) {
                pendingPhoto = null
                publishState(
                    context,
                    version,
                    RecipeActionState(
                        outcome = RecipeActionOutcome.PARTIAL,
                        scope = context.scope,
                        recipeId = pending.recipeId,
                        message = "Choose the photo again",
                        needsPhotoSelection = true,
                    ),
                )
                return@mutateAndSettle
            }
            val acknowledged = try {
                api.updateRecipeCover(context.token, context.scope.cookbookId, pending.recipeId, file)
            } catch (failure: Throwable) {
                handlePhotoFailure(context, version, pending, failure)
                return@mutateAndSettle
            }
            pendingPhoto = null
            if (commitDetails(context, permit, context.scope, pending.recipeId, acknowledged) == null) {
                publishSuccess(context, version, pending.recipeId, "Photo saved")
            } else {
                publishReconciliation(context, version, pending.recipeId, "Photo saved on the server, but local data needs refreshing")
            }
        }
    }

    fun moveRecipe(recipeId: Long, targetId: Long): Job = launch { context, version ->
        val known = context.recipes.firstOrNull { it.id == recipeId && it.importStatus == "completed" }
        if (known == null || targetId == context.scope.cookbookId || targetId !in context.cookbookIds) {
            publishFailure(context, version, recipeId, "This recipe cannot be moved")
            return@launch
        }
        if (!host.prepareMutation(context)) return@launch
        publishRunning(context, version, RecipeActionOperation.MOVING, recipeId)
        mutateAndSettle(context, recipeId) { permit ->
            val detail = try {
                api.moveRecipe(context.token, context.scope.cookbookId, recipeId, targetId)
            } catch (failure: Throwable) {
                handleMutationFailure(context, version, recipeId, failure, "Could not move recipe")
                return@mutateAndSettle
            }
            val targetScope = RecipeScope(context.userId, targetId)
            host.publishRemovalCount(context, targetId)
            var localFailure: Throwable? = null
            try {
                repository.commitPartialRecipe(permit, targetScope, known, detail) { host.canCommit(context) }
                host.publishDetails(context, targetScope, recipeId)
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                localFailure = failure
            }
            try {
                repository.commitRecipeRemoval(permit, context.scope, recipeId) { host.canCommit(context) }
                host.publishRemoval(context, context.scope, recipeId)
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                localFailure = localFailure ?: failure
                host.retainPendingRemoval(context, context.scope, recipeId, failure)
            }
            if (localFailure == null) {
                publishSuccess(context, version, recipeId, "Recipe moved")
            } else {
                publishReconciliation(context, version, recipeId, "Recipe moved on the server, but local data needs refreshing")
            }
        }
    }

    fun deleteRecipe(recipeId: Long): Job = launch { context, version ->
        if (context.recipes.none { it.id == recipeId && it.importStatus == "completed" }) {
            publishFailure(context, version, recipeId, "This recipe cannot be deleted")
            return@launch
        }
        if (!host.prepareMutation(context)) return@launch
        publishRunning(context, version, RecipeActionOperation.DELETING, recipeId)
        mutateAndSettle(context, recipeId) { permit ->
            try {
                api.deleteRecipe(context.token, context.scope.cookbookId, recipeId)
            } catch (failure: Throwable) {
                if (failure !is ApiFailure || failure.status != 404) {
                    handleMutationFailure(context, version, recipeId, failure, "Could not delete recipe")
                    return@mutateAndSettle
                }
            }
            host.publishRemovalCount(context, null)
            try {
                repository.commitRecipeRemoval(permit, context.scope, recipeId) { host.canCommit(context) }
                host.publishRemoval(context, context.scope, recipeId)
                publishSuccess(context, version, recipeId, "Recipe deleted")
            } catch (failure: Throwable) {
                if (failure is CancellationException) throw failure
                host.retainPendingRemoval(context, context.scope, recipeId, failure)
                publishReconciliation(context, version, recipeId, "Recipe was deleted on the server, but local data needs refreshing")
            }
        }
    }

    fun addReviewedIngredients(recipeId: Long, items: List<ShoppingItemInput>): Job = launch { context, version ->
        val frozen = items.map(ShoppingItemInput::copy)
        if (frozen.isEmpty() || frozen.any { it.name.isBlank() || it.sourceRecipeId != recipeId } ||
            frozen.map(ShoppingItemInput::clientId).toSet().size != frozen.size ||
            context.recipes.none { it.id == recipeId && it.importStatus == "completed" }
        ) {
            publishFailure(context, version, recipeId, "Select at least one valid ingredient")
            return@launch
        }
        if (!host.prepareMutation(context)) return@launch
        publishRunning(context, version, RecipeActionOperation.ADDING_INGREDIENTS, recipeId, frozen)
        mutateAndSettle(context, recipeId) {
            val added = try {
                api.addRecipeIngredients(
                    context.token,
                    context.scope.cookbookId,
                    ShoppingItemsRequest(frozen.map(ShoppingItemInput::toRequest)),
                )
            } catch (failure: Throwable) {
                handleMutationFailure(context, version, recipeId, failure, "Could not add ingredients", frozen)
                return@mutateAndSettle
            }
            publishState(
                context,
                version,
                RecipeActionState(
                    outcome = RecipeActionOutcome.SUCCEEDED,
                    scope = context.scope,
                    recipeId = recipeId,
                    message = "Added ${added.size} items",
                    acknowledgedCount = added.size,
                    frozenShoppingItems = frozen,
                ),
            )
        }
    }

    fun retryRecipeReconciliation(): Job = launch { context, version ->
        val pending = mutableState.value
        if (!pending.canRetryReconciliation || pending.scope != context.scope) {
            publishFailure(context, version, pending.recipeId, "There is no recipe reconciliation to retry")
            return@launch
        }
        val recipeId = pending.recipeId
        publishRunning(context, version, RecipeActionOperation.RECONCILING, recipeId)
        host.settleRecipeReads(context, recipeId)
        publishSuccess(context, version, recipeId, "Recipe data refreshed")
    }

    fun clearRecipeAction(): Job = host.launch {
        stateVersion.incrementAndGet()
        pendingPhoto = null
        mutableState.value = RecipeActionState()
    }

    internal fun reset() {
        stateVersion.incrementAndGet()
        pendingPhoto = null
        mutableState.value = RecipeActionState()
    }

    private fun launch(block: suspend (RecipeActionContext, Long) -> Unit): Job {
        val version = stateVersion.incrementAndGet()
        return host.launch { context -> operations.withLock { block(context, version) } }
    }

    private suspend fun mutateAndSettle(
        context: RecipeActionContext,
        recipeId: Long?,
        block: suspend (com.getmaincourse.app.features.session.RecipeMutationPermit) -> Unit,
    ) {
        var authorizationHandled = false
        try {
            repository.withRecipeMutation(block)
        } catch (failure: DeferredAuthorizationFailure) {
            authorizationHandled = true
            host.handleAuthorizationFailure(context, failure.failure)
        } finally {
            if (!authorizationHandled) host.settleRecipeReads(context, recipeId)
        }
    }

    private suspend fun commitDetails(
        context: RecipeActionContext,
        permit: com.getmaincourse.app.features.session.RecipeMutationPermit,
        scope: RecipeScope,
        recipeId: Long,
        detail: com.getmaincourse.app.data.model.RecipeDetail,
    ): Throwable? = try {
        repository.commitRecipeDetails(permit, scope, listOf(detail)) { host.canCommit(context) }
        host.publishDetails(context, scope, recipeId)
        null
    } catch (failure: Throwable) {
        if (failure is CancellationException) throw failure
        failure
    }

    private suspend fun handlePhotoFailure(
        context: RecipeActionContext,
        version: Long,
        pending: PendingPhoto,
        failure: Throwable,
    ) {
        if (failure is CancellationException) throw failure
        if (failure.isAuthorizationFailure()) throw DeferredAuthorizationFailure(failure as ApiFailure)
        publishState(
            context,
            version,
            RecipeActionState(
                outcome = RecipeActionOutcome.PARTIAL,
                scope = context.scope,
                recipeId = pending.recipeId,
                message = "Recipe details were saved, but the photo was not confirmed.",
                canRetryPhoto = true,
            ),
        )
    }

    private suspend fun handleMutationFailure(
        context: RecipeActionContext,
        version: Long,
        recipeId: Long?,
        failure: Throwable,
        fallback: String,
        frozen: List<ShoppingItemInput> = emptyList(),
    ) {
        if (failure is CancellationException) throw failure
        if (failure.isAuthorizationFailure()) throw DeferredAuthorizationFailure(failure as ApiFailure)
        val ambiguous = failure !is ApiFailure || failure.status == null
        publishState(
            context,
            version,
            RecipeActionState(
                outcome = if (ambiguous) RecipeActionOutcome.AMBIGUOUS else RecipeActionOutcome.FAILED,
                scope = context.scope,
                recipeId = recipeId,
                message = (failure as? ApiFailure)?.message?.takeIf(String::isNotBlank)
                    ?: if (ambiguous) "$fallback; the server result is unconfirmed. Retry deliberately." else fallback,
                frozenShoppingItems = frozen,
            ),
        )
    }

    private suspend fun publishRunning(
        context: RecipeActionContext,
        version: Long,
        operation: RecipeActionOperation,
        recipeId: Long?,
        frozen: List<ShoppingItemInput> = emptyList(),
    ) = publishState(
        context,
        version,
        RecipeActionState(operation, RecipeActionOutcome.RUNNING, context.scope, recipeId, frozenShoppingItems = frozen),
    )

    private suspend fun publishSuccess(context: RecipeActionContext, version: Long, recipeId: Long?, message: String) =
        publishState(
            context,
            version,
            RecipeActionState(outcome = RecipeActionOutcome.SUCCEEDED, scope = context.scope, recipeId = recipeId, message = message),
        )

    private suspend fun publishFailure(context: RecipeActionContext, version: Long, recipeId: Long?, message: String) =
        publishState(
            context,
            version,
            RecipeActionState(outcome = RecipeActionOutcome.FAILED, scope = context.scope, recipeId = recipeId, message = message),
        )

    private suspend fun publishReconciliation(context: RecipeActionContext, version: Long, recipeId: Long?, message: String) =
        publishState(
            context,
            version,
            RecipeActionState(
                outcome = RecipeActionOutcome.RECONCILIATION_REQUIRED,
                scope = context.scope,
                recipeId = recipeId,
                message = message,
                canRetryReconciliation = true,
            ),
        )

    private suspend fun publishState(context: RecipeActionContext, version: Long, value: RecipeActionState) {
        if (version == stateVersion.get() && host.canPublish(context)) mutableState.value = value
    }

    private fun RecipeDraftError.userMessage(): String = when (this) {
        RecipeDraftError.NAME_REQUIRED -> "Name is required"
        RecipeDraftError.PREP_MINUTES_INVALID -> "Prep time must be a nonnegative whole number"
        RecipeDraftError.COOK_MINUTES_INVALID -> "Cook time must be a nonnegative whole number"
        RecipeDraftError.SERVINGS_INVALID -> "Servings must be a positive whole number"
        RecipeDraftError.SOURCE_URL_INVALID -> "Source must be an HTTP or HTTPS URL without credentials"
    }

    private data class PendingPhoto(
        val generation: Long,
        val userId: Long,
        val scope: RecipeScope,
        val recipeId: Long,
        val image: PreparedRecipeImage,
    )

    private class DeferredAuthorizationFailure(val failure: ApiFailure) : Exception()

    private fun Throwable.isAuthorizationFailure(): Boolean =
        this is ApiFailure && (status == 401 || status == 403)
}
