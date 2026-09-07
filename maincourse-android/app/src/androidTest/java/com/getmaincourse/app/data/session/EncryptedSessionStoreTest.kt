package com.getmaincourse.app.data.session

import android.content.Context
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.getmaincourse.app.data.model.SessionResponse
import com.getmaincourse.app.data.model.User
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.KeyStore
import java.security.KeyStoreException
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKey

@RunWith(AndroidJUnit4::class)
class EncryptedSessionStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var keyAlias: String
    private lateinit var fileName: String
    private lateinit var file: File
    private lateinit var store: EncryptedSessionStore

    @Before
    fun setUp() {
        val suffix = UUID.randomUUID().toString()
        keyAlias = "maincourse-session-test-$suffix"
        fileName = "session-test-$suffix.bin"
        file = File(context.noBackupFilesDir, fileName)
        store = EncryptedSessionStore(context, keyAlias = keyAlias, fileName = fileName)
    }

    @After
    fun tearDown() = runBlocking {
        AtomicFile(file).delete()
        deleteKey(keyAlias)
    }

    @Test
    fun encryptedRoundTripDoesNotPersistSessionFieldsAsPlaintext() = runBlocking {
        val session = session()

        store.write(session)

        val storedBytes = file.readBytes()
        val storedText = storedBytes.toString(Charsets.UTF_8)
        assertFalse(storedText.contains(session.response.token))
        assertFalse(storedText.contains(session.baseUrl))
        assertFalse(storedText.contains(session.response.user.email))
        assertEquals(session, store.read())
    }

    @Test
    fun alteredCiphertextReturnsNoSessionAndRemovesUnusableRecord() = runBlocking {
        store.write(session())
        val altered = file.readBytes()
        altered[altered.lastIndex] = (altered.last().toInt() xor 0x01).toByte()
        file.writeBytes(altered)

        assertNull(store.read())
        assertFalse(file.exists())
    }

    @Test
    fun interruptedAtomicWriteRecoversTheAuthenticatedBackup() = runBlocking {
        val expected = session()
        store.write(expected)
        val backup = File(file.path + ".bak")
        assertTrue(file.renameTo(backup))

        assertEquals(expected, store.read())
        assertTrue(file.exists())
        assertFalse(backup.exists())
    }

    @Test
    fun missingKeyReturnsNoSessionAndRemovesUnusableRecord() = runBlocking {
        store.write(session())
        deleteKey(keyAlias)
        assertFalse(keyExists(keyAlias))

        assertNull(store.read())
        assertFalse(file.exists())
    }

    @Test
    fun transientKeyStoreFailurePropagatesWithoutDeletingValidSession() = runBlocking {
        val expected = session()
        store.write(expected)
        val failingStore = EncryptedSessionStore(
            context = context,
            keyAlias = keyAlias,
            fileName = fileName,
            keyLookup = { throw KeyStoreException("temporarily unavailable") },
        )

        val failure = runCatching { failingStore.read() }.exceptionOrNull()

        assertTrue(failure is KeyStoreException)
        assertTrue(file.exists())
        assertEquals(expected, store.read())
    }

    @Test
    fun absentRecordReturnsNullWithoutConsultingFailingKeyStore() = runBlocking {
        val failingStore = EncryptedSessionStore(
            context = context,
            keyAlias = keyAlias,
            fileName = fileName,
            keyLookup = { throw KeyStoreException("temporarily unavailable") },
        )

        assertNull(failingStore.read())
    }

    @Test
    fun transientFileReadFailurePropagatesWithoutBeingReportedAsAbsence() = runBlocking {
        val expected = session()
        store.write(expected)
        Os.chmod(file.path, 0)

        val failure = try {
            runCatching { store.read() }.exceptionOrNull()
        } finally {
            Os.chmod(file.path, OsConstants.S_IRUSR or OsConstants.S_IWUSR)
        }

        assertTrue("Expected file read failure, got $failure", failure is java.io.FileNotFoundException)
        assertTrue(file.exists())
        assertEquals(expected, store.read())
    }

    @Test
    fun unknownJsonFieldsRemainCompatibleWithStoredSessions() = runBlocking {
        store.write(session())
        writeEncryptedJson(
            """{"baseUrl":"https://example.test/api/","response":{"token":"secret-token-that-must-never-be-plaintext","expires_at":"2026-12-01T00:00:00Z","user":{"id":42,"name":"Ada","email":"ada@example.test","lifecycle_notifications_enabled":true,"future_user":1},"future_response":true},"future_root":"kept"}""",
        )

        assertEquals(session(), store.read())
    }

    @Test
    fun clearRemovesEncryptedRecordAndItsKey() = runBlocking {
        store.write(session())
        assertTrue(file.exists())
        assertTrue(keyExists(keyAlias))

        store.clear()

        assertFalse(file.exists())
        assertFalse(keyExists(keyAlias))
        assertNull(store.read())
    }

    @Test
    fun writeFailureIsVisibleToCallerWithoutPlaintextFallback() = runBlocking {
        val blockedParent = File(context.noBackupFilesDir, "$fileName-blocked")
        blockedParent.writeText("not a directory")
        val blockedStore = EncryptedSessionStore(
            context,
            keyAlias = keyAlias,
            fileName = "$fileName-blocked/session.bin",
        )

        val failure = runCatching { blockedStore.write(session()) }.exceptionOrNull()

        assertNotNull(failure)
        assertEquals("not a directory", blockedParent.readText())
        assertFalse(blockedParent.readText().contains(session().response.token))
        blockedParent.delete()
        Unit
    }

    private fun session() = StoredSession(
        baseUrl = "https://example.test/api/",
        response = SessionResponse(
            token = "secret-token-that-must-never-be-plaintext",
            expiresAt = "2026-12-01T00:00:00Z",
            user = User(
                id = 42,
                name = "Ada",
                email = "ada@example.test",
                lifecycleNotificationsEnabled = true,
            ),
        ),
    )

    private fun keyExists(alias: String): Boolean = keyStore().containsAlias(alias)

    private fun deleteKey(alias: String) {
        keyStore().apply {
            if (containsAlias(alias)) deleteEntry(alias)
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun writeEncryptedJson(value: String) {
        val key = keyStore().getKey(keyAlias, null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key)
            updateAAD(byteArrayOf(0x4d, 0x43, 0x53, 0x01))
        }
        val ciphertext = cipher.doFinal(value.encodeToByteArray())
        val record = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.write(byteArrayOf(0x4d, 0x43, 0x53, 0x01))
                output.writeInt(cipher.iv.size)
                output.write(cipher.iv)
                output.writeInt(ciphertext.size)
                output.write(ciphertext)
            }
            bytes.toByteArray()
        }
        val atomicFile = AtomicFile(file)
        val output = atomicFile.startWrite()
        try {
            output.write(record)
            atomicFile.finishWrite(output)
        } catch (failure: Throwable) {
            atomicFile.failWrite(output)
            throw failure
        }
    }
}
