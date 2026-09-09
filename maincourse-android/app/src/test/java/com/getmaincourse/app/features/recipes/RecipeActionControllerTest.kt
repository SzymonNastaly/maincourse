package com.getmaincourse.app.features.recipes

import com.getmaincourse.app.data.cache.CachedRecipes
import com.getmaincourse.app.data.cache.CatalogStore
import com.getmaincourse.app.data.cache.RecipeScope
import com.getmaincourse.app.data.images.PreparedRecipeImage
import com.getmaincourse.app.data.images.PreparedRecipeImageUnavailable
import com.getmaincourse.app.data.model.AccountUpdateRequest
import com.getmaincourse.app.data.model.AppleAuthenticationExchangeRequest
import com.getmaincourse.app.data.model.AppleAuthenticationStartRequest
import com.getmaincourse.app.data.model.AppleAuthenticationStartResponse
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.GoogleSignInRequest
import com.getmaincourse.app.data.model.OnboardingRequest
import com.getmaincourse.app.data.model.OnboardingResponse
import com.getmaincourse.app.data.model.RecipeBatchResponse
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeSummary
import com.getmaincourse.app.data.model.RecipeUpdateRequest
import com.getmaincourse.app.data.model.SessionResponse
import com.getmaincourse.app.data.model.ShoppingItem
import com.getmaincourse.app.data.model.ShoppingItemsRequest
import com.getmaincourse.app.data.model.SignInRequest
import com.getmaincourse.app.data.model.SignUpRequest
import com.getmaincourse.app.data.model.User
import com.getmaincourse.app.data.network.ApiFailure
import com.getmaincourse.app.data.network.MainCourseApi
import com.getmaincourse.app.features.search.RecipeSearchDocument
import com.getmaincourse.app.features.session.CatalogRepository
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecipeActionControllerTest {
    @Test
    fun textAcknowledgementIsCommittedBeforeCoverFailureAndPhotoRetryDoesNotRepeatTextPatch() = runTest {
        val api = FakeApi().apply {
            updateResult = UPDATED
            coverFailure = IOException("offline")
        }
        val host = FakeHost(this)
        val store = FakeStore()
        val staged = PreparedRecipeImage("/private/user-7/photo.jpg", 7, "photo")
        val image = File.createTempFile("recipe-action", ".jpg").apply { writeBytes(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte())) }
        val controller = controller(api, store, host) { _, _ -> image }

        controller.saveRecipe(changedDraft(), staged).join()

        assertEquals(1, api.updateCalls)
        assertEquals(1, api.coverCalls)
        assertEquals(UPDATED, store.detail(SOURCE, RECIPE.id))
        assertEquals(RecipeActionOutcome.PARTIAL, controller.state.value.outcome)
        assertTrue(controller.state.value.canRetryPhoto)

        api.coverFailure = null
        api.coverResult = UPDATED.copy(coverImageUrl = "/cover.jpg", updatedAt = "2026-09-08T12:02:00Z")
        controller.retryRecipePhoto().join()

        assertEquals(1, api.updateCalls)
        assertEquals(2, api.coverCalls)
        assertEquals(RecipeActionOutcome.SUCCEEDED, controller.state.value.outcome)
        image.delete()
    }

    @Test
    fun missingStagedPhotoRequestsAChooseAgainWithoutCallingTheNetwork() = runTest {
        val api = FakeApi().apply { updateResult = UPDATED }
        val replacementFile = File.createTempFile("replacement", ".jpg")
        val replacement = PreparedRecipeImage("/private/replacement.jpg", 7, "replacement")
        val controller = controller(api, FakeStore(), FakeHost(this)) { prepared, _ ->
            if (prepared == replacement) replacementFile else throw PreparedRecipeImageUnavailable("Choose the photo again")
        }

        controller.saveRecipe(changedDraft(), PreparedRecipeImage("/missing", 7, "missing")).join()

        assertEquals(1, api.updateCalls)
        assertEquals(0, api.coverCalls)
        assertEquals(RecipeActionOutcome.PARTIAL, controller.state.value.outcome)
        assertTrue(controller.state.value.needsPhotoSelection)
        assertFalse(controller.state.value.canRetryPhoto)

        controller.retryRecipePhoto(replacement).join()

        assertEquals(1, api.updateCalls)
        assertEquals(1, api.coverCalls)
        assertEquals(RecipeActionOutcome.SUCCEEDED, controller.state.value.outcome)
        replacementFile.delete()
    }

    @Test
    fun evictedPendingPhotoCanBeReplacedWithoutRepeatingAcknowledgedTextPatch() = runTest {
        val initial = PreparedRecipeImage("/private/initial.jpg", 7, "initial")
        val replacement = PreparedRecipeImage("/private/replacement.jpg", 7, "replacement")
        val initialFile = File.createTempFile("initial", ".jpg")
        val replacementFile = File.createTempFile("replacement", ".jpg")
        val discarded = mutableListOf<PreparedRecipeImage>()
        var initialEvicted = false
        val api = FakeApi().apply {
            updateResult = UPDATED
            coverFailure = IOException("offline")
        }
        val controller = controller(
            api,
            FakeStore(),
            FakeHost(this),
            resolve = { prepared, _ ->
                when (prepared) {
                    initial -> if (initialEvicted) throw PreparedRecipeImageUnavailable("evicted") else initialFile
                    replacement -> replacementFile
                    else -> error("unexpected image")
                }
            },
            discard = { discarded += it },
        )

        controller.saveRecipe(changedDraft().copy(requestKey = "editor-a"), initial).join()
        assertTrue(controller.ownsPendingPhoto("editor-a", initial))
        initialEvicted = true
        api.coverFailure = null
        controller.retryRecipePhoto().join()

        assertTrue(controller.state.value.needsPhotoSelection)
        assertTrue(controller.ownsPendingPhoto("editor-a", initial))
        controller.retryRecipePhoto(replacement).join()

        assertEquals(1, api.updateCalls)
        assertEquals(2, api.coverCalls)
        assertEquals(RecipeActionOutcome.SUCCEEDED, controller.state.value.outcome)
        assertEquals("editor-a", controller.state.value.requestKey)
        assertFalse(controller.ownsPendingPhoto("editor-a", replacement))
        assertEquals(listOf(initial, replacement), discarded)
        initialFile.delete()
        replacementFile.delete()
    }

    @Test
    fun aNewEditorSaveExplicitlySupersedesAndDiscardsOlderPhotoRecovery() = runTest {
        val pending = PreparedRecipeImage("/private/pending.jpg", 7, "pending")
        val file = File.createTempFile("pending", ".jpg")
        val discarded = mutableListOf<PreparedRecipeImage>()
        val api = FakeApi().apply { coverFailure = IOException("offline") }
        val controller = controller(
            api,
            FakeStore(),
            FakeHost(this),
            discard = { discarded += it },
            resolve = { _, _ -> file },
        )
        controller.saveRecipe(changedDraft().copy(requestKey = "editor-a"), pending).join()
        api.coverFailure = null

        controller.saveRecipe(changedDraft().copy(requestKey = "editor-b"), null).join()

        assertEquals(2, api.updateCalls)
        assertEquals(listOf(pending), discarded)
        assertFalse(controller.ownsPendingPhoto("editor-a", pending))
        file.delete()
    }

    @Test
    fun aNewPhotoMakesAnOtherwiseUnchangedDraftSaveable() = runTest {
        val api = FakeApi()
        val image = File.createTempFile("recipe-photo-only", ".jpg").apply {
            writeBytes(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte()))
        }
        val controller = controller(api, FakeStore(), FakeHost(this)) { _, _ -> image }
        val changed = changedDraft()
        val unchanged = changed.copy(values = changed.original)

        controller.saveRecipe(unchanged, PreparedRecipeImage("/private/photo.jpg", 7, "photo")).join()

        assertEquals(1, api.updateCalls)
        assertEquals(1, api.coverCalls)
        assertEquals(RecipeActionOutcome.SUCCEEDED, controller.state.value.outcome)
        image.delete()
    }

    @Test
    fun serverAcknowledgedCacheFailureOffersReconciliationWithoutResendingPatch() = runTest {
        val api = FakeApi().apply { updateResult = UPDATED }
        val store = FakeStore().apply { saveFailure = IOException("disk full") }
        val host = FakeHost(this)
        val controller = controller(api, store, host)

        controller.saveRecipe(changedDraft(), null).join()

        assertEquals(1, api.updateCalls)
        assertEquals(RecipeActionOutcome.RECONCILIATION_REQUIRED, controller.state.value.outcome)
        assertTrue(controller.state.value.canRetryReconciliation)
        controller.retryRecipeReconciliation().join()
        assertEquals(1, api.updateCalls)
        assertEquals(2, host.settleCalls)
        assertEquals(RecipeActionOutcome.RECONCILIATION_REQUIRED, controller.state.value.outcome)
        assertTrue(controller.state.value.canRetryReconciliation)
    }

    @Test
    fun reconciliationRetryKeepsTheOwningEditorRequestThroughFailureAndSuccess() = runTest {
        val host = FakeHost(this).apply { settleResult = false }
        val api = FakeApi()
        val controller = controller(api, FakeStore().apply { saveFailure = IOException("disk") }, host)
        controller.saveRecipe(changedDraft().copy(requestKey = "editor-a"), null).join()
        assertEquals("editor-a", controller.state.value.requestKey)

        controller.retryRecipeReconciliation().join()
        assertEquals(RecipeActionOutcome.RECONCILIATION_REQUIRED, controller.state.value.outcome)
        assertEquals("editor-a", controller.state.value.requestKey)
        assertEquals(1, api.updateCalls)

        host.settleResult = true
        controller.retryRecipeReconciliation().join()
        assertEquals(RecipeActionOutcome.SUCCEEDED, controller.state.value.outcome)
        assertEquals("editor-a", controller.state.value.requestKey)
        assertEquals(1, api.updateCalls)
    }

    @Test
    fun blockedPhotoRetryKeepsThePendingEditorsRequestKey() = runTest {
        val image = File.createTempFile("blocked-photo", ".jpg")
        val prepared = PreparedRecipeImage("/private/photo.jpg", 7, "photo")
        val host = FakeHost(this)
        val api = FakeApi().apply { coverFailure = IOException("offline") }
        val controller = controller(api, FakeStore(), host) { _, _ -> image }
        controller.saveRecipe(changedDraft().copy(requestKey = "editor-a"), prepared).join()
        host.preparationAllowed = false

        controller.retryRecipePhoto().join()

        assertEquals(RecipeActionOutcome.RECONCILIATION_REQUIRED, controller.state.value.outcome)
        assertEquals("editor-a", controller.state.value.requestKey)
        image.delete()
    }

    @Test
    fun reconciliationReportsSuccessOnlyWhenPurgeAndRefreshActuallyResolve() = runTest {
        val host = FakeHost(this).apply { settleResult = false }
        val controller = controller(FakeApi(), FakeStore().apply { saveFailure = IOException("disk") }, host)
        controller.saveRecipe(changedDraft(), null).join()

        controller.retryRecipeReconciliation().join()

        assertEquals(RecipeActionOutcome.RECONCILIATION_REQUIRED, controller.state.value.outcome)
        host.settleResult = true
        controller.retryRecipeReconciliation().join()
        assertEquals(RecipeActionOutcome.SUCCEEDED, controller.state.value.outcome)
    }

    @Test
    fun blockedPurgeProducesRetryableTerminalFeedbackInsteadOfASilentNoOp() = runTest {
        val host = FakeHost(this).apply { preparationAllowed = false }
        val controller = controller(FakeApi(), FakeStore(), host)

        controller.saveRecipe(changedDraft(), null).join()

        assertEquals(RecipeActionOutcome.RECONCILIATION_REQUIRED, controller.state.value.outcome)
        assertTrue(controller.state.value.canRetryReconciliation)
    }

    @Test
    fun successfulMovePartiallyUpsertsCapturedTargetThenRemovesCapturedSource() = runTest {
        val api = FakeApi().apply { moveResult = UPDATED }
        val store = FakeStore()
        val host = FakeHost(this)
        val controller = controller(api, store, host)

        controller.moveRecipe(RECIPE.id, TARGET.cookbookId).join()

        assertEquals(UPDATED, store.detail(TARGET, RECIPE.id))
        assertNull(store.detail(SOURCE, RECIPE.id))
        assertEquals(listOf(TARGET to RECIPE.id, SOURCE to RECIPE.id), store.mutationOrder)
        assertEquals(RecipeActionOutcome.SUCCEEDED, controller.state.value.outcome)
    }

    @Test
    fun moveNotFoundDoesNotRemoveTheSourceOrInventTheTarget() = runTest {
        val api = FakeApi().apply { moveFailure = ApiFailure(404, "missing") }
        val store = FakeStore()
        val controller = controller(api, store, FakeHost(this))

        controller.moveRecipe(RECIPE.id, TARGET.cookbookId).join()

        assertEquals(RECIPE_DETAIL, store.detail(SOURCE, RECIPE.id))
        assertNull(store.detail(TARGET, RECIPE.id))
        assertEquals(RecipeActionOutcome.FAILED, controller.state.value.outcome)
    }

    @Test
    fun deleteNotFoundIsAcknowledgedAndALocalRemovalFailureHidesUntilPurgeRecovery() = runTest {
        val api = FakeApi().apply { deleteFailure = ApiFailure(404, "missing") }
        val store = FakeStore().apply { removeFailure = IOException("disk full") }
        val host = FakeHost(this)
        val controller = controller(api, store, host)

        controller.deleteRecipe(RECIPE.id).join()

        assertEquals(listOf(SOURCE to RECIPE.id), host.pendingRemovals)
        assertEquals(RecipeActionOutcome.RECONCILIATION_REQUIRED, controller.state.value.outcome)
        assertTrue(controller.state.value.canRetryReconciliation)
    }

    @Test
    fun cookbookSwitchAllowsCapturedScopeCommitButSameUserReloginRejectsTheLateCommit() = runTest {
        val response = CompletableDeferred<RecipeDetail>()
        val api = FakeApi().apply { updateBlock = { response.await() } }
        val switchedHost = FakeHost(this)
        val switchedStore = FakeStore()
        val switched = controller(api, switchedStore, switchedHost)

        val switchingAction = switched.saveRecipe(changedDraft(), null)
        runCurrent()
        switchedHost.activeScope = TARGET
        response.complete(UPDATED)
        switchingAction.join()

        assertEquals(UPDATED, switchedStore.detail(SOURCE, RECIPE.id))
        assertEquals(0, switchedHost.publishedDetails)

        val reloginResponse = CompletableDeferred<RecipeDetail>()
        api.updateBlock = { reloginResponse.await() }
        val reloginHost = FakeHost(this)
        val reloginStore = FakeStore()
        val relogin = controller(api, reloginStore, reloginHost)
        val late = relogin.saveRecipe(changedDraft(), null)
        runCurrent()
        reloginHost.generation++
        reloginResponse.complete(UPDATED)
        late.join()

        assertEquals(RECIPE_DETAIL, reloginStore.detail(SOURCE, RECIPE.id))
    }

    @Test
    fun failedIngredientSubmissionFreezesExactPayloadAndNeverAutomaticallyPostsAgain() = runTest {
        val api = FakeApi().apply { shoppingFailure = IOException("disconnected") }
        val controller = controller(api, FakeStore(), FakeHost(this))
        val payload = listOf(ShoppingItemInput("stable", "onion", "2 cups", null, RECIPE.id))

        controller.addReviewedIngredients(RECIPE.id, payload).join()

        assertEquals(1, api.shoppingRequests.size)
        assertEquals(payload, controller.state.value.frozenShoppingItems)
        assertEquals(RecipeActionOutcome.AMBIGUOUS, controller.state.value.outcome)
        assertEquals("stable", api.shoppingRequests.single().items.single().clientId)

        api.shoppingFailure = null
        controller.addReviewedIngredients(RECIPE.id, controller.state.value.frozenShoppingItems).join()

        assertEquals(2, api.shoppingRequests.size)
        assertEquals(api.shoppingRequests[0], api.shoppingRequests[1])
        assertEquals(RecipeActionOutcome.SUCCEEDED, controller.state.value.outcome)
        assertEquals(1, controller.state.value.acknowledgedCount)
    }

    @Test
    fun ingredientSubmissionDoesNotCancelRecipeReadsOrTriggerRecipeSettlement() = runTest {
        val api = FakeApi()
        val repository = CatalogRepository(api, FakeStore())
        val host = FakeHost(this)
        val controller = RecipeActionController(api, repository, host) { _, _ -> error("unused") }
        val readEntered = CompletableDeferred<Unit>()
        val releaseRead = CompletableDeferred<Unit>()
        val read = launch {
            repository.withRecipeRead {
                readEntered.complete(Unit)
                releaseRead.await()
            }
        }
        readEntered.await()

        controller.addReviewedIngredients(
            RECIPE.id,
            listOf(ShoppingItemInput("stable", "onion", null, null, RECIPE.id)),
        ).join()

        assertTrue(read.isActive)
        assertEquals(0, host.settleCalls)
        assertEquals(1, api.shoppingRequests.size)
        releaseRead.complete(Unit)
        read.join()
    }

    @Test
    fun ingredientAuthorizationFailureUsesSessionRecoveryWithoutLeavingBusyState() = runTest {
        val api = FakeApi().apply { shoppingFailure = ApiFailure(403, "access changed") }
        val host = FakeHost(this).apply { onAuthorizationFailure = { activeScope = null } }
        val controller = controller(api, FakeStore(), host)

        controller.addReviewedIngredients(
            RECIPE.id,
            listOf(ShoppingItemInput("stable", "onion", null, null, RECIPE.id)),
        ).join()

        assertEquals(1, host.authorizationFailures)
        assertEquals(RecipeActionOutcome.IDLE, controller.state.value.outcome)
    }

    @Test
    fun duplicateActionIsRejectedWithoutSupersedingTheFirstResult() = runTest {
        val response = CompletableDeferred<RecipeDetail>()
        val api = FakeApi().apply { updateBlock = { response.await() } }
        val controller = controller(api, FakeStore(), FakeHost(this))

        val first = controller.saveRecipe(changedDraft(), null)
        runCurrent()
        val second = controller.saveRecipe(changedDraft(), null)
        runCurrent()

        assertEquals(1, api.updateCalls)
        assertEquals(RecipeActionOutcome.RUNNING, controller.state.value.outcome)
        assertTrue(controller.state.value.isBusy)
        assertTrue(second.isCompleted)
        response.complete(UPDATED)
        first.join()
        assertEquals(1, api.updateCalls)
        assertEquals(RecipeActionOutcome.SUCCEEDED, controller.state.value.outcome)
        assertFalse(controller.state.value.isBusy)
    }

    @Test
    fun actionRemainsBusyUntilPostMutationReadSettlementFinishes() = runTest {
        val settleStarted = CompletableDeferred<Unit>()
        val releaseSettle = CompletableDeferred<Unit>()
        val api = FakeApi()
        val host = FakeHost(this).apply {
            onSettle = {
                settleStarted.complete(Unit)
                releaseSettle.await()
            }
        }
        val controller = controller(api, FakeStore(), host)

        val first = controller.saveRecipe(changedDraft(), null)
        settleStarted.await()
        val rejected = controller.deleteRecipe(RECIPE.id)

        assertTrue(rejected.isCompleted)
        assertTrue(controller.state.value.isBusy)
        assertEquals(0, api.deleteCalls)
        releaseSettle.complete(Unit)
        first.join()
        assertFalse(controller.state.value.isBusy)
    }

    @Test
    fun actionRemainsGloballyBusyAcrossScopeSwitchAndSettlesWhenCapturedWorkFinishes() = runTest {
        val response = CompletableDeferred<RecipeDetail>()
        val api = FakeApi().apply { updateBlock = { response.await() } }
        val host = FakeHost(this)
        val controller = controller(api, FakeStore(), host)

        val first = controller.saveRecipe(changedDraft(), null)
        runCurrent()
        host.activeScope = TARGET
        controller.scopeChanged()
        val rejected = controller.saveRecipe(changedDraft().copy(scope = TARGET), null)

        assertTrue(rejected.isCompleted)
        assertEquals(RecipeActionOutcome.RUNNING, controller.state.value.outcome)
        response.complete(UPDATED)
        first.join()
        assertEquals(1, api.updateCalls)
        assertEquals(RecipeActionOutcome.IDLE, controller.state.value.outcome)
    }

    @Test
    fun finalPublicationKeepsAdmissionOwnedUntilBusyStateIsSettled() = runTest {
        val finalPublishStarted = CompletableDeferred<Unit>()
        val releaseFinalPublish = CompletableDeferred<Unit>()
        val host = FakeHost(this).apply {
            onCanPublish = { call ->
                if (call == 4) {
                    finalPublishStarted.complete(Unit)
                    releaseFinalPublish.await()
                }
            }
        }
        val api = FakeApi()
        val controller = controller(api, FakeStore(), host)

        val first = controller.saveRecipe(changedDraft(), null)
        finalPublishStarted.await()
        val rejected = controller.deleteRecipe(RECIPE.id)

        assertTrue(rejected.isCompleted)
        assertEquals(0, api.deleteCalls)
        assertTrue(controller.state.value.isBusy)
        releaseFinalPublish.complete(Unit)
        first.join()
        assertFalse(controller.state.value.isBusy)

        controller.deleteRecipe(RECIPE.id).join()
        assertEquals(1, api.deleteCalls)
    }

    @Test
    fun clearActionStateDoesNotRequireAnActiveCookbook() = runTest {
        val api = FakeApi().apply { updateFailure = ApiFailure(422, "invalid") }
        val host = FakeHost(this)
        val controller = controller(api, FakeStore(), host)
        controller.saveRecipe(changedDraft(), null).join()
        host.activeScope = null

        controller.clearRecipeAction().join()

        assertEquals(RecipeActionState(), controller.state.value)
    }

    @Test
    fun forbiddenRecoveryThatRemovesTheActiveCookbookCannotLeaveActionRunning() = runTest {
        val api = FakeApi().apply { updateFailure = ApiFailure(403, "access changed") }
        val host = FakeHost(this).apply {
            onAuthorizationFailure = { activeScope = null }
        }
        val controller = controller(api, FakeStore(), host)

        controller.saveRecipe(changedDraft(), null).join()

        assertEquals(1, host.authorizationFailures)
        assertEquals(RecipeActionOutcome.IDLE, controller.state.value.outcome)
    }

    @Test
    fun coverValidationAndMissingRecipeStatusesHaveDistinctRecovery() = runTest {
        val image = File.createTempFile("recipe-cover-status", ".jpg")
        val staged = PreparedRecipeImage("/private/photo.jpg", 7, "photo")
        val host = FakeHost(this)
        val store = FakeStore()
        val api = FakeApi().apply { coverFailure = ApiFailure(422, "Choose a smaller image") }
        val controller = controller(api, store, host) { _, _ -> image }

        controller.saveRecipe(changedDraft(), staged).join()

        assertEquals(RecipeActionOutcome.PARTIAL, controller.state.value.outcome)
        assertEquals("Choose a smaller image", controller.state.value.message)
        assertTrue(controller.state.value.needsPhotoSelection)
        assertFalse(controller.state.value.canRetryPhoto)

        api.coverFailure = ApiFailure(404, "missing")
        controller.saveRecipe(changedDraft(), staged).join()

        assertNull(store.detail(SOURCE, RECIPE.id))
        assertFalse(controller.state.value.canRetryPhoto)
        assertEquals(RecipeActionOutcome.FAILED, controller.state.value.outcome)
        image.delete()
    }

    @Test
    fun unexpectedResolverIoFailureBecomesChooseAgainWithoutCrashingTheJob() = runTest {
        val api = FakeApi()
        val controller = controller(api, FakeStore(), FakeHost(this)) { _, _ -> throw IOException("evicted") }

        val job = controller.saveRecipe(changedDraft(), PreparedRecipeImage("/private/photo.jpg", 7, "photo"))
        job.join()

        assertFalse(job.isCancelled)
        assertEquals(0, api.coverCalls)
        assertTrue(controller.state.value.needsPhotoSelection)
    }

    @Test
    fun postMutationReadRecoveryRunsAfterTheMutationPermitIsReleasedOnFailure() = runTest {
        val api = FakeApi().apply { updateFailure = ApiFailure(422, "invalid") }
        val store = FakeStore()
        lateinit var repository: CatalogRepository
        val host = FakeHost(this).apply {
            onSettle = { repository.withRecipeRead { } }
        }
        repository = CatalogRepository(api, store)
        val controller = RecipeActionController(api, repository, host) { _, _ -> error("unused") }

        controller.saveRecipe(changedDraft(), null).join()

        assertEquals(1, host.settleCalls)
        assertEquals(RecipeActionOutcome.FAILED, controller.state.value.outcome)
    }

    private fun controller(
        api: FakeApi,
        store: FakeStore,
        host: FakeHost,
        discard: suspend (PreparedRecipeImage) -> Unit = {},
        resolve: suspend (PreparedRecipeImage, Long) -> File = { _, _ -> error("unused") },
    ) = RecipeActionController(api, CatalogRepository(api, store), host, discard, resolve)

    private fun changedDraft() = RecipeEditDraft(
        SOURCE,
        RECIPE.id,
        RecipeEditValues("Soup", "10", "20", "4", listOf(RecipeEditRow("i", "onion")), listOf(RecipeEditRow("s", "cook")), "", ""),
        RecipeEditValues("Soup!", "", "", "", emptyList(), emptyList(), "", ""),
    )

    private class FakeHost(private val scope: CoroutineScope) : RecipeActionHost {
        var generation = 1L
        var activeScope: RecipeScope? = SOURCE
        var settleCalls = 0
        var settleResult = false
        var preparationAllowed = true
        var authorizationFailures = 0
        var onAuthorizationFailure: suspend () -> Unit = {}
        var publishedDetails = 0
        var onSettle: suspend () -> Unit = {}
        var onCanPublish: suspend (Int) -> Unit = {}
        var canPublishCalls = 0
        val pendingRemovals = mutableListOf<Pair<RecipeScope, Long>>()

        override fun launch(block: suspend (RecipeActionContext) -> Unit): Job {
            val capturedScope = activeScope ?: return Job().apply { complete() }
            val context = RecipeActionContext(generation, USER.id, TOKEN, capturedScope, listOf(RECIPE), setOf(1, 2))
            return scope.launch { block(context) }
        }

        override suspend fun prepareMutation(context: RecipeActionContext) = preparationAllowed && canCommit(context)
        override suspend fun canCommit(context: RecipeActionContext) = context.generation == generation
        override suspend fun canPublish(context: RecipeActionContext): Boolean {
            canPublishCalls++
            onCanPublish(canPublishCalls)
            return canCommit(context) && activeScope == context.scope
        }
        override suspend fun publishDetails(context: RecipeActionContext, scope: RecipeScope, recipeId: Long) {
            if (canPublish(context) && scope == activeScope) publishedDetails++
        }
        override suspend fun publishRemoval(context: RecipeActionContext, scope: RecipeScope, recipeId: Long) = Unit
        override suspend fun publishRemovalCount(context: RecipeActionContext, targetCookbookId: Long?) = Unit
        override suspend fun retainPendingRemoval(context: RecipeActionContext, scope: RecipeScope, recipeId: Long, failure: Throwable) {
            pendingRemovals += scope to recipeId
        }
        override suspend fun settleRecipeReads(context: RecipeActionContext): Boolean {
            settleCalls++
            onSettle()
            return settleResult
        }
        override suspend fun handleAuthorizationFailure(context: RecipeActionContext, failure: ApiFailure): Boolean {
            authorizationFailures++
            onAuthorizationFailure()
            return true
        }
    }

    private class FakeStore : CatalogStore {
        private val details = mutableMapOf((SOURCE to RECIPE.id) to RECIPE_DETAIL)
        val mutationOrder = mutableListOf<Pair<RecipeScope, Long>>()
        var saveFailure: Throwable? = null
        var removeFailure: Throwable? = null
        override suspend fun cookbooks(userId: Long) = listOf(Cookbook(1, "Source", true, 1, emptyList()), Cookbook(2, "Target", false, 0, emptyList()))
        override suspend fun replaceCookbooks(userId: Long, items: List<Cookbook>) = Unit
        override suspend fun selectedCookbookId(userId: Long): Long? = 1
        override suspend fun selectCookbook(userId: Long, cookbookId: Long) = Unit
        override suspend fun recipes(scope: RecipeScope) = CachedRecipes(if (scope == SOURCE) listOf(RECIPE) else emptyList(), true)
        override suspend fun replaceRecipes(scope: RecipeScope, items: List<RecipeSummary>) = Unit
        override suspend fun detail(scope: RecipeScope, recipeId: Long) = details[scope to recipeId]
        override suspend fun saveRecipeDetails(scope: RecipeScope, details: List<RecipeDetail>) {
            saveFailure?.let { throw it }
            details.forEach { this.details[scope to it.id] = it }
        }
        override suspend fun searchDocuments(scope: RecipeScope) = emptyList<RecipeSearchDocument>()
        override suspend fun upsertPartialRecipe(scope: RecipeScope, knownSummary: RecipeSummary, detail: RecipeDetail) {
            mutationOrder += scope to detail.id
            details[scope to detail.id] = detail
        }
        override suspend fun removeRecipe(scope: RecipeScope, recipeId: Long) {
            mutationOrder += scope to recipeId
            removeFailure?.let { throw it }
            details.remove(scope to recipeId)
        }
        override suspend fun removeCookbook(scope: RecipeScope) = Unit
        override suspend fun clear() = Unit
    }

    private class FakeApi : MainCourseApi {
        var updateCalls = 0
        var coverCalls = 0
        var deleteCalls = 0
        var updateResult = UPDATED
        var coverResult = UPDATED
        var moveResult = UPDATED
        var updateFailure: Throwable? = null
        var coverFailure: Throwable? = null
        var moveFailure: Throwable? = null
        var deleteFailure: Throwable? = null
        var shoppingFailure: Throwable? = null
        var updateBlock: (suspend () -> RecipeDetail)? = null
        val shoppingRequests = mutableListOf<ShoppingItemsRequest>()
        override suspend fun updateRecipe(token: String, cookbookId: Long, recipeId: Long, request: RecipeUpdateRequest): RecipeDetail {
            updateCalls++
            updateFailure?.let { throw it }
            return updateBlock?.invoke() ?: updateResult
        }
        override suspend fun updateRecipeCover(token: String, cookbookId: Long, recipeId: Long, image: File): RecipeDetail {
            coverCalls++
            coverFailure?.let { throw it }
            return coverResult
        }
        override suspend fun moveRecipe(token: String, sourceCookbookId: Long, recipeId: Long, targetCookbookId: Long): RecipeDetail {
            moveFailure?.let { throw it }
            return moveResult
        }
        override suspend fun deleteRecipe(token: String, cookbookId: Long, recipeId: Long) {
            deleteCalls++
            deleteFailure?.let { throw it }
        }
        override suspend fun addRecipeIngredients(token: String, cookbookId: Long, request: ShoppingItemsRequest): List<ShoppingItem> {
            shoppingRequests += request
            shoppingFailure?.let { throw it }
            return request.items.mapIndexed { index, item ->
                ShoppingItem(index.toLong(), item.clientId, item.name, item.details, item.checkedAt, item.sourceRecipeId, "now", "now")
            }
        }
        override suspend fun signIn(request: SignInRequest): SessionResponse = error("unused")
        override suspend fun signInWithGoogle(request: GoogleSignInRequest): SessionResponse = error("unused")
        override suspend fun startAppleAuthentication(request: AppleAuthenticationStartRequest): AppleAuthenticationStartResponse = error("unused")
        override suspend fun exchangeAppleAuthentication(request: AppleAuthenticationExchangeRequest): SessionResponse = error("unused")
        override suspend fun signUp(request: SignUpRequest): SessionResponse = error("unused")
        override suspend fun signOut(token: String) = Unit
        override suspend fun updateAccount(token: String, request: AccountUpdateRequest): User = error("unused")
        override suspend fun deleteAccount(token: String) = Unit
        override suspend fun submitOnboarding(request: OnboardingRequest): OnboardingResponse = error("unused")
        override suspend fun cookbooks(token: String) = emptyList<Cookbook>()
        override suspend fun recipes(token: String, cookbookId: Long) = emptyList<RecipeSummary>()
        override suspend fun recipe(token: String, cookbookId: Long, recipeId: Long) = error("unused")
        override suspend fun recipeBatch(token: String, cookbookId: Long, cursor: String?) = RecipeBatchResponse(emptyList(), null)
    }

    private companion object {
        const val TOKEN = "private-token"
        val USER = User(7, "Cook", "cook@example.com", true)
        val SOURCE = RecipeScope(USER.id, 1)
        val TARGET = RecipeScope(USER.id, 2)
        val RECIPE = RecipeSummary(10, "Soup", 10, 20, false, null, null, "completed", null, "2026-09-08T12:00:00Z")
        val RECIPE_DETAIL = RecipeDetail(10, "Soup", 10, 20, 4, false, listOf("onion"), emptyList(), listOf("cook"), null, null, emptyList(), null, null, "2026-09-08T10:00:00Z", "2026-09-08T12:00:00Z")
        val UPDATED = RECIPE_DETAIL.copy(name = "Soup!", ingredients = emptyList(), instructions = emptyList(), updatedAt = "2026-09-08T12:01:00Z")
    }
}
