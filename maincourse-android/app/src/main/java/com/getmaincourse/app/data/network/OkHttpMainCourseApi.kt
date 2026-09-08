package com.getmaincourse.app.data.network

import com.getmaincourse.app.data.model.AccountResponse
import com.getmaincourse.app.data.model.AccountUpdateRequest
import com.getmaincourse.app.data.model.AppleAuthenticationExchangeRequest
import com.getmaincourse.app.data.model.AppleAuthenticationStartRequest
import com.getmaincourse.app.data.model.AppleAuthenticationStartResponse
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.GoogleSignInRequest
import com.getmaincourse.app.data.model.OnboardingRequest
import com.getmaincourse.app.data.model.OnboardingResponse
import com.getmaincourse.app.data.model.MoveRecipeRequest
import com.getmaincourse.app.data.model.RecipeBatchResponse
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeUpdateRequest
import com.getmaincourse.app.data.model.RecipeSummary
import com.getmaincourse.app.data.model.SessionResponse
import com.getmaincourse.app.data.model.ShoppingItem
import com.getmaincourse.app.data.model.ShoppingItemsRequest
import com.getmaincourse.app.data.model.SignInRequest
import com.getmaincourse.app.data.model.SignUpRequest
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
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

    override suspend fun signInWithGoogle(request: GoogleSignInRequest): SessionResponse =
        executeJson(
            requestBuilder("api/v1/oauth_session")
                .post(json.encodeToString(request).toRequestBody(JSON_MEDIA_TYPE))
                .build(),
        )

    override suspend fun startAppleAuthentication(
        request: AppleAuthenticationStartRequest,
    ): AppleAuthenticationStartResponse {
        val response = executeJson<AppleAuthenticationStartResponse>(
            requestBuilder("api/v1/apple_auth_transaction")
                .post(json.encodeToString(request).toRequestBody(JSON_MEDIA_TYPE))
                .build(),
        )
        if (!response.hasExpectedBrowserUrl(baseUrl)) {
            throw ApiFailure(null, "Invalid response from server")
        }
        return response
    }

    override suspend fun exchangeAppleAuthentication(request: AppleAuthenticationExchangeRequest): SessionResponse =
        executeJson(
            requestBuilder("api/v1/apple_auth_transaction/exchange")
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
        val response = execute(
            authenticatedRequestBuilder("api/v1/session", token)
                .delete()
                .build(),
        )
        if (!response.isSuccessful) {
            throw withContext(Dispatchers.Default) { response.toApiFailure() }
        }
    }

    override suspend fun updateAccount(token: String, request: AccountUpdateRequest) =
        executeJson<AccountResponse>(
            authenticatedRequestBuilder("api/v1/account", token)
                .patch(json.encodeToString(request).toRequestBody(JSON_MEDIA_TYPE))
                .build(),
        ).user

    override suspend fun deleteAccount(token: String) {
        val response = execute(
            authenticatedRequestBuilder("api/v1/account", token)
                .delete()
                .build(),
        )
        if (!response.isSuccessful) {
            throw withContext(Dispatchers.Default) { response.toApiFailure() }
        }
    }

    override suspend fun submitOnboarding(request: OnboardingRequest): OnboardingResponse =
        executeJson(
            requestBuilder("api/v1/onboarding_response")
                .post(json.encodeToString(request).toRequestBody(JSON_MEDIA_TYPE))
                .build(),
        )

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

    override suspend fun recipeBatch(
        token: String,
        cookbookId: Long,
        cursor: String?,
    ): RecipeBatchResponse {
        val url = baseUrl.newBuilder()
            .addPathSegments("api/v1/recipes/batch")
            .addQueryParameter("limit", RECIPE_BATCH_LIMIT.toString())
            .apply { cursor?.let { addQueryParameter("cursor", it) } }
            .build()
        return executeJson(cookbookRequestBuilder(url, token, cookbookId).get().build())
    }

    override suspend fun updateRecipe(
        token: String,
        cookbookId: Long,
        recipeId: Long,
        request: RecipeUpdateRequest,
    ): RecipeDetail =
        executeJson(
            cookbookRequestBuilder("api/v1/recipes/$recipeId", token, cookbookId)
                .patch(json.encodeToString(request).toRequestBody(JSON_MEDIA_TYPE))
                .build(),
        )

    override suspend fun updateRecipeCover(
        token: String,
        cookbookId: Long,
        recipeId: Long,
        image: File,
    ): RecipeDetail {
        validateJpegUpload(image)
        return executeJson(
            cookbookRequestBuilder("api/v1/recipes/$recipeId", token, cookbookId)
                .patch(
                    MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart(
                            "cover_image",
                            image.name,
                            image.asRequestBody(JPEG_MEDIA_TYPE),
                        )
                        .build(),
                )
                .build(),
        )
    }

    override suspend fun moveRecipe(
        token: String,
        sourceCookbookId: Long,
        recipeId: Long,
        targetCookbookId: Long,
    ): RecipeDetail =
        executeJson(
            cookbookRequestBuilder("api/v1/recipes/$recipeId", token, sourceCookbookId)
                .patch(
                    json.encodeToString(MoveRecipeRequest(targetCookbookId)).toRequestBody(JSON_MEDIA_TYPE),
                )
                .build(),
        )

    override suspend fun deleteRecipe(token: String, cookbookId: Long, recipeId: Long) {
        val response = execute(
            cookbookRequestBuilder("api/v1/recipes/$recipeId", token, cookbookId)
                .delete()
                .build(),
        )
        if (!response.isSuccessful) {
            throw withContext(Dispatchers.Default) { response.toApiFailure() }
        }
    }

    override suspend fun addRecipeIngredients(
        token: String,
        cookbookId: Long,
        request: ShoppingItemsRequest,
    ): List<ShoppingItem> =
        executeJson(
            cookbookRequestBuilder("api/v1/shopping_list_items", token, cookbookId)
                .post(json.encodeToString(request).toRequestBody(JSON_MEDIA_TYPE))
                .build(),
        )

    private inline suspend fun <reified T> executeJson(request: Request): T {
        val response = execute(request)
        return withContext(Dispatchers.Default) {
            if (!response.isSuccessful) throw response.toApiFailure()

            try {
                json.decodeFromString<T>(response.body)
            } catch (_: SerializationException) {
                throw ApiFailure(null, "Invalid response from server")
            } catch (_: IllegalArgumentException) {
                throw ApiFailure(null, "Invalid response from server")
            }
        }
    }

    private suspend fun validateJpegUpload(image: File) = withContext(Dispatchers.IO) {
        require(SAFE_UPLOAD_NAME.matches(image.name)) { "Invalid image filename" }
        require(image.isFile && image.canRead()) { "Image file is unavailable" }
        require(image.length() in 3 until MAX_COVER_BYTES) { "Image file has an invalid size" }
        val signature = image.inputStream().use { input -> ByteArray(3).also { require(input.read(it) == it.size) } }
        require(signature.contentEquals(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte()))) {
            "Image file is not JPEG data"
        }
    }

    private suspend fun execute(request: Request): BufferedResponse =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(ApiFailure(null, "Network request failed"))
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (!continuation.isActive) {
                            response.close()
                            return
                        }
                        try {
                            val bufferedResponse = response.use {
                                BufferedResponse(
                                    status = it.code,
                                    body = it.body?.string().orEmpty(),
                                )
                            }
                            continuation.resume(bufferedResponse) { _, _, _ -> }
                        } catch (_: IOException) {
                            if (continuation.isActive) {
                                continuation.resumeWithException(ApiFailure(null, "Network request failed"))
                            }
                        }
                    }
                },
            )
        }

    private fun requestBuilder(path: String): Request.Builder =
        requestBuilder(baseUrl.newBuilder().addPathSegments(path).build())

    private fun requestBuilder(url: HttpUrl): Request.Builder =
        Request.Builder()
            .url(url)
            .header("Accept", JSON_MEDIA_TYPE.toString())

    private fun authenticatedRequestBuilder(path: String, token: String): Request.Builder =
        requestBuilder(path).header("Authorization", "Bearer $token")

    private fun cookbookRequestBuilder(
        path: String,
        token: String,
        cookbookId: Long,
    ): Request.Builder =
        cookbookRequestBuilder(baseUrl.newBuilder().addPathSegments(path).build(), token, cookbookId)

    private fun cookbookRequestBuilder(
        url: HttpUrl,
        token: String,
        cookbookId: Long,
    ): Request.Builder =
        requestBuilder(url)
            .header("Authorization", "Bearer $token")
            .header("X-Cookbook-Id", cookbookId.toString())

    private fun BufferedResponse.toApiFailure(): ApiFailure {
        val fallback = "Request failed with HTTP status $status"
        val apiError = try {
            json.parseToJsonElement(body) as? JsonObject
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        val errorCode = apiError.string("error_code")
        val limit = (apiError?.get("limit") as? JsonPrimitive)?.intOrNull
        val message = apiError.string("error")
            ?: apiError?.get("errors")?.errorMessages()?.takeIf { it.isNotEmpty() }?.joinToString("\n")
            ?: fallback
        return ApiFailure(status, message, errorCode, limit)
    }

    private data class BufferedResponse(
        val status: Int,
        val body: String,
    ) {
        val isSuccessful: Boolean
            get() = status in 200..299
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()
        val SAFE_UPLOAD_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}\\.jpe?g", RegexOption.IGNORE_CASE)
        const val RECIPE_BATCH_LIMIT = 100
        const val MAX_COVER_BYTES = 15_000_000L
    }
}

