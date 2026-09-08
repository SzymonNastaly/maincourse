package com.getmaincourse.app.features.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.ClearCredentialException
import kotlinx.coroutines.withTimeoutOrNull

class GoogleCredentialSessionCleaner internal constructor(
    private val clearOperation: suspend () -> Unit,
) {
    constructor(context: Context) : this(clearOperation(context.applicationContext))

    suspend fun clear() {
        withTimeoutOrNull(CLEAR_TIMEOUT_MILLIS) {
            try {
                clearOperation()
            } catch (_: ClearCredentialException) {
                // Selection-state cleanup is best effort and must not block local cleanup.
            }
        }
    }

    private companion object {
        const val CLEAR_TIMEOUT_MILLIS = 2_000L

        fun clearOperation(applicationContext: Context): suspend () -> Unit {
            val credentialManager = CredentialManager.create(applicationContext)
            return { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
        }
    }
}
