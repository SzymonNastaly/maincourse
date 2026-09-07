package com.getmaincourse.app.data.images

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath

class SessionImages(
    context: Context,
    val apiBaseUrl: String,
    private val clientFactory: () -> OkHttpClient = { OkHttpClient() },
) {
    private val appContext = context.applicationContext
    private val lock = Any()
    private var holder: Holder? = null

    internal val cacheDirectory = File(appContext.cacheDir, "session-images")

    fun resolve(path: String?): String? = resolveImageUrl(apiBaseUrl, path)

    fun loaderFor(userId: Long): ImageLoader = synchronized(lock) {
        holder?.takeIf { it.userId == userId }?.loader ?: run {
            clearLocked()
            val directory = File(cacheDirectory, "user-$userId")
            check(directory.mkdirs() || directory.isDirectory) { "Could not create the image cache directory" }
            val client = clientFactory()
            val loader = ImageLoader.Builder(appContext)
                .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
                .memoryCache { MemoryCache.Builder().maxSizePercent(appContext, 0.10).build() }
                .diskCache { DiskCache.Builder().directory(directory.toOkioPath()).maxSizeBytes(MAX_CACHE_BYTES).build() }
                .build()
            holder = Holder(userId, loader, client)
            loader
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        synchronized(lock) { clearLocked() }
    }

    private fun clearLocked() {
        holder?.let { active ->
            active.client.dispatcher.cancelAll()
            active.loader.memoryCache?.clear()
            active.loader.diskCache?.clear()
            active.loader.shutdown()
            active.client.connectionPool.evictAll()
            active.client.cache?.close()
        }
        holder = null
        cacheDirectory.deleteRecursively()
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
