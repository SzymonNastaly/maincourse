package com.getmaincourse.app.data.images

import android.content.Context
import androidx.annotation.VisibleForTesting
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath

class SessionImages internal constructor(
    context: Context,
    val apiBaseUrl: String,
    internal val cacheDirectory: File,
    private val clientFactory: () -> OkHttpClient = { OkHttpClient() },
    private val deleteDirectory: (File) -> Boolean = File::deleteRecursively,
    private val beforePublish: suspend (Long) -> Unit = {},
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    constructor(context: Context, apiBaseUrl: String) : this(
        context = context,
        apiBaseUrl = apiBaseUrl,
        cacheDirectory = File(context.applicationContext.cacheDir, "session-images"),
    )

    private val appContext = context.applicationContext
    private val mutex = Mutex()
    private var holder: Holder? = null

    fun resolve(path: String?): String? = resolveImageUrl(apiBaseUrl, path)

    suspend fun prepare(userId: Long): ImageLoader = withContext(ioDispatcher) {
        mutex.withLock {
            currentCoroutineContext().ensureActive()
            holder?.takeIf { it.userId == userId }?.let { return@withLock it.loader }

            holder?.let(::dispose)
            holder = null
            val directory = prepareDirectory(userId)
            currentCoroutineContext().ensureActive()

            val client = clientFactory()
            var loader: ImageLoader? = null
            try {
                loader = ImageLoader.Builder(appContext)
                    .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
                    .memoryCache { MemoryCache.Builder().maxSizePercent(appContext, 0.10).build() }
                    .diskCache { DiskCache.Builder().directory(directory.toOkioPath()).maxSizeBytes(MAX_CACHE_BYTES).build() }
                    .build()
                beforePublish(userId)
                currentCoroutineContext().ensureActive()
                holder = Holder(userId, loader, client)
                loader
            } catch (failure: Throwable) {
                loader?.shutdown()
                client.dispatcher.cancelAll()
                client.connectionPool.evictAll()
                client.cache?.close()
                if (failure is CancellationException) throw failure
                throw failure
            }
        }
    }

    suspend fun clear() = withContext(ioDispatcher) {
        mutex.withLock {
            var firstFailure: Throwable? = null
            holder?.let { active ->
                try {
                    dispose(active)
                } catch (failure: Throwable) {
                    firstFailure = failure
                }
            }
            holder = null
            try {
                remove(cacheDirectory)
            } catch (failure: Throwable) {
                if (firstFailure == null) firstFailure = failure
            }
            firstFailure?.let { throw it }
        }
    }

    @VisibleForTesting
    internal suspend fun preparedUserIdForTest(): Long? = withContext(ioDispatcher) {
        mutex.withLock { holder?.userId }
    }

    private fun prepareDirectory(userId: Long): File {
        check(cacheDirectory.mkdirs() || cacheDirectory.isDirectory) {
            "Could not create the image cache directory"
        }
        val directory = File(cacheDirectory, "user-$userId")
        cacheDirectory.listFiles().orEmpty().filterNot { it == directory }.forEach(::remove)
        check(directory.mkdirs() || directory.isDirectory) {
            "Could not create the image cache directory"
        }
        return directory
    }

    private fun dispose(active: Holder) {
        var firstFailure: Throwable? = null
        active.client.dispatcher.cancelAll()
        try {
            active.loader.memoryCache?.clear()
            active.loader.diskCache?.clear()
        } catch (failure: Throwable) {
            firstFailure = failure
        }
        try {
            active.loader.shutdown()
        } catch (failure: Throwable) {
            if (firstFailure == null) firstFailure = failure
        }
        active.client.connectionPool.evictAll()
        try {
            active.client.cache?.close()
        } catch (failure: Throwable) {
            if (firstFailure == null) firstFailure = failure
        }
        firstFailure?.let { throw it }
    }

    private fun remove(directory: File) {
        if (directory.exists() && !deleteDirectory(directory)) {
            error("Could not remove the image cache directory")
        }
    }

    private data class Holder(
        val userId: Long,
        val loader: ImageLoader,
        val client: OkHttpClient,
    )

    private companion object {
        const val MAX_CACHE_BYTES = 64L * 1024L * 1024L
    }
}
