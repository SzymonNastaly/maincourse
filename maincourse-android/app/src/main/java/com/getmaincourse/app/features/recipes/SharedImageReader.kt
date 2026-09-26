package com.getmaincourse.app.features.recipes

import android.content.Context
import com.getmaincourse.app.R
import com.getmaincourse.app.ui.UiMessage
import androidx.core.net.toUri
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SharedImage(
    val bytes: ByteArray,
    val mimeType: String,
)

class SharedImageReadException(val userMessage: UiMessage) : Exception()

class SharedImageReader(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    suspend fun read(uriValue: String, declaredMimeType: String): SharedImage = withContext(Dispatchers.IO) {
        val uri = runCatching { uriValue.toUri() }.getOrNull()
            ?: throw SharedImageReadException(UiMessage.Resource(R.string.error_read_image))
        val mimeType = resolver.getType(uri)
            ?.takeIf { it.startsWith("image/") }
            ?: declaredMimeType.takeIf { it.startsWith("image/") }
            ?: throw SharedImageReadException(UiMessage.Resource(R.string.error_file_not_image))
        val input = resolver.openInputStream(uri)
            ?: throw SharedImageReadException(UiMessage.Resource(R.string.error_read_image))

        input.use { source ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0
            while (true) {
                val read = source.read(buffer)
                if (read < 0) break
                total += read
                if (total > MAX_SHARED_IMAGE_BYTES) {
                    throw SharedImageReadException(UiMessage.Resource(R.string.error_image_size))
                }
                output.write(buffer, 0, read)
            }
            SharedImage(output.toByteArray(), mimeType)
        }
    }
}

private const val MAX_SHARED_IMAGE_BYTES = 15 * 1024 * 1024
