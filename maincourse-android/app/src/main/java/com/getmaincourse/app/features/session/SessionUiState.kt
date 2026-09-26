package com.getmaincourse.app.features.session

import com.getmaincourse.app.data.model.SessionResponse
import com.getmaincourse.app.ui.UiMessage

sealed interface SessionUiState {
    data object Restoring : SessionUiState

    data class SignedOut(
        val authError: UiMessage? = null,
        val busy: Boolean = false,
    ) : SessionUiState

    data class SignedIn(
        val session: SessionResponse,
    ) : SessionUiState

    data class RestoreError(
        val message: UiMessage,
    ) : SessionUiState

    data class CleanupError(
        val message: UiMessage,
    ) : SessionUiState
}
