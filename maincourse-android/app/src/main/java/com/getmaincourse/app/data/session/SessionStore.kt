package com.getmaincourse.app.data.session

import com.getmaincourse.app.data.model.SessionResponse
import kotlinx.serialization.Serializable

@Serializable
data class StoredSession(
    val baseUrl: String,
    val response: SessionResponse,
)

interface SessionStore {
    suspend fun read(): StoredSession?

    suspend fun write(session: StoredSession)

    suspend fun clear()
}
