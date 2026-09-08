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

    suspend fun prepare(userId: Long, uri: Uri): PreparedRecipeImage = mutex.withLock {
        withContext(ioDispatcher) {
            rootDirectory.listFiles().orEmpty().filterNot { it.name == "user-$userId" }.forEach { obsolete ->
                if (obsolete.exists() && !deleteDirectory(obsolete)) error("Could not remove another user's staged images")
            }
        }
        preparer.prepare(userId, uri)
    }

    suspend fun resolve(image: PreparedRecipeImage, currentUserId: Long): File = mutex.withLock {
        withContext(ioDispatcher) {
            if (image.userId != currentUserId || !SAFE_KEY.matches(image.key)) unavailable()
            val expectedRoot = File(rootDirectory, "user-$currentUserId").canonicalFile
            val file = File(image.path).canonicalFile
            if (file.parentFile != expectedRoot || file.name != "${image.key}.jpg" || !file.isFile || !file.canRead()) unavailable()
            val signature = FileInputStream(file).use { input ->
                ByteArray(3).also { if (input.read(it) != it.size) unavailable() }
            }
            if (!signature.contentEquals(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte()))) unavailable()
            file
        }
    }

    suspend fun clear() = mutex.withLock {
        withContext(ioDispatcher) {
            if (rootDirectory.exists() && !deleteDirectory(rootDirectory)) error("Could not remove staged recipe images")
        }
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
