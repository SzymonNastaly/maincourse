package com.getmaincourse.app.data.network

import com.getmaincourse.app.data.model.SessionResponse
import com.getmaincourse.app.data.model.User
import com.getmaincourse.app.data.session.SessionProvider
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class AuthInterceptorTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun protectedRequestAddsCurrentBearer() = runTest {
        val provider = SessionProvider().apply { set(session("token")) }
        val client = client(provider, SessionEvents())
        server.enqueue(MockResponse())

        client.execute("/recipes")

        assertEquals("Bearer token", server.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun anonymousRequestRemovesMarkerAndAuthorization() = runTest {
        val provider = SessionProvider().apply { set(session("secret")) }
        val client = client(provider, SessionEvents())
        server.enqueue(MockResponse())

        client.execute("/session", anonymous = true)

        val request = server.takeRequest()
        assertNull(request.getHeader("X-MainCourse-Anonymous"))
        assertNull(request.getHeader("Authorization"))
    }

    @Test
    fun protectedRequestPreservesExplicitCookbookHeader() = runTest {
        val provider = SessionProvider().apply { set(session("token")) }
        val client = client(provider, SessionEvents())
        server.enqueue(MockResponse())

        client.execute("/recipes", cookbookId = 42L)

        assertEquals("42", server.takeRequest().getHeader("X-Cookbook-Id"))
    }

    @Test
    fun protectedUnauthorizedResponseEmitsExpiry() = runTest {
        val events = SessionEvents()
        val expiries = mutableListOf<String>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            events.expired.collect { expiries += it }
        }
        val provider = SessionProvider().apply { set(session("expired")) }
        val client = client(provider, events)
        server.enqueue(MockResponse().setResponseCode(401))

        client.execute("/recipes")
        yield()

        assertEquals(listOf("expired"), expiries)
    }

    @Test
    fun anonymousUnauthorizedResponseDoesNotEmitExpiry() = runTest {
        val events = SessionEvents()
        val expiries = mutableListOf<String>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            events.expired.collect { expiries += it }
        }
        val provider = SessionProvider().apply { set(session("valid")) }
        val client = client(provider, events)
        server.enqueue(MockResponse().setResponseCode(401))

        client.execute("/session", anonymous = true)
        yield()

        assertEquals(0, expiries.size)
    }

    private fun client(provider: SessionProvider, events: SessionEvents) =
        OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(provider, events))
            .build()

    private fun OkHttpClient.execute(
        path: String,
        anonymous: Boolean = false,
        cookbookId: Long? = null,
    ) {
        val request = Request.Builder()
            .url(server.url(path))
            .apply {
                if (anonymous) header("X-MainCourse-Anonymous", "true")
                if (cookbookId != null) header("X-Cookbook-Id", cookbookId.toString())
            }
            .build()
        newCall(request).execute().close()
    }

    private fun session(token: String) = SessionResponse(
        token = token,
        expiresAt = "2026-12-06T10:15:30Z",
        user = User(
            id = 7,
            name = "Cook",
            email = "cook@example.com",
            lifecycleNotificationsEnabled = true,
        ),
    )
}
