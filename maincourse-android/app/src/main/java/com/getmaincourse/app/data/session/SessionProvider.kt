package com.getmaincourse.app.data.session

import com.getmaincourse.app.data.model.SessionResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionProvider {
    private val mutableSession = MutableStateFlow<SessionResponse?>(null)

    val session: StateFlow<SessionResponse?> = mutableSession.asStateFlow()

    fun set(value: SessionResponse) {
        mutableSession.value = value
    }

    fun clear() {
        mutableSession.value = null
    }
}
