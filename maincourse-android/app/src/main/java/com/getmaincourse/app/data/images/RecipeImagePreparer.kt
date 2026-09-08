package com.getmaincourse.app.data.images

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal class RecipeImagePreparer(
    private val contentResolver: ContentResolver,
    private val rootDirectory: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val newKey: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun prepare(userId: Long, uri: Uri): PreparedRecipeImage = withContext(ioDispatcher) {
        val key = newKey()
        val userDirectory = File(rootDirectory, "user-$userId")
        check(userDirectory.mkdirs() || userDirectory.isDirectory) { "Could not create image staging directory" }
        val source = File(userDirectory, ".$key.source")
        val pending = File(userDirectory, ".$key.jpg.tmp")
        val output = File(userDirectory, "$key.jpg")
        try {
            open(uri).use { input -> copyBounded(input, source) }
            currentCoroutineContext().ensureActive()
            val bitmap = decode(source)
            try {
                encodeJpeg(bitmap, pending)
            } finally {
                bitmap.recycle()
            }
            check(pending.length() in 1 until MAX_JPEG_BYTES) { "Prepared image is too large" }
            check(pending.renameTo(output)) { "Could not finish preparing image" }
            PreparedRecipeImage(output.canonicalPath, userId, key)
        } catch (failure: Throwable) {
            source.delete()
            pending.delete()
            output.delete()
            if (failure is CancellationException) throw failure
            throw PreparedRecipeImageUnavailable("This photo could not be prepared. Choose another image.")
        } finally {
            source.delete()
            pending.delete()
        }
    }

    private fun open(uri: Uri): InputStream = if (uri.scheme == ContentResolver.SCHEME_FILE) {
        FileInputStream(checkNotNull(uri.path))
    } else {
        contentResolver.openInputStream(uri) ?: error("Could not open selected image")
    }

    private fun copyBounded(input: InputStream, destination: File) {
        FileOutputStream(destination).use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var copied = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                copied += count
                check(copied <= MAX_INPUT_BYTES) { "Selected image is too large" }
                output.write(buffer, 0, count)
            }
        }
    }

    private fun decode(source: File): Bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
        val width = info.size.width
        val height = info.size.height
        check(width > 0 && height > 0 && width <= MAX_DIMENSION && height <= MAX_DIMENSION &&
            width.toLong() * height.toLong() <= MAX_PIXELS
        ) { "Selected image dimensions are unsupported" }
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val longest = maxOf(width, height)
        if (longest > TARGET_LONG_EDGE) {
            val scale = TARGET_LONG_EDGE.toDouble() / longest
            decoder.setTargetSize(
                (width * scale).roundToInt().coerceAtLeast(1),
                (height * scale).roundToInt().coerceAtLeast(1),
            )
        }
    }

    private fun encodeJpeg(bitmap: Bitmap, destination: File) {
        var quality = 92
        while (quality >= 40) {
            FileOutputStream(destination, false).use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) { "Could not encode JPEG" }
            }
            if (destination.length() < MAX_JPEG_BYTES) return
            quality -= 8
        }
        error("Prepared image is too large")
    }

    private companion object {
        const val MAX_INPUT_BYTES = 32L * 1024L * 1024L
        const val MAX_JPEG_BYTES = 15_000_000L
        const val MAX_PIXELS = 100_000_000L
        const val MAX_DIMENSION = 32_000
        const val TARGET_LONG_EDGE = 3_000
    }
}
