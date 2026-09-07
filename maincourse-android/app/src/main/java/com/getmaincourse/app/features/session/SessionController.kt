package com.getmaincourse.app.features.session

import com.getmaincourse.app.data.cache.RecipeScope
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.SessionResponse
import com.getmaincourse.app.data.model.SignInRequest
import com.getmaincourse.app.data.model.SignUpRequest
import com.getmaincourse.app.data.network.ApiFailure
import com.getmaincourse.app.data.network.MainCourseApi
import com.getmaincourse.app.data.session.SessionStore
import com.getmaincourse.app.data.session.StoredSession
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
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
    private val revokeTimeoutMillis: Long = 5_000,
) {
    private val mutableState = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = mutableState.asStateFlow()

    private val transition = Mutex()
    private val catalogTransition = Mutex()
    private val cookbookTransition = Mutex()
    private val jobsLock = Any()
    private val authenticatedJobs = mutableSetOf<Job>()
    private val catalogJobs = mutableSetOf<Job>()
    private val cookbookJobs = mutableSetOf<Job>()
    private val detailJobs = mutableSetOf<Job>()
    private var authAttempt: Job? = null
    private var restoreJob: Job? = null
    private var cleanupJob: Job? = null
    private var admission = Admission.INITIAL
    private var session: SessionResponse? = null
    private var userGeneration = 0L
    private var cookbookGeneration = 0L
    private var detailGeneration = 0L
    private val catalogRequest = AtomicLong()
    private val cookbookRequest = AtomicLong()
    private val detailRequest = AtomicLong()
    private var pendingPurge: PendingPurge? = null

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
            return
        }

        if (stored == null || stored.baseUrl != baseUrl || isExpired(stored.response)) {
            cleanupProtectedState(finalAuthError = null, restoring = true)
            return
        }

        establishSession(stored.response)?.join()
    }

    fun signIn(request: SignInRequest): Job = authenticate("Could not sign in") { api.signIn(request) }

    fun signUp(request: SignUpRequest): Job = authenticate("Could not create account") { api.signUp(request) }

    fun switchCookbook(id: Long): Job {
        if (mutableState.value.cookbooks.none { it.id == id }) return completedJob()
        val requestVersion = cookbookRequest.incrementAndGet()
        return scope.launch {
            val context = activeContextOrExpire() ?: return@launch
            if (mutableState.value.cookbooks.none { it.id == id }) return@launch
            val activation = activateCookbook(context, id, requestVersion) ?: return@launch
            startRecipeRefresh(activation).join()
        }
    }

    fun refresh(): Job = scope.launch {
        val context = activeContextOrExpire() ?: return@launch
        if (!retryPendingPurge(context)) return@launch
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

    fun openRecipe(id: Long): Job {
        if (mutableState.value.recipes.none { it.id == id }) return completedJob()
        val requestVersion = detailRequest.incrementAndGet()
        return scope.launch {
            val context = activeContextOrExpire() ?: return@launch
            val current = mutableState.value
            val cookbookId = current.activeCookbookId ?: return@launch
            val summary = current.recipes.firstOrNull { it.id == id } ?: return@launch
            val cookbookVersion = transition.withLock { cookbookGeneration }
            cancelJobs(trackedSnapshot(detailJobs))
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
            } ?: return@launch

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

    fun closeRecipe(): Job {
        val requestVersion = detailRequest.incrementAndGet()
        return scope.launch {
            cancelJobs(trackedSnapshot(detailJobs))
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

    fun logout(): Job = requestCleanup(revoke = true)

    fun reset(): Job = requestCleanup(revoke = false)

    fun checkExpiry(): Job = scope.launch {
        activeContextOrExpire()
    }

    private fun requestCleanup(revoke: Boolean): Job {
        lateinit var launched: Job
        val token: String?
        synchronized(jobsLock) {
            cleanupJob?.takeIf { it.isActive }?.let { return it }
            if (admission == Admission.CLEANING) return completedJob()
            admission = Admission.CLEANING
            token = session?.token
            launched = scope.launch(start = CoroutineStart.LAZY) {
                val callerContext = currentCoroutineContext()
                val ownerJob = callerContext[Job]
                var revokeFailure: Throwable? = null
                try {
                    withContext(NonCancellable) {
                        beginCleanup(SessionPhase.SIGNING_OUT, ownerJob)
                    }
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
                    withContext(NonCancellable) { finishCleanup(finalAuthError = null) }
                }
                revokeFailure?.let { throw it }
                callerContext.ensureActive()
            }
            cleanupJob = launched
        }
        launched.invokeOnCompletion {
            synchronized(jobsLock) { if (cleanupJob == launched) cleanupJob = null }
        }
        launched.start()
        return launched
    }

    private fun authenticate(fallbackMessage: String, request: suspend () -> SessionResponse): Job {
        lateinit var launched: Job
        synchronized(jobsLock) {
            if (admission != Admission.SIGNED_OUT) return completedJob()
            admission = Admission.AUTHENTICATING
        }
        launched = scope.launch(start = CoroutineStart.LAZY) {
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
                    sessionStore.write(StoredSession(baseUrl, response))
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: Throwable) {
                    cleanupProtectedState(failure.userMessage("Could not save the session"))
                    return@launch
                }

                val startup = establishSession(response)
                startup?.join()
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
                transition.withLock {
                    if (authAttempt == currentCoroutineContext()[Job]) authAttempt = null
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
                if (admission != Admission.RESTORING && admission != Admission.AUTHENTICATING) {
                    false
                } else {
                    admission = Admission.AUTHENTICATED
                    true
                }
            }
            if (!accepted) return@withLock null
            session = response
            userGeneration++
            cookbookGeneration++
            detailGeneration++
            mutableState.value = SessionState(
                phase = SessionPhase.LOADING_COOKBOOKS,
                user = response.user,
                catalogStatus = LoadStatus.LOADING,
                canReset = true,
            )
            UserContext(response, userGeneration)
        } ?: return null
        return startCatalogLoad(context)
    }

    private fun startCatalogLoad(context: UserContext): Job {
        val requestVersion = catalogRequest.incrementAndGet()
        return scope.launch {
            val work = catalogTransition.withLock {
                if (!isCurrentCatalog(context, requestVersion)) return@withLock null
                cancelJobs(trackedSnapshot(catalogJobs))
                if (!isCurrentCatalog(context, requestVersion)) return@withLock null
                tracked(catalogJobs, also = authenticatedJobs) {
                    performCatalogLoad(context, requestVersion)
                }
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
                cancelJobs(cookbookJobs)
                transition.withLock {
                    if (isCurrentCatalogLocked(context, requestVersion)) {
                        cookbookGeneration++
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
        val currentJob = currentCoroutineContext()[Job]
        cancelJobs(trackedSnapshot(cookbookJobs).filterNot { it == currentJob })
        if (requestVersion != cookbookRequest.get()) return null
        if (catalogVersion != null && catalogVersion != catalogRequest.get()) return null
        val version = transition.withLock {
            if (!isCurrentLocked(context)) return@withLock null
            if (catalogVersion != null && catalogVersion != catalogRequest.get()) return@withLock null
            cookbookGeneration++
            detailGeneration++
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
            cookbookGeneration
        } ?: return null

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

    private fun startRecipeRefresh(activation: Activation): Job = tracked(cookbookJobs) {
        val mayRun = transition.withLock {
            if (isCurrentLocked(activation)) {
                mutableState.value = mutableState.value.copy(recipeStatus = LoadStatus.LOADING)
                true
            } else {
                false
            }
        }
        if (!mayRun) return@tracked
        try {
            val items = catalogRepository.refreshRecipes(activation.context.response, activation.recipeScope)
            transition.withLock {
                if (isCurrentLocked(activation)) {
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
                }
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            when {
                failure is ApiFailure && failure.status == 401 ->
                    invalidateAuthenticatedSession(activation.context, failure.message)
                failure is ApiFailure && failure.status == 403 ->
                    recoverForbiddenCookbook(
                        activation,
                        failure.message,
                        rediscover = activation.allowForbiddenRecovery,
                    )
                else -> transition.withLock {
                    if (isCurrentLocked(activation)) {
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

    private fun startDetailLoad(
        context: UserContext,
        recipeScope: RecipeScope,
        recipeId: Long,
        cookbookVersion: Long,
        detailVersion: Long,
        requestVersion: Long,
    ): Job = tracked(detailJobs, also = cookbookJobs) {
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
        if (!mayRequest) return@tracked

        try {
            val detail = catalogRepository.refreshDetail(context.response, recipeScope, recipeId)
            transition.withLock {
                if (isCurrentLocked(context, recipeScope.cookbookId, cookbookVersion) &&
                    detailGeneration == detailVersion && requestVersion == detailRequest.get() &&
                    mutableState.value.recipes.any { it.id == recipeId }
                ) {
                    mutableState.value = mutableState.value.copy(
                        detail = RecipeDetailState(recipeId, DetailStatus.FRESH, detail),
                    )
                }
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            val stillCurrent = transition.withLock {
                isCurrentLocked(context, recipeScope.cookbookId, cookbookVersion) &&
                    detailGeneration == detailVersion && requestVersion == detailRequest.get()
            }
            if (!stillCurrent) return@tracked
            when {
                failure is ApiFailure && failure.status == 401 -> invalidateAuthenticatedSession(context, failure.message)
                failure is ApiFailure && failure.status == 403 -> recoverForbiddenCookbook(
                    Activation(context, recipeScope, cookbookVersion, cookbookRequest.get(), true),
                    failure.message,
                    rediscover = true,
                )
                failure is ApiFailure && failure.status == 404 -> removeMissingRecipe(
                    context,
                    recipeScope,
                    recipeId,
                    cookbookVersion,
                )
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

    private suspend fun removeMissingRecipe(
        context: UserContext,
        recipeScope: RecipeScope,
        recipeId: Long,
        expectedCookbookGeneration: Long,
    ) {
        if (!isCurrent(context)) return
        val currentJob = currentCoroutineContext()[Job]
        val transitionResult = transition.withLock {
            if (!isCurrentLocked(context, recipeScope.cookbookId, expectedCookbookGeneration)) {
                return@withLock null
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
            bumpedGeneration to trackedSnapshot(cookbookJobs).filterNot { it == currentJob }
        } ?: return
        val (bumpedGeneration, siblings) = transitionResult
        cancelJobs(siblings)
        if (!retryPendingPurge(context)) return
        transition.withLock {
            if (isCurrentLocked(context, recipeScope.cookbookId, bumpedGeneration)) {
                val settledRecipeStatus = if (mutableState.value.recipesFetched) {
                    LoadStatus.FRESH
                } else {
                    LoadStatus.DEGRADED
                }
                mutableState.value = mutableState.value.copy(
                    recipeStatus = settledRecipeStatus,
                    message = mutableState.value.message.takeIf {
                        mutableState.value.catalogStatus.isFailure() || settledRecipeStatus.isFailure()
                    },
                    canRetry = mutableState.value.catalogStatus.isFailure() ||
                        settledRecipeStatus.isFailure(),
                )
            }
        }
    }

    private suspend fun recoverForbiddenCookbook(
        activation: Activation,
        message: String?,
        rediscover: Boolean,
    ) {
        val currentJob = currentCoroutineContext()[Job]
        val siblings = transition.withLock {
            if (!isCurrentLocked(activation)) return
            cookbookGeneration++
            detailGeneration++
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
            trackedSnapshot(cookbookJobs).filterNot { it == currentJob }
        }
        cancelJobs(siblings)
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
            transition.withLock {
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

    private suspend fun activeContextOrExpire(): UserContext? {
        if (synchronized(jobsLock) { admission != Admission.AUTHENTICATED }) return null
        val context = transition.withLock {
            session?.let { UserContext(it, userGeneration) }
        } ?: return null
        if (!isExpired(context.response)) return context
        cleanupProtectedState(SESSION_EXPIRED_MESSAGE)
        return null
    }

    private suspend fun invalidateAuthenticatedSession(context: UserContext, message: String?) {
        if (!isCurrent(context)) return
        cleanupProtectedState(message ?: SESSION_EXPIRED_MESSAGE)
    }

    private suspend fun cleanupProtectedState(finalAuthError: String?, restoring: Boolean = false) {
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
                beginCleanup(
                    if (restoring) SessionPhase.RESTORING else SessionPhase.SIGNING_OUT,
                    ownerJob,
                )
            } finally {
                finishCleanup(finalAuthError)
            }
        }
        callerContext.ensureActive()
    }

    private suspend fun beginCleanup(phase: SessionPhase, ownerJob: Job?) {
        val jobs = transition.withLock {
            userGeneration++
            cookbookGeneration++
            detailGeneration++
            catalogRequest.incrementAndGet()
            detailRequest.incrementAndGet()
            session = null
            pendingPurge = null
            mutableState.value = SessionState(phase = phase)
            val tracked = trackedSnapshot(authenticatedJobs) + trackedSnapshot(catalogJobs) + trackedSnapshot(cookbookJobs) +
                trackedSnapshot(detailJobs) + listOfNotNull(authAttempt, restoreJob)
            tracked.distinct().filterNot { it == ownerJob }
        }
        cancelJobs(jobs)
    }

    private suspend fun finishCleanup(finalAuthError: String?) = withContext(NonCancellable) {
        val failures = buildList {
            try {
                sessionStore.clear()
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
            admission = if (failures.isEmpty()) Admission.SIGNED_OUT else Admission.CLEANUP_FAILED
        }
    }

    private suspend fun isCurrent(context: UserContext): Boolean = transition.withLock { isCurrentLocked(context) }

    private suspend fun isCurrentCatalog(context: UserContext, requestVersion: Long): Boolean =
        transition.withLock { isCurrentCatalogLocked(context, requestVersion) }

    private fun isCurrentLocked(context: UserContext): Boolean =
        session === context.response && userGeneration == context.generation

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

    private fun tracked(
        primary: MutableSet<Job>,
        also: MutableSet<Job>? = null,
        block: suspend () -> Unit,
    ): Job {
        lateinit var launched: Job
        launched = scope.launch(start = CoroutineStart.LAZY) {
            try {
                block()
            } finally {
                synchronized(jobsLock) {
                    primary.remove(launched)
                    also?.remove(launched)
                }
            }
        }
        synchronized(jobsLock) {
            primary += launched
            also?.add(launched)
        }
        launched.start()
        return launched
    }

    private fun trackedSnapshot(jobs: Collection<Job>): List<Job> = synchronized(jobsLock) { jobs.toList() }

    private suspend fun cancelJobs(jobs: Collection<Job>) {
        val snapshot = jobs.distinct()
        snapshot.forEach { it.cancel() }
        snapshot.forEach { it.cancelAndJoin() }
    }

    private fun completedJob(): Job = Job().apply { complete() }

    private fun LoadStatus.isFailure(): Boolean = this == LoadStatus.DEGRADED || this == LoadStatus.ERROR

    private fun Throwable.userMessage(fallback: String): String =
        if (this is ApiFailure) message?.takeIf { it.isNotBlank() } ?: fallback else fallback

    private data class UserContext(val response: SessionResponse, val generation: Long)

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
    }
}