private fun JsonObject?.string(key: String): String? =
    this?.get(key)?.let { it as? JsonPrimitive }?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonElement.errorMessages(): List<String> = when (this) {
    is JsonPrimitive -> contentOrNull?.takeIf { it.isNotBlank() }?.let(::listOf).orEmpty()
    is JsonArray -> flatMap { it.errorMessages() }
    is JsonObject -> get("error")?.errorMessages()?.takeIf { it.isNotEmpty() }
        ?: values.flatMap { it.errorMessages() }
}

private fun AppleAuthenticationStartResponse.hasExpectedBrowserUrl(baseUrl: HttpUrl): Boolean {
    if (!OPAQUE_APPLE_VALUE.matches(transactionId) || browserUrl.length > 2_048) return false
    val parsed = browserUrl.toHttpUrlOrNull() ?: return false
    return parsed.scheme == baseUrl.scheme && parsed.host == baseUrl.host && parsed.port == baseUrl.port &&
        parsed.username.isEmpty() && parsed.password.isEmpty() && parsed.fragment == null &&
        parsed.encodedPath == "/android/apple/sign_in" && parsed.encodedQuery == "transaction_id=$transactionId"
}

private val OPAQUE_APPLE_VALUE = Regex("[A-Za-z0-9_-]{43}")
