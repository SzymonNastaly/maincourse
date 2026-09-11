package com.getmaincourse.app.features.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getmaincourse.app.data.CookbookRepository
import com.getmaincourse.app.data.CookbookSelection
import com.getmaincourse.app.data.RecipeRepository
import com.getmaincourse.app.data.ShoppingListRepository
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.network.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecipeDetailUiState(
    val recipe: RecipeDetail? = null,
    val cookbooks: List<Cookbook> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
)

enum class RecipeAction {
    MOVE,
    DELETE,
    ADD_INGREDIENTS,
}

sealed interface RecipeActionUiState {
    data object Idle : RecipeActionUiState
    data class Running(val action: RecipeAction) : RecipeActionUiState
    data class Succeeded(val message: String) : RecipeActionUiState
    data class Failed(val message: String) : RecipeActionUiState
}

class RecipeDetailViewModel internal constructor(
    private val observeDetail: () -> Flow<RecipeDetail?>,
    private val refreshDetail: suspend () -> Unit,
    observeCookbooks: () -> Flow<CookbookSelection> = {
        flowOf(CookbookSelection(emptyList(), null))
    },
    private val moveRecipe: suspend (Long) -> Unit = {},
    private val deleteRecipe: suspend () -> Unit = {},
    private val addReviewedIngredients: suspend (List<ShoppingItemInput>) -> Unit = {},
) : ViewModel() {
    constructor(
        userId: Long,
        cookbookId: Long,
        recipeId: Long,
        repository: RecipeRepository,
        shoppingListRepository: ShoppingListRepository,
        cookbookRepository: CookbookRepository? = null,
    ) : this(
        observeDetail = { repository.observeDetail(userId, cookbookId, recipeId) },
        refreshDetail = { repository.refreshDetail(userId, cookbookId, recipeId) },
        observeCookbooks = {
            cookbookRepository?.observe(userId) ?: flowOf(CookbookSelection(emptyList(), null))
        },
        moveRecipe = { targetCookbookId ->
            repository.move(userId, cookbookId, recipeId, targetCookbookId)
        },
        deleteRecipe = { repository.delete(userId, cookbookId, recipeId) },
        addReviewedIngredients = { rows ->
            shoppingListRepository.create(userId, cookbookId, rows.map(ShoppingItemInput::toRequest))
        },
    )

    private val refreshState = MutableStateFlow(RefreshState(running = true))
    private var refreshJob: Job? = null
    private val mutableAction = MutableStateFlow<RecipeActionUiState>(RecipeActionUiState.Idle)
    val action: StateFlow<RecipeActionUiState> = mutableAction.asStateFlow()
    private var actionJob: Job? = null

    val state = combine(observeDetail(), refreshState, observeCookbooks()) { recipe, refresh, selection ->
        RecipeDetailUiState(
            recipe = recipe,
            cookbooks = selection.cookbooks,
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

    fun moveTo(targetCookbookId: Long): Job = launchAction(
        action = RecipeAction.MOVE,
        successMessage = "Recipe moved",
        fallbackMessage = "Could not move recipe",
    ) {
        moveRecipe(targetCookbookId)
    }

    fun delete(): Job = launchAction(
        action = RecipeAction.DELETE,
        successMessage = "Recipe deleted",
        fallbackMessage = "Could not delete recipe",
        operation = deleteRecipe,
    )

    fun addIngredients(rows: List<ShoppingItemInput>): Job = launchAction(
        action = RecipeAction.ADD_INGREDIENTS,
        successMessage = "Ingredients added",
        fallbackMessage = "Could not add ingredients",
    ) {
        addReviewedIngredients(rows)
    }

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

    private fun launchAction(
        action: RecipeAction,
        successMessage: String,
        fallbackMessage: String,
        operation: suspend () -> Unit,
    ): Job {
        actionJob?.takeIf(Job::isActive)?.let { return it }
        mutableAction.value = RecipeActionUiState.Running(action)
        return viewModelScope.launch {
            try {
                operation()
                mutableAction.value = RecipeActionUiState.Succeeded(successMessage)
            } catch (failure: CancellationException) {
                mutableAction.value = RecipeActionUiState.Idle
                throw failure
            } catch (failure: Throwable) {
                mutableAction.value = RecipeActionUiState.Failed(failure.userMessage(fallbackMessage))
            }
        }.also { actionJob = it }
    }

    private data class RefreshState(
        val running: Boolean,
        val error: String? = null,
    )
}
