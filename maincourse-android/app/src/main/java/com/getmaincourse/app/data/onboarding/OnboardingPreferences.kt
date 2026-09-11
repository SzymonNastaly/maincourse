package com.getmaincourse.app.data.onboarding

import android.content.Context
import androidx.core.content.edit
import java.util.UUID

/** Small, non-sensitive first-run state. Authentication credentials never live here. */
class OnboardingPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    val hasCompleted: Boolean
        get() = preferences.contains(COMPLETED_AT_KEY)

    val hasReachedAuthentication: Boolean
        get() = preferences.contains(AUTH_REACHED_AT_KEY)

    @Synchronized
    fun deviceId(): String {
        preferences.getString(DEVICE_ID_KEY, null)?.let { return it }
        return UUID.randomUUID().toString().also { generated ->
            preferences.edit { putString(DEVICE_ID_KEY, generated) }
        }
    }

    fun pendingDeviceId(): String? = preferences.getString(DEVICE_ID_KEY, null)

    fun prepareForAuthentication(deviceId: String) {
        preferences.edit {
            putString(DEVICE_ID_KEY, deviceId)
            putLong(AUTH_REACHED_AT_KEY, System.currentTimeMillis())
        }
    }

    fun complete() {
        preferences.edit {
            putLong(COMPLETED_AT_KEY, System.currentTimeMillis())
            remove(AUTH_REACHED_AT_KEY)
        }
    }

    fun clearPendingDeviceId() {
        preferences.edit {
            remove(DEVICE_ID_KEY)
            remove(AUTH_REACHED_AT_KEY)
        }
    }

    private companion object {
        const val FILE_NAME = "maincourse_onboarding"
        const val DEVICE_ID_KEY = "device_id"
        const val COMPLETED_AT_KEY = "completed_at"
        const val AUTH_REACHED_AT_KEY = "auth_reached_at"
    }
}
