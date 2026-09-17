package com.getmaincourse.app.data.network

import com.getmaincourse.app.data.session.SessionProvider
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

class ApiCallFactoryTest {
    private val server = MockWebServer()
    private val client = OkHttpClient.Builder()
        .callTimeout(3, TimeUnit.SECONDS)
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(1, TimeUnit.SECONDS)
        .build()

    @Before
    fun setUp() {
        server.start(InetAddress.getByName("127.0.0.1"), 0)
    }

    @After
    fun tearDown() {
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
        server.shutdown()
    }

    @Test
    fun readsAndMutationsReachAWorkingAddressWithoutWaitingForTheFirstAddressTimeout() {
        for (method in listOf("GET", "POST", "PATCH", "DELETE")) {
            client.connectionPool.evictAll()
            val factory = ApiCallFactory(
                SessionProvider(),
                SessionEvents(),
                client.newBuilder().dns(object : Dns {
                    override fun lookup(hostname: String) = listOf(
                        InetAddress.getByName("127.0.0.2"),
                        InetAddress.getByName("127.0.0.1"),
                    )
                }).build(),
            )
            server.enqueue(MockResponse().setBody("acknowledged"))
            val body = if (method in listOf("POST", "PATCH")) "{}".toRequestBody() else null
            val request = Request.Builder()
                .url(server.url("/").newBuilder().host("api.test").build())
                .method(method, body)
                .build()

            factory.newCall(request).execute().use {
                assertEquals("acknowledged", it.body!!.string())
            }
            assertEquals(method, server.takeRequest().method)
        }
        assertEquals(4, server.requestCount)
    }

    @Test
    fun mutationsAreNotReplayedWhenTheServerReceivesThemButTheResponseIsLost() {
        val factory = ApiCallFactory(SessionProvider(), SessionEvents(), client)
        for (method in listOf("POST", "PATCH", "DELETE")) {
            // Establish a pooled connection so a retry would have a known working route.
            server.enqueue(MockResponse())
            factory.newCall(Request.Builder().url(server.url("/")).build()).execute().close()
            server.takeRequest()
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            val request = Request.Builder().url(server.url("/mutation"))
                .method(method, if (method == "DELETE") null else "{}".toRequestBody())
                .build()

            assertThrows(IOException::class.java) { factory.newCall(request).execute().close() }
            assertEquals(method, server.takeRequest().method)
        }
        assertEquals(6, server.requestCount)
    }

    @Test
    fun readRecoversFromAPooledConnectionThatClosesBeforeItsResponse() {
        val factory = ApiCallFactory(SessionProvider(), SessionEvents(), client)
        server.enqueue(MockResponse())
        val request = Request.Builder().url(server.url("/")).build()
        factory.newCall(request).execute().close()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(MockResponse().setBody("fresh"))

        factory.newCall(request).execute().use { assertEquals("fresh", it.body!!.string()) }
        assertEquals(3, server.requestCount)
    }

    @Test
    fun failuresReachTheConfiguredDiagnosticLogger() {
        val messages = mutableListOf<String>()
        val factory = ApiCallFactory(SessionProvider(), SessionEvents(), client, messages::add)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        val request = Request.Builder().url(server.url("/private-token"))
            .post("private-body".toRequestBody()).build()

        assertThrows(IOException::class.java) { factory.newCall(request).execute().close() }

        org.junit.Assert.assertTrue(messages.single().contains("failure="))
        org.junit.Assert.assertFalse(messages.single().contains("private-"))
    }

    @Test
    fun mutationsAreNotReplayedOnRetryAfterResponses() {
        val factory = ApiCallFactory(SessionProvider(), SessionEvents(), client)
        server.enqueue(MockResponse().setResponseCode(503).setHeader("Retry-After", "0"))
        server.enqueue(MockResponse().setResponseCode(200))
        val request = Request.Builder().url(server.url("/mutation")).delete().build()

        factory.newCall(request).execute().use { assertEquals(503, it.code) }
        assertEquals(1, server.requestCount)
    }
}
