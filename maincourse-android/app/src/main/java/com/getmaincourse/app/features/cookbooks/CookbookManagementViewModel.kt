package com.getmaincourse.app.features.cookbooks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getmaincourse.app.data.CookbookRepository
import com.getmaincourse.app.data.CookbookSelection
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.CookbookInvitation
import com.getmaincourse.app.data.network.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CookbookManagementUiState(
    val cookbooks: List<Cookbook> = emptyList(),
    val selectedCookbookId: Long? = null,
    val loading: Boolean = true,
    val working: Boolean = false,
    val error: String? = null,
    val invitation: CookbookInvitation? = null,
) {
    val personalCookbook: Cookbook?
        get() = cookbooks.firstOrNull(Cookbook::personal)

    val sharedCookbook: Cookbook?
        get() = cookbooks.firstOrNull { !it.personal }
}

class CookbookManagementViewModel internal constructor(
    private val userId: Long,
    observeCookbooks: (Long) -> Flow<CookbookSelection>,
    private val refreshCookbooks: suspend (Long) -> Unit,
    private val createSharedCookbook: suspend (Long, String, Boolean) -> Cookbook,
    private val deleteSharedCookbook: suspend (Long, Long) -> Unit,
    private val leaveSharedCookbook: suspend (Long, Long) -> Unit,
    private val createCookbookInvitation: suspend (Long) -> CookbookInvitation,
) : ViewModel() {
    constructor(userId: Long, repository: CookbookRepository) : this(
        userId = userId,
        observeCookbooks = repository::observe,
        refreshCookbooks = repository::refresh,
        createSharedCookbook = repository::createShared,
        deleteSharedCookbook = repository::deleteShared,
        leaveSharedCookbook = repository::leaveShared,
        createCookbookInvitation = repository::createInvitation,
    )

    private val mutableState = MutableStateFlow(CookbookManagementUiState())
    val state: StateFlow<CookbookManagementUiState> = mutableState.asStateFlow()
    private var action: Job? = null

    init {
        viewModelScope.launch {
            observeCookbooks(userId).collect { selection ->
                mutableState.value = mutableState.value.copy(
                    cookbooks = selection.cookbooks,
                    selectedCookbookId = selection.selectedId,
                )
            }
        }
        refresh()
    }

    fun refresh() = launchAction("Could not load cookbooks", loading = true) {
        refreshCookbooks(userId)
    }

    fun create(name: String, movePersonalRecipes: Boolean) {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) {
            mutableState.value = mutableState.value.copy(error = "Enter a cookbook name.")
            return
        }
        launchAction("Could not create cookbook") {
            createSharedCookbook(userId, normalizedName, movePersonalRecipes)
        }
    }

    fun generateInvitation() {
        val shared = mutableState.value.sharedCookbook ?: return
        launchAction("Could not create invitation") {
            val invitation = createCookbookInvitation(shared.id)
            mutableState.value = mutableState.value.copy(invitation = invitation)
        }
    }

    fun leave() {
        val shared = mutableState.value.sharedCookbook ?: return
        launchAction("Could not leave cookbook") {
            leaveSharedCookbook(userId, shared.id)
        }
    }

    fun delete() {
        val shared = mutableState.value.sharedCookbook ?: return
        launchAction("Could not delete cookbook") {
            deleteSharedCookbook(userId, shared.id)
        }
    }

    fun clearInvitation() {
        mutableState.value = mutableState.value.copy(invitation = null)
    }

    fun clearError() {
        mutableState.value = mutableState.value.copy(error = null)
    }

    private fun launchAction(
        fallback: String,
        loading: Boolean = false,
        block: suspend () -> Unit,
    ): Job {
        action?.takeIf(Job::isActive)?.let { return it }
        return viewModelScope.launch {
            mutableState.value = mutableState.value.copy(
                loading = loading,
                working = !loading,
                error = null,
            )
            try {
                block()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                mutableState.value = mutableState.value.copy(error = failure.userMessage(fallback))
            } finally {
                mutableState.value = mutableState.value.copy(loading = false, working = false)
            }
        }.also { action = it }
    }
}
