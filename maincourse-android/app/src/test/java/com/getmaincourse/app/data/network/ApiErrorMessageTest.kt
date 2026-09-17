package com.getmaincourse.app.data.network

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
            SocketTimeoutException() to "Connection timed out. Please try again.",
            UnknownHostException() to "Could not find the server. Check your connection.",
            ConnectException() to "Could not connect to the server. Check your connection.",
            SSLHandshakeException("certificate") to "Could not establish a secure connection.",
            SocketException("reset") to "Connection interrupted. Please try again.",
        )
        for ((failure, message) in failures) {
            assertEquals(message, failure.userMessage("Could not refresh"))
        }
    }

    @Test
    fun otherIoFailuresKeepTheOperationContext() {
        assertEquals("Could not save the session", IOException("disk full").userMessage("Could not save the session"))
    }
}
