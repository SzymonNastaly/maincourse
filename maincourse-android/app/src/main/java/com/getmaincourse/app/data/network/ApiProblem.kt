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
