package com.getmaincourse.app.features.shopping

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getmaincourse.app.data.CookbookRepository
import com.getmaincourse.app.data.CookbookSelection
import com.getmaincourse.app.data.ShoppingListRepository
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.ShoppingItem
import com.getmaincourse.app.data.model.ShoppingItemRequest
import com.getmaincourse.app.data.network.userMessage
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ShoppingAction {
    ADD,
    TOGGLE,
    DELETE,
    CLEAR,
}

data class ShoppingListUiState(
    val cookbooks: List<Cookbook> = emptyList(),
    val selectedCookbookId: Long? = null,
    val items: List<ShoppingItem> = emptyList(),
    val draft: String = "",
    val initialLoading: Boolean = true,
    val refreshing: Boolean = false,
    val action: ShoppingAction? = null,
    val actionItemId: Long? = null,
    val error: String? = null,
) {
    val uncheckedItems: List<ShoppingItem>
        get() = items.filter { it.checkedAt == null }

    val checkedItems: List<ShoppingItem>
        get() = items.filter { it.checkedAt != null }

    val busy: Boolean
        get() = action != null
}

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingListViewModel internal constructor(
    observeCookbooks: () -> Flow<CookbookSelection>,
    observeItems: (Long) -> Flow<List<ShoppingItem>>,
    private val refreshCookbooks: suspend () -> Unit,
    private val refreshItems: suspend (Long) -> Unit,
    private val createItem: suspend (Long, ShoppingItemRequest) -> Unit,
    private val setItemChecked: suspend (Long, Long, Boolean) -> Unit,
    private val deleteItem: suspend (Long, Long) -> Unit,
    private val clearItems: suspend (Long) -> Unit,
    private val newClientId: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    constructor(
        userId: Long,
        cookbookRepository: CookbookRepository,
        shoppingListRepository: ShoppingListRepository,
    ) : this(
        observeCookbooks = { cookbookRepository.observe(userId) },
        observeItems = { cookbookId -> shoppingListRepository.observeItems(userId, cookbookId) },
        refreshCookbooks = { cookbookRepository.refresh(userId) },
        refreshItems = { cookbookId -> shoppingListRepository.refresh(userId, cookbookId) },
        createItem = { cookbookId, item -> shoppingListRepository.create(userId, cookbookId, listOf(item)) },
        setItemChecked = { cookbookId, itemId, checked ->
            shoppingListRepository.setChecked(userId, cookbookId, itemId, checked)
        },
        deleteItem = { cookbookId, itemId -> shoppingListRepository.delete(userId, cookbookId, itemId) },
        clearItems = { cookbookId -> shoppingListRepository.clear(userId, cookbookId) },
    )

    private val cookbookFlow = observeCookbooks()
    private val refreshState = MutableStateFlow(OperationState(running = true))
    private val mutationState = MutableStateFlow(MutationState())
    private val draft = MutableStateFlow("")
    private var refreshJob: Job? = null
    private var mutationJob: Job? = null
    private var pendingAdd: ShoppingItemRequest? = null

    private val content = cookbookFlow.flatMapLatest { selection ->
        val items = selection.selectedId?.let(observeItems) ?: flowOf(emptyList())
        items.map { selection to it }
    }

    val state = combine(content, refreshState, mutationState, draft) { (selection, items), refresh, mutation, text ->
        val hasContent = selection.cookbooks.isNotEmpty() || items.isNotEmpty()
        ShoppingListUiState(
            cookbooks = selection.cookbooks,
            selectedCookbookId = selection.selectedId,
            items = items,
            draft = text,
            initialLoading = refresh.running && !hasContent,
            refreshing = refresh.running && hasContent,
            action = mutation.action,
            actionItemId = mutation.itemId,
            error = mutation.error ?: refresh.error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ShoppingListUiState(),
    )

    init {
        refresh()
        viewModelScope.launch {
            cookbookFlow
                .map { selection -> selection.selectedId }
                .distinctUntilChanged()
                .drop(1)
                .collectLatest { cookbookId ->
                    cookbookId?.let { refreshSelectedCookbook(it) }
                }
        }
    }

    fun refresh(): Job {
        refreshJob?.cancel()
        return viewModelScope.launch {
            refreshState.value = OperationState(running = true)
            try {
                try {
                    refreshCookbooks()
                } catch (failure: IOException) {
                    // A transient cookbook request failure needn't block an already selected list.
                    if (cookbookFlow.first().selectedId == null) throw failure
                }
                cookbookFlow.first().selectedId?.let { refreshItems(it) }
                refreshState.value = OperationState(running = false)
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                refreshState.value = OperationState(
                    running = false,
                    error = failure.userMessage("Could not refresh shopping list"),
                )
            }
        }.also { refreshJob = it }
    }

    fun updateDraft(value: String) {
        if (value != draft.value) pendingAdd = null
        draft.value = value
    }

    fun addItem(): Job? {
        val cookbookId = state.value.selectedCookbookId ?: return null
        val name = draft.value.trim()
        if (name.isEmpty()) return null
        val item = pendingAdd?.takeIf { it.name == name } ?: ShoppingItemRequest(
            clientId = newClientId(),
            name = name,
            details = null,
            checkedAt = null,
            sourceRecipeId = null,
        ).also { pendingAdd = it }
        return launchMutation(ShoppingAction.ADD) {
            createItem(cookbookId, item)
            pendingAdd = null
            draft.value = ""
        }
    }

    fun toggleItem(item: ShoppingItem): Job? {
        val cookbookId = state.value.selectedCookbookId ?: return null
        return launchMutation(ShoppingAction.TOGGLE, item.id) {
            setItemChecked(cookbookId, item.id, item.checkedAt == null)
        }
    }

    fun deleteItem(item: ShoppingItem): Job? {
        val cookbookId = state.value.selectedCookbookId ?: return null
        return launchMutation(ShoppingAction.DELETE, item.id) {
            deleteItem(cookbookId, item.id)
        }
    }

    fun clearItems(): Job? {
        val cookbookId = state.value.selectedCookbookId ?: return null
        return launchMutation(ShoppingAction.CLEAR) {
            clearItems(cookbookId)
        }
    }

    fun clearError() {
        mutationState.value = mutationState.value.copy(error = null)
        refreshState.value = refreshState.value.copy(error = null)
    }

    private suspend fun refreshSelectedCookbook(cookbookId: Long) {
        refreshState.value = OperationState(running = true)
        try {
            refreshItems(cookbookId)
            refreshState.value = OperationState(running = false)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            refreshState.value = OperationState(
                running = false,
                error = failure.userMessage("Could not open cookbook"),
            )
        }
    }

    private fun launchMutation(
        action: ShoppingAction,
        itemId: Long? = null,
        operation: suspend () -> Unit,
    ): Job {
        mutationJob?.takeIf(Job::isActive)?.let { return it }
        mutationState.value = MutationState(action = action, itemId = itemId)
        return viewModelScope.launch {
            try {
                operation()
                mutationState.value = MutationState()
            } catch (failure: CancellationException) {
                mutationState.value = MutationState()
                throw failure
            } catch (failure: Throwable) {
                mutationState.value = MutationState(
                    error = failure.userMessage("Could not update shopping list"),
                )
            }
        }.also { mutationJob = it }
    }

    private data class OperationState(
        val running: Boolean,
        val error: String? = null,
    )

    private data class MutationState(
        val action: ShoppingAction? = null,
        val itemId: Long? = null,
        val error: String? = null,
    )
}
