package com.getmaincourse.app.data.onboarding

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class AtomicOnboardingStore(
    context: Context,
    private val origin: String,
    fileName: String = DEFAULT_FILE_NAME,
    json: Json = Json,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : OnboardingStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, fileName))
    private val json = Json(json) { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    override suspend fun read(): OnboardingRecord? = withContext(ioDispatcher) {
        mutex.withLock {
            val bytes = try {
                file.openRead().use { it.readBytes() }
            } catch (failure: FileNotFoundException) {
                return@withLock if (hasAtomicRecord()) throw failure else null
            }

            val record = try {
                json.decodeFromString<OnboardingRecord>(bytes.decodeToString())
            } catch (_: SerializationException) {
                return@withLock discardRecord()
            } catch (_: IllegalArgumentException) {
                return@withLock discardRecord()
            }
            if (!record.isValidFor(origin)) return@withLock discardRecord()
            record
        }
    }

    override suspend fun write(record: OnboardingRecord) = withContext(ioDispatcher) {
        require(record.isValidFor(origin)) { "Invalid onboarding record" }
        mutex.withLock {
            val output = file.startWrite()
            try {
                output.write(json.encodeToString(record).encodeToByteArray())
                file.finishWrite(output)
            } catch (failure: Throwable) {
                file.failWrite(output)
                throw failure
            }
        }
    }

    private fun OnboardingRecord.isValidFor(expectedOrigin: String): Boolean {
        if (schemaVersion != CURRENT_ONBOARDING_SCHEMA_VERSION || origin != expectedOrigin) return false
        if (householdSize != null && householdSize !in ALLOWED_HOUSEHOLD_SIZES) return false
        if (!saveToday.hasOnlySortedUniqueValues(ALLOWED_SAVING_VALUES)) return false
        if (!diet.hasOnlySortedUniqueValues(ALLOWED_DIET_VALUES)) return false
        if (completed) {
            return step == OnboardingStep.COMPLETE && deviceId == null &&
                householdSize == null && saveToday.isEmpty() && diet.isEmpty()
        }
        if (step == OnboardingStep.COMPLETE || deviceId == null) return false
        return runCatching { UUID.fromString(deviceId) }.isSuccess
    }

    private fun List<String>.hasOnlySortedUniqueValues(allowed: Set<String>): Boolean =
        all { it in allowed } && this == distinct().sorted()

    private fun discardRecord(): OnboardingRecord? {
        file.delete()
        return null
    }

    private fun hasAtomicRecord(): Boolean =
        file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()

    private companion object {
        const val DEFAULT_FILE_NAME = "onboarding.json"
    }
}
