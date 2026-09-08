package com.getmaincourse.app.data.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.core.content.FileProvider
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecipeImagePreparerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val roots = mutableListOf<File>()

    @After
    fun cleanup() {
        roots.forEach(File::deleteRecursively)
    }

    @Test
    fun preparationDownsamplesLongEdgeAndWritesARealBoundedJpegInTheUsersPrivateRoot() = runBlocking {
        val root = newRoot()
        val input = File(root.parentFile, "wide-${UUID.randomUUID()}.png")
        Bitmap.createBitmap(3_200, 800, Bitmap.Config.ARGB_8888).also { bitmap ->
            FileOutputStream(input).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        val owner = RecipeImageOwner(context, root)

        val prepared = owner.prepare(7, Uri.fromFile(input))
        val output = owner.resolve(prepared, 7)
        val bounds = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
        BitmapFactory.decodeFile(output.path, bounds)

        assertEquals(3_000, bounds.outWidth)
        assertEquals(750, bounds.outHeight)
        assertTrue(output.length() < 15_000_000)
        assertTrue(output.readBytes().take(3) == listOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte()))
        assertEquals(File(root, "user-7").canonicalFile, checkNotNull(output.parentFile).canonicalFile)
        input.delete()
        Unit
    }

    @Test
    fun oversizedInputAndMalformedImageLeaveNoIntermediateOrOutputFiles() = runBlocking {
        val root = newRoot()
        val owner = RecipeImageOwner(context, root)
        val oversized = File(root.parentFile, "oversized-${UUID.randomUUID()}").apply {
            outputStream().use { output ->
                val block = ByteArray(1024 * 1024)
                repeat(33) { output.write(block) }
            }
        }

        expectUnavailable { owner.prepare(7, Uri.fromFile(oversized)) }
        assertTrue(File(root, "user-7").listFiles().orEmpty().isEmpty())

        val malformed = File(root.parentFile, "malformed-${UUID.randomUUID()}").apply { writeText("not an image") }
        expectUnavailable { owner.prepare(7, Uri.fromFile(malformed)) }
        assertTrue(File(root, "user-7").listFiles().orEmpty().isEmpty())
        oversized.delete()
        malformed.delete()
        Unit
    }

    @Test
    fun resolverRejectsWrongUserOutsidePathsMissingFilesAndNonJpegContent() = runBlocking {
        val root = newRoot()
        val owner = RecipeImageOwner(context, root)
        val userRoot = File(root, "user-7").apply { mkdirs() }
        val valid = File(userRoot, "safe-key.jpg").apply {
            writeBytes(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 1))
        }

        assertEquals(valid.canonicalFile, owner.resolve(PreparedRecipeImage(valid.path, 7, "safe-key"), 7))
        expectUnavailable { owner.resolve(PreparedRecipeImage(valid.path, 7, "safe-key"), 8) }
        expectUnavailable { owner.resolve(PreparedRecipeImage(File(root.parentFile, "outside.jpg").path, 7, "outside"), 7) }
        expectUnavailable { owner.resolve(PreparedRecipeImage(File(userRoot, "gone.jpg").path, 7, "gone"), 7) }
        val fake = File(userRoot, "fake.jpg").apply { writeText("not jpeg") }
        expectUnavailable { owner.resolve(PreparedRecipeImage(fake.path, 7, "fake"), 7) }
    }

    @Test
    fun preparationNormalizesExifRotationIntoTheOutputPixels() = runBlocking {
        val root = newRoot()
        val plain = File(root.parentFile, "plain-${UUID.randomUUID()}.jpg")
        Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888).also { bitmap ->
            FileOutputStream(plain).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bitmap.recycle()
        }
        val oriented = File(root.parentFile, "oriented-${UUID.randomUUID()}.jpg")
        val jpeg = plain.readBytes()
        oriented.writeBytes(jpeg.copyOfRange(0, 2) + orientationSixExifSegment() + jpeg.copyOfRange(2, jpeg.size))
        val owner = RecipeImageOwner(context, root)

        val output = owner.resolve(owner.prepare(7, Uri.fromFile(oriented)), 7)
        val bounds = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
        BitmapFactory.decodeFile(output.path, bounds)

        assertEquals(20, bounds.outWidth)
        assertEquals(40, bounds.outHeight)
        plain.delete()
        oriented.delete()
        Unit
    }

    @Test
    fun preparationReadsAnActualFileProviderContentUri() = runBlocking {
        val root = newRoot()
        val input = File(context.cacheDir, "provider-${UUID.randomUUID()}.png")
        Bitmap.createBitmap(2, 1, Bitmap.Config.ARGB_8888).also { bitmap ->
            FileOutputStream(input).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.recipe-image-test", input)
        val owner = RecipeImageOwner(context, root)

        val output = owner.resolve(owner.prepare(7, uri), 7)

        assertTrue(output.isFile)
        input.delete()
        Unit
    }

    @Test
    fun forgedValidPngHeadersAreRejectedByDimensionAndPixelGuardsBeforePixelDecode() = runBlocking {
        val root = newRoot()
        listOf(32_001 to 1, 11_000 to 10_000).forEach { (width, height) ->
            val input = File(context.cacheDir, "huge-${UUID.randomUUID()}.png").apply {
                writeBytes(pngWithDimensions(width, height))
            }
            try {
                RecipeImageOwner(context, root).prepare(7, Uri.fromFile(input))
                fail("oversized dimensions should be rejected")
            } catch (expected: PreparedRecipeImageUnavailable) {
                assertTrue(expected.message.orEmpty().contains("dimensions"))
            }
            input.delete()
        }
        assertTrue(File(root, "user-7").listFiles().orEmpty().isEmpty())
        Unit
    }

    @Test
    fun discardIsScopedAndIdempotentForPreparedImages() = runBlocking {
        val root = newRoot()
        val owner = RecipeImageOwner(context, root)
        val file = File(root, "user-7/safe.jpg").apply {
            parentFile!!.mkdirs()
            writeBytes(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte()))
        }
        val image = PreparedRecipeImage(file.path, 7, "safe")

        owner.discard(image)
        owner.discard(image)

        assertFalse(file.exists())
        val outside = File(root.parentFile, "outside-${UUID.randomUUID()}.jpg").apply { writeText("outside") }
        expectUnavailable { owner.discard(PreparedRecipeImage(outside.path, 7, "outside")) }
        assertTrue(outside.exists())
        outside.delete()
        Unit
    }

    @Test
    fun preparationRequestedDuringClearCannotQueueAndRecreateOldUserFiles() = runBlocking {
        val root = newRoot().apply { mkdirs() }
        File(root, "marker").writeText("old")
        val deletionStarted = CountDownLatch(1)
        val allowDeletion = CountDownLatch(1)
        val owner = RecipeImageOwner(
            context = context,
            rootDirectory = root,
            deleteDirectory = { directory ->
                deletionStarted.countDown()
                check(allowDeletion.await(5, TimeUnit.SECONDS))
                directory.deleteRecursively()
            },
        )
        val clearing = async(Dispatchers.IO) { owner.clear() }
        assertTrue(withContext(Dispatchers.IO) { deletionStarted.await(5, TimeUnit.SECONDS) })

        expectUnavailable { owner.prepare(7, Uri.fromFile(File(root, "old.png"))) }
        allowDeletion.countDown()
        clearing.await()

        assertFalse(root.exists())
    }

    @Test
    fun ownerCleanupDeletesEveryUsersStagingFiles() = runBlocking {
        val root = newRoot()
        val owner = RecipeImageOwner(context, root)
        File(root, "user-7").apply { mkdirs() }.resolve("one.jpg").writeText("one")
        File(root, "user-8").apply { mkdirs() }.resolve("two.jpg").writeText("two")

        owner.clear()

        assertFalse(root.exists())
    }

    @Test
    fun preparingForAnotherUserRemovesPriorUsersStagedFiles() = runBlocking {
        val root = newRoot()
        val oldDirectory = File(root, "user-7").apply { mkdirs() }
        File(oldDirectory, "old.jpg").writeText("old")
        val input = File(root.parentFile, "next-${UUID.randomUUID()}.png")
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).also { bitmap ->
            FileOutputStream(input).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }

        RecipeImageOwner(context, root).prepare(8, Uri.fromFile(input))

        assertFalse(oldDirectory.exists())
        assertTrue(File(root, "user-8").isDirectory)
        input.delete()
        Unit
    }

    private fun newRoot() = File(context.cacheDir, "recipe-staging-test-${UUID.randomUUID()}").also(roots::add)

    private suspend fun expectUnavailable(block: suspend () -> Unit) {
        try {
            block()
            fail("expected prepared image to be unavailable")
        } catch (_: PreparedRecipeImageUnavailable) {
            // Expected.
        }
    }

    private fun orientationSixExifSegment(): ByteArray = byteArrayOf(
        0xff.toByte(), 0xe1.toByte(), 0x00, 0x22,
        'E'.code.toByte(), 'x'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(), 0x00, 0x00,
        'M'.code.toByte(), 'M'.code.toByte(), 0x00, 0x2a, 0x00, 0x00, 0x00, 0x08,
        0x00, 0x01,
        0x01, 0x12, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01, 0x00, 0x06, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00,
    )

    private fun pngWithDimensions(width: Int, height: Int): ByteArray {
        val signature = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        val header = byteArrayOf(
            (width ushr 24).toByte(), (width ushr 16).toByte(), (width ushr 8).toByte(), width.toByte(),
            (height ushr 24).toByte(), (height ushr 16).toByte(), (height ushr 8).toByte(), height.toByte(),
            8, 2, 0, 0, 0,
        )
        val compressed = ByteArrayOutputStream().also { bytes ->
            DeflaterOutputStream(bytes).use { it.write(byteArrayOf(0, 0, 0, 0)) }
        }.toByteArray()
        return signature + pngChunk("IHDR", header) + pngChunk("IDAT", compressed) + pngChunk("IEND", byteArrayOf())
    }

    private fun pngChunk(type: String, data: ByteArray): ByteArray {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply {
            update(typeBytes)
            update(data)
        }.value.toInt()
        return byteArrayOf(
            (data.size ushr 24).toByte(), (data.size ushr 16).toByte(), (data.size ushr 8).toByte(), data.size.toByte(),
        ) + typeBytes + data + byteArrayOf(
            (crc ushr 24).toByte(), (crc ushr 16).toByte(), (crc ushr 8).toByte(), crc.toByte(),
        )
    }
}
