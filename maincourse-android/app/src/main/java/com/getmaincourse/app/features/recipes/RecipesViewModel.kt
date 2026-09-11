package com.getmaincourse.app.features.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getmaincourse.app.data.CookbookRepository
import com.getmaincourse.app.data.CookbookSelection
import com.getmaincourse.app.data.RecipeRepository
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.RecipeSummary
import com.getmaincourse.app.data.network.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class RecipesUiState(
    val cookbooks: List<Cookbook> = emptyList(),
    val selectedCookbookId: Long? = null,
    val recipes: List<RecipeSummary> = emptyList(),
    val initialLoading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class RecipesViewModel internal constructor(
    private val observeCookbooks: () -> Flow<CookbookSelection>,
    private val observeRecipes: (Long) -> Flow<List<RecipeSummary>>,
    private val refreshCookbooks: suspend () -> Unit,
    private val refreshRecipes: suspend (Long) -> Unit,
    private val syncRecipeDetails: suspend (Long) -> Unit = {},
    private val selectCookbook: suspend (Long) -> Unit,
    private val hasUnsettledImport: (Long) -> Boolean = { false },
    private val markImportSettled: (Long) -> Unit = {},
) : ViewModel() {
    constructor(
        userId: Long,
        cookbookRepository: CookbookRepository,
        recipeRepository: RecipeRepository,
    ) : this(
        observeCookbooks = { cookbookRepository.observe(userId) },
        observeRecipes = { cookbookId -> recipeRepository.observeSummaries(userId, cookbookId) },
        refreshCookbooks = { cookbookRepository.refresh(userId) },
        refreshRecipes = { cookbookId -> recipeRepository.refreshList(userId, cookbookId) },
        syncRecipeDetails = { cookbookId -> recipeRepository.syncDetails(userId, cookbookId) },
        selectCookbook = { cookbookId -> cookbookRepository.select(userId, cookbookId) },
        hasUnsettledImport = recipeRepository::hasUnsettledImport,
        markImportSettled = recipeRepository::markImportSettled,
    )

    private val refreshState = MutableStateFlow(RefreshState(running = true))
    private var refreshJob: Job? = null
    private var resumeReconciliationJob: Job? = null
    private var pollingJob: Job? = null
    private var pollingCookbookId: Long? = null
    private var detailSyncJob: Job? = null

    private val content = observeCookbooks().flatMapLatest { selection ->
        val recipes = selection.selectedId?.let(observeRecipes) ?: flowOf(emptyList())
        recipes.map { selection to it }
    }

    val state = combine(content, refreshState) { (selection, recipes), refresh ->
        val hasContent = selection.cookbooks.isNotEmpty() || recipes.isNotEmpty()
        RecipesUiState(
            cookbooks = selection.cookbooks,
            selectedCookbookId = selection.selectedId,
            recipes = recipes,
            initialLoading = refresh.running && !hasContent,
            refreshing = refresh.running && hasContent,
            error = refresh.error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecipesUiState(),
    )

    init {
        refresh()
        viewModelScope.launch {
            content.map { (selection, recipes) ->
                selection.selectedId?.takeIf { recipes.any(RecipeSummary::isImportPending) }
            }.distinctUntilChanged().collect { cookbookId ->
                cookbookId?.let(::startPolling)
            }
        }
    }

    fun refresh(): Job {
        refreshJob?.cancel()
        return viewModelScope.launch {
            refreshState.value = RefreshState(running = true)
            try {
                refreshCookbooks()
                observeCookbooks().first().selectedId?.let { refreshRecipeCache(it) }
                refreshState.value = RefreshState(running = false)
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                currentCoroutineContext().ensureActive()
                refreshState.value = RefreshState(
                    running = false,
                    error = failure.userMessage("Could not refresh recipes"),
                )
            }
        }.also { refreshJob = it }
    }

    fun selectCookbook(cookbookId: Long): Job = viewModelScope.launch {
        detailSyncJob?.cancel()
        refreshState.value = refreshState.value.copy(error = null)
        try {
            selectCookbook.invoke(cookbookId)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            refreshState.value = RefreshState(
                running = false,
                error = failure.userMessage("Could not select cookbook"),
            )
            return@launch
        }

        refreshState.value = RefreshState(running = true)
        try {
            refreshRecipeCache(cookbookId)
            refreshState.value = RefreshState(running = false)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            refreshState.value = RefreshState(
                running = false,
                error = failure.userMessage("Could not refresh recipes"),
            )
        }
    }

    fun importAccepted(cookbookId: Long) {
        refreshJob?.cancel()
        refreshState.value = RefreshState(running = false)
        startPolling(cookbookId, restart = true)
    }

    fun reconcileAfterResume(): Job {
        resumeReconciliationJob?.cancel()
        pollingJob?.cancel()
        pollingCookbookId = null
        return viewModelScope.launch {
            val cookbookId = observeCookbooks().first().selectedId ?: return@launch
            try {
                refreshRecipeCache(cookbookId)
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Throwable) {
                // Foreground reconciliation is best-effort and must not hide cached content.
            }

            val hasPendingRecipe = observeRecipes(cookbookId).first().any(RecipeSummary::isImportPending)
            if (hasUnsettledImport(cookbookId) || hasPendingRecipe) {
                startPolling(cookbookId, restart = true)
            }
        }.also { resumeReconciliationJob = it }
    }

    private fun startPolling(cookbookId: Long, restart: Boolean = false) {
        if (!restart && pollingCookbookId == cookbookId && pollingJob?.isActive == true) return
        pollingJob?.cancel()
        pollingCookbookId = cookbookId
        pollingJob = viewModelScope.launch {
            var pendingAttempts = 0
            var settledAttempts = 0
            var totalAttempts = 0
            while (
                pendingAttempts < IMPORT_POLL_ATTEMPTS &&
                settledAttempts < IMPORT_SETTLE_ATTEMPTS &&
                totalAttempts < IMPORT_MAX_POLL_ATTEMPTS
            ) {
                delay(IMPORT_POLL_INTERVAL_MILLIS)
                totalAttempts += 1
                try {
                    refreshRecipeCache(cookbookId)
                    refreshState.value = RefreshState(running = false)
                    val stillPending = observeRecipes(cookbookId).first()
                        .any(RecipeSummary::isImportPending)
                    if (stillPending) {
                        pendingAttempts += 1
                        settledAttempts = 0
                    } else {
                        settledAttempts += 1
                    }
                } catch (failure: CancellationException) {
                    throw failure
                } catch (_: Throwable) {
                    // Import polling is best-effort and should not replace visible cached content.
                    if (settledAttempts == 0) pendingAttempts += 1
                }
            }
            if (settledAttempts == IMPORT_SETTLE_ATTEMPTS) markImportSettled(cookbookId)
        }
    }

    private suspend fun refreshRecipeCache(cookbookId: Long) {
        refreshRecipes(cookbookId)
        startDetailSync(cookbookId)
    }

    private fun startDetailSync(cookbookId: Long) {
        detailSyncJob?.cancel()
        detailSyncJob = viewModelScope.launch {
            try {
                syncRecipeDetails(cookbookId)
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Throwable) {
                // Detail hydration is best-effort; its Room cursor makes a later run resumable.
            }
        }
    }

    private data class RefreshState(
        val running: Boolean,
        val error: String? = null,
    )
}

private fun RecipeSummary.isImportPending(): Boolean = importStatus == "pending" || importStatus == "processing"

private const val IMPORT_POLL_INTERVAL_MILLIS = 3_000L
private const val IMPORT_POLL_ATTEMPTS = 10
private const val IMPORT_SETTLE_ATTEMPTS = 5
private const val IMPORT_MAX_POLL_ATTEMPTS = IMPORT_POLL_ATTEMPTS + IMPORT_SETTLE_ATTEMPTS
