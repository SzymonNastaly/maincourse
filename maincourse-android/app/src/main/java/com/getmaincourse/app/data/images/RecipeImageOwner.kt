package com.getmaincourse.app.data.images

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RecipeImageOwner internal constructor(
    context: Context,
    internal val rootDirectory: File,
    private val deleteDirectory: (File) -> Boolean = File::deleteRecursively,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    constructor(context: Context) : this(
        context,
        File(context.applicationContext.cacheDir, "recipe-staging"),
    )

    private val preparer = RecipeImagePreparer(context.applicationContext.contentResolver, rootDirectory, ioDispatcher)
    private val mutex = Mutex()
    private val lifecycleLock = Any()
    private var lifecycleEpoch = 0L
    private var clearing = false

    suspend fun prepare(userId: Long, uri: Uri): PreparedRecipeImage {
        val capturedEpoch = synchronized(lifecycleLock) {
            if (clearing) unavailable()
            lifecycleEpoch
        }
        return mutex.withLock {
            if (!ownsEpoch(capturedEpoch)) unavailable()
            withContext(ioDispatcher) {
                rootDirectory.listFiles().orEmpty().filterNot { it.name == "user-$userId" }.forEach { obsolete ->
                    if (obsolete.exists() && !deleteDirectory(obsolete)) error("Could not remove another user's staged images")
                }
            }
            val prepared = preparer.prepare(userId, uri)
            if (!ownsEpoch(capturedEpoch)) {
                withContext(ioDispatcher) { runCatching { discardFile(prepared) } }
                unavailable()
            }
            prepared
        }
    }

    suspend fun resolve(image: PreparedRecipeImage, currentUserId: Long): File = mutex.withLock {
        withContext(ioDispatcher) {
            val file = ownedFile(image, currentUserId)
            if (!file.isFile || !file.canRead()) unavailable()
            val signature = FileInputStream(file).use { input ->
                ByteArray(3).also { if (input.read(it) != it.size) unavailable() }
            }
            if (!signature.contentEquals(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte()))) unavailable()
            file
        }
    }

    suspend fun discard(image: PreparedRecipeImage) = mutex.withLock {
        withContext(ioDispatcher) { discardFile(image) }
    }

    suspend fun clear() {
        synchronized(lifecycleLock) {
            lifecycleEpoch++
            clearing = true
        }
        try {
            mutex.withLock {
                withContext(ioDispatcher) {
                    if (rootDirectory.exists() && !deleteDirectory(rootDirectory)) error("Could not remove staged recipe images")
                }
            }
        } finally {
            synchronized(lifecycleLock) { clearing = false }
        }
    }

    private fun ownsEpoch(epoch: Long): Boolean = synchronized(lifecycleLock) {
        !clearing && lifecycleEpoch == epoch
    }

    private fun discardFile(image: PreparedRecipeImage) {
        val file = ownedFile(image, image.userId)
        if (file.exists() && !file.delete()) error("Could not remove staged recipe image")
    }

    private fun ownedFile(image: PreparedRecipeImage, currentUserId: Long): File {
        if (image.userId != currentUserId || !SAFE_KEY.matches(image.key)) unavailable()
        val expectedRoot = File(rootDirectory, "user-$currentUserId").canonicalFile
        val file = File(image.path).canonicalFile
        if (file.parentFile != expectedRoot || file.name != "${image.key}.jpg") unavailable()
        return file
    }

    private fun unavailable(): Nothing = throw PreparedRecipeImageUnavailable("Choose the photo again")

    private companion object {
        val SAFE_KEY = Regex("[A-Za-z0-9-]{1,100}")
    }
}

internal suspend fun clearImageResources(
    clearDisplayImages: suspend () -> Unit,
    clearStagedImages: suspend () -> Unit,
) {
    var firstFailure: Throwable? = null
    try {
        clearDisplayImages()
    } catch (failure: Throwable) {
        firstFailure = failure
    }
    try {
        clearStagedImages()
    } catch (failure: Throwable) {
        if (firstFailure == null) firstFailure = failure
    }
    firstFailure?.let { throw it }
}
