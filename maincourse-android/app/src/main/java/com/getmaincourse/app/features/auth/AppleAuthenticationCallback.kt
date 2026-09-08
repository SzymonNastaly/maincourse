package com.getmaincourse.app.features.auth

import java.net.URI

sealed interface AppleAuthenticationCallback {
    val transactionId: String

    data class Success(
        override val transactionId: String,
        val exchangeCode: String,
    ) : AppleAuthenticationCallback

    data class Error(
        override val transactionId: String,
        val error: AppleAuthenticationError,
    ) : AppleAuthenticationCallback
}

enum class AppleAuthenticationError(val wireValue: String) {
    CANCELLED("cancelled"),
    AUTHENTICATION_FAILED("authentication_failed"),
    TRANSACTION_EXPIRED("transaction_expired"),
    TRANSACTION_UNAVAILABLE("transaction_unavailable"),
    ACCOUNT_LINK_REQUIRED("account_link_required"),
    PROVIDER_UNAVAILABLE("provider_unavailable"),
    ;

    companion object {
        fun fromWireValue(value: String): AppleAuthenticationError? = entries.firstOrNull { it.wireValue == value }
    }
}

object AppleAuthenticationCallbackParser {
    private const val MAX_URI_LENGTH = 2_048
    private val opaqueValue = Regex("[A-Za-z0-9_-]{43}")

    fun parse(rawUri: String, isDebugBuild: Boolean): AppleAuthenticationCallback? {
        if (rawUri.length > MAX_URI_LENGTH) return null
        val uri = try {
            URI(rawUri)
        } catch (_: Exception) {
            return null
        }
        if (uri.fragment != null || uri.userInfo != null || uri.port != -1) return null
        if (!isAllowedDestination(uri, isDebugBuild)) return null

        val parameters = strictQuery(uri.rawQuery ?: return null) ?: return null
        val transactionId = parameters["transaction_id"]?.takeIf(opaqueValue::matches) ?: return null
        return when (parameters.keys) {
            setOf("transaction_id", "exchange_code") -> {
                val code = parameters["exchange_code"]?.takeIf(opaqueValue::matches) ?: return null
                AppleAuthenticationCallback.Success(transactionId, code)
            }
            setOf("transaction_id", "error") -> {
                val error = parameters["error"]?.let(AppleAuthenticationError::fromWireValue) ?: return null
                AppleAuthenticationCallback.Error(transactionId, error)
            }
            else -> null
        }
    }

    private fun isAllowedDestination(uri: URI, isDebugBuild: Boolean): Boolean {
        val release = uri.scheme == "https" && uri.host == "app.getmaincourse.com" &&
            uri.rawAuthority == "app.getmaincourse.com" && uri.rawPath == "/android/auth/apple"
        val debug = isDebugBuild && uri.scheme == "com.getmaincourse.app.debug" &&
            uri.rawAuthority == null && uri.rawPath == "/oauth/apple"
        return release || debug
    }

    private fun strictQuery(rawQuery: String): Map<String, String>? {
        if (rawQuery.isEmpty()) return null
        val result = linkedMapOf<String, String>()
        for (part in rawQuery.split('&')) {
            if (part.isEmpty() || part.count { it == '=' } != 1) return null
            val (key, value) = part.split('=', limit = 2)
            if (key.isEmpty() || value.isEmpty() || '%' in key || '%' in value || '+' in key || '+' in value) return null
            if (result.put(key, value) != null) return null
        }
        return result
    }
}
