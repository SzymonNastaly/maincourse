package com.getmaincourse.app.features.recipes

import android.content.Context
import androidx.core.net.toUri
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SharedImage(
    val bytes: ByteArray,
    val mimeType: String,
)

class SharedImageReadException(message: String) : Exception(message)

class SharedImageReader(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    suspend fun read(uriValue: String, declaredMimeType: String): SharedImage = withContext(Dispatchers.IO) {
        val uri = runCatching { uriValue.toUri() }.getOrNull()
            ?: throw SharedImageReadException("Could not read the shared image")
        val mimeType = resolver.getType(uri)
            ?.takeIf { it.startsWith("image/") }
            ?: declaredMimeType.takeIf { it.startsWith("image/") }
            ?: throw SharedImageReadException("The shared file is not an image")
        val input = resolver.openInputStream(uri)
            ?: throw SharedImageReadException("Could not read the shared image")

        input.use { source ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = source.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_SHARED_IMAGE_BYTES) {
                    throw SharedImageReadException("That image is too big (max 15 MB)")
                }
                output.write(buffer, 0, read)
            }
            SharedImage(output.toByteArray(), mimeType)
        }
    }
}

private const val MAX_SHARED_IMAGE_BYTES = 15 * 1024 * 1024
