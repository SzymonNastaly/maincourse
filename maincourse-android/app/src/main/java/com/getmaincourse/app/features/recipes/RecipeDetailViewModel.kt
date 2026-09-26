package com.getmaincourse.app.features.recipes

import androidx.lifecycle.ViewModel
import com.getmaincourse.app.R
import com.getmaincourse.app.ui.UiMessage
import androidx.lifecycle.viewModelScope
import com.getmaincourse.app.data.CookbookRepository
import com.getmaincourse.app.data.CookbookSelection
import com.getmaincourse.app.data.RecipeRepository
import com.getmaincourse.app.data.ShoppingListRepository
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.ShoppingItem
import com.getmaincourse.app.data.network.userMessage
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecipeDetailUiState(
    val recipe: RecipeDetail? = null,
    val cookbooks: List<Cookbook> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: UiMessage? = null,
    val shoppingListNeedsReview: Boolean = false,
    val shoppingListReviewReady: Boolean = false,
)

enum class RecipeAction {
    MOVE,
    DELETE,
    ADD_INGREDIENTS,
}

sealed interface RecipeActionUiState {
    data object Idle : RecipeActionUiState
    data class Running(val action: RecipeAction) : RecipeActionUiState
    data class Succeeded(val message: UiMessage) : RecipeActionUiState
    data class Failed(val message: UiMessage) : RecipeActionUiState
}

class RecipeDetailViewModel internal constructor(
    private val observeDetail: () -> Flow<RecipeDetail?>,
    private val refreshDetail: suspend () -> Unit,
    observeCookbooks: () -> Flow<CookbookSelection> = {
        flowOf(CookbookSelection(emptyList(), null))
    },
    private val moveRecipe: suspend (Long) -> Unit = {},
    private val deleteRecipe: suspend () -> Unit = {},
    private val addReviewedIngredients: suspend (List<ShoppingItemInput>, Boolean) -> Unit = { _, _ -> },
    observeShoppingItems: () -> Flow<List<ShoppingItem>> = { flowOf(emptyList()) },
    private val refreshShoppingItems: suspend () -> List<ShoppingItem> = { emptyList() },
    private val now: () -> Instant = Instant::now,
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
        addReviewedIngredients = { rows, clearExisting ->
            shoppingListRepository.create(
                userId,
                cookbookId,
                rows.map(ShoppingItemInput::toRequest),
                clearExisting = clearExisting,
            )
        },
        observeShoppingItems = { shoppingListRepository.observeItems(userId, cookbookId) },
        refreshShoppingItems = { shoppingListRepository.refresh(userId, cookbookId) },
    )

    private val refreshState = MutableStateFlow(RefreshState(running = true))
    private var refreshJob: Job? = null
    private val mutableAction = MutableStateFlow<RecipeActionUiState>(RecipeActionUiState.Idle)
    val action: StateFlow<RecipeActionUiState> = mutableAction.asStateFlow()
    private var actionJob: Job? = null
    private var latestShoppingItems: List<ShoppingItem> = emptyList()
    private val shoppingItemsCacheReady = CompletableDeferred<Unit>()
    private val shoppingItems = observeShoppingItems().onEach {
        latestShoppingItems = it
        shoppingItemsCacheReady.complete(Unit)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList(),
    )

    val state = combine(
        observeDetail(),
        refreshState,
        observeCookbooks(),
        shoppingItems,
    ) { recipe, refresh, selection, shoppingItems ->
        RecipeDetailUiState(
            recipe = recipe,
            cookbooks = selection.cookbooks,
            loading = refresh.running && recipe == null,
            refreshing = refresh.running && recipe != null,
            error = refresh.error,
            shoppingListNeedsReview = shoppingItems.needsRecipeAdditionReview(now()),
            shoppingListReviewReady = refresh.shoppingListRefreshCompleted,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RecipeDetailUiState(),
    )

    init {
        launchRefresh()
    }

    fun refresh(): Job = launchRefresh()

    fun moveTo(targetCookbookId: Long): Job = launchAction(
        action = RecipeAction.MOVE,
        successMessage = UiMessage.Resource(R.string.recipe_moved),
        fallbackMessage = UiMessage.Resource(R.string.error_move_recipe),
    ) {
        moveRecipe(targetCookbookId)
    }

    fun delete(): Job = launchAction(
        action = RecipeAction.DELETE,
        successMessage = UiMessage.Resource(R.string.recipe_deleted),
        fallbackMessage = UiMessage.Resource(R.string.error_delete_recipe),
        operation = deleteRecipe,
    )

    fun addIngredients(
        rows: List<ShoppingItemInput>,
        clearExisting: Boolean = false,
    ): Job = launchAction(
        action = RecipeAction.ADD_INGREDIENTS,
        successMessage = UiMessage.Resource(R.string.ingredients_added),
        fallbackMessage = UiMessage.Resource(R.string.error_add_ingredients),
    ) {
        addReviewedIngredients(rows, clearExisting)
    }

    fun shoppingListNeedsReview(): Boolean = latestShoppingItems.needsRecipeAdditionReview(now())

    private fun launchRefresh(): Job {
        refreshJob?.cancel()
        return viewModelScope.launch {
            refreshState.value = RefreshState(running = true, shoppingListRefreshCompleted = false)
            launch {
                val recipeError = try {
                    refreshDetail()
                    null
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: Throwable) {
                    failure.userMessage(UiMessage.Resource(R.string.error_load_recipe))
                }
                refreshState.update { it.copy(running = false, error = recipeError) }
            }

            launch {
                try {
                    latestShoppingItems = refreshShoppingItems()
                } catch (failure: CancellationException) {
                    throw failure
                } catch (_: Throwable) {
                    // The cached list still gives the review flow an offline fallback.
                }
                shoppingItemsCacheReady.await()
                refreshState.update { it.copy(shoppingListRefreshCompleted = true) }
            }
        }.also { refreshJob = it }
    }

    private fun launchAction(
        action: RecipeAction,
        successMessage: UiMessage,
        fallbackMessage: UiMessage,
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
        val error: UiMessage? = null,
        val shoppingListRefreshCompleted: Boolean = false,
    )
}

private val recipeAdditionReviewAge: Duration = Duration.ofHours(36)

private fun List<ShoppingItem>.needsRecipeAdditionReview(now: Instant): Boolean = any { item ->
    runCatching {
        Instant.parse(item.createdAt).isBefore(now.minus(recipeAdditionReviewAge))
    }.getOrDefault(false)
}
