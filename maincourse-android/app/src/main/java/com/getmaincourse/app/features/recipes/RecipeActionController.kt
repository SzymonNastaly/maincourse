package com.getmaincourse.app.features.recipes

import com.getmaincourse.app.data.cache.RecipeScope
import com.getmaincourse.app.data.images.PreparedRecipeImage
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
    suspend fun settleRecipeReads(context: RecipeActionContext): Boolean
    suspend fun handleAuthorizationFailure(context: RecipeActionContext, failure: ApiFailure): Boolean
}

class RecipeActionController internal constructor(
    private val api: MainCourseApi,
    private val repository: CatalogRepository,
    private val host: RecipeActionHost,
    private val discardPreparedImage: suspend (PreparedRecipeImage) -> Unit = {},
    private val resolvePreparedImage: suspend (PreparedRecipeImage, Long) -> File,
) {
    private val mutableState = MutableStateFlow(RecipeActionState())
    val state: StateFlow<RecipeActionState> = mutableState.asStateFlow()
    private val admissionLock = Any()
    private val stateVersion = AtomicLong()
    private var activeAction: Any? = null
    private var pendingPhoto: PendingPhoto? = null

    fun saveRecipe(draft: RecipeEditDraft, image: PreparedRecipeImage?): Job = launch { context, version ->
        if (draft.scope != context.scope) {
            publishFailure(context, version, draft.recipeId, "This recipe is no longer available", draft.requestKey)
            return@launch
        }
        if (!prepareMutation(context, version, draft.recipeId, draft.requestKey)) return@launch
        if (context.recipes.none { it.id == draft.recipeId && it.importStatus == "completed" }) {
            publishFailure(context, version, draft.recipeId, "This recipe is no longer available", draft.requestKey)
            return@launch
        }
        val validation = draft.validate()
        if (validation is RecipeDraftValidation.Invalid) {
            publishFailure(context, version, draft.recipeId, validation.error.userMessage(), draft.requestKey)
            return@launch
        }
        if (!draft.hasChanges() && image == null) {
            publishFailure(context, version, draft.recipeId, "Change something before saving", draft.requestKey)
            return@launch
        }
        discardPendingPhotoFromAnotherRequest(draft.requestKey)
        val request = (validation as RecipeDraftValidation.Valid).request
        val pending = image?.let {
            PendingPhoto(context.generation, context.userId, context.scope, draft.recipeId, it, draft.requestKey)
        }
        if (!draft.hasChanges()) {
            publishRunning(context, version, RecipeActionOperation.UPLOADING_PHOTO, draft.recipeId, requestKey = draft.requestKey)
            mutateAndSettle(context) { permit ->
                uploadPendingPhoto(context, version, permit, checkNotNull(pending))
            }
            return@launch
        }
        publishRunning(context, version, RecipeActionOperation.SAVING, draft.recipeId, requestKey = draft.requestKey)
        mutateAndSettle(context) { permit ->
            val acknowledged = try {
                api.updateRecipe(context.token, context.scope.cookbookId, draft.recipeId, request)
            } catch (failure: Throwable) {
                handleMutationFailure(context, version, draft.recipeId, failure, "Could not save recipe", requestKey = draft.requestKey)
                return@mutateAndSettle
            }

            var reconciliationFailure = commitDetails(context, permit, context.scope, draft.recipeId, acknowledged)
            if (image == null) {
                if (reconciliationFailure == null) {
                    publishSuccess(context, version, draft.recipeId, "Recipe saved", draft.requestKey)
                } else {
                    publishReconciliation(context, version, draft.recipeId, "Recipe saved on the server, but local data needs refreshing", draft.requestKey)
                }
                return@mutateAndSettle
            }
            publishRunning(context, version, RecipeActionOperation.UPLOADING_PHOTO, draft.recipeId, requestKey = draft.requestKey)
            uploadPendingPhoto(
                context,
                version,
                permit,
                checkNotNull(pending),
                needsReconciliation = reconciliationFailure != null,
                detailsAcknowledged = true,
            )
        }
    }

    fun retryRecipePhoto(replacement: PreparedRecipeImage? = null): Job = launch { context, version ->
        val owned = synchronized(admissionLock) { pendingPhoto }?.takeIf {
            it.generation == context.generation && it.userId == context.userId && it.scope == context.scope
        } ?: run {
            publishFailure(context, version, null, "Choose a photo again")
            return@launch
        }
        val pending = if (replacement == null) owned else owned.copy(image = replacement)
        if (!prepareMutation(context, version, pending.recipeId, pending.requestKey)) return@launch
        publishRunning(context, version, RecipeActionOperation.UPLOADING_PHOTO, pending.recipeId, requestKey = pending.requestKey)
        mutateAndSettle(context) { permit ->
            uploadPendingPhoto(context, version, permit, pending)
        }
    }

    fun moveRecipe(recipeId: Long, targetId: Long): Job = launch { context, version ->
        if (!prepareMutation(context, version, recipeId)) return@launch
        val known = context.recipes.firstOrNull { it.id == recipeId && it.importStatus == "completed" }
        if (known == null || targetId == context.scope.cookbookId || targetId !in context.cookbookIds) {
            publishFailure(context, version, recipeId, "This recipe cannot be moved")
            return@launch
        }
        publishRunning(context, version, RecipeActionOperation.MOVING, recipeId)
        mutateAndSettle(context) { permit ->
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
        if (!prepareMutation(context, version, recipeId)) return@launch
        if (context.recipes.none { it.id == recipeId && it.importStatus == "completed" }) {
            publishFailure(context, version, recipeId, "This recipe cannot be deleted")
            return@launch
        }
        publishRunning(context, version, RecipeActionOperation.DELETING, recipeId)
        mutateAndSettle(context) { permit ->
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
        if (!prepareMutation(context, version, recipeId)) return@launch
        if (frozen.isEmpty() || frozen.any { it.name.isBlank() || it.sourceRecipeId != recipeId } ||
            frozen.map(ShoppingItemInput::clientId).toSet().size != frozen.size ||
            context.recipes.none { it.id == recipeId && it.importStatus == "completed" }
        ) {
            publishFailure(
                context,
                version,
                recipeId,
                "Select at least one valid ingredient",
                operation = RecipeActionOperation.ADDING_INGREDIENTS,
            )
            return@launch
        }
        publishRunning(context, version, RecipeActionOperation.ADDING_INGREDIENTS, recipeId, frozen)
        val added = try {
            api.addRecipeIngredients(
                context.token,
                context.scope.cookbookId,
                ShoppingItemsRequest(frozen.map(ShoppingItemInput::toRequest)),
            )
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            if (failure.isAuthorizationFailure()) {
                host.handleAuthorizationFailure(context, failure as ApiFailure)
                mutableState.value = RecipeActionState()
                return@launch
            }
            handleMutationFailure(
                context,
                version,
                recipeId,
                failure,
                "Could not add ingredients",
                frozen = frozen,
                operation = RecipeActionOperation.ADDING_INGREDIENTS,
            )
            return@launch
        }
        publishState(
            context,
            version,
            RecipeActionState(
                operation = RecipeActionOperation.ADDING_INGREDIENTS,
                outcome = RecipeActionOutcome.SUCCEEDED,
                scope = context.scope,
                recipeId = recipeId,
                message = "Added ${added.size} items",
                acknowledgedCount = added.size,
                frozenShoppingItems = frozen,
            ),
        )
    }

    fun retryRecipeReconciliation(): Job = launch { context, version ->
        val pending = mutableState.value
        if (!pending.canRetryReconciliation || pending.scope != context.scope) {
            publishFailure(context, version, pending.recipeId, "There is no recipe reconciliation to retry")
            return@launch
        }
        val recipeId = pending.recipeId
        val requestKey = pending.requestKey
        if (!prepareMutation(context, version, recipeId, requestKey)) return@launch
        publishRunning(context, version, RecipeActionOperation.RECONCILING, recipeId, requestKey = requestKey)
        val refreshed = host.settleRecipeReads(context)
        val ownedPhoto = synchronized(admissionLock) {
            pendingPhoto?.takeIf {
                it.generation == context.generation && it.userId == context.userId && it.scope == context.scope &&
                    it.requestKey == requestKey
            }
        }
        if (ownedPhoto != null) {
            val needsPhotoSelection = pending.needsPhotoSelection
            publishState(
                context,
                version,
                RecipeActionState(
                    outcome = RecipeActionOutcome.PARTIAL,
                    scope = context.scope,
                    recipeId = recipeId,
                    message = if (refreshed) {
                        "Recipe data refreshed; the photo is still not confirmed."
                    } else {
                        "Recipe data still needs refreshing, and the photo is not confirmed."
                    },
                    canRetryPhoto = !needsPhotoSelection,
                    needsPhotoSelection = needsPhotoSelection,
                    canRetryReconciliation = !refreshed,
                    requestKey = requestKey,
                ),
            )
        } else if (refreshed) {
            publishSuccess(context, version, recipeId, "Recipe data refreshed", requestKey)
        } else {
            publishReconciliation(context, version, recipeId, "Recipe data still needs refreshing", requestKey)
        }
    }

    fun clearRecipeAction(): Job {
        synchronized(admissionLock) {
            if (activeAction != null) return completedJob()
            resetLocked()
        }
        return completedJob()
    }

    internal fun clearRecipeActionAndTakePendingPhoto(): Pair<Job, PreparedRecipeImage?> = synchronized(admissionLock) {
        if (activeAction != null) return@synchronized completedJob() to null
        completedJob() to resetLocked()
    }

    internal fun scopeChanged(): PreparedRecipeImage? = synchronized(admissionLock) {
        if (activeAction == null) resetLocked() else null
    }

    internal fun reset(): PreparedRecipeImage? = synchronized(admissionLock) { resetLocked() }

    private fun resetLocked(): PreparedRecipeImage? {
        val image = pendingPhoto?.image
        stateVersion.incrementAndGet()
        pendingPhoto = null
        mutableState.value = RecipeActionState()
        return image
    }

    private fun launch(block: suspend (RecipeActionContext, Long) -> Unit): Job {
        val owner = Any()
        val version: Long
        val job: Job
        synchronized(admissionLock) {
            if (activeAction != null) return completedJob()
            activeAction = owner
            version = stateVersion.incrementAndGet()
            job = host.launch { context ->
                try {
                    block(context, version)
                } finally {
                    val canPublish = host.canPublish(context)
                    val abandonedImage = synchronized(admissionLock) {
                        var image: PreparedRecipeImage? = null
                        if (activeAction === owner) {
                            activeAction = null
                            if (version == stateVersion.get()) {
                                mutableState.value = if (canPublish) {
                                    mutableState.value.copy(isBusy = false)
                                } else {
                                    image = pendingPhoto?.image
                                    pendingPhoto = null
                                    RecipeActionState()
                                }
                            }
                        }
                        image
                    }
                    abandonedImage?.let { runCatching { discardPreparedImage(it) } }
                }
            }
            job.invokeOnCompletion {
                synchronized(admissionLock) {
                    if (activeAction === owner) activeAction = null
                }
            }
        }
        return job
    }

    private suspend fun mutateAndSettle(
        context: RecipeActionContext,
        block: suspend (com.getmaincourse.app.features.session.RecipeMutationPermit) -> Unit,
    ) {
        var authorizationHandled = false
        try {
            repository.withRecipeMutation(block)
        } catch (failure: DeferredAuthorizationFailure) {
            authorizationHandled = true
            host.handleAuthorizationFailure(context, failure.failure)
            mutableState.value = RecipeActionState()
        } finally {
            if (!authorizationHandled) host.settleRecipeReads(context)
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
        permit: com.getmaincourse.app.features.session.RecipeMutationPermit,
        pending: PendingPhoto,
        failure: Throwable,
        needsReconciliation: Boolean = false,
        detailsAcknowledged: Boolean = true,
    ) {
        if (failure is CancellationException) throw failure
        if (failure.isAuthorizationFailure()) throw DeferredAuthorizationFailure(failure as ApiFailure)
        if (failure is ApiFailure && failure.status == 404) {
            releasePendingPhoto(pending.image)
            host.publishRemovalCount(context, null)
            try {
                repository.commitRecipeRemoval(permit, context.scope, pending.recipeId) { host.canCommit(context) }
                host.publishRemoval(context, context.scope, pending.recipeId)
                publishFailure(context, version, pending.recipeId, failure.message ?: "Recipe is no longer available")
            } catch (removalFailure: Throwable) {
                if (removalFailure is CancellationException) throw removalFailure
                host.retainPendingRemoval(context, context.scope, pending.recipeId, removalFailure)
                publishReconciliation(context, version, pending.recipeId, "Recipe is gone, but local data still needs cleanup")
            }
            return
        }
        if (failure is ApiFailure && failure.status == 422) {
            ownPendingPhoto(pending)
            publishState(
                context,
                version,
                RecipeActionState(
                    outcome = RecipeActionOutcome.PARTIAL,
                    scope = context.scope,
                    recipeId = pending.recipeId,
                    message = failure.message ?: "Choose another photo",
                    needsPhotoSelection = true,
                    canRetryReconciliation = needsReconciliation,
                    requestKey = pending.requestKey,
                ),
            )
            return
        }
        ownPendingPhoto(pending)
        publishState(
            context,
            version,
            RecipeActionState(
                outcome = RecipeActionOutcome.PARTIAL,
                scope = context.scope,
                recipeId = pending.recipeId,
                message = if (detailsAcknowledged) {
                    "Recipe details were saved, but the photo was not confirmed."
                } else {
                    "The photo was not confirmed."
                },
                canRetryPhoto = true,
                canRetryReconciliation = needsReconciliation,
                requestKey = pending.requestKey,
            ),
        )
    }

    private suspend fun uploadPendingPhoto(
        context: RecipeActionContext,
        version: Long,
        permit: com.getmaincourse.app.features.session.RecipeMutationPermit,
        pending: PendingPhoto,
        needsReconciliation: Boolean = false,
        detailsAcknowledged: Boolean = false,
    ) {
        ownPendingPhoto(pending)
        val file = try {
            resolvePreparedImage(pending.image, context.userId)
        } catch (failure: Throwable) {
            if (failure is CancellationException) throw failure
            publishState(
                context,
                version,
                RecipeActionState(
                    outcome = RecipeActionOutcome.PARTIAL,
                    scope = context.scope,
                    recipeId = pending.recipeId,
                    message = if (detailsAcknowledged) {
                        "Recipe details were saved. Choose the photo again."
                    } else {
                        "Choose the photo again"
                    },
                    needsPhotoSelection = true,
                    canRetryReconciliation = needsReconciliation,
                    requestKey = pending.requestKey,
                ),
            )
            return
        }
        val acknowledged = try {
            api.updateRecipeCover(context.token, context.scope.cookbookId, pending.recipeId, file)
        } catch (failure: Throwable) {
            handlePhotoFailure(
                context,
                version,
                permit,
                pending,
                failure,
                needsReconciliation,
                detailsAcknowledged,
            )
            return
        }
        val coverReconciliationFailure = commitDetails(context, permit, context.scope, pending.recipeId, acknowledged)
        releasePendingPhoto(pending.image)
        if (coverReconciliationFailure == null && !needsReconciliation) {
            publishSuccess(
                context,
                version,
                pending.recipeId,
                if (detailsAcknowledged) "Recipe and photo saved" else "Photo saved",
                pending.requestKey,
            )
        } else {
            publishReconciliation(
                context,
                version,
                pending.recipeId,
                if (detailsAcknowledged) {
                    "Recipe and photo were saved on the server, but local data needs refreshing"
                } else {
                    "Photo saved on the server, but local data needs refreshing"
                },
                pending.requestKey,
            )
        }
    }

    private suspend fun handleMutationFailure(
        context: RecipeActionContext,
        version: Long,
        recipeId: Long?,
        failure: Throwable,
        fallback: String,
        frozen: List<ShoppingItemInput> = emptyList(),
        requestKey: String? = null,
        operation: RecipeActionOperation = RecipeActionOperation.IDLE,
    ) {
        if (failure is CancellationException) throw failure
        if (failure.isAuthorizationFailure()) throw DeferredAuthorizationFailure(failure as ApiFailure)
        val ambiguous = failure !is ApiFailure || failure.status == null
        publishState(
            context,
            version,
            RecipeActionState(
                operation = operation,
                outcome = if (ambiguous) RecipeActionOutcome.AMBIGUOUS else RecipeActionOutcome.FAILED,
                scope = context.scope,
                recipeId = recipeId,
                message = (failure as? ApiFailure)?.message?.takeIf(String::isNotBlank)
                    ?: if (ambiguous) "$fallback; the server result is unconfirmed. Retry deliberately." else fallback,
                frozenShoppingItems = frozen,
                requestKey = requestKey,
            ),
        )
    }

    private suspend fun publishRunning(
        context: RecipeActionContext,
        version: Long,
        operation: RecipeActionOperation,
        recipeId: Long?,
        frozen: List<ShoppingItemInput> = emptyList(),
        requestKey: String? = null,
    ) = publishState(
        context,
        version,
        RecipeActionState(operation, RecipeActionOutcome.RUNNING, context.scope, recipeId, frozenShoppingItems = frozen, requestKey = requestKey),
    )

    private suspend fun publishSuccess(context: RecipeActionContext, version: Long, recipeId: Long?, message: String, requestKey: String? = null) =
        publishState(
            context,
            version,
            RecipeActionState(outcome = RecipeActionOutcome.SUCCEEDED, scope = context.scope, recipeId = recipeId, message = message, requestKey = requestKey),
        )

    private suspend fun publishFailure(
        context: RecipeActionContext,
        version: Long,
        recipeId: Long?,
        message: String,
        requestKey: String? = null,
        operation: RecipeActionOperation = RecipeActionOperation.IDLE,
    ) =
        publishState(
            context,
            version,
            RecipeActionState(
                operation = operation,
                outcome = RecipeActionOutcome.FAILED,
                scope = context.scope,
                recipeId = recipeId,
                message = message,
                requestKey = requestKey,
            ),
        )

    private suspend fun publishReconciliation(context: RecipeActionContext, version: Long, recipeId: Long?, message: String, requestKey: String? = null) =
        publishState(
            context,
            version,
            RecipeActionState(
                outcome = RecipeActionOutcome.RECONCILIATION_REQUIRED,
                scope = context.scope,
                recipeId = recipeId,
                message = message,
                canRetryReconciliation = true,
                requestKey = requestKey,
            ),
        )

    private suspend fun publishState(context: RecipeActionContext, version: Long, value: RecipeActionState) {
        if (version == stateVersion.get() && host.canPublish(context)) mutableState.value = value.copy(isBusy = true)
    }

    private suspend fun prepareMutation(
        context: RecipeActionContext,
        version: Long,
        recipeId: Long?,
        requestKey: String? = null,
    ): Boolean {
        if (host.prepareMutation(context)) return true
        publishReconciliation(
            context,
            version,
            recipeId,
            "Finish local recipe cleanup before trying another recipe action",
            requestKey,
        )
        return false
    }

    private fun completedJob(): Job = Job().apply { complete() }

    internal fun ownsPendingPhoto(requestKey: String, image: PreparedRecipeImage): Boolean = synchronized(admissionLock) {
        pendingPhoto?.let { it.requestKey == requestKey && it.image == image } == true
    }

    private suspend fun ownPendingPhoto(pending: PendingPhoto) {
        val previous = synchronized(admissionLock) {
            pendingPhoto.also { pendingPhoto = pending }
        }
        if (previous != null && previous.image != pending.image) runCatching { discardPreparedImage(previous.image) }
    }

    private suspend fun releasePendingPhoto(image: PreparedRecipeImage) {
        val released = synchronized(admissionLock) {
            pendingPhoto?.takeIf { it.image == image }?.also { pendingPhoto = null }?.image
        }
        released?.let { runCatching { discardPreparedImage(it) } }
    }

    private suspend fun discardPendingPhotoFromAnotherRequest(requestKey: String?) {
        val abandoned = synchronized(admissionLock) {
            pendingPhoto?.takeIf { it.requestKey != requestKey }?.also { pendingPhoto = null }?.image
        }
        abandoned?.let { runCatching { discardPreparedImage(it) } }
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
        val requestKey: String?,
    )

    private class DeferredAuthorizationFailure(val failure: ApiFailure) : Exception()

    private fun Throwable.isAuthorizationFailure(): Boolean =
        this is ApiFailure && (status == 401 || status == 403)
}
