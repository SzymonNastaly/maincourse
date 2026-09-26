package com.getmaincourse.app.features.cookbooks

import androidx.lifecycle.ViewModel
import com.getmaincourse.app.R
import com.getmaincourse.app.ui.UiMessage
import androidx.lifecycle.viewModelScope
import com.getmaincourse.app.data.CookbookRepository
import com.getmaincourse.app.data.CookbookSelection
import com.getmaincourse.app.data.model.CookbookInvitationAcceptance
import com.getmaincourse.app.data.model.CookbookInvitationPreview
import com.getmaincourse.app.data.network.userMessage
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class InvitationUiState(
    val preview: CookbookInvitationPreview? = null,
    val hasSharedCookbook: Boolean = false,
    val loading: Boolean = true,
    val accepting: Boolean = false,
    val acceptance: CookbookInvitationAcceptance? = null,
    val declined: Boolean = false,
    val error: UiMessage? = null,
)

class InvitationViewModel internal constructor(
    private val userId: Long,
    private val token: String,
    observeCookbooks: (Long) -> Flow<CookbookSelection>,
    private val loadInvitation: suspend (String) -> CookbookInvitationPreview,
    private val acceptInvitation: suspend (Long, String) -> CookbookInvitationAcceptance,
    private val rejectInvitation: suspend (String) -> Unit,
    private val clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    constructor(userId: Long, token: String, repository: CookbookRepository) : this(
        userId = userId,
        token = token,
        observeCookbooks = repository::observe,
        loadInvitation = repository::invitation,
        acceptInvitation = repository::acceptInvitation,
        rejectInvitation = repository::rejectInvitation,
    )

    private val mutableState = MutableStateFlow(InvitationUiState())
    val state: StateFlow<InvitationUiState> = mutableState.asStateFlow()
    private var action: Job? = null

    init {
        viewModelScope.launch {
            observeCookbooks(userId).collect { selection ->
                mutableState.value = mutableState.value.copy(
                    hasSharedCookbook = selection.cookbooks.any { !it.personal },
                )
            }
        }
        load()
    }

    fun load() = launchAction(UiMessage.Resource(R.string.error_load_invitation), loading = true) {
        val preview = loadInvitation(token)
        val unavailable = when {
            preview.status != "pending" -> UiMessage.Resource(R.string.error_invitation_unavailable)
            preview.isExpired(clock) -> UiMessage.Resource(R.string.error_invitation_expired)
            else -> null
        }
        mutableState.value = mutableState.value.copy(preview = preview, error = unavailable)
    }

    fun accept() {
        if (mutableState.value.hasSharedCookbook || mutableState.value.error != null) return
        launchAction(UiMessage.Resource(R.string.error_join_cookbook), accepting = true) {
            val acceptance = acceptInvitation(userId, token)
            mutableState.value = mutableState.value.copy(acceptance = acceptance)
        }
    }

    fun decline() {
        launchAction(UiMessage.Resource(R.string.error_decline_invitation)) {
            try {
                rejectInvitation(token)
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Throwable) {
                // Declining is best-effort; closing the invitation is always available.
            }
            mutableState.value = mutableState.value.copy(declined = true)
        }
    }

    private fun launchAction(
        fallback: UiMessage,
        loading: Boolean = false,
        accepting: Boolean = false,
        block: suspend () -> Unit,
    ): Job {
        action?.takeIf(Job::isActive)?.let { return it }
        return viewModelScope.launch {
            mutableState.value = mutableState.value.copy(
                loading = loading,
                accepting = accepting,
                error = if (loading) null else mutableState.value.error,
            )
            try {
                block()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                mutableState.value = mutableState.value.copy(error = failure.userMessage(fallback))
            } finally {
                mutableState.value = mutableState.value.copy(loading = false, accepting = false)
            }
        }.also { action = it }
    }
}

private fun CookbookInvitationPreview.isExpired(clock: Clock): Boolean = try {
    !Instant.parse(expiresAt).isAfter(clock.instant())
} catch (_: DateTimeParseException) {
    true
}
