package com.getmaincourse.app.data.onboarding

import android.content.Context
import android.annotation.SuppressLint
import androidx.core.content.edit
import kotlinx.serialization.json.Json

/** Small, non-sensitive first-run state. Authentication credentials never live here. */
class OnboardingPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    val hasCompleted: Boolean
        get() = preferences.contains(COMPLETED_AT_KEY)

    val hasReachedAuthentication: Boolean
        get() = preferences.contains(AUTH_REACHED_AT_KEY)

    val demoCompleted: Boolean get() = preferences.getBoolean("demo_completed", false)
    val authLogin: Boolean get() = preferences.getBoolean("auth_login", false)

    fun finishDemo() {
        preferences.edit { putBoolean("demo_completed", true); putBoolean("pending_demo_dismissal", true) }
    }

    fun bindDemoDismissal(userId: Long) {
        if (preferences.getBoolean("pending_demo_dismissal", false)) {
            preferences.edit { putBoolean("demo_dismissed_$userId", true); remove("pending_demo_dismissal") }
        }
    }

    fun isDemoDismissed(userId: Long): Boolean = preferences.getBoolean("demo_dismissed_$userId", false)

    fun dismissDemo(userId: Long) { preferences.edit { putBoolean("demo_dismissed_$userId", true) } }

    fun saveIntent(): SampleSaveIntent? = preferences.getString("sample_save", null)?.let {
        runCatching { Json.decodeFromString<SampleSaveIntent>(it) }.getOrNull()
    }

    @SuppressLint("UseKtx") // Unlike edit(commit = true), this checks the persistence result.
    fun writeSaveIntent(intent: SampleSaveIntent?) {
        // Commit the idempotency key before any request can leave the device.
        check(preferences.edit().apply {
            if (intent == null) remove("sample_save") else putString("sample_save", Json.encodeToString(intent))
        }.commit())
    }

    fun prepareForAuthentication(login: Boolean) {
        preferences.edit {
            putLong(AUTH_REACHED_AT_KEY, System.currentTimeMillis())
            putBoolean("auth_login", login)
        }
    }

    // Finish linking an answer submitted by an older installation, without creating new quiz IDs.
    fun pendingDeviceId(): String? = preferences.getString(DEVICE_ID_KEY, null)

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
