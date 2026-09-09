package com.getmaincourse.app.data.network

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import retrofit2.HttpException

fun Throwable.userMessage(fallback: String): String = when (this) {
    is CancellationException -> throw this
    is IOException -> "You're offline"
    is HttpException -> apiMessage() ?: fallback
    is ApiFailure -> message?.takeIf(String::isNotBlank) ?: fallback
    else -> fallback
}

private fun HttpException.apiMessage(): String? {
    val body = try {
        response()?.errorBody()?.string().orEmpty()
    } catch (_: IOException) {
        return null
    }
    val error = try {
        Json.parseToJsonElement(body) as? JsonObject
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }
    return error.string("error")
        ?: error?.get("errors")?.messages()?.takeIf(List<String>::isNotEmpty)?.joinToString("\n")
}

private fun JsonObject?.string(key: String): String? =
    this?.get(key)?.let { it as? JsonPrimitive }?.contentOrNull?.takeIf(String::isNotBlank)

private fun JsonElement.messages(): List<String> = when (this) {
    is JsonPrimitive -> contentOrNull?.takeIf(String::isNotBlank)?.let(::listOf).orEmpty()
    is JsonArray -> flatMap(JsonElement::messages)
    is JsonObject -> get("error")?.messages()?.takeIf(List<String>::isNotEmpty)
        ?: values.flatMap(JsonElement::messages)
}
