package com.getmaincourse.app.data.network

import com.getmaincourse.app.data.model.AccountUpdateRequest
import com.getmaincourse.app.data.model.AppleAuthenticationExchangeRequest
import com.getmaincourse.app.data.model.AppleAuthenticationStartRequest
import com.getmaincourse.app.data.model.AppleAuthenticationStartResponse
import com.getmaincourse.app.data.model.GoogleSignInRequest
import com.getmaincourse.app.data.model.MoveRecipeRequest
import com.getmaincourse.app.data.model.OnboardingRequest
import com.getmaincourse.app.data.model.RecipeUpdateRequest
import com.getmaincourse.app.data.model.ShoppingItemsRequest
import com.getmaincourse.app.data.model.SignInRequest
import com.getmaincourse.app.data.model.SignUpRequest
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class RetrofitMainCourseApi(
    private val baseUrl: HttpUrl,
    client: OkHttpClient = OkHttpClient(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = true
    },
) : MainCourseApi {
    private val service = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(
            client.newBuilder()
                .callTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .followRedirects(false)
                .followSslRedirects(false)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("Accept", JSON_MEDIA_TYPE.toString())
                        .build()
                    chain.proceed(request)
                }
                .build(),
        )
        .addConverterFactory(json.asConverterFactory(JSON_MEDIA_TYPE))
        .build()
        .create(RetrofitMainCourseService::class.java)

    override suspend fun signIn(request: SignInRequest) = apiCall { service.signIn(request) }

    override suspend fun signInWithGoogle(request: GoogleSignInRequest) =
        apiCall { service.signInWithGoogle(request) }

    override suspend fun startAppleAuthentication(request: AppleAuthenticationStartRequest) =
        apiCall { service.startAppleAuthentication(request) }.also { response ->
            if (!response.hasExpectedBrowserUrl(baseUrl)) throw ApiFailure(null, "Invalid response from server")
        }

    override suspend fun exchangeAppleAuthentication(request: AppleAuthenticationExchangeRequest) =
        apiCall { service.exchangeAppleAuthentication(request) }

    override suspend fun signUp(request: SignUpRequest) = apiCall { service.signUp(request) }

    override suspend fun signOut(token: String) = apiCall { service.signOut(token.bearer()) }

    override suspend fun updateAccount(token: String, request: AccountUpdateRequest) =
        apiCall { service.updateAccount(token.bearer(), request) }.user

    override suspend fun deleteAccount(token: String) = apiCall { service.deleteAccount(token.bearer()) }

    override suspend fun submitOnboarding(request: OnboardingRequest) =
        apiCall { service.submitOnboarding(request) }

    override suspend fun cookbooks(token: String) = apiCall { service.cookbooks(token.bearer()) }

    override suspend fun recipes(token: String, cookbookId: Long) =
        apiCall { service.recipes(token.bearer(), cookbookId) }

    override suspend fun recipe(token: String, cookbookId: Long, recipeId: Long) =
        apiCall { service.recipe(token.bearer(), cookbookId, recipeId) }

    override suspend fun recipeBatch(token: String, cookbookId: Long, cursor: String?) =
        apiCall { service.recipeBatch(token.bearer(), cookbookId, RECIPE_BATCH_LIMIT, cursor) }

    override suspend fun updateRecipe(
        token: String,
        cookbookId: Long,
        recipeId: Long,
        request: RecipeUpdateRequest,
    ) = apiCall { service.updateRecipe(token.bearer(), cookbookId, recipeId, request) }

    override suspend fun updateRecipeCover(token: String, cookbookId: Long, recipeId: Long, image: File) =
        withContext(Dispatchers.IO) {
            validateJpegUpload(image)
            val cover = MultipartBody.Part.createFormData(
                "cover_image",
                image.name,
                image.asRequestBody(JPEG_MEDIA_TYPE),
            )
            apiCall { service.updateRecipeCover(token.bearer(), cookbookId, recipeId, cover) }
        }

    override suspend fun moveRecipe(
        token: String,
        sourceCookbookId: Long,
        recipeId: Long,
        targetCookbookId: Long,
    ) = apiCall {
        service.moveRecipe(token.bearer(), sourceCookbookId, recipeId, MoveRecipeRequest(targetCookbookId))
    }

    override suspend fun deleteRecipe(token: String, cookbookId: Long, recipeId: Long) =
        apiCall { service.deleteRecipe(token.bearer(), cookbookId, recipeId) }

    override suspend fun addRecipeIngredients(
        token: String,
        cookbookId: Long,
        request: ShoppingItemsRequest,
    ) = apiCall { service.addRecipeIngredients(token.bearer(), cookbookId, request) }

    private suspend fun <T> apiCall(call: suspend () -> T): T = withContext(Dispatchers.IO) {
        try {
            call()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: HttpException) {
            throw failure.toApiFailure()
        } catch (_: IOException) {
            throw ApiFailure(null, "Network request failed")
        } catch (_: SerializationException) {
            throw ApiFailure(null, "Invalid response from server")
        } catch (_: NullPointerException) {
            throw ApiFailure(null, "Invalid response from server")
        }
    }

    private fun HttpException.toApiFailure(): ApiFailure {
        val status = code()
        val fallback = "Request failed with HTTP status $status"
        val body = try {
            response()?.errorBody()?.string().orEmpty()
        } catch (_: IOException) {
            throw ApiFailure(null, "Network request failed")
        }
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

    private fun validateJpegUpload(image: File) {
        require(SAFE_UPLOAD_NAME.matches(image.name)) { "Invalid image filename" }
        require(image.isFile && image.canRead()) { "Image file is unavailable" }
        require(image.length() in 3 until MAX_COVER_BYTES) { "Image file has an invalid size" }
        val signature = image.inputStream().use { input ->
            ByteArray(3).also { require(input.read(it) == it.size) }
        }
        require(signature.contentEquals(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte()))) {
            "Image file is not JPEG data"
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        val JPEG_MEDIA_TYPE = "image/jpeg".toMediaType()
        val SAFE_UPLOAD_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}\\.jpe?g", RegexOption.IGNORE_CASE)
        const val RECIPE_BATCH_LIMIT = 100
        const val MAX_COVER_BYTES = 15_000_000L
    }
}

private fun String.bearer() = "Bearer $this"

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
