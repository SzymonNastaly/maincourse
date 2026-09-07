package com.getmaincourse.app.data.images

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionImagesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var images: SessionImages? = null
    private var server: MockWebServer? = null

    @After
    fun cleanUp() {
        runBlocking {
            images?.clear()
            server?.shutdown()
        }
    }

    @Test
    fun loaderIsReusedUntilCleanupThenRecreatedWithoutSharingTheCacheDirectory() = runBlocking {
        images = SessionImages(context, "https://app.example.test/")
        val first = images!!.loaderFor(7L)
        assertSame(first, images!!.loaderFor(7L))
        val cacheDirectory = images!!.cacheDirectory
        assertTrue(cacheDirectory.exists())

        images!!.clear()
        assertFalse(cacheDirectory.exists())

        val second = images!!.loaderFor(7L)
        assertNotSame(first, second)
    }

    @Test
    fun imageRequestsNeverReceiveAnApiAuthorizationHeader() = runBlocking {
        server = MockWebServer().apply {
            enqueue(
                MockResponse().setResponseCode(200).setHeader("Content-Type", "image/png")
                    .setBody(Buffer().write(PNG.decodeBase64()!!)),
            )
            start()
        }
        images = SessionImages(context, server!!.url("/").toString())
        val result = images!!.loaderFor(7L).execute(
            ImageRequest.Builder(context).data(server!!.url("cover.png").toString()).build(),
        )
        assertTrue(result is SuccessResult)
        assertNull(server!!.takeRequest().getHeader("Authorization"))
    }

    private companion object {
        const val PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    }
}
