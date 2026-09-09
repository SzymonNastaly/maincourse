package com.getmaincourse.app.features.session

import com.getmaincourse.app.data.cache.RecipeScope
import com.getmaincourse.app.data.images.PreparedRecipeImage
import com.getmaincourse.app.data.images.PreparedRecipeImageUnavailable
import com.getmaincourse.app.data.model.AccountAttributes
import com.getmaincourse.app.data.model.AccountUpdateRequest
import com.getmaincourse.app.data.model.AppleAuthenticationExchangeRequest
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.GoogleSignInRequest
import com.getmaincourse.app.data.model.RecipeSummary
import com.getmaincourse.app.data.model.SessionResponse
import com.getmaincourse.app.data.model.SignInRequest
import com.getmaincourse.app.data.model.SignUpRequest
import com.getmaincourse.app.data.network.ApiFailure
import com.getmaincourse.app.data.network.MainCourseApi
import com.getmaincourse.app.data.session.SessionStore
import com.getmaincourse.app.data.session.StoredSession
import com.getmaincourse.app.features.search.RecipeSearchCoordinator
import com.getmaincourse.app.features.search.RecipeSearchState
import com.getmaincourse.app.features.search.SearchHydrationStatus
import com.getmaincourse.app.features.recipes.RecipeActionContext
import com.getmaincourse.app.features.recipes.RecipeActionController
import com.getmaincourse.app.features.recipes.RecipeActionHost
import com.getmaincourse.app.features.recipes.RecipeActionState
import com.getmaincourse.app.features.recipes.RecipeEditDraft
import com.getmaincourse.app.features.recipes.RecipeImagePreparationState
import com.getmaincourse.app.features.recipes.RecipeImagePreparationStatus
import com.getmaincourse.app.features.recipes.RecipeEditorImageSelection
import com.getmaincourse.app.features.recipes.ShoppingItemInput
import com.getmaincourse.app.features.settings.AccountOperation
import com.getmaincourse.app.features.settings.AccountState
import java.io.File
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class SessionController(
    private val api: MainCourseApi,
    private val sessionStore: SessionStore,
    private val catalogRepository: CatalogRepository,
    private val baseUrl: String,
    private val clock: Clock,
    private val scope: CoroutineScope,
    private val imageCleanup: suspend () -> Unit,
    private val credentialStateCleanup: suspend () -> Unit = {},
    private val revokeTimeoutMillis: Long = 5_000,
    private val deleteTimeoutMillis: Long = 30_000,
    hydrationTimeoutMillis: Long = 120_000,
    searchDispatcher: CoroutineDispatcher = Dispatchers.Default,
    resolvePreparedImage: suspend (PreparedRecipeImage, Long) -> File = { _, _ ->
        throw PreparedRecipeImageUnavailable("Choose the photo again")
    },
    prepareRecipeImage: suspend (Long, String) -> PreparedRecipeImage = { _, _ ->
        throw PreparedRecipeImageUnavailable("Choose the photo again")
    },
    discardRecipeImage: suspend (PreparedRecipeImage) -> Unit = {},
) {
    private val mutableState = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = mutableState.asStateFlow()
    private val mutableAccountState = MutableStateFlow(AccountState())
    val accountState: StateFlow<AccountState> = mutableAccountState.asStateFlow()
    private val search = RecipeSearchCoordinator(
        scope = scope,
        loadDocuments = ::visibleSearchDocuments,
        searchDispatcher = searchDispatcher,
    )
    val searchState: StateFlow<RecipeSearchState> = search.state
    private val recipeHydrator = RecipeHydrator(catalogRepository, hydrationTimeoutMillis)

    private val transition = Mutex()
    private val credentialTransition = Mutex()
    private val catalogTransition = Mutex()
    private val cookbookTransition = Mutex()
    private val recipeRefreshTransition = Mutex()
    private val jobsLock = Any()
    private val rootJob = requireNotNull(scope.coroutineContext[Job]) { "SessionController scope requires a parent Job" }
    private val rootContext = scope.coroutineContext.minusKey(Job)
    private val controllerGeneration = AtomicLong()
    private var sessionLifetime: WorkLifetime? = null
    private var cookbookLifetime: WorkLifetime? = null
    private var refreshLifetime: WorkLifetime? = null
    private var catalogJob: Job? = null
    private var detailJob: Job? = null
    private var hydrationJob: Job? = null
    private var cookbookRecoveryJob: Job? = null
    private var authAttempt: Job? = null
    private var restoreJob: Job? = null
    private var cleanupJob: Job? = null
    private var cleanupCancelsAnonymous = false
    private var accountJob: Job? = null
    private var admission = Admission.INITIAL
    private var session: SessionResponse? = null
    private var userGeneration = 0L
    private var cookbookGeneration = 0L
    private var detailGeneration = 0L
    private val catalogRequest = AtomicLong()
    private val cookbookRequest = AtomicLong()
    private val detailRequest = AtomicLong()
    private val recipeRefreshRequest = AtomicLong()
    private val hydrationRequest = AtomicLong()
    private var pendingPurge: PendingPurge? = null
    private var pendingAccountPersistence: PendingAccountPersistence? = null
    private val recipeActions = RecipeActionController(
        api = api,
        repository = catalogRepository,
        host = object : RecipeActionHost {
            override fun launch(block: suspend (RecipeActionContext) -> Unit): Job = launchRecipeAction(block)

            override suspend fun prepareMutation(context: RecipeActionContext): Boolean {
                val userContext = recipeActionUserContext(context) ?: return false
                return retryPendingPurge(userContext)
            }

            override suspend fun canCommit(context: RecipeActionContext): Boolean =
                transition.withLock { isCurrentRecipeActionLocked(context) }

            override suspend fun canPublish(context: RecipeActionContext): Boolean = transition.withLock {
                isCurrentRecipeActionLocked(context) && mutableState.value.activeCookbookId == context.scope.cookbookId
            }

            override suspend fun publishDetails(
                context: RecipeActionContext,
                scope: RecipeScope,
                recipeId: Long,
            ) = publishRecipeActionDetails(context, scope, recipeId)

            override suspend fun publishRemoval(
                context: RecipeActionContext,
                scope: RecipeScope,
                recipeId: Long,
            ) = publishRecipeActionRemoval(context, scope, recipeId)

            override suspend fun publishRemovalCount(context: RecipeActionContext, targetCookbookId: Long?) =
                publishRecipeActionCounts(context, targetCookbookId)

            override suspend fun retainPendingRemoval(
                context: RecipeActionContext,
                scope: RecipeScope,
                recipeId: Long,
                failure: Throwable,
            ) = retainRecipeActionRemoval(context, scope, recipeId, failure)

            override suspend fun settleRecipeReads(context: RecipeActionContext): Boolean =
                settleRecipeActionReads(context)

            override suspend fun handleAuthorizationFailure(
                context: RecipeActionContext,
                failure: ApiFailure,
            ): Boolean = handleRecipeActionAuthorizationFailure(context, failure)
        },
        resolvePreparedImage = resolvePreparedImage,
        discardPreparedImage = discardRecipeImage,
    )
    val recipeActionState: StateFlow<RecipeActionState> = recipeActions.state
    private val prepareRecipeImageResource = prepareRecipeImage
    private val discardRecipeImageResource = discardRecipeImage
    private val mutableRecipeImagePreparationState = MutableStateFlow(RecipeImagePreparationState())
    val recipeImagePreparationState: StateFlow<RecipeImagePreparationState> =
        mutableRecipeImagePreparationState.asStateFlow()
    private val imagePreparationRequest = AtomicLong()
    private var imagePreparationJob: Job? = null

    init {
        rootJob.invokeOnCompletion { controllerGeneration.incrementAndGet() }
    }

    fun restore(): Job {
        lateinit var launched: Job
        synchronized(jobsLock) {
            if (admission != Admission.INITIAL && admission != Admission.RESTORE_FAILED) return completedJob()
            admission = Admission.RESTORING
            launched = scope.launch(start = CoroutineStart.LAZY) { performRestore() }
            restoreJob = launched
        }
        launched.invokeOnCompletion {
            synchronized(jobsLock) { if (restoreJob == launched) restoreJob = null }
        }
        launched.start()
        return launched
    }

    private suspend fun performRestore() {
        completeWithRecovery {
            mutableState.value = SessionState(phase = SessionPhase.RESTORING)
            val stored = try {
                sessionStore.read()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                val stillRestoring = synchronized(jobsLock) { admission == Admission.RESTORING }
                if (stillRestoring) {
                    transition.withLock {
                        mutableState.value = SessionState(
                            phase = SessionPhase.RESTORE_FAILED,
                            message = failure.userMessage("Could not read the saved session"),
                            canRetry = true,
                            canReset = true,
                        )
                    }
                    synchronized(jobsLock) {
                        if (admission == Admission.RESTORING) admission = Admission.RESTORE_FAILED
                    }
                }
                return@completeWithRecovery
            }

            if (stored == null || stored.baseUrl != baseUrl || isExpired(stored.response)) {
                cleanupProtectedState(
                    finalAuthError = null,
                    restoring = true,
                    notifyCredentialProvider = stored != null,
                )
                return@completeWithRecovery
            }

            establishSession(stored.response)?.join()
        }
    }

    fun signIn(request: SignInRequest): Job = authenticate("Could not sign in") { api.signIn(request) }

    fun signUp(request: SignUpRequest): Job = authenticate("Could not create account") { api.signUp(request) }

    fun signInWithGoogle(request: GoogleSignInRequest): Job =
        authenticate("Could not sign in with Google") { api.signInWithGoogle(request) }

    fun signInWithApple(request: AppleAuthenticationExchangeRequest): Job =
        authenticate("Could not sign in with Apple") { api.exchangeAppleAuthentication(request) }

    fun clearAuthenticationError() {
        synchronized(jobsLock) {
            if (admission == Admission.SIGNED_OUT) {
                mutableState.value = mutableState.value.copy(authError = null)
            }
        }
    }

    fun reportAuthenticationFailure(message: String) {
        synchronized(jobsLock) {
            if (admission == Admission.SIGNED_OUT) {
                mutableState.value = mutableState.value.copy(authError = message)
            }
        }
    }

    fun switchCookbook(id: Long): Job {
        if (mutableState.value.cookbooks.none { it.id == id }) return completedJob()
        val requestVersion = cookbookRequest.incrementAndGet()
        return scope.launch {
            completeWithRecovery {
                val context = activeContextOrExpire(deferCleanup = false) ?: return@completeWithRecovery
                if (mutableState.value.cookbooks.none { it.id == id }) return@completeWithRecovery
                val activation = activateCookbook(context, id, requestVersion) ?: return@completeWithRecovery
                startRecipeRefresh(activation).join()
            }
        }
    }

    fun refresh(): Job = scope.launch {
        completeWithRecovery {
            val context = activeContextOrExpire(deferCleanup = false) ?: return@completeWithRecovery
            if (!retryPendingPurge(context)) return@completeWithRecovery
            val current = mutableState.value
            val cookbookId = current.activeCookbookId
            if (cookbookId == null || current.catalogStatus != LoadStatus.FRESH) {
                startCatalogLoad(context).join()
            } else {
                val activation = Activation(
                    context = context,
                    recipeScope = RecipeScope(context.response.user.id, cookbookId),
                    cookbookGeneration = transition.withLock { cookbookGeneration },
                    requestVersion = cookbookRequest.get(),
                    allowForbiddenRecovery = true,
                )
                startRecipeRefresh(activation).join()
            }
        }
    }

    fun updateSearchQuery(query: String): Job = search.updateQuery(query)

    fun saveRecipe(draft: RecipeEditDraft, image: PreparedRecipeImage?): Job = recipeActions.saveRecipe(draft, image)

    fun retryRecipePhoto(image: PreparedRecipeImage? = null): Job = recipeActions.retryRecipePhoto(image)

    fun moveRecipe(recipeId: Long, targetId: Long): Job = recipeActions.moveRecipe(recipeId, targetId)

    fun deleteRecipe(recipeId: Long): Job = recipeActions.deleteRecipe(recipeId)

    fun addReviewedIngredients(recipeId: Long, items: List<ShoppingItemInput>): Job =
        recipeActions.addReviewedIngredients(recipeId, items)

    fun retryRecipeReconciliation(): Job = recipeActions.retryRecipeReconciliation()

    fun clearRecipeAction(): Job {
        val (cleared, pendingImage) = recipeActions.clearRecipeActionAndTakePendingPhoto()
        if (pendingImage == null) return cleared
        return scope.launch {
            cleared.join()
            runCatching { discardRecipeImageResource(pendingImage) }
        }
    }

    fun prepareRecipeImage(uri: String, requestKey: String = uri): Job = launchRecipeImageOperation { context, requestVersion ->
        val prior = transition.withLock {
            if (!isCurrentLocked(context.userContext, context.recipeScope.cookbookId, context.cookbookGeneration) ||
                requestVersion != imagePreparationRequest.get()
            ) {
                return@withLock null
            }
            val old = mutableRecipeImagePreparationState.value.image
            mutableRecipeImagePreparationState.value = RecipeImagePreparationState(
                status = RecipeImagePreparationStatus.PREPARING,
                scope = context.recipeScope,
                image = old,
                requestKey = requestKey,
            )
            old
        }
        if (prior != null) {
            try {
                discardRecipeImageResource(prior)
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                publishRecipeImageError(context, requestVersion, failure.userMessage("Could not replace the selected photo"))
                return@launchRecipeImageOperation
            }
            transition.withLock {
                if (isCurrentLocked(context.userContext, context.recipeScope.cookbookId, context.cookbookGeneration) &&
                    requestVersion == imagePreparationRequest.get()
                ) {
                    mutableRecipeImagePreparationState.value = mutableRecipeImagePreparationState.value.copy(image = null)
                }
            }
        }
        val prepared = try {
            prepareRecipeImageResource(context.userContext.response.user.id, uri)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            publishRecipeImageError(context, requestVersion, failure.userMessage("Could not prepare the selected photo"))
            return@launchRecipeImageOperation
        }
        withContext(NonCancellable) {
            val accepted = transition.withLock {
                if (isCurrentLocked(context.userContext, context.recipeScope.cookbookId, context.cookbookGeneration) &&
                    requestVersion == imagePreparationRequest.get()
                ) {
                    mutableRecipeImagePreparationState.value = RecipeImagePreparationState(
                        status = RecipeImagePreparationStatus.READY,
                        scope = context.recipeScope,
                        image = prepared,
                        requestKey = requestKey,
                    )
                    true
                } else {
                    false
                }
            }
            if (!accepted) {
                runCatching { discardRecipeImageResource(prepared) }
            }
        }
        currentCoroutineContext().ensureActive()
    }

    fun releaseRecipeEditorImage(selection: RecipeEditorImageSelection): Job {
        val preparation = synchronized(jobsLock) {
            val currentKey = mutableRecipeImagePreparationState.value.requestKey
            if (currentKey != null && currentKey == selection.requestKey) {
                imagePreparationRequest.incrementAndGet()
                imagePreparationJob
            } else {
                null
            }
        }
        return scope.launch {
            preparation?.cancelAndJoin()
            val publishedImage = transition.withLock {
                val current = mutableRecipeImagePreparationState.value
                if (current.requestKey != null && current.requestKey == selection.requestKey) {
                    mutableRecipeImagePreparationState.value = RecipeImagePreparationState()
                    current.image
                } else {
                    null
                }
            }
            listOfNotNull(publishedImage, selection.image).distinct().forEach { image ->
                if (!recipeActions.ownsPendingPhoto(selection.editorId, image)) {
                    runCatching { discardRecipeImageResource(image) }
                }
            }
        }
    }

    fun openRecipe(id: Long): Job {
        if (mutableState.value.recipes.none { it.id == id }) return completedJob()
        val requestVersion = detailRequest.incrementAndGet()
        return scope.launch {
            completeWithRecovery detail@{
                val context = activeContextOrExpire(deferCleanup = false) ?: return@detail
                val current = mutableState.value
                val cookbookId = current.activeCookbookId ?: return@detail
                val summary = current.recipes.firstOrNull { it.id == id } ?: return@detail
                val cookbookVersion = transition.withLock { cookbookGeneration }
                synchronized(jobsLock) { detailJob }?.cancelAndJoin()
                val detailVersion = transition.withLock {
                    if (!isCurrentLocked(context, cookbookId, cookbookVersion) ||
                        requestVersion != detailRequest.get()
                    ) {
                        return@withLock null
                    }
                    detailGeneration++
                    val version = detailGeneration
                    if (summary.importStatus != COMPLETED_IMPORT_STATUS) {
                        mutableState.value = mutableState.value.copy(
                            detail = RecipeDetailState(
                                recipeId = id,
                                status = DetailStatus.NOT_READY,
                                message = summary.errorMessage ?: "Recipe is still being prepared",
                            ),
                        )
                        return@withLock null
                    }
                    version
                } ?: return@detail

                startDetailLoad(
                    context = context,
                    recipeScope = RecipeScope(context.response.user.id, cookbookId),
                    recipeId = id,
                    cookbookVersion = cookbookVersion,
                    detailVersion = detailVersion,
                    requestVersion = requestVersion,
                ).join()
            }
        }
    }

    fun closeRecipe(): Job {
        val requestVersion = detailRequest.incrementAndGet()
        return scope.launch {
            synchronized(jobsLock) { detailJob }?.cancelAndJoin()
            transition.withLock {
                if (requestVersion == detailRequest.get() &&
                    synchronized(jobsLock) { admission == Admission.AUTHENTICATED }
                ) {
                    detailGeneration++
                    mutableState.value = mutableState.value.copy(detail = null)
                }
            }
        }
    }

    fun updateName(name: String): Job = updateAccount(AccountAttributes(name = name))

    fun updateLifecycleNotifications(enabled: Boolean): Job =
        updateAccount(AccountAttributes(lifecycleNotificationsEnabled = enabled))

    fun retryAccountPersistence(): Job = launchAccountOperation(
        operation = AccountOperation.SAVING,
        clearErrorOnStart = false,
    ) {
        val persistence = transition.withLock {
            pendingAccountPersistence?.takeIf { isCurrentLocked(it.context) }
        } ?: return@launchAccountOperation
        persistAcceptedAccount(persistence)
    }

    fun deleteAccount(): Job = launchAccountOperation(AccountOperation.DELETING) {
        performAccountDeletion()
    }

    fun clearAccountError(): Job = scope.launch {
        transition.withLock {
            mutableAccountState.value = mutableAccountState.value.copy(error = null)
        }
    }

    fun logout(): Job = requestCleanup(revoke = true)

    fun reset(): Job = requestCleanup(revoke = false)

    fun checkExpiry(): Job = scope.launch {
        activeContextOrExpire(deferCleanup = false)
        awaitRecovery()
    }

    private fun updateAccount(attributes: AccountAttributes): Job {
        require(attributes.name != null || attributes.lifecycleNotificationsEnabled != null) {
            "Account attributes cannot be empty"
        }
        return launchAccountOperation(AccountOperation.SAVING) {
            performAccountUpdate(attributes)
        }
    }

    private suspend fun performAccountUpdate(attributes: AccountAttributes) {
        val context = activeContextOrExpire() ?: return
        val currentUser = context.response.user
        if (attributes.name == currentUser.name && attributes.lifecycleNotificationsEnabled == null) return
        if (attributes.lifecycleNotificationsEnabled == currentUser.lifecycleNotificationsEnabled && attributes.name == null) {
            return
        }

        val updatedUser = try {
            api.updateAccount(context.identity.token, AccountUpdateRequest(attributes))
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            if (failure is ApiFailure && failure.status == 401) {
                invalidateAuthenticatedSession(context, failure.message)
            } else {
                setAccountError(context, failure.userMessage("Could not update account"))
            }
            return
        }

        if (updatedUser.id != context.identity.userId) {
            setAccountError(context, "The server returned a different account")
            return
        }

        val persistence = transition.withLock {
            if (!isCurrentLocked(context)) return@withLock null
            val acceptedResponse = checkNotNull(session).copy(user = updatedUser)
            session = acceptedResponse
            mutableState.value = mutableState.value.copy(user = updatedUser)
            PendingAccountPersistence(context, StoredSession(baseUrl, acceptedResponse)).also {
                pendingAccountPersistence = it
            }
        } ?: return
        persistAcceptedAccount(persistence)
    }

    private suspend fun persistAcceptedAccount(persistence: PendingAccountPersistence) {
        try {
            credentialTransition.withLock {
                val mayWrite = transition.withLock {
                    isCurrentLocked(persistence.context) && pendingAccountPersistence == persistence
                }
                if (!mayWrite) return
                sessionStore.write(persistence.storedSession)
            }
        } catch (failure: CancellationException) {
            withContext(NonCancellable) { markAccountPersistenceFailure(persistence) }
            throw failure
        } catch (_: Throwable) {
            markAccountPersistenceFailure(persistence)
            return
        }

        transition.withLock {
            if (isCurrentLocked(persistence.context) && pendingAccountPersistence == persistence) {
                pendingAccountPersistence = null
                mutableAccountState.value = mutableAccountState.value.copy(
                    canRetryPersistence = false,
                )
            }
        }
    }

    private suspend fun markAccountPersistenceFailure(persistence: PendingAccountPersistence) {
        transition.withLock {
            if (isCurrentLocked(persistence.context) && pendingAccountPersistence == persistence) {
                mutableAccountState.value = mutableAccountState.value.copy(
                    canRetryPersistence = true,
                )
            }
        }
    }

    private suspend fun performAccountDeletion() {
        val context = activeContextOrExpire() ?: return
        try {
            withTimeout(deleteTimeoutMillis) { api.deleteAccount(context.identity.token) }
            scheduleAuthenticatedCleanup(context, finalAuthError = null)
        } catch (_: TimeoutCancellationException) {
            setAccountError(context, DELETION_AMBIGUOUS_MESSAGE)
        } catch (failure: CancellationException) {
            withContext(NonCancellable) {
                setAccountError(context, DELETION_AMBIGUOUS_MESSAGE)
            }
            throw failure
        } catch (failure: Throwable) {
            when {
                failure is ApiFailure && failure.status == 401 ->
                    invalidateAuthenticatedSession(context, failure.message)
                failure is ApiFailure && failure.status != null ->
                    setAccountError(context, failure.userMessage("Could not delete account"))
                else -> setAccountError(context, DELETION_AMBIGUOUS_MESSAGE)
            }
        }
    }

    private suspend fun setAccountError(context: UserContext, message: String) {
        transition.withLock {
            if (isCurrentLocked(context)) {
                mutableAccountState.value = mutableAccountState.value.copy(error = message)
            }
        }
    }

    private fun launchAccountOperation(
        operation: AccountOperation,
        clearErrorOnStart: Boolean = true,
        block: suspend () -> Unit,
    ): Job {
        lateinit var launched: Job
        synchronized(jobsLock) {
            if (admission != Admission.AUTHENTICATED || accountJob?.isActive == true) return completedJob()
            val lifetime = sessionLifetime ?: return completedJob()
            launched = scope.launch(start = CoroutineStart.LAZY) {
                val worker = lifetime.scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    try {
                        transition.withLock {
                            mutableAccountState.value = mutableAccountState.value.copy(
                                operation = operation,
                                error = if (clearErrorOnStart) null else mutableAccountState.value.error,
                            )
                        }
                        block()
                    } finally {
                        withContext(NonCancellable) {
                            transition.withLock {
                                mutableAccountState.value = mutableAccountState.value.copy(
                                    operation = AccountOperation.IDLE,
                                )
                            }
                        }
                    }
                }
                try {
                    worker.join()
                } finally {
                    awaitRecovery()
                }
            }
            accountJob = launched
            launched.invokeOnCompletion {
                synchronized(jobsLock) {
                    if (accountJob == launched) accountJob = null
                }
            }
        }
        launched.start()
        return launched
    }

    private fun requestCleanup(revoke: Boolean): Job {
        lateinit var launched: Job
        val token: String?
        synchronized(jobsLock) {
            cleanupJob?.takeIf { it.isActive }?.let { return it }
            if (admission == Admission.CLEANING) return completedJob()
            admission = Admission.CLEANING
            token = session?.token
            launched = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                var revokeFailure: Throwable? = null
                withContext(NonCancellable) {
                    val cleanup = prepareCleanup(SessionPhase.SIGNING_OUT, ownerJob = null, includeAnonymous = true)
                    try {
                        retireCleanupWork(cleanup)
                        if (revoke && token != null) {
                            try {
                                withTimeout(revokeTimeoutMillis) { api.signOut(token) }
                            } catch (_: TimeoutCancellationException) {
                                // Local removal is authoritative for logout.
                            } catch (failure: CancellationException) {
                                revokeFailure = failure
                            } catch (_: Throwable) {
                                // Local removal is authoritative for logout.
                            }
                        }
                    } finally {
                        finishCleanup(finalAuthError = null, notifyCredentialProvider = true)
                    }
                }
                revokeFailure?.let { throw it }
                currentCoroutineContext().ensureActive()
            }
            cleanupJob = launched
        }
        launched.invokeOnCompletion {
            synchronized(jobsLock) { if (cleanupJob == launched) cleanupJob = null }
        }
        return launched
    }

    private fun authenticate(fallbackMessage: String, request: suspend () -> SessionResponse): Job {
        lateinit var launched: Job
        synchronized(jobsLock) {
            if (admission != Admission.SIGNED_OUT) return completedJob()
            admission = Admission.AUTHENTICATING
        }
        launched = scope.launch(start = CoroutineStart.LAZY) {
            val authenticationJob = currentCoroutineContext()[Job]
            transition.withLock {
                mutableState.value = SessionState(
                    phase = SessionPhase.LOADING_COOKBOOKS,
                    catalogStatus = LoadStatus.LOADING,
                )
            }

            try {
                val response = request()
                if (isExpired(response)) {
                    cleanupProtectedState("The returned session is already expired")
                    return@launch
                }
                try {
                    credentialTransition.withLock {
                        val mayWrite = synchronized(jobsLock) {
                            rootJob.isActive && admission == Admission.AUTHENTICATING &&
                                authAttempt == authenticationJob
                        }
                        if (!mayWrite) return@withLock
                        sessionStore.write(StoredSession(baseUrl, response))
                    }
                    if (!rootJob.isActive) return@launch
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: Throwable) {
                    cleanupProtectedState(failure.userMessage("Could not save the session"))
                    return@launch
                }

                val startup = establishSession(response)
                startup?.join()
                awaitRecovery()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                val stillAuthenticating = synchronized(jobsLock) { admission == Admission.AUTHENTICATING }
                transition.withLock {
                    if (stillAuthenticating && authAttempt == currentCoroutineContext()[Job]) {
                        mutableState.value = SessionState(
                            phase = SessionPhase.SIGNED_OUT,
                            authError = failure.userMessage(fallbackMessage),
                        )
                    }
                }
                synchronized(jobsLock) {
                    if (admission == Admission.AUTHENTICATING) admission = Admission.SIGNED_OUT
                }
            } finally {
                withContext(NonCancellable) {
                    val cancelledByCleanup = synchronized(jobsLock) {
                        cleanupCancelsAnonymous && authAttempt == authenticationJob
                    }
                    transition.withLock {
                        if (authAttempt == authenticationJob) authAttempt = null
                    }
                    if (!cancelledByCleanup) awaitRecovery(authenticationJob)
                }
            }
        }
        synchronized(jobsLock) { authAttempt = launched }
        launched.start()
        return launched
    }

    private suspend fun establishSession(response: SessionResponse): Job? {
        val context = transition.withLock {
            val accepted = synchronized(jobsLock) {
                if (!rootJob.isActive ||
                    (admission != Admission.RESTORING && admission != Admission.AUTHENTICATING)
                ) {
                    false
                } else {
                    admission = Admission.AUTHENTICATED
                    true
                }
            }
            if (!accepted) return@withLock null
            session = response
            pendingAccountPersistence = null
            userGeneration++
            cookbookGeneration++
            detailGeneration++
            mutableState.value = SessionState(
                phase = SessionPhase.LOADING_COOKBOOKS,
                user = response.user,
                catalogStatus = LoadStatus.LOADING,
                canReset = true,
            )
            mutableAccountState.value = AccountState()
            synchronized(jobsLock) {
                sessionLifetime = newLifetime(rootJob)
                cookbookLifetime = null
                refreshLifetime = null
            }
            UserContext(
                response = response,
                identity = SessionIdentity(
                    controllerGeneration.get(),
                    userGeneration,
                    response.user.id,
                    response.token,
                ),
            )
        } ?: return null
        return startCatalogLoad(context)
    }

    private fun startCatalogLoad(context: UserContext): Job {
        val requestVersion = catalogRequest.incrementAndGet()
        return scope.launch {
            val work = catalogTransition.withLock {
                if (!isCurrentCatalog(context, requestVersion)) return@withLock null
                synchronized(jobsLock) { catalogJob }?.cancelAndJoin()
                if (!isCurrentCatalog(context, requestVersion)) return@withLock null
                val lifetime = synchronized(jobsLock) { sessionLifetime } ?: return@withLock null
                lateinit var launched: Job
                launched = lifetime.scope.launch(start = CoroutineStart.LAZY) {
                    performCatalogLoad(context, requestVersion)
                }
                synchronized(jobsLock) { catalogJob = launched }
                launched.invokeOnCompletion {
                    synchronized(jobsLock) { if (catalogJob == launched) catalogJob = null }
                }
                launched.start()
                launched
            }
            work?.join()
        }
    }

    private suspend fun performCatalogLoad(context: UserContext, requestVersion: Long) {
        if (!retryPendingPurge(context)) return
        var hadCachedMembership = false
        try {
            val cached = catalogRepository.cachedCookbooks(context.response.user.id)
            if (isCurrentCatalog(context, requestVersion)) {
                hadCachedMembership = cached.isNotEmpty()
                if (cached.isNotEmpty()) {
                    val storedSelection = catalogRepository.selectedCookbookId(context.response.user.id)
                    reconcileCatalogSelection(
                        context = context,
                        memberships = cached,
                        catalogVersion = requestVersion,
                        fallbackPreferredId = storedSelection,
                    )
                }
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Throwable) {
            // Remote discovery below can still recover a broken cache read.
        }

        try {
            val remote = catalogRepository.discoverCookbooks(context.response) {
                requestVersion == catalogRequest.get()
            }
            if (!isCurrentCatalog(context, requestVersion)) return
            if (remote.isEmpty()) {
                val oldCookbook = synchronized(jobsLock) {
                    cookbookLifetime.also {
                        cookbookLifetime = null
                        refreshLifetime = null
                    }
                }
                oldCookbook?.job?.cancelAndJoin()
                search.activate(null)
                val imagesToDiscard = mutableListOf<PreparedRecipeImage>()
                transition.withLock {
                    if (isCurrentCatalogLocked(context, requestVersion)) {
                        cookbookGeneration++
                        imagePreparationRequest.incrementAndGet()
                        recipeActions.scopeChanged()?.let(imagesToDiscard::add)
                        mutableRecipeImagePreparationState.value.image?.let(imagesToDiscard::add)
                        mutableRecipeImagePreparationState.value = RecipeImagePreparationState()
                        mutableState.value = mutableState.value.copy(
                            phase = SessionPhase.READY,
                            cookbooks = emptyList(),
                            activeCookbookId = null,
                            recipes = emptyList(),
                            recipesFetched = false,
                            catalogStatus = LoadStatus.FRESH,
                            recipeStatus = LoadStatus.IDLE,
                            detail = null,
                            message = null,
                            canRetry = false,
                        )
                    }
                }
                imagesToDiscard.distinct().forEach { runCatching { discardRecipeImageResource(it) } }
                return
            }
            val activation = reconcileCatalogSelection(
                context = context,
                memberships = remote,
                catalogVersion = requestVersion,
            ) ?: return
            startRecipeRefresh(activation).join()
            transition.withLock {
                if (isCurrentCatalogLocked(context, requestVersion) &&
                    mutableState.value.activeCookbookId == activation.recipeScope.cookbookId
                ) {
                    mutableState.value = mutableState.value.copy(
                        catalogStatus = LoadStatus.FRESH,
                        message = mutableState.value.message.takeIf {
                            mutableState.value.recipeStatus.isFailure()
                        },
                        canRetry = mutableState.value.recipeStatus.isFailure(),
                    )
                }
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            if (!isCurrentCatalog(context, requestVersion)) return
            if (failure is ApiFailure && failure.status == 401) {
                invalidateAuthenticatedSession(context, failure.message)
            } else {
                transition.withLock {
                    if (isCurrentCatalogLocked(context, requestVersion)) {
                        val hasVisibleCache = mutableState.value.activeCookbookId != null || hadCachedMembership
                        mutableState.value = mutableState.value.copy(
                            phase = if (hasVisibleCache) SessionPhase.READY else SessionPhase.LOADING_COOKBOOKS,
                            catalogStatus = if (hasVisibleCache) LoadStatus.DEGRADED else LoadStatus.ERROR,
                            recipeStatus = if (
                                hasVisibleCache && !mutableState.value.recipesFetched &&
                                mutableState.value.recipeStatus == LoadStatus.LOADING
                            ) {
                                LoadStatus.ERROR
                            } else {
                                mutableState.value.recipeStatus
                            },
                            message = failure.userMessage("Could not load cookbooks"),
                            canRetry = true,
                            canReset = true,
                        )
                    }
                }
            }
        }
    }

    private suspend fun activateCookbook(
        context: UserContext,
        cookbookId: Long,
        requestVersion: Long = cookbookRequest.incrementAndGet(),
        allowForbiddenRecovery: Boolean = true,
        catalogVersion: Long? = null,
    ): Activation? = cookbookTransition.withLock {
        activateCookbookLocked(
            context,
            cookbookId,
            requestVersion,
            allowForbiddenRecovery,
            catalogVersion,
        )
    }

    private suspend fun reconcileCatalogSelection(
        context: UserContext,
        memberships: List<Cookbook>,
        catalogVersion: Long,
        fallbackPreferredId: Long? = null,
    ): Activation? = cookbookTransition.withLock {
        val selection = transition.withLock {
            if (!isCurrentCatalogLocked(context, catalogVersion)) return@withLock null
            val requestVersion = cookbookRequest.get()
            val activeCookbookId = mutableState.value.activeCookbookId
            val selected = chooseCookbook(memberships, activeCookbookId ?: fallbackPreferredId)
            if (selected.id == activeCookbookId) {
                mutableState.value = mutableState.value.copy(cookbooks = memberships)
                CatalogSelection(
                    selected.id,
                    requestVersion,
                    Activation(
                        context = context,
                        recipeScope = RecipeScope(context.response.user.id, selected.id),
                        cookbookGeneration = cookbookGeneration,
                        requestVersion = requestVersion,
                        allowForbiddenRecovery = true,
                    ),
                )
            } else {
                CatalogSelection(selected.id, requestVersion)
            }
        } ?: return@withLock null
        selection.currentActivation ?: activateCookbookLocked(
            context = context,
            cookbookId = selection.cookbookId,
            requestVersion = selection.requestVersion,
            allowForbiddenRecovery = true,
            catalogVersion = catalogVersion,
        )
    }

    private suspend fun activateCookbookLocked(
        context: UserContext,
        cookbookId: Long,
        requestVersion: Long,
        allowForbiddenRecovery: Boolean,
        catalogVersion: Long?,
    ): Activation? {
        if (requestVersion != cookbookRequest.get()) return null
        if (catalogVersion != null && catalogVersion != catalogRequest.get()) return null
        if (!isCurrent(context)) return null
        if (!retryPendingPurge(context)) return null
        val imagesToDiscard = mutableListOf<PreparedRecipeImage>()
        val lifetimeChange = transition.withLock {
            if (!isCurrentLocked(context)) return@withLock null
            if (requestVersion != cookbookRequest.get()) return@withLock null
            if (catalogVersion != null && catalogVersion != catalogRequest.get()) return@withLock null
            val sessionOwner = synchronized(jobsLock) { sessionLifetime } ?: return@withLock null
            val nextLifetime = newLifetime(sessionOwner.job)
            val oldLifetime = synchronized(jobsLock) {
                cookbookLifetime.also {
                    cookbookLifetime = nextLifetime
                    refreshLifetime = null
                }
            }
            cookbookGeneration++
            detailGeneration++
            imagePreparationRequest.incrementAndGet()
            recipeActions.scopeChanged()?.let(imagesToDiscard::add)
            mutableRecipeImagePreparationState.value.image?.let(imagesToDiscard::add)
            mutableRecipeImagePreparationState.value = RecipeImagePreparationState()
            mutableState.value = mutableState.value.copy(
                phase = SessionPhase.READY,
                activeCookbookId = cookbookId,
                recipes = emptyList(),
                recipesFetched = false,
                recipeStatus = LoadStatus.LOADING,
                detail = null,
                message = null,
                canRetry = false,
            )
            search.activate(RecipeScope(context.response.user.id, cookbookId), nextLifetime.scope)
            CookbookLifetimeChange(cookbookGeneration, oldLifetime)
        } ?: return null
        val version = lifetimeChange.generation
        lifetimeChange.old?.job?.cancelAndJoin()
        imagesToDiscard.distinct().forEach { runCatching { discardRecipeImageResource(it) } }
        catalogRepository.invalidateAndJoinRecipeReads()
        if (requestVersion != cookbookRequest.get()) return null
        if (catalogVersion != null && catalogVersion != catalogRequest.get()) return null

        val recipeScope = RecipeScope(context.response.user.id, cookbookId)
        return try {
            catalogRepository.selectCookbook(context.response.user.id, cookbookId)
            val cached = catalogRepository.cachedRecipes(recipeScope)
            val memberships = catalogRepository.cachedCookbooks(context.response.user.id)
            if (requestVersion != cookbookRequest.get()) return null
            if (catalogVersion != null && catalogVersion != catalogRequest.get()) return null
            transition.withLock {
                if (isCurrentLocked(context, cookbookId, version) &&
                    (catalogVersion == null || catalogVersion == catalogRequest.get())
                ) {
                    mutableState.value = mutableState.value.copy(
                        phase = SessionPhase.READY,
                        cookbooks = memberships,
                        activeCookbookId = cookbookId,
                        recipes = cached.items,
                        recipesFetched = cached.fetched,
                        recipeStatus = if (cached.fetched) LoadStatus.FRESH else LoadStatus.LOADING,
                    )
                    search.documentsChanged(recipeScope)
                }
            }
            Activation(context, recipeScope, version, requestVersion, allowForbiddenRecovery)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            transition.withLock {
                if (isCurrentLocked(context, cookbookId, version)) {
                    mutableState.value = mutableState.value.copy(
                        recipeStatus = LoadStatus.ERROR,
                        message = failure.userMessage("Could not read saved recipes"),
                        canRetry = true,
                    )
                }
            }
            null
        }
    }

    private fun startRecipeRefresh(activation: Activation): Job {
        val requestVersion = recipeRefreshRequest.incrementAndGet()
        hydrationRequest.incrementAndGet()
        search.setHydrationStatus(activation.recipeScope, SearchHydrationStatus.IDLE)
        return scope.launch {
            val work = recipeRefreshTransition.withLock {
                val cookbookOwner = synchronized(jobsLock) { cookbookLifetime } ?: return@withLock null
                val nextLifetime = newLifetime(cookbookOwner.job)
                val oldLifetime = synchronized(jobsLock) {
                    refreshLifetime.also { refreshLifetime = nextLifetime }
                }
                oldLifetime?.job?.cancelAndJoin()
                if (requestVersion != recipeRefreshRequest.get()) return@withLock null
                val launched = nextLifetime.scope.launch(start = CoroutineStart.LAZY) {
                    performRecipeRefresh(activation, requestVersion)
                }
                launched.start()
                launched
            }
            work?.join()
        }
    }

    private suspend fun performRecipeRefresh(activation: Activation, requestVersion: Long) {
        val mayRun = transition.withLock {
            if (isCurrentLocked(activation) && requestVersion == recipeRefreshRequest.get()) {
                mutableState.value = mutableState.value.copy(recipeStatus = LoadStatus.LOADING)
                true
            } else {
                false
            }
        }
        if (!mayRun) return
        try {
            val items = catalogRepository.refreshRecipes(activation.context.response, activation.recipeScope) {
                transition.withLock {
                    isCurrentLocked(activation) && requestVersion == recipeRefreshRequest.get()
                }
            }
            transition.withLock {
                if (isCurrentLocked(activation) && requestVersion == recipeRefreshRequest.get()) {
                    val selectedDetail = mutableState.value.detail
                    mutableState.value = mutableState.value.copy(
                        recipes = items,
                        recipesFetched = true,
                        recipeStatus = LoadStatus.FRESH,
                        detail = when {
                            selectedDetail == null -> null
                            items.none { it.id == selectedDetail.recipeId } -> RecipeDetailState(
                                selectedDetail.recipeId,
                                DetailStatus.UNAVAILABLE,
                                message = "Recipe is no longer available",
                            )
                            selectedDetail.status == DetailStatus.UNAVAILABLE -> null
                            else -> selectedDetail
                        },
                        message = null,
                        canRetry = false,
                    )
                    search.documentsChanged(activation.recipeScope)
                }
            }
            if (isCurrentRecipeRefresh(activation, requestVersion)) {
                startRecipeHydration(activation, items, requestVersion)
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            when {
                failure is ApiFailure && failure.status == 401 ->
                    invalidateAuthenticatedSession(activation.context, failure.message)
                failure is ApiFailure && failure.status == 403 ->
                    scheduleCookbookRecovery {
                        recoverForbiddenCookbook(
                        activation,
                        failure.message,
                        rediscover = activation.allowForbiddenRecovery,
                        )
                    }
                else -> transition.withLock {
                    if (isCurrentLocked(activation) && requestVersion == recipeRefreshRequest.get()) {
                        val cached = mutableState.value.recipesFetched
                        mutableState.value = mutableState.value.copy(
                            recipeStatus = if (cached) LoadStatus.DEGRADED else LoadStatus.ERROR,
                            message = failure.userMessage("Could not refresh recipes"),
                            canRetry = true,
                        )
                    }
                }
            }
        }
    }

    private fun startRecipeHydration(
        activation: Activation,
        recipes: List<RecipeSummary>,
        refreshVersion: Long,
    ): Job {
        val hydrationVersion = hydrationRequest.incrementAndGet()
        search.setHydrationStatus(activation.recipeScope, SearchHydrationStatus.HYDRATING)
        val lifetime = synchronized(jobsLock) { refreshLifetime } ?: return completedJob()
        lateinit var launched: Job
        launched = lifetime.scope.launch(start = CoroutineStart.LAZY) {
            try {
                recipeHydrator.hydrate(
                    session = activation.context.response,
                    scope = activation.recipeScope,
                    knownRecipes = recipes,
                    canCommit = {
                        isCurrentRecipeHydration(activation, refreshVersion, hydrationVersion)
                    },
                    onPageSaved = {
                        publishHydratedRecipes(activation, refreshVersion, hydrationVersion)
                    },
                )
                if (isCurrentRecipeHydration(activation, refreshVersion, hydrationVersion)) {
                    search.setHydrationStatus(activation.recipeScope, SearchHydrationStatus.COMPLETE)
                }
            } catch (_: TimeoutCancellationException) {
                if (ownsHydrationStatus(activation, refreshVersion, hydrationVersion)) {
                    search.setHydrationStatus(
                        activation.recipeScope,
                        SearchHydrationStatus.INCOMPLETE,
                        "Search details are incomplete. Cached results are still available.",
                    )
                }
            } catch (failure: CancellationException) {
                if (ownsHydrationStatus(activation, refreshVersion, hydrationVersion)) {
                    search.setHydrationStatus(
                        activation.recipeScope,
                        SearchHydrationStatus.INCOMPLETE,
                        "Search details are incomplete. Cached results are still available.",
                    )
                }
                throw failure
            } catch (failure: Throwable) {
                if (!ownsHydrationStatus(activation, refreshVersion, hydrationVersion)) return@launch
                when {
                    failure is ApiFailure && failure.status == 401 ->
                        invalidateAuthenticatedSession(activation.context, failure.message)
                    failure is ApiFailure && failure.status == 403 -> scheduleCookbookRecovery {
                        recoverForbiddenCookbook(
                            activation,
                            failure.message,
                            rediscover = activation.allowForbiddenRecovery,
                        )
                    }
                    else -> search.setHydrationStatus(
                        activation.recipeScope,
                        SearchHydrationStatus.INCOMPLETE,
                        "Search details are incomplete. Cached results are still available.",
                    )
                }
            }
        }
        synchronized(jobsLock) { hydrationJob = launched }
        launched.invokeOnCompletion {
            synchronized(jobsLock) { if (hydrationJob == launched) hydrationJob = null }
        }
        launched.start()
        return launched
    }

    private suspend fun publishHydratedRecipes(
        activation: Activation,
        refreshVersion: Long,
        hydrationVersion: Long,
    ) {
        val cached = catalogRepository.cachedRecipes(activation.recipeScope)
        transition.withLock {
            if (isCurrentLocked(activation) && refreshVersion == recipeRefreshRequest.get() &&
                hydrationVersion == hydrationRequest.get()
            ) {
                mutableState.value = mutableState.value.copy(
                    recipes = cached.items.withHiddenRecipeRemoved(activation.recipeScope),
                )
                search.documentsChanged(activation.recipeScope)
            }
        }
    }

    private suspend fun isCurrentRecipeRefresh(activation: Activation, refreshVersion: Long): Boolean =
        transition.withLock { isCurrentLocked(activation) && refreshVersion == recipeRefreshRequest.get() }

    private suspend fun isCurrentRecipeHydration(
        activation: Activation,
        refreshVersion: Long,
        hydrationVersion: Long,
    ): Boolean = transition.withLock {
        isCurrentLocked(activation) && refreshVersion == recipeRefreshRequest.get() &&
            hydrationVersion == hydrationRequest.get()
    }

    private suspend fun ownsHydrationStatus(
        activation: Activation,
        refreshVersion: Long,
        hydrationVersion: Long,
    ): Boolean = transition.withLock {
        isCurrentLocked(activation.context) &&
            mutableState.value.activeCookbookId == activation.recipeScope.cookbookId &&
            refreshVersion == recipeRefreshRequest.get() && hydrationVersion == hydrationRequest.get()
    }

    private suspend fun visibleSearchDocuments(recipeScope: RecipeScope) =
        catalogRepository.searchDocuments(recipeScope).let { documents ->
            transition.withLock {
                val hiddenId = hiddenRecipeIdLocked(recipeScope)
                if (hiddenId == null) documents else documents.filterNot { it.summary.id == hiddenId }
            }
        }

    private fun launchRecipeAction(block: suspend (RecipeActionContext) -> Unit): Job {
        lateinit var launched: Job
        synchronized(jobsLock) {
            if (admission != Admission.AUTHENTICATED) return completedJob()
            val lifetime = sessionLifetime ?: return completedJob()
            launched = scope.launch(start = CoroutineStart.LAZY) {
                val worker = lifetime.scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    val context = transition.withLock {
                        val response = session ?: return@withLock null
                        val cookbookId = mutableState.value.activeCookbookId ?: return@withLock null
                        RecipeActionContext(
                            generation = userGeneration,
                            userId = response.user.id,
                            token = response.token,
                            scope = RecipeScope(response.user.id, cookbookId),
                            recipes = mutableState.value.recipes,
                            cookbookIds = mutableState.value.cookbooks.map(Cookbook::id).toSet(),
                        )
                    } ?: return@launch
                    block(context)
                }
                try {
                    worker.join()
                } finally {
                    awaitRecovery()
                }
            }
        }
        launched.start()
        return launched
    }

    private fun launchRecipeImageOperation(
        block: suspend (RecipeImageOperationContext, Long) -> Unit,
    ): Job {
        lateinit var launched: Job
        val context: RecipeImageOperationContext
        val requestVersion: Long
        synchronized(jobsLock) {
            if (admission != Admission.AUTHENTICATED || imagePreparationJob?.isActive == true) return completedJob()
            val lifetime = cookbookLifetime ?: return completedJob()
            val response = session ?: return completedJob()
            val cookbookId = mutableState.value.activeCookbookId ?: return completedJob()
            context = RecipeImageOperationContext(
                userContext = UserContext(
                    response,
                    SessionIdentity(controllerGeneration.get(), userGeneration, response.user.id, response.token),
                ),
                recipeScope = RecipeScope(response.user.id, cookbookId),
                cookbookGeneration = cookbookGeneration,
            )
            requestVersion = imagePreparationRequest.incrementAndGet()
            launched = lifetime.scope.launch(start = CoroutineStart.LAZY) { block(context, requestVersion) }
            imagePreparationJob = launched
            launched.invokeOnCompletion {
                synchronized(jobsLock) {
                    if (imagePreparationJob == launched) imagePreparationJob = null
                }
            }
        }
        launched.start()
        return launched
    }

    private suspend fun publishRecipeImageError(
        context: RecipeImageOperationContext,
        requestVersion: Long,
        message: String,
    ) {
        transition.withLock {
            if (isCurrentLocked(context.userContext, context.recipeScope.cookbookId, context.cookbookGeneration) &&
                requestVersion == imagePreparationRequest.get()
            ) {
                mutableRecipeImagePreparationState.value = RecipeImagePreparationState(
                    status = RecipeImagePreparationStatus.ERROR,
                    scope = context.recipeScope,
                    image = mutableRecipeImagePreparationState.value.image,
                    message = message,
                    requestKey = mutableRecipeImagePreparationState.value.requestKey,
                )
            }
        }
    }

    private fun isCurrentRecipeActionLocked(context: RecipeActionContext): Boolean =
        rootJob.isActive && userGeneration == context.generation &&
            session?.user?.id == context.userId && session?.token == context.token

    private suspend fun recipeActionUserContext(context: RecipeActionContext): UserContext? = transition.withLock {
        if (!isCurrentRecipeActionLocked(context)) return@withLock null
        UserContext(
            checkNotNull(session),
            SessionIdentity(controllerGeneration.get(), context.generation, context.userId, context.token),
        )
    }

    private suspend fun publishRecipeActionDetails(
        context: RecipeActionContext,
        recipeScope: RecipeScope,
        recipeId: Long,
    ) {
        if (!transition.withLock {
                isCurrentRecipeActionLocked(context) && mutableState.value.activeCookbookId == context.scope.cookbookId
            }
        ) {
            return
        }
        val cached = runCatching { catalogRepository.cachedRecipes(recipeScope) }.getOrNull() ?: return
        val detail = runCatching { catalogRepository.cachedDetail(recipeScope, recipeId) }.getOrNull()
        transition.withLock {
            if (!isCurrentRecipeActionLocked(context)) return
            if (mutableState.value.activeCookbookId != recipeScope.cookbookId) return
            mutableState.value = mutableState.value.copy(
                recipes = cached.items.withHiddenRecipeRemoved(recipeScope),
                recipesFetched = cached.fetched,
                detail = mutableState.value.detail?.takeIf { it.recipeId == recipeId }?.let {
                    if (detail == null) it else RecipeDetailState(recipeId, DetailStatus.FRESH, detail)
                } ?: mutableState.value.detail,
            )
            search.documentsChanged(recipeScope)
        }
    }

    private suspend fun publishRecipeActionRemoval(
        context: RecipeActionContext,
        recipeScope: RecipeScope,
        recipeId: Long,
    ) {
        transition.withLock {
            if (!isCurrentRecipeActionLocked(context)) return
            if (mutableState.value.activeCookbookId != recipeScope.cookbookId) return
            mutableState.value = mutableState.value.copy(
                recipes = mutableState.value.recipes.filterNot { it.id == recipeId },
                detail = if (mutableState.value.detail?.recipeId == recipeId) {
                    RecipeDetailState(recipeId, DetailStatus.UNAVAILABLE, message = "Recipe is no longer available")
                } else {
                    mutableState.value.detail
                },
            )
            search.documentsChanged(recipeScope)
        }
    }

    private suspend fun retainRecipeActionRemoval(
        context: RecipeActionContext,
        recipeScope: RecipeScope,
        recipeId: Long,
        failure: Throwable,
    ) {
        transition.withLock {
            if (!isCurrentRecipeActionLocked(context)) return
            pendingPurge = PendingPurge.Recipe(context.generation, recipeScope, recipeId)
            if (mutableState.value.activeCookbookId == recipeScope.cookbookId) {
                mutableState.value = mutableState.value.copy(
                    recipes = mutableState.value.recipes.filterNot { it.id == recipeId },
                    recipeStatus = LoadStatus.ERROR,
                    detail = if (mutableState.value.detail?.recipeId == recipeId) {
                        RecipeDetailState(recipeId, DetailStatus.UNAVAILABLE, message = "Recipe is no longer available")
                    } else {
                        mutableState.value.detail
                    },
                    message = failure.userMessage("Could not remove acknowledged recipe data"),
                    canRetry = true,
                    canReset = true,
                )
                search.documentsChanged(recipeScope)
            }
        }
    }

    private suspend fun publishRecipeActionCounts(context: RecipeActionContext, targetCookbookId: Long?) {
        transition.withLock {
            if (!isCurrentRecipeActionLocked(context) || mutableState.value.activeCookbookId != context.scope.cookbookId) return
            mutableState.value = mutableState.value.copy(
                cookbooks = mutableState.value.cookbooks.map { cookbook ->
                    when (cookbook.id) {
                        context.scope.cookbookId -> cookbook.copy(
                            recipeCount = (cookbook.recipeCount - 1).coerceAtLeast(0),
                        )
                        targetCookbookId -> cookbook.copy(recipeCount = cookbook.recipeCount + 1)
                        else -> cookbook
                    }
                },
            )
        }
    }

    private suspend fun settleRecipeActionReads(context: RecipeActionContext): Boolean {
        val recovery = transition.withLock {
            if (!isCurrentRecipeActionLocked(context) || mutableState.value.activeCookbookId != context.scope.cookbookId) {
                return@withLock null
            }
            RecipeReadRecovery(
                activation = Activation(
                    context = UserContext(
                        checkNotNull(session),
                        SessionIdentity(controllerGeneration.get(), context.generation, context.userId, context.token),
                    ),
                    recipeScope = context.scope,
                    cookbookGeneration = cookbookGeneration,
                    requestVersion = cookbookRequest.get(),
                    allowForbiddenRecovery = true,
                ),
                selectedLoadingRecipeId = mutableState.value.detail
                    ?.takeIf { it.status == DetailStatus.LOADING }
                    ?.recipeId,
            )
        } ?: return false
        if (!retryPendingPurge(recovery.activation.context)) return false
        startRecipeRefresh(recovery.activation).join()
        val reopen = transition.withLock {
            recovery.selectedLoadingRecipeId?.takeIf { id ->
                isCurrentLocked(recovery.activation) && mutableState.value.detail?.recipeId == id &&
                    mutableState.value.recipes.any { it.id == id }
            }
        }
        if (reopen != null) openRecipe(reopen).join()
        return transition.withLock {
            isCurrentLocked(recovery.activation) && pendingPurge == null &&
                mutableState.value.recipeStatus == LoadStatus.FRESH &&
                mutableState.value.detail?.status != DetailStatus.LOADING
        }
    }

    private suspend fun handleRecipeActionAuthorizationFailure(
        context: RecipeActionContext,
        failure: ApiFailure,
    ): Boolean {
        val userContext = recipeActionUserContext(context) ?: return true
        if (failure.status == 401) {
            invalidateAuthenticatedSession(userContext, failure.message)
            return true
        }
        if (failure.status != 403) return false
        val activation = transition.withLock {
            if (mutableState.value.activeCookbookId == context.scope.cookbookId) {
                Activation(userContext, context.scope, cookbookGeneration, cookbookRequest.get(), true)
            } else {
                pendingPurge = PendingPurge.Cookbook(context.generation, context.scope)
                null
            }
        }
        if (activation != null) {
            scheduleCookbookRecovery { recoverForbiddenCookbook(activation, failure.message, rediscover = true) }
        } else {
            retryPendingPurge(userContext)
        }
        return true
    }

    private fun startDetailLoad(
        context: UserContext,
        recipeScope: RecipeScope,
        recipeId: Long,
        cookbookVersion: Long,
        detailVersion: Long,
        requestVersion: Long,
    ): Job {
        val lifetime = synchronized(jobsLock) { cookbookLifetime } ?: return completedJob()
        lateinit var launched: Job
        launched = lifetime.scope.launch(start = CoroutineStart.LAZY) {
        val cached = try {
            catalogRepository.cachedDetail(recipeScope, recipeId)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Throwable) {
            null
        }
        val mayRequest = transition.withLock {
            if (isCurrentLocked(context, recipeScope.cookbookId, cookbookVersion) &&
                detailGeneration == detailVersion && requestVersion == detailRequest.get()
            ) {
                mutableState.value = mutableState.value.copy(
                    detail = RecipeDetailState(recipeId, DetailStatus.LOADING, cached),
                )
                true
            } else {
                false
            }
        }
        if (!mayRequest) return@launch

        try {
            val detail = catalogRepository.refreshDetail(context.response, recipeScope, recipeId) {
                transition.withLock {
                    isCurrentLocked(context, recipeScope.cookbookId, cookbookVersion) &&
                        detailGeneration == detailVersion && requestVersion == detailRequest.get()
                }
            }
            val refreshedRecipes = runCatching { catalogRepository.cachedRecipes(recipeScope).items }.getOrNull()
            transition.withLock {
                if (isCurrentLocked(context, recipeScope.cookbookId, cookbookVersion) &&
                    detailGeneration == detailVersion && requestVersion == detailRequest.get() &&
                    mutableState.value.recipes.any { it.id == recipeId }
                ) {
                    mutableState.value = mutableState.value.copy(
                        recipes = refreshedRecipes?.withHiddenRecipeRemoved(recipeScope) ?: mutableState.value.recipes,
                        detail = RecipeDetailState(recipeId, DetailStatus.FRESH, detail),
                    )
                    search.documentsChanged(recipeScope)
                }
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            val stillCurrent = transition.withLock {
                isCurrentLocked(context, recipeScope.cookbookId, cookbookVersion) &&
                    detailGeneration == detailVersion && requestVersion == detailRequest.get()
            }
            if (!stillCurrent) return@launch
            when {
                failure is ApiFailure && failure.status == 401 -> invalidateAuthenticatedSession(context, failure.message)
                failure is ApiFailure && failure.status == 403 -> scheduleCookbookRecovery {
                    recoverForbiddenCookbook(
                        Activation(context, recipeScope, cookbookVersion, cookbookRequest.get(), true),
                        failure.message,
                        rediscover = true,
                    )
                }
                failure is ApiFailure && failure.status == 404 -> scheduleCookbookRecovery {
                    removeMissingRecipe(context, recipeScope, recipeId, cookbookVersion)
                }
                else -> transition.withLock {
                    if (isCurrentLocked(context, recipeScope.cookbookId, cookbookVersion) &&
                        detailGeneration == detailVersion && requestVersion == detailRequest.get()
                    ) {
                        mutableState.value = mutableState.value.copy(
                            detail = RecipeDetailState(
                                recipeId,
                                if (cached == null) DetailStatus.ERROR else DetailStatus.SAVED_OFFLINE,
                                cached,
                                failure.userMessage("Connect to load this recipe"),
                            ),
                        )
                    }
                }
            }
        }
        }
        synchronized(jobsLock) { detailJob = launched }
        launched.invokeOnCompletion {
            synchronized(jobsLock) { if (detailJob == launched) detailJob = null }
        }
        launched.start()
        return launched
    }

    private suspend fun removeMissingRecipe(
        context: UserContext,
        recipeScope: RecipeScope,
        recipeId: Long,
        expectedCookbookGeneration: Long,
    ) {
        if (!isCurrent(context)) return
        val transitionResult = transition.withLock {
            if (!isCurrentLocked(context, recipeScope.cookbookId, expectedCookbookGeneration)) {
                return@withLock null
            }
            val sessionOwner = synchronized(jobsLock) { sessionLifetime } ?: return@withLock null
            val nextLifetime = newLifetime(sessionOwner.job)
            val oldLifetime = synchronized(jobsLock) {
                cookbookLifetime.also {
                    cookbookLifetime = nextLifetime
                    refreshLifetime = null
                }
            }
            cookbookGeneration++
            detailGeneration++
            val bumpedGeneration = cookbookGeneration
            pendingPurge = PendingPurge.Recipe(context.generation, recipeScope, recipeId)
            mutableState.value = mutableState.value.copy(
                recipes = mutableState.value.recipes.filterNot { it.id == recipeId },
                detail = RecipeDetailState(
                    recipeId,
                    DetailStatus.UNAVAILABLE,
                    message = "Recipe is no longer available",
                ),
            )
            search.activate(recipeScope, nextLifetime.scope)
            bumpedGeneration to oldLifetime
        } ?: return
        val (bumpedGeneration, oldLifetime) = transitionResult
        oldLifetime?.job?.cancelAndJoin()
        if (!retryPendingPurge(context)) return
        transition.withLock {
            if (isCurrentLocked(context, recipeScope.cookbookId, bumpedGeneration)) {
                val priorRecipeStatus = mutableState.value.recipeStatus
                val settledRecipeStatus = if (priorRecipeStatus != LoadStatus.LOADING) {
                    priorRecipeStatus
                } else if (mutableState.value.recipesFetched) {
                    LoadStatus.FRESH
                } else {
                    LoadStatus.DEGRADED
                }
                mutableState.value = mutableState.value.copy(
                    recipeStatus = settledRecipeStatus,
                    message = if (priorRecipeStatus == LoadStatus.LOADING) {
                        mutableState.value.message.takeIf {
                            mutableState.value.catalogStatus.isFailure() || settledRecipeStatus.isFailure()
                        }
                    } else {
                        mutableState.value.message
                    },
                    canRetry = if (priorRecipeStatus == LoadStatus.LOADING) {
                        mutableState.value.catalogStatus.isFailure() || settledRecipeStatus.isFailure()
                    } else {
                        mutableState.value.canRetry
                    },
                )
            }
        }
    }

    private suspend fun recoverForbiddenCookbook(
        activation: Activation,
        message: String?,
        rediscover: Boolean,
    ) {
        val imagesToDiscard = mutableListOf<PreparedRecipeImage>()
        val oldLifetime = transition.withLock {
            if (!isCurrentLocked(activation)) return
            cookbookGeneration++
            detailGeneration++
            imagePreparationRequest.incrementAndGet()
            recipeActions.scopeChanged()?.let(imagesToDiscard::add)
            mutableRecipeImagePreparationState.value.image?.let(imagesToDiscard::add)
            mutableRecipeImagePreparationState.value = RecipeImagePreparationState()
            pendingPurge = PendingPurge.Cookbook(
                activation.context.generation,
                activation.recipeScope,
            )
            mutableState.value = mutableState.value.copy(
                cookbooks = mutableState.value.cookbooks.filterNot { it.id == activation.recipeScope.cookbookId },
                activeCookbookId = null,
                recipes = emptyList(),
                recipesFetched = false,
                recipeStatus = LoadStatus.IDLE,
                detail = null,
                catalogStatus = LoadStatus.LOADING,
                message = message ?: "Cookbook access changed",
                canRetry = true,
            )
            search.activate(null)
            synchronized(jobsLock) {
                cookbookLifetime.also {
                    cookbookLifetime = null
                    refreshLifetime = null
                }
            }
        }
        oldLifetime?.job?.cancelAndJoin()
        imagesToDiscard.distinct().forEach { runCatching { discardRecipeImageResource(it) } }
        if (!retryPendingPurge(activation.context)) return

        if (!rediscover) {
            transition.withLock {
                if (isCurrentLocked(activation.context)) {
                    mutableState.value = mutableState.value.copy(
                        phase = SessionPhase.LOADING_COOKBOOKS,
                        catalogStatus = LoadStatus.ERROR,
                        message = message ?: "Cookbook access changed",
                        canRetry = true,
                        canReset = true,
                    )
                }
            }
            return
        }

        try {
            val memberships = catalogRepository.discoverCookbooks(
                activation.context.response,
                excludingCookbookId = activation.recipeScope.cookbookId,
            )
            if (!isCurrent(activation.context)) return
            if (memberships.isEmpty()) {
                transition.withLock {
                    if (isCurrentLocked(activation.context)) {
                        mutableState.value = mutableState.value.copy(
                            phase = SessionPhase.READY,
                            cookbooks = emptyList(),
                            catalogStatus = LoadStatus.FRESH,
                            message = null,
                            canRetry = false,
                        )
                    }
                }
            } else {
                val selected = chooseCookbook(memberships, mutableState.value.activeCookbookId)
                activateCookbook(
                    activation.context,
                    selected.id,
                    allowForbiddenRecovery = false,
                )?.let { recovered ->
                    startRecipeRefresh(recovered).join()
                    transition.withLock {
                        if (isCurrentLocked(recovered)) {
                            mutableState.value = mutableState.value.copy(
                                catalogStatus = LoadStatus.FRESH,
                                message = mutableState.value.message.takeIf {
                                    mutableState.value.recipeStatus.isFailure()
                                },
                                canRetry = mutableState.value.recipeStatus.isFailure(),
                            )
                        }
                    }
                }
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            if (failure is ApiFailure && failure.status == 401) {
                invalidateAuthenticatedSession(activation.context, failure.message)
            } else {
                transition.withLock {
                    if (isCurrentLocked(activation.context)) {
                        mutableState.value = mutableState.value.copy(
                            phase = SessionPhase.LOADING_COOKBOOKS,
                            cookbooks = mutableState.value.cookbooks.filterNot {
                                it.id == activation.recipeScope.cookbookId
                            },
                            activeCookbookId = null,
                            recipes = emptyList(),
                            catalogStatus = LoadStatus.ERROR,
                            message = failure.userMessage("Could not rediscover cookbooks"),
                            canRetry = true,
                            canReset = true,
                        )
                    }
                }
            }
        }
    }

    private suspend fun retryPendingPurge(context: UserContext): Boolean {
        val purge = transition.withLock {
            pendingPurge?.takeIf { it.userGeneration == context.generation }
        } ?: return true
        return try {
            when (purge) {
                is PendingPurge.Cookbook -> catalogRepository.removeCookbook(purge.scope)
                is PendingPurge.Recipe -> catalogRepository.removeRecipe(purge.scope, purge.recipeId)
            }
            val retained = transition.withLock {
                when {
                    !isCurrentLocked(context) -> false
                    pendingPurge == purge -> {
                        pendingPurge = null
                        true
                    }
                    pendingPurge == null -> true
                    else -> false
                }
            }
            if (retained && purge is PendingPurge.Recipe) search.documentsChanged(purge.scope)
            retained
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            transition.withLock {
                if (isCurrentLocked(context) && pendingPurge == purge) {
                    mutableState.value = when (purge) {
                        is PendingPurge.Cookbook -> mutableState.value.copy(
                            phase = SessionPhase.LOADING_COOKBOOKS,
                            cookbooks = mutableState.value.cookbooks.filterNot {
                                it.id == purge.scope.cookbookId
                            },
                            activeCookbookId = null,
                            recipes = emptyList(),
                            recipesFetched = false,
                            catalogStatus = LoadStatus.ERROR,
                            recipeStatus = LoadStatus.IDLE,
                            detail = null,
                            message = failure.userMessage("Could not remove inaccessible cookbook data"),
                            canRetry = true,
                            canReset = true,
                        )
                        is PendingPurge.Recipe -> mutableState.value.copy(
                            recipes = mutableState.value.recipes.filterNot { it.id == purge.recipeId },
                            recipeStatus = LoadStatus.ERROR,
                            detail = if (
                                mutableState.value.detail == null ||
                                mutableState.value.detail?.recipeId == purge.recipeId
                            ) {
                                RecipeDetailState(
                                    purge.recipeId,
                                    DetailStatus.UNAVAILABLE,
                                    message = "Recipe is no longer available",
                                )
                            } else {
                                mutableState.value.detail
                            },
                            message = failure.userMessage("Could not remove unavailable recipe data"),
                            canRetry = true,
                            canReset = true,
                        )
                    }
                }
            }
            false
        }
    }

    private suspend fun scheduleCookbookRecovery(block: suspend () -> Unit): Job {
        val owner = currentCoroutineContext()[Job]
        lateinit var launched: Job
        synchronized(jobsLock) {
            val lifetime = sessionLifetime ?: return completedJob()
            launched = lifetime.scope.launch(start = CoroutineStart.LAZY) {
                owner?.join()
                block()
            }
            cookbookRecoveryJob = launched
        }
        launched.invokeOnCompletion {
            synchronized(jobsLock) { if (cookbookRecoveryJob == launched) cookbookRecoveryJob = null }
        }
        launched.start()
        return launched
    }

    private suspend fun awaitRecovery() = awaitRecovery(currentCoroutineContext()[Job])

    private suspend fun awaitRecovery(owner: Job?) {
        while (true) {
            val pending = synchronized(jobsLock) {
                if (cleanupCancelsAnonymous && (owner == authAttempt || owner == restoreJob)) return
                listOfNotNull(cleanupJob, cookbookRecoveryJob).firstOrNull { it.isActive && it != owner }
            } ?: return
            withContext(NonCancellable) { pending.join() }
        }
    }

    private suspend inline fun completeWithRecovery(crossinline block: suspend () -> Unit) {
        try {
            block()
        } finally {
            awaitRecovery()
        }
    }

    private suspend fun activeContextOrExpire(deferCleanup: Boolean = true): UserContext? {
        if (synchronized(jobsLock) { admission != Admission.AUTHENTICATED }) return null
        val context = transition.withLock {
            session?.let {
                UserContext(
                    response = it,
                    identity = SessionIdentity(controllerGeneration.get(), userGeneration, it.user.id, it.token),
                )
            }
        } ?: return null
        if (!isExpired(context.response)) return context
        if (deferCleanup) {
            scheduleAuthenticatedCleanup(context, SESSION_EXPIRED_MESSAGE)
        } else {
            cleanupProtectedState(SESSION_EXPIRED_MESSAGE)
        }
        return null
    }

    private suspend fun invalidateAuthenticatedSession(context: UserContext, message: String?) {
        if (!isCurrent(context)) return
        scheduleAuthenticatedCleanup(context, message ?: SESSION_EXPIRED_MESSAGE)
    }

    private suspend fun scheduleAuthenticatedCleanup(context: UserContext, finalAuthError: String?): Job {
        if (!isCurrent(context)) return completedJob()
        val ownerJob = currentCoroutineContext()[Job]
        val admitted = synchronized(jobsLock) {
            if (admission == Admission.CLEANING) false else {
                admission = Admission.CLEANING
                true
            }
        }
        if (!admitted) return synchronized(jobsLock) { cleanupJob } ?: completedJob()
        val cleanup = prepareCleanup(SessionPhase.SIGNING_OUT, ownerJob, includeAnonymous = false)
        lateinit var launched: Job
        launched = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                ownerJob?.join()
                try {
                    retireCleanupWork(cleanup)
                } finally {
                    finishCleanup(finalAuthError, notifyCredentialProvider = true)
                }
            }
        }
        synchronized(jobsLock) { cleanupJob = launched }
        launched.invokeOnCompletion {
            synchronized(jobsLock) { if (cleanupJob == launched) cleanupJob = null }
        }
        return launched
    }

    private suspend fun cleanupProtectedState(
        finalAuthError: String?,
        restoring: Boolean = false,
        notifyCredentialProvider: Boolean = true,
    ) {
        val admitted = synchronized(jobsLock) {
            if (admission == Admission.CLEANING) {
                false
            } else {
                admission = Admission.CLEANING
                true
            }
        }
        if (!admitted) return
        val callerContext = currentCoroutineContext()
        val ownerJob = callerContext[Job]
        withContext(NonCancellable) {
            try {
                val cleanup = prepareCleanup(
                    if (restoring) SessionPhase.RESTORING else SessionPhase.SIGNING_OUT,
                    ownerJob,
                    includeAnonymous = true,
                )
                retireCleanupWork(cleanup)
            } finally {
                finishCleanup(finalAuthError, notifyCredentialProvider)
            }
        }
        callerContext.ensureActive()
    }

    private suspend fun prepareCleanup(
        phase: SessionPhase,
        ownerJob: Job?,
        includeAnonymous: Boolean,
    ): CleanupWork = transition.withLock {
            userGeneration++
            cookbookGeneration++
            detailGeneration++
            catalogRequest.incrementAndGet()
            detailRequest.incrementAndGet()
            recipeRefreshRequest.incrementAndGet()
            hydrationRequest.incrementAndGet()
            imagePreparationRequest.incrementAndGet()
            session = null
            pendingPurge = null
            pendingAccountPersistence = null
            recipeActions.reset()
            mutableRecipeImagePreparationState.value = RecipeImagePreparationState()
            mutableState.value = SessionState(phase = phase)
            mutableAccountState.value = AccountState()
            search.clear()
            val oldSession = synchronized(jobsLock) {
                cleanupCancelsAnonymous = includeAnonymous
                sessionLifetime.also {
                    sessionLifetime = null
                    cookbookLifetime = null
                    refreshLifetime = null
                    catalogJob = null
                    detailJob = null
                    hydrationJob = null
                    imagePreparationJob = null
                }
            }
            CleanupWork(
                session = oldSession,
                anonymousJobs = synchronized(jobsLock) {
                    if (includeAnonymous) {
                        listOfNotNull(authAttempt, restoreJob).filterNot { it == ownerJob }
                    } else {
                        emptyList()
                    }
                },
            )
        }

    private suspend fun retireCleanupWork(cleanup: CleanupWork) {
        cleanup.session?.job?.cancelAndJoin()
        cleanup.anonymousJobs.forEach { it.cancel() }
        cleanup.anonymousJobs.forEach { it.cancelAndJoin() }
        catalogRepository.invalidateAndJoinRecipeReads()
    }

    private suspend fun finishCleanup(
        finalAuthError: String?,
        notifyCredentialProvider: Boolean,
    ) = withContext(NonCancellable) {
        coroutineScope {
            val providerCleanup = launch(start = CoroutineStart.UNDISPATCHED) {
                if (!notifyCredentialProvider) return@launch
                try {
                    credentialStateCleanup()
                } catch (_: Throwable) {
                    // Provider selection cleanup is bounded best effort and never gates local deletion.
                }
            }
            val failures = buildList {
                try {
                    credentialTransition.withLock { sessionStore.clear() }
                } catch (failure: Throwable) {
                    add(failure)
                }
                try {
                    catalogRepository.clear()
                } catch (failure: Throwable) {
                    add(failure)
                }
                try {
                    imageCleanup()
                } catch (failure: Throwable) {
                    add(failure)
                }
            }
            providerCleanup.join()
            transition.withLock {
                mutableState.value = if (failures.isEmpty()) {
                    SessionState(phase = SessionPhase.SIGNED_OUT, authError = finalAuthError)
                } else {
                    SessionState(
                        phase = SessionPhase.CLEANUP_FAILED,
                        message = failures.first().userMessage("Could not finish local cleanup"),
                        canRetry = true,
                        canReset = true,
                    )
                }
            }
            synchronized(jobsLock) {
                cleanupCancelsAnonymous = false
                admission = if (failures.isEmpty()) Admission.SIGNED_OUT else Admission.CLEANUP_FAILED
            }
        }
    }

    private suspend fun isCurrent(context: UserContext): Boolean = transition.withLock { isCurrentLocked(context) }

    private suspend fun isCurrentCatalog(context: UserContext, requestVersion: Long): Boolean =
        transition.withLock { isCurrentCatalogLocked(context, requestVersion) }

    private fun isCurrentLocked(context: UserContext): Boolean =
        rootJob.isActive && controllerGeneration.get() == context.identity.controllerGeneration &&
            userGeneration == context.identity.generation &&
            session?.user?.id == context.identity.userId &&
            session?.token == context.identity.token

    private fun isCurrentCatalogLocked(context: UserContext, requestVersion: Long): Boolean =
        isCurrentLocked(context) && requestVersion == catalogRequest.get()

    private fun isCurrentLocked(context: UserContext, cookbookId: Long, cookbookVersion: Long): Boolean =
        isCurrentLocked(context) && mutableState.value.activeCookbookId == cookbookId &&
            cookbookGeneration == cookbookVersion

    private fun isCurrentLocked(activation: Activation): Boolean = isCurrentLocked(
        activation.context,
        activation.recipeScope.cookbookId,
        activation.cookbookGeneration,
    ) && activation.requestVersion == cookbookRequest.get()

    private fun chooseCookbook(items: List<Cookbook>, preferredId: Long?): Cookbook =
        items.firstOrNull { it.id == preferredId } ?: items.firstOrNull { it.personal } ?: items.first()

    private fun isExpired(response: SessionResponse): Boolean = try {
        !Instant.parse(response.expiresAt).isAfter(clock.instant())
    } catch (_: Throwable) {
        true
    }

    private fun completedJob(): Job = Job(rootJob).apply { complete() }

    private fun newLifetime(parent: Job): WorkLifetime {
        val job = SupervisorJob(parent)
        return WorkLifetime(job, CoroutineScope(rootContext + job))
    }

    private fun LoadStatus.isFailure(): Boolean = this == LoadStatus.DEGRADED || this == LoadStatus.ERROR

    private fun List<RecipeSummary>.withHiddenRecipeRemoved(scope: RecipeScope): List<RecipeSummary> {
        val hiddenId = hiddenRecipeIdLocked(scope) ?: return this
        return filterNot { it.id == hiddenId }
    }

    private fun hiddenRecipeIdLocked(scope: RecipeScope): Long? =
        (pendingPurge as? PendingPurge.Recipe)
            ?.takeIf { it.userGeneration == userGeneration && it.scope == scope }
            ?.recipeId

    private fun Throwable.userMessage(fallback: String): String =
        if (this is ApiFailure) message?.takeIf { it.isNotBlank() } ?: fallback else fallback

    private data class SessionIdentity(
        val controllerGeneration: Long,
        val generation: Long,
        val userId: Long,
        val token: String,
    )

    private data class WorkLifetime(
        val job: Job,
        val scope: CoroutineScope,
    )

    private data class CookbookLifetimeChange(
        val generation: Long,
        val old: WorkLifetime?,
    )

    private data class CleanupWork(
        val session: WorkLifetime?,
        val anonymousJobs: List<Job>,
    )

    private data class UserContext(
        val response: SessionResponse,
        val identity: SessionIdentity,
    ) {
        val generation: Long get() = identity.generation
    }

    private data class PendingAccountPersistence(
        val context: UserContext,
        val storedSession: StoredSession,
    )

    private data class Activation(
        val context: UserContext,
        val recipeScope: RecipeScope,
        val cookbookGeneration: Long,
        val requestVersion: Long,
        val allowForbiddenRecovery: Boolean,
    )

    private data class CatalogSelection(
        val cookbookId: Long,
        val requestVersion: Long,
        val currentActivation: Activation? = null,
    )

    private data class RecipeReadRecovery(
        val activation: Activation,
        val selectedLoadingRecipeId: Long?,
    )

    private data class RecipeImageOperationContext(
        val userContext: UserContext,
        val recipeScope: RecipeScope,
        val cookbookGeneration: Long,
    )

    private sealed interface PendingPurge {
        val userGeneration: Long

        data class Cookbook(
            override val userGeneration: Long,
            val scope: RecipeScope,
        ) : PendingPurge

        data class Recipe(
            override val userGeneration: Long,
            val scope: RecipeScope,
            val recipeId: Long,
        ) : PendingPurge
    }

    private enum class Admission { INITIAL, RESTORING, RESTORE_FAILED, SIGNED_OUT, AUTHENTICATING, AUTHENTICATED, CLEANING, CLEANUP_FAILED }

    private companion object {
        const val COMPLETED_IMPORT_STATUS = "completed"
        const val SESSION_EXPIRED_MESSAGE = "Your session has expired"
        const val DELETION_AMBIGUOUS_MESSAGE =
            "Account deletion could not be confirmed. Retry deliberately or sign out."
    }
}
