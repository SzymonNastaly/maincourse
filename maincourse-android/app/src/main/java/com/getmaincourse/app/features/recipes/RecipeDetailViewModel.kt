package com.getmaincourse.app.features.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getmaincourse.app.data.RecipeRepository
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.network.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecipeDetailUiState(
    val recipe: RecipeDetail? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
)

class RecipeDetailViewModel internal constructor(
    private val observeDetail: () -> Flow<RecipeDetail?>,
    private val refreshDetail: suspend () -> Unit,
) : ViewModel() {
    constructor(
        userId: Long,
        cookbookId: Long,
        recipeId: Long,
        repository: RecipeRepository,
    ) : this(
        observeDetail = { repository.observeDetail(userId, cookbookId, recipeId) },
        refreshDetail = { repository.refreshDetail(userId, cookbookId, recipeId) },
    )

    private val refreshState = MutableStateFlow(RefreshState(running = true))
    private var refreshJob: Job? = null

    val state = combine(observeDetail(), refreshState) { recipe, refresh ->
        RecipeDetailUiState(
            recipe = recipe,
            loading = refresh.running && recipe == null,
            refreshing = refresh.running && recipe != null,
            error = refresh.error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecipeDetailUiState(),
    )

    init {
        launchRefresh(onlyWhenMissing = true)
    }

    fun refresh(): Job = launchRefresh(onlyWhenMissing = false)

    private fun launchRefresh(onlyWhenMissing: Boolean): Job {
        refreshJob?.cancel()
        return viewModelScope.launch {
            refreshState.value = RefreshState(running = true)
            try {
                if (!onlyWhenMissing || observeDetail().first() == null) refreshDetail()
                refreshState.value = RefreshState(running = false)
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                refreshState.value = RefreshState(
                    running = false,
                    error = failure.userMessage("Could not load recipe"),
                )
            }
        }.also { refreshJob = it }
    }

    private data class RefreshState(
        val running: Boolean,
        val error: String? = null,
    )
}
