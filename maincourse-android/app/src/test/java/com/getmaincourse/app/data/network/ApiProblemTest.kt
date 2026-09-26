package com.getmaincourse.app.data.network

import com.getmaincourse.app.R
import com.getmaincourse.app.ui.UiMessage
import com.getmaincourse.app.data.session.SessionProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class ApiProblemTest {
    private val strings = ApiStrings { resource, arguments -> "$resource:${arguments.joinToString("|")}" }

    @Test
    fun knownCodesUseResourcesAndUnknownValuesUseExplicitFallbacks() {
        val problem = ApiProblem.parse("""{"error_code":"validation_failed","error":"DO NOT DISPLAY","error_details":[{"field":"name","code":"too_long","params":{"count":50}}]}""")
        assertEquals(
            strings.text(R.string.api_rule_too_long_count, strings.text(R.string.api_field_name), 50L),
            problem.message(strings),
        )
        for (body in listOf("{}", "<html>bad gateway</html>", """{"error_code":"future"}""",
            """{"error_code":"text_too_long","error_params":{"count":"invalid"}}""")) {
            assertEquals(strings.text(R.string.api_error_invalid_request), ApiProblem.parse(body).message(strings))
        }
        val unknownField = ApiProblem.parse("""{"error_code":"validation_failed","error_details":[{"field":"future","code":"future"}]}""")
        assertEquals(strings.text(R.string.api_rule_invalid, strings.text(R.string.api_field_base)), unknownField.message(strings))
        assertEquals(strings.text(R.string.api_import_no_recipe_in_photo), importFailureMessage("no_recipe_in_photo", strings))
        assertEquals(strings.text(R.string.api_import_failed), importFailureMessage("future", strings))
    }

    @Test
    fun retrofitAdapterUsesContractAndPreservesHttpStatusForExistingRecovery() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val service = Retrofit.Builder().baseUrl(server.url("/"))
                .callFactory(ApiCallFactory(SessionProvider(), SessionEvents()))
                .addCallAdapterFactory(ApiErrorCallAdapterFactory())
                .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
                .build().create(MainCourseService::class.java)
            for ((status, code, resource) in listOf(
                Triple(401, "invalid_credentials", R.string.api_error_invalid_credentials),
                Triple(413, "content_too_large", R.string.api_error_content_too_large),
                Triple(422, "image_required", R.string.api_error_image_required),
                Triple(503, "oauth_unavailable", R.string.api_error_oauth_unavailable),
            )) {
                server.enqueue(MockResponse().setResponseCode(status).setBody("""{"error_code":"$code","error":"DO NOT DISPLAY"}"""))
                val failure = runCatching { service.cookbooks() }.exceptionOrNull()
                assertTrue(failure is HttpException)
                assertEquals(status, (failure as HttpException).code())
                assertEquals(strings.text(resource), failure.userMessage(UiMessage.Resource(R.string.error_sign_in)).resolve(strings))
                assertEquals(code, (failure as LocalizedApiException).problem.errorCode)
            }
            server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
            assertEquals(emptyList<Any>(), service.cookbooks())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun retrofitAdapterFallsBackToHttpStatusForMissingUnknownAndMalformedCodes() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            val service = Retrofit.Builder().baseUrl(server.url("/"))
                .callFactory(ApiCallFactory(SessionProvider(), SessionEvents()))
                .addCallAdapterFactory(ApiErrorCallAdapterFactory())
                .addConverterFactory(Json.asConverterFactory("application/json".toMediaType()))
                .build().create(MainCourseService::class.java)
            for ((status, resource) in listOf(
                502 to R.string.api_error_server_unavailable,
                429 to R.string.api_error_rate_limited,
                401 to R.string.api_error_unauthorized,
                403 to R.string.api_error_forbidden,
                404 to R.string.api_error_not_found,
                422 to R.string.api_error_invalid_request,
                409 to R.string.api_error_request_failed,
            )) {
                for (body in listOf(
                    "<html>bad gateway</html>",
                    "",
                    """{"error":"DO NOT DISPLAY"}""",
                    """{"error_code":"future","error":"DO NOT DISPLAY"}""",
                    """{"error_code":123}""",
                )) {
                    server.enqueue(MockResponse().setResponseCode(status).setBody(body))
                    val failure = runCatching { service.cookbooks() }.exceptionOrNull()
                    assertTrue(failure is HttpException)
                    assertEquals(status, (failure as HttpException).code())
                    assertEquals("HTTP $status: $body", strings.text(resource), failure.userMessage(UiMessage.Resource(R.string.error_sign_in)).resolve(strings))
                }
            }
        } finally {
            server.shutdown()
        }
    }
}
