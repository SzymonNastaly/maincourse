package com.getmaincourse.app.data.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.runBlocking
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
}
