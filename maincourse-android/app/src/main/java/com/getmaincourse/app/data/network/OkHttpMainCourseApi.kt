package com.getmaincourse.app.data.network

import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeSummary
import com.getmaincourse.app.data.model.SessionResponse
import com.getmaincourse.app.data.model.SignInRequest
import com.getmaincourse.app.data.model.SignUpRequest
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

class OkHttpMainCourseApi(
    private val baseUrl: HttpUrl,
    client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json { ignoreUnknownKeys = true },
) : MainCourseApi {
    private val client = client.newBuilder()
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    override suspend fun signIn(request: SignInRequest): SessionResponse =
        executeJson(
            requestBuilder("api/v1/session")
                .post(json.encodeToString(request).toRequestBody(JSON_MEDIA_TYPE))
                .build(),
        )

    override suspend fun signUp(request: SignUpRequest): SessionResponse =
        executeJson(
            requestBuilder("api/v1/registration")
                .post(json.encodeToString(request).toRequestBody(JSON_MEDIA_TYPE))
                .build(),
        )

    override suspend fun signOut(token: String) {
        execute(
            authenticatedRequestBuilder("api/v1/session", token)
                .delete()
                .build(),
        ).use { response ->
            if (!response.isSuccessful) throw response.toApiFailure()
        }
    }

    override suspend fun cookbooks(token: String): List<Cookbook> =
        executeJson(
            authenticatedRequestBuilder("api/v1/cookbooks", token).get().build(),
        )

    override suspend fun recipes(token: String, cookbookId: Long): List<RecipeSummary> =
        executeJson(
            cookbookRequestBuilder("api/v1/recipes", token, cookbookId).get().build(),
        )

    override suspend fun recipe(token: String, cookbookId: Long, recipeId: Long): RecipeDetail =
        executeJson(
            cookbookRequestBuilder("api/v1/recipes/$recipeId", token, cookbookId).get().build(),
        )

    private inline suspend fun <reified T> executeJson(request: Request): T =
        execute(request).use { response ->
            if (!response.isSuccessful) throw response.toApiFailure()

            val body = response.body?.string().orEmpty()
            try {
                json.decodeFromString<T>(body)
            } catch (_: SerializationException) {
                throw ApiFailure(null, "Invalid response from server")
            } catch (_: IllegalArgumentException) {
                throw ApiFailure(null, "Invalid response from server")
            }
        }

    private suspend fun execute(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                ApiFailure(null, "Network request failed"),
                            )
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        continuation.resume(response) { _, unconsumedResponse, _ ->
                            unconsumedResponse.close()
                        }
                    }
                },
            )
        }

    private fun requestBuilder(path: String): Request.Builder =
        Request.Builder()
            .url(baseUrl.newBuilder().addPathSegments(path).build())
            .header("Accept", JSON_MEDIA_TYPE.toString())

    private fun authenticatedRequestBuilder(path: String, token: String): Request.Builder =
        requestBuilder(path).header("Authorization", "Bearer $token")

    private fun cookbookRequestBuilder(
        path: String,
        token: String,
        cookbookId: Long,
    ): Request.Builder =
        authenticatedRequestBuilder(path, token)
            .header("X-Cookbook-Id", cookbookId.toString())

    private fun Response.toApiFailure(): ApiFailure {
        val fallback = "Request failed with HTTP status $code"
        val responseBody = body?.string().orEmpty()
        val apiError = try {
            json.decodeFromString<ApiErrorBody>(responseBody)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        val message = apiError?.error?.takeIf { it.isNotBlank() }
            ?: apiError?.errors?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() }?.joinToString("\n")
            ?: fallback
        return ApiFailure(code, message)
    }

    @Serializable
    private data class ApiErrorBody(
        val error: String? = null,
        val errors: List<String>? = null,
    )

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
