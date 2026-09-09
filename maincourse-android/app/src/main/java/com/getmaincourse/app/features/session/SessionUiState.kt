package com.getmaincourse.app.features.session

import com.getmaincourse.app.data.model.SessionResponse

sealed interface SessionUiState {
    data object Restoring : SessionUiState

    data class SignedOut(
        val authError: String? = null,
        val busy: Boolean = false,
    ) : SessionUiState

    data class SignedIn(
        val session: SessionResponse,
    ) : SessionUiState

    data class RestoreError(
        val message: String,
    ) : SessionUiState
}
