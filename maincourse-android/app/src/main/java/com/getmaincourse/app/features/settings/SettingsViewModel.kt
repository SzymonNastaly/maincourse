package com.getmaincourse.app.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getmaincourse.app.data.model.AccountAttributes
import com.getmaincourse.app.data.model.AccountUpdateRequest
import com.getmaincourse.app.data.model.User
import com.getmaincourse.app.data.network.MainCourseService
import com.getmaincourse.app.data.network.userMessage
import com.getmaincourse.app.data.session.SessionProvider
import com.getmaincourse.app.data.session.SessionStore
import com.getmaincourse.app.data.session.StoredSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val user: User? = null,
    val saving: Boolean = false,
    val deleting: Boolean = false,
    val error: String? = null,
)

class SettingsViewModel(
    private val service: MainCourseService,
    private val sessionStore: SessionStore,
    private val sessionProvider: SessionProvider,
    private val deleteAccount: () -> Job,
    private val signOut: () -> Job,
) : ViewModel() {
    private val action = MutableStateFlow(ActionState())
    private var actionJob: Job? = null

    val state = combine(sessionProvider.session, action) { session, actionState ->
        SettingsUiState(
            user = session?.user,
            saving = actionState.saving,
            deleting = actionState.deleting,
            error = actionState.error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsUiState(user = sessionProvider.session.value?.user),
    )

    fun saveProfile(name: String, remindersEnabled: Boolean): Job = launchAction(saving = true) {
        val current = sessionProvider.session.value ?: return@launchAction
        val requestedName = name.trim()
        val stored = try {
            sessionStore.read()
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Throwable) {
            action.value = action.value.copy(error = SAVE_FAILURE)
            return@launchAction
        }
        if (stored == null || stored.response.token != current.token) {
            action.value = action.value.copy(error = SAVE_FAILURE)
            return@launchAction
        }

        val updatedUser = try {
            service.updateAccount(
                AccountUpdateRequest(
                    AccountAttributes(
                        name = requestedName,
                        lifecycleNotificationsEnabled = remindersEnabled,
                    ),
                ),
            ).user
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            action.value = action.value.copy(error = failure.userMessage("Could not update account"))
            return@launchAction
        }
        if (updatedUser.id != current.user.id || sessionProvider.session.value?.token != current.token) {
            action.value = action.value.copy(error = SAVE_FAILURE)
            return@launchAction
        }

        val accepted = stored.copy(response = current.copy(user = updatedUser))
        persistAndPublish(accepted, current.token)
    }

    fun deleteAccount(): Job = launchAction(deleting = true) {
        deleteAccount.invoke().join()
        if (sessionProvider.session.value != null) {
            action.value = action.value.copy(error = "Could not delete account")
        }
    }

    fun signOut(): Job = signOut.invoke()

    fun clearError() {
        action.value = action.value.copy(error = null)
    }

    private suspend fun persistAndPublish(session: StoredSession, token: String) {
        try {
            sessionStore.write(session)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Throwable) {
            action.value = action.value.copy(error = SAVE_FAILURE)
            return
        }
        if (sessionProvider.session.value?.token == token) {
            sessionProvider.set(session.response)
        }
    }

    private fun launchAction(
        saving: Boolean = false,
        deleting: Boolean = false,
        block: suspend () -> Unit,
    ): Job {
        actionJob?.takeIf(Job::isActive)?.let { return it }
        lateinit var launched: Job
        launched = viewModelScope.launch(start = CoroutineStart.LAZY) {
            action.value = ActionState(saving = saving, deleting = deleting)
            try {
                block()
            } finally {
                action.value = action.value.copy(saving = false, deleting = false)
                if (actionJob === launched) actionJob = null
            }
        }
        actionJob = launched
        launched.start()
        return launched
    }

    private data class ActionState(
        val saving: Boolean = false,
        val deleting: Boolean = false,
        val error: String? = null,
    )

    private companion object {
        const val SAVE_FAILURE = "Could not save account changes"
    }
}
