package com.getmaincourse.app.data.network

import com.getmaincourse.app.R
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

fun interface ApiStrings {
    fun text(resource: Int, vararg arguments: Any): String
}

@Serializable
data class ApiProblem(
    @SerialName("error_code") val errorCode: String? = null,
    @SerialName("error_params") val parameters: Parameters? = null,
    @SerialName("error_details") val details: List<Detail>? = null,
) {
    @Serializable data class Parameters(val count: Long? = null)
    @Serializable data class Detail(val field: String, val code: String, val params: Parameters? = null)

    fun message(strings: ApiStrings): String {
        val code = ApiErrorCode.fromWire(errorCode) ?: return strings.text(R.string.api_error_invalid_request)
        if (code == ApiErrorCode.VALIDATION_FAILED && !details.isNullOrEmpty()) {
            return details.joinToString("\n") { detail ->
                val field = (ValidationFieldCode.fromWire(detail.field) ?: ValidationFieldCode.BASE).message(strings)
                val rule = ValidationRuleCode.fromWire(detail.code) ?: ValidationRuleCode.INVALID
                rule.message(strings, field, detail.params?.count)
            }
        }
        return code.message(strings, parameters?.count)
    }

    fun message(strings: ApiStrings, status: Int?): String {
        if (ApiErrorCode.fromWire(errorCode) != null) return message(strings)
        val resource = when (status) {
            401 -> R.string.api_error_unauthorized
            403 -> R.string.api_error_forbidden
            404 -> R.string.api_error_not_found
            400, 422 -> R.string.api_error_invalid_request
            413 -> R.string.api_error_content_too_large
            429 -> R.string.api_error_rate_limited
            in 500..599 -> R.string.api_error_server_unavailable
            else -> R.string.api_error_request_failed
        }
        return strings.text(resource)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(body: String): ApiProblem = try {
            json.decodeFromString<ApiProblem>(body)
        } catch (_: IllegalArgumentException) {
            ApiProblem()
        }
    }
}

fun importFailureMessage(code: String?, strings: ApiStrings): String =
    (ImportErrorCode.fromWire(code) ?: ImportErrorCode.IMPORT_FAILED).message(strings)
