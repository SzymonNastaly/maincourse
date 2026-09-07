package com.getmaincourse.app.data.images

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.ByteString.Companion.decodeBase64
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionImagesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val roots = mutableListOf<File>()
    private var images: SessionImages? = null
    private var server: MockWebServer? = null

    @After
    fun cleanUp() {
        runBlocking {
            runCatching { images?.clear() }
            server?.shutdown()
            roots.forEach(File::deleteRecursively)
        }
    }

    @Test
    fun loaderIsReusedUntilCleanupThenRecreatedWithoutSharingTheCacheDirectory() = runBlocking {
        images = testImages()
        val first = images!!.prepare(7L)
        assertSame(first, images!!.prepare(7L))
        val cacheDirectory = images!!.cacheDirectory
        assertTrue(cacheDirectory.exists())

        images!!.clear()
        assertFalse(cacheDirectory.exists())

        val second = images!!.prepare(7L)
        assertNotSame(first, second)
    }

    @Test
    fun aNewManagerReusesTheSameUsersDiskCache() = runBlocking {
        server = imageServer(responseCount = 1)
        val root = newRoot()
        val url = server!!.url("cover.png").toString()
        val firstImages = testImages(root)
        val firstLoader = firstImages.prepare(7L)
        assertTrue(firstLoader.execute(ImageRequest.Builder(context).data(url).build()) is SuccessResult)
        firstLoader.shutdown()

        images = testImages(root)
        val secondLoader = images!!.prepare(7L)
        assertTrue(secondLoader.execute(ImageRequest.Builder(context).data(url).build()) is SuccessResult)
        assertEquals(1, server!!.requestCount)
    }

    @Test
    fun preparingAnotherUserRemovesTheOldUsersCacheFirst() = runBlocking {
        images = testImages()
        images!!.prepare(7L)
        val oldDirectory = File(images!!.cacheDirectory, "user-7")
        File(oldDirectory, "marker").writeText("old user")

        images!!.prepare(8L)

        assertFalse(oldDirectory.exists())
        assertTrue(File(images!!.cacheDirectory, "user-8").isDirectory)
    }

    @Test
    fun imageRequestsNeverReceiveAnApiAuthorizationHeader() = runBlocking {
        server = imageServer(responseCount = 1)
        images = testImages(baseUrl = server!!.url("/").toString())
        val result = images!!.prepare(7L).execute(
            ImageRequest.Builder(context).data(server!!.url("cover.png").toString()).build(),
        )
        assertTrue(result is SuccessResult)
        assertNull(server!!.takeRequest().getHeader("Authorization"))
    }

    @Test
    fun preparationCalledFromMainBuildsTheLoaderOffMain() = runBlocking {
        val factoryRanOnMain = AtomicBoolean(true)
        images = testImages(
            clientFactory = {
                factoryRanOnMain.set(Looper.myLooper() == Looper.getMainLooper())
                OkHttpClient()
            },
        )

        withContext(Dispatchers.Main) { images!!.prepare(7L) }

        assertFalse(factoryRanOnMain.get())
    }

    @Test
    fun failedDirectoryRemovalMakesCleanupFail() = runBlocking {
        images = testImages(deleteDirectory = { false })
        images!!.prepare(7L)

        try {
            images!!.clear()
            fail("cleanup should report an undeleted cache")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("image cache"))
        }
    }

    @Test
    fun cancelledOldUserPreparationCannotPublishAfterCleanup() = runBlocking {
        val reachedPublish = CompletableDeferred<Unit>()
        val releasePublish = CompletableDeferred<Unit>()
        images = testImages(
            beforePublish = {
                reachedPublish.complete(Unit)
                releasePublish.await()
            },
        )
        val preparation = async { images!!.prepare(7L) }
        reachedPublish.await()
        preparation.cancel()
        val cleanup = async { images!!.clear() }
        releasePublish.complete(Unit)
        preparation.cancelAndJoin()
        cleanup.await()

        assertFalse(images!!.cacheDirectory.exists())
    }

    private fun testImages(
        root: File = newRoot(),
        baseUrl: String = "https://app.example.test/",
        clientFactory: () -> OkHttpClient = { OkHttpClient() },
        deleteDirectory: (File) -> Boolean = File::deleteRecursively,
        beforePublish: suspend (Long) -> Unit = {},
    ) = SessionImages(
        context = context,
        apiBaseUrl = baseUrl,
        cacheDirectory = root,
        clientFactory = clientFactory,
        deleteDirectory = deleteDirectory,
        beforePublish = beforePublish,
    )

    private fun newRoot(): File = File(context.cacheDir, "session-images-test-${UUID.randomUUID()}").also(roots::add)

    private fun imageServer(responseCount: Int) = MockWebServer().apply {
        repeat(responseCount) {
            enqueue(
                MockResponse().setResponseCode(200)
                    .setHeader("Content-Type", "image/png")
                    .setHeader("Cache-Control", "public, max-age=3600")
                    .setBody(Buffer().write(PNG.decodeBase64()!!)),
            )
        }
        start()
    }

    private companion object {
        const val PNG = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    }
}
