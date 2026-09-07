package com.getmaincourse.app.data.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class EncryptedSessionStore(
    context: Context,
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
    fileName: String = DEFAULT_FILE_NAME,
    private val json: Json = Json,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SessionStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, fileName))
    private val mutex = Mutex()

    override suspend fun read(): StoredSession? = withContext(ioDispatcher) {
        mutex.withLock {
            try {
                val key = existingKey() ?: return@withLock discardRecord()
                val encrypted = file.openRead().use { it.readBytes() }
                json.decodeFromString<StoredSession>(decrypt(encrypted, key).decodeToString())
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                discardRecord()
            }
        }
    }

    override suspend fun write(session: StoredSession) = withContext(ioDispatcher) {
        mutex.withLock {
            val plaintext = json.encodeToString(session).encodeToByteArray()
            val encrypted = encrypt(plaintext, getOrCreateKey())
            val output = file.startWrite()
            try {
                output.write(encrypted)
                file.finishWrite(output)
            } catch (failure: Throwable) {
                file.failWrite(output)
                throw failure
            }
        }
    }

    override suspend fun clear() = withContext(ioDispatcher) {
        mutex.withLock {
            file.delete()
            keyStore().apply {
                if (containsAlias(keyAlias)) deleteEntry(keyAlias)
            }
            Unit
        }
    }

    private fun encrypt(plaintext: ByteArray, key: SecretKey): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(FILE_HEADER)
        val ciphertext = cipher.doFinal(plaintext)
        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.write(FILE_HEADER)
                output.writeInt(cipher.iv.size)
                output.write(cipher.iv)
                output.writeInt(ciphertext.size)
                output.write(ciphertext)
            }
            bytes.toByteArray()
        }
    }

    private fun decrypt(record: ByteArray, key: SecretKey): ByteArray =
        DataInputStream(ByteArrayInputStream(record)).use { input ->
            val header = ByteArray(FILE_HEADER.size)
            input.readFully(header)
            require(header.contentEquals(FILE_HEADER))

            val ivSize = input.readInt()
            require(ivSize in 12..16)
            val iv = ByteArray(ivSize).also(input::readFully)
            val ciphertextSize = input.readInt()
            require(ciphertextSize in 16..MAX_CIPHERTEXT_BYTES)
            val ciphertext = ByteArray(ciphertextSize).also(input::readFully)
            require(input.read() == -1)

            Cipher.getInstance(TRANSFORMATION).run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                updateAAD(FILE_HEADER)
                doFinal(ciphertext)
            }
        }

    private fun existingKey(): SecretKey? = keyStore().getKey(keyAlias, null) as? SecretKey

    private fun getOrCreateKey(): SecretKey = existingKey() ?: KeyGenerator
        .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        .apply {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
        }
        .generateKey()

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    private fun discardRecord(): StoredSession? {
        file.delete()
        return null
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val MAX_CIPHERTEXT_BYTES = 1024 * 1024
        const val DEFAULT_KEY_ALIAS = "com.getmaincourse.app.session"
        const val DEFAULT_FILE_NAME = "session.enc"
        val FILE_HEADER = byteArrayOf(0x4d, 0x43, 0x53, 0x01)
    }
}
