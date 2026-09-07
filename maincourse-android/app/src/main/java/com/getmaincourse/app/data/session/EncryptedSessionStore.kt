package com.getmaincourse.app.data.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.EOFException
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class EncryptedSessionStore private constructor(
    context: Context,
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
    fileName: String = DEFAULT_FILE_NAME,
    json: Json,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val keyLookup: (() -> SecretKey?)?,
) : SessionStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, fileName))
    private val json = Json(json) { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    constructor(
        context: Context,
        keyAlias: String = DEFAULT_KEY_ALIAS,
        fileName: String = DEFAULT_FILE_NAME,
        json: Json = Json,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ) : this(context, keyAlias, fileName, json, ioDispatcher, keyLookup = null)

    internal constructor(
        context: Context,
        keyAlias: String,
        fileName: String,
        keyLookup: () -> SecretKey?,
    ) : this(context, keyAlias, fileName, Json, Dispatchers.IO, keyLookup)

    override suspend fun read(): StoredSession? = withContext(ioDispatcher) {
        mutex.withLock {
            val encrypted = try {
                file.openRead().use { it.readBytes() }
            } catch (failure: FileNotFoundException) {
                return@withLock if (hasAtomicRecord()) throw failure else null
            }

            val key = try {
                existingKey()
            } catch (_: KeyPermanentlyInvalidatedException) {
                return@withLock discardRecord()
            } ?: return@withLock discardRecord()

            val plaintext = try {
                decrypt(encrypted, key)
            } catch (_: EOFException) {
                return@withLock discardRecord()
            } catch (_: InvalidSessionRecordException) {
                return@withLock discardRecord()
            } catch (_: AEADBadTagException) {
                return@withLock discardRecord()
            } catch (_: KeyPermanentlyInvalidatedException) {
                return@withLock discardRecord()
            }

            try {
                json.decodeFromString<StoredSession>(plaintext.decodeToString())
            } catch (_: SerializationException) {
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
            if (!header.contentEquals(FILE_HEADER)) throw InvalidSessionRecordException()

            val ivSize = input.readInt()
            if (ivSize !in 12..16) throw InvalidSessionRecordException()
            val iv = ByteArray(ivSize).also(input::readFully)
            val ciphertextSize = input.readInt()
            if (ciphertextSize !in 16..MAX_CIPHERTEXT_BYTES) throw InvalidSessionRecordException()
            val ciphertext = ByteArray(ciphertextSize).also(input::readFully)
            if (input.read() != -1) throw InvalidSessionRecordException()

            Cipher.getInstance(TRANSFORMATION).run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
                updateAAD(FILE_HEADER)
                doFinal(ciphertext)
            }
        }

    private fun existingKey(): SecretKey? = if (keyLookup != null) {
        keyLookup.invoke()
    } else {
        keyStore().getKey(keyAlias, null) as? SecretKey
    }

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

    private fun hasAtomicRecord(): Boolean =
        file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()

    private class InvalidSessionRecordException : Exception()

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
