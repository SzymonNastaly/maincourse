package com.getmaincourse.app.notifications

import android.content.Context
import androidx.core.content.edit

class PushRegistrationStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    var currentToken: String?
        get() = preferences.getString(CURRENT_TOKEN, null)
        set(value) = preferences.edit { putString(CURRENT_TOKEN, value) }

    var uploadedToken: String?
        get() = preferences.getString(UPLOADED_TOKEN, null)
        set(value) = preferences.edit { putString(UPLOADED_TOKEN, value) }

    var uploadedAtMillis: Long
        get() = preferences.getLong(UPLOADED_AT, 0L)
        set(value) = preferences.edit { putLong(UPLOADED_AT, value) }

    var permissionAsked: Boolean
        get() = preferences.getBoolean(PERMISSION_ASKED, false)
        set(value) = preferences.edit { putBoolean(PERMISSION_ASKED, value) }

    fun clearRegistration() {
        preferences.edit {
            remove(CURRENT_TOKEN)
            remove(UPLOADED_TOKEN)
            remove(UPLOADED_AT)
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "push_registration"
        const val CURRENT_TOKEN = "current_token"
        const val UPLOADED_TOKEN = "uploaded_token"
        const val UPLOADED_AT = "uploaded_at"
        const val PERMISSION_ASKED = "permission_asked"
    }
}
