package com.getmaincourse.app.data.network
import com.getmaincourse.app.R
import com.getmaincourse.app.ui.UiMessage

import java.io.IOException
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Test

class ApiErrorMessageTest {
    @Test
    fun networkFailuresExplainTheFailureWithoutClaimingTheDeviceIsOffline() {
        val failures = listOf(
            SocketTimeoutException() to R.string.error_connection_timeout,
            UnknownHostException() to R.string.error_server_not_found,
            ConnectException() to R.string.error_connection_failed,
            SSLHandshakeException("certificate") to R.string.error_secure_connection,
            SocketException("reset") to R.string.error_connection_interrupted,
        )
        for ((failure, message) in failures) {
            assertEquals(UiMessage.Resource(message), failure.userMessage(UiMessage.Resource(R.string.error_refresh_recipes)))
        }
    }

    @Test
    fun otherIoFailuresKeepTheOperationContext() {
        val fallback = UiMessage.Resource(R.string.error_save_session)
        assertEquals(fallback, IOException("disk full").userMessage(fallback))
    }
}
