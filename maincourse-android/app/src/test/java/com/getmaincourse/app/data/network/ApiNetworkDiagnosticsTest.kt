package com.getmaincourse.app.data.network

import java.net.SocketTimeoutException
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Invocation
import retrofit2.Retrofit

class ApiNetworkDiagnosticsTest {
    @Test
    fun failureReportsOperationAndStageWithoutRequestOrExceptionSecrets() {
        val messages = mutableListOf<String>()
        val diagnostics = ApiNetworkDiagnostics(messages::add)
        val service = Retrofit.Builder().baseUrl("https://app.getmaincourse.com/")
            .build().create(MainCourseService::class.java)
        val request = Request.Builder()
            .url("https://app.getmaincourse.com/api/v1/invitations/private-token?email=private-email")
            .header("Authorization", "Bearer private-bearer")
            .tag(Invocation::class.java, Invocation.of(
                MainCourseService::class.java,
                service,
                MainCourseService::class.java.methods.first { it.name == "cookbookInvitation" },
                listOf("private-argument"),
            ))
            .build()
        val call = OkHttpClient().newCall(request)

        diagnostics.callStart(call)
        diagnostics.secureConnectStart(call)
        diagnostics.callFailed(call, SocketTimeoutException("private-exception-message"))

        val message = messages.single()
        assertTrue(message.contains("operation=cookbookInvitation"))
        assertTrue(message.contains("host=app.getmaincourse.com"))
        assertTrue(message.contains("phase=tls"))
        assertTrue(message.contains("failure=SocketTimeoutException"))
        assertTrue(message.contains("elapsedMs="))
        assertFalse(message.contains("private-"))
        assertEquals(1, messages.size)
    }
}
