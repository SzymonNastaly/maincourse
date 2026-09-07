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
    private val cookbookTransition = Mutex()
    private val jobsLock = Any()
    private val authenticatedJobs = mutableSetOf<Job>()
    private val cookbookJobs = mutableSetOf<Job>()
    private val detailJobs = mutableSetOf<Job>()
    private var authAttempt: Job? = null
    private var restoreRunning = false
    private var session: SessionResponse? = null
    private var userGeneration = 0L
    private var cookbookGeneration = 0L
    private var detailGeneration = 0L
    private val cookbookRequest = AtomicLong()

    fun restore(): Job = scope.launch {
        val shouldRun = transition.withLock {
            if (restoreRunning || session != null || mutableState.value.phase == SessionPhase.CLEANUP_FAILED) {
                false
            } else {
                restoreRunning = true
                mutableState.value = SessionState(phase = SessionPhase.RESTORING)
                true
            }
        }
        if (!shouldRun) return@launch

        try {
            val stored = try {
                sessionStore.read()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                transition.withLock {
                    mutableState.value = SessionState(
                        phase = SessionPhase.RESTORE_FAILED,
                        message = failure.userMessage("Could not read the saved session"),
                        canRetry = true,
                        canReset = true,
                    )
                }
                return@launch
            }

            if (stored == null || stored.baseUrl != baseUrl || isExpired(stored.response)) {
                cleanupProtectedState(finalAuthError = null, restoring = true)
                return@launch
            }

            val startup = establishSession(stored.response)
            startup?.join()
        } finally {
            transition.withLock { restoreRunning = false }
        }
    }

    fun signIn(request: SignInRequest): Job = authenticate("Could not sign in") { api.signIn(request) }

    fun signUp(request: SignUpRequest): Job = authenticate("Could not create account") { api.signUp(request) }

    fun switchCookbook(id: Long): Job {
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
        val current = mutableState.value
        val cookbookId = current.activeCookbookId
        if (cookbookId == null) {
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

    fun openRecipe(id: Long): Job = scope.launch {
        val context = activeContextOrExpire() ?: return@launch
        val current = mutableState.value
        val cookbookId = current.activeCookbookId ?: return@launch
        val summary = current.recipes.firstOrNull { it.id == id } ?: return@launch
        cancelJobs(detailJobs)
        val detailVersion = transition.withLock {
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

        val cookbookVersion = transition.withLock { cookbookGeneration }
        startDetailLoad(
            context = context,
            recipeScope = RecipeScope(context.response.user.id, cookbookId),
            recipeId = id,
            cookbookVersion = cookbookVersion,
            detailVersion = detailVersion,
        ).join()
    }

    fun closeRecipe(): Job = scope.launch {
        cancelJobs(detailJobs)
        transition.withLock {
            detailGeneration++
            mutableState.value = mutableState.value.copy(detail = null)
        }
    }

    fun logout(): Job = scope.launch {
        val token = transition.withLock { session?.token }
        beginCleanup(SessionPhase.SIGNING_OUT)
        var revokeFailure: Throwable? = null
        if (token != null) {
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
        finishCleanup(finalAuthError = null)
        revokeFailure?.let { throw it }
    }

    fun reset(): Job = scope.launch {
        beginCleanup(SessionPhase.SIGNING_OUT)
        finishCleanup(finalAuthError = null)
    }

    fun checkExpiry(): Job = scope.launch {
        activeContextOrExpire()
    }

    private fun authenticate(fallbackMessage: String, request: suspend () -> SessionResponse): Job {
        lateinit var launched: Job
        launched = scope.launch(start = CoroutineStart.LAZY) {
            val admitted = transition.withLock {
                if (mutableState.value.phase != SessionPhase.SIGNED_OUT || authAttempt != null) {
                    false
                } else {
                    authAttempt = currentCoroutineContext()[Job]
                    mutableState.value = SessionState(
                        phase = SessionPhase.LOADING_COOKBOOKS,
                        catalogStatus = LoadStatus.LOADING,
                    )
                    true
                }
            }
            if (!admitted) return@launch

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
                transition.withLock {
                    if (authAttempt == currentCoroutineContext()[Job]) {
                        mutableState.value = SessionState(
                            phase = SessionPhase.SIGNED_OUT,
                            authError = failure.userMessage(fallbackMessage),
                        )
                    }
                }
            } finally {
                transition.withLock {
                    if (authAttempt == currentCoroutineContext()[Job]) authAttempt = null
                }
            }
        }
        launched.start()
        return launched
    }

    private suspend fun establishSession(response: SessionResponse): Job? {
        val context = transition.withLock {
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
        }
        return startCatalogLoad(context)
    }

    private fun startCatalogLoad(context: UserContext): Job = tracked(authenticatedJobs) {
        var hadCachedMembership = false
        try {
            val cached = catalogRepository.cachedCookbooks(context.response.user.id)
            if (isCurrent(context)) {
                hadCachedMembership = cached.isNotEmpty()
                if (cached.isNotEmpty()) {
                    val storedSelection = catalogRepository.selectedCookbookId(context.response.user.id)
                    val selected = chooseCookbook(cached, storedSelection)
                    activateCookbook(context, selected.id)
                }
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Throwable) {
            // Remote discovery below can still recover a broken cache read.
        }

        try {
            val remote = catalogRepository.discoverCookbooks(context.response)
            if (!isCurrent(context)) return@tracked
            if (remote.isEmpty()) {
                cancelJobs(cookbookJobs)
                transition.withLock {
                    if (isCurrentLocked(context)) {
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
                return@tracked
            }
            val preferred = mutableState.value.activeCookbookId
            val selected = chooseCookbook(remote, preferred)
            activateCookbook(context, selected.id)?.let { startRecipeRefresh(it).join() }
            transition.withLock {
                if (isCurrentLocked(context) && mutableState.value.activeCookbookId == selected.id) {
                    mutableState.value = mutableState.value.copy(
                        catalogStatus = LoadStatus.FRESH,
                        message = null,
                        canRetry = mutableState.value.recipeStatus == LoadStatus.ERROR,
                    )
                }
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            if (failure is ApiFailure && failure.status == 401) {
                invalidateAuthenticatedSession(context, failure.message)
            } else {
                transition.withLock {
                    if (isCurrentLocked(context)) {
                        val hasVisibleCache = mutableState.value.activeCookbookId != null || hadCachedMembership
                        mutableState.value = mutableState.value.copy(
                            phase = if (hasVisibleCache) SessionPhase.READY else SessionPhase.LOADING_COOKBOOKS,
                            catalogStatus = if (hasVisibleCache) LoadStatus.DEGRADED else LoadStatus.ERROR,
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
    ): Activation? = cookbookTransition.withLock {
        if (requestVersion != cookbookRequest.get()) return@withLock null
        if (!isCurrent(context)) return null
        val currentJob = currentCoroutineContext()[Job]
        cancelJobs(trackedSnapshot(cookbookJobs).filterNot { it == currentJob })
        if (requestVersion != cookbookRequest.get()) return@withLock null
        val version = transition.withLock {
            if (!isCurrentLocked(context)) return@withLock null
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
        return@withLock try {
            catalogRepository.selectCookbook(context.response.user.id, cookbookId)
            val cached = catalogRepository.cachedRecipes(recipeScope)
            val memberships = catalogRepository.cachedCookbooks(context.response.user.id)
            if (requestVersion != cookbookRequest.get()) return@withLock null
            transition.withLock {
                if (isCurrentLocked(context, cookbookId, version)) {
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
                        detail = if (selectedDetail != null && items.none { it.id == selectedDetail.recipeId }) {
                            RecipeDetailState(selectedDetail.recipeId, DetailStatus.UNAVAILABLE, message = "Recipe is no longer available")
                        } else {
                            selectedDetail
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
    ): Job = tracked(detailJobs, also = cookbookJobs) {
        val cached = try {
            catalogRepository.cachedDetail(recipeScope, recipeId)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Throwable) {
            null
        }
        transition.withLock {
            if (isCurrentLocked(context, recipeScope.cookbookId, cookbookVersion) && detailGeneration == detailVersion) {
                mutableState.value = mutableState.value.copy(
                    detail = RecipeDetailState(recipeId, DetailStatus.LOADING, cached),
                )
            }
        }

        try {
            val detail = catalogRepository.refreshDetail(context.response, recipeScope, recipeId)
            transition.withLock {
                if (isCurrentLocked(context, recipeScope.cookbookId, cookbookVersion) &&
                    detailGeneration == detailVersion && mutableState.value.recipes.any { it.id == recipeId }
                ) {
                    mutableState.value = mutableState.value.copy(
                        detail = RecipeDetailState(recipeId, DetailStatus.FRESH, detail),
                    )
                }
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
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
                    if (isCurrentLocked(context, recipeScope.cookbookId, cookbookVersion) && detailGeneration == detailVersion) {
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
        val siblings = transition.withLock {
            if (!isCurrentLocked(context, recipeScope.cookbookId, expectedCookbookGeneration)) {
                return@withLock null
            }
            cookbookGeneration++
            detailGeneration++
            trackedSnapshot(cookbookJobs).filterNot { it == currentJob }
        } ?: return
        cancelJobs(siblings)
        catalogRepository.removeRecipe(recipeScope, recipeId)
        transition.withLock {
            if (isCurrentLocked(context, recipeScope.cookbookId, cookbookGeneration)) {
                mutableState.value = mutableState.value.copy(
                    recipes = mutableState.value.recipes.filterNot { it.id == recipeId },
                    detail = RecipeDetailState(recipeId, DetailStatus.UNAVAILABLE, message = "Recipe is no longer available"),
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
        catalogRepository.removeCookbook(activation.recipeScope)

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
                                message = null,
                                canRetry = mutableState.value.recipeStatus == LoadStatus.ERROR,
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

    private suspend fun activeContextOrExpire(): UserContext? {
        val context = transition.withLock {
            session?.let { UserContext(it, userGeneration) }
        } ?: return null
        if (!isExpired(context.response)) return context
        cleanupProtectedState(null)
        return null
    }

    private suspend fun invalidateAuthenticatedSession(context: UserContext, message: String?) {
        if (!isCurrent(context)) return
        cleanupProtectedState(message ?: "Your session has expired")
    }

    private suspend fun cleanupProtectedState(finalAuthError: String?, restoring: Boolean = false) {
        beginCleanup(if (restoring) SessionPhase.RESTORING else SessionPhase.SIGNING_OUT)
        finishCleanup(finalAuthError)
    }

    private suspend fun beginCleanup(phase: SessionPhase) {
        val current = currentCoroutineContext()[Job]
        val jobs = transition.withLock {
            userGeneration++
            cookbookGeneration++
            detailGeneration++
            session = null
            mutableState.value = SessionState(phase = phase)
            val tracked = trackedSnapshot(authenticatedJobs) + trackedSnapshot(cookbookJobs) +
                trackedSnapshot(detailJobs) + listOfNotNull(authAttempt)
            tracked.distinct().filterNot { it == current }
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
    }

    private suspend fun isCurrent(context: UserContext): Boolean = transition.withLock { isCurrentLocked(context) }

    private fun isCurrentLocked(context: UserContext): Boolean =
        session === context.response && userGeneration == context.generation

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

    private fun Throwable.userMessage(fallback: String): String = message?.takeIf { it.isNotBlank() } ?: fallback

    private data class UserContext(val response: SessionResponse, val generation: Long)

    private data class Activation(
        val context: UserContext,
        val recipeScope: RecipeScope,
        val cookbookGeneration: Long,
        val requestVersion: Long,
        val allowForbiddenRecovery: Boolean,
    )

    private companion object {
        const val COMPLETED_IMPORT_STATUS = "completed"
    }
}
