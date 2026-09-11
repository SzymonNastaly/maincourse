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
    private val selectCookbook: suspend (Long) -> Unit,
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
        selectCookbook = { cookbookId -> cookbookRepository.select(userId, cookbookId) },
    )

    private val refreshState = MutableStateFlow(RefreshState(running = true))
    private var refreshJob: Job? = null
    private var pollingJob: Job? = null
    private var pollingCookbookId: Long? = null

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
                observeCookbooks().first().selectedId?.let { refreshRecipes(it) }
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
        refreshState.value = refreshState.value.copy(error = null)
        try {
            selectCookbook.invoke(cookbookId)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            refreshState.value = refreshState.value.copy(
                error = failure.userMessage("Could not select cookbook"),
            )
        }
    }

    fun importAccepted(cookbookId: Long) {
        refreshJob?.cancel()
        refreshState.value = RefreshState(running = false)
        startPolling(cookbookId, restart = true)
    }

    private fun startPolling(cookbookId: Long, restart: Boolean = false) {
        if (!restart && pollingCookbookId == cookbookId && pollingJob?.isActive == true) return
        pollingJob?.cancel()
        pollingCookbookId = cookbookId
        pollingJob = viewModelScope.launch {
            repeat(IMPORT_POLL_ATTEMPTS) {
                delay(IMPORT_POLL_INTERVAL_MILLIS)
                try {
                    refreshRecipes(cookbookId)
                    refreshState.value = RefreshState(running = false)
                    val stillPending = observeRecipes(cookbookId).first()
                        .any(RecipeSummary::isImportPending)
                    if (!stillPending) return@launch
                } catch (failure: CancellationException) {
                    throw failure
                } catch (_: Throwable) {
                    // Import polling is best-effort and should not replace visible cached content.
                }
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
