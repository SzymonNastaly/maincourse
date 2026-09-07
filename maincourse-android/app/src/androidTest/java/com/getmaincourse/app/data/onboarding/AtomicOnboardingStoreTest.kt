package com.getmaincourse.app.data.onboarding

import android.content.Context
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AtomicOnboardingStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var fileName: String
    private lateinit var file: File
    private lateinit var store: AtomicOnboardingStore

    @Before
    fun setUp() {
        fileName = "onboarding-test-${UUID.randomUUID()}.json"
        file = File(context.noBackupFilesDir, fileName)
        store = AtomicOnboardingStore(context, ORIGIN, fileName)
    }

    @After
    fun tearDown() {
        AtomicFile(file).delete()
    }

    @Test
    fun realAtomicFileSurvivesStoreRecreationAndInterruptedWriteRecovery() = runBlocking {
        store.write(draft())
        val backup = File(file.path + ".bak")
        assertTrue(file.renameTo(backup))

        val restored = AtomicOnboardingStore(context, ORIGIN, fileName).read()

        assertEquals(draft(), restored)
        assertTrue(file.exists())
        assertFalse(backup.exists())
    }

    @Test
    fun corruptJsonReturnsNullAndRemovesOnlyTheOnboardingRecord() = runBlocking {
        file.writeText("{not-json")

        assertNull(store.read())
        assertFalse(file.exists())
    }

    @Test
    fun unknownFieldsAreIgnored() = runBlocking {
        file.writeText(
            """{"schemaVersion":1,"origin":"$ORIGIN","deviceId":"$DEVICE_ID","step":"DIET","householdSize":3,"saveToday":["screenshots"],"diet":["vegan"],"completed":false,"future":true}""",
        )

        assertEquals(draft(), store.read())
    }

    @Test
    fun originMismatchReturnsNullAndDiscardsTheForeignDraft() = runBlocking {
        AtomicOnboardingStore(context, "https://other.example/", fileName).write(
            draft(origin = "https://other.example/"),
        )

        assertNull(store.read())
        assertFalse(file.exists())
    }

    @Test
    fun transientReadFailurePropagatesWithoutDiscardingValidData() = runBlocking {
        val expected = draft()
        store.write(expected)
        Os.chmod(file.path, 0)

        val failure = try {
            runCatching { store.read() }.exceptionOrNull()
        } finally {
            Os.chmod(file.path, OsConstants.S_IRUSR or OsConstants.S_IWUSR)
        }

        assertTrue("Expected file read failure, got $failure", failure is FileNotFoundException)
        assertTrue(file.exists())
        assertEquals(expected, store.read())
    }

    @Test
    fun transientWriteFailureRetainsThePreviousAtomicRecord() = runBlocking {
        val directory = File(context.noBackupFilesDir, "onboarding-dir-${UUID.randomUUID()}")
        assertTrue(directory.mkdir())
        val nestedName = "${directory.name}/record.json"
        val nestedStore = AtomicOnboardingStore(context, ORIGIN, nestedName)
        val expected = draft()
        nestedStore.write(expected)
        Os.chmod(directory.path, OsConstants.S_IRUSR or OsConstants.S_IXUSR)

        val failure = try {
            runCatching { nestedStore.write(expected.copy(householdSize = 5)) }.exceptionOrNull()
        } finally {
            Os.chmod(
                directory.path,
                OsConstants.S_IRUSR or OsConstants.S_IWUSR or OsConstants.S_IXUSR,
            )
        }

        assertTrue("Expected a transient write failure, got $failure", failure is IOException)
        assertEquals(expected, nestedStore.read())
        AtomicFile(File(directory, "record.json")).delete()
        assertTrue(directory.delete())
    }

    private fun draft(origin: String = ORIGIN) = OnboardingRecord(
        origin = origin,
        deviceId = DEVICE_ID,
        step = OnboardingStep.DIET,
        householdSize = 3,
        saveToday = listOf("screenshots"),
        diet = listOf("vegan"),
    )

    private companion object {
        const val ORIGIN = "https://app.getmaincourse.com/"
        const val DEVICE_ID = "11111111-2222-3333-4444-555555555555"
    }
}
