package com.getmaincourse.app.data.session

import com.getmaincourse.app.data.model.SessionResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionProvider {
    private val mutableSession = MutableStateFlow<SessionResponse?>(null)
    private val mutablePendingAcceptedSession = MutableStateFlow<SessionResponse?>(null)

    val session: StateFlow<SessionResponse?> = mutableSession.asStateFlow()
    val pendingAcceptedSession: StateFlow<SessionResponse?> = mutablePendingAcceptedSession.asStateFlow()

    fun set(value: SessionResponse) {
        mutablePendingAcceptedSession.value = null
        mutableSession.value = value
    }

    fun setPendingAcceptedSession(value: SessionResponse) {
        mutablePendingAcceptedSession.value = value
    }

    fun clear() {
        mutablePendingAcceptedSession.value = null
        mutableSession.value = null
    }
}
