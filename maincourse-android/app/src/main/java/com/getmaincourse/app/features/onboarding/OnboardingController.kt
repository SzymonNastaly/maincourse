package com.getmaincourse.app.features.onboarding

import com.getmaincourse.app.data.model.OnboardingAnswers
import com.getmaincourse.app.data.model.OnboardingRequest
import com.getmaincourse.app.data.network.MainCourseApi
import com.getmaincourse.app.data.onboarding.ALLOWED_DIET_VALUES
import com.getmaincourse.app.data.onboarding.ALLOWED_HOUSEHOLD_SIZES
import com.getmaincourse.app.data.onboarding.ALLOWED_SAVING_VALUES
import com.getmaincourse.app.data.onboarding.OnboardingRecord
import com.getmaincourse.app.data.onboarding.OnboardingStore
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

class OnboardingController(
    private val store: OnboardingStore,
    private val api: MainCourseApi,
    private val origin: String,
    private val scope: CoroutineScope,
    private val uuidFactory: () -> String = { UUID.randomUUID().toString() },
    private val submissionTimeoutMillis: Long = 5_000,
) {
    private val mutableState = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = mutableState.asStateFlow()

    private val operationMutex = Mutex()
    private val submissionLock = Any()
    private var record: OnboardingRecord? = null
    private var savingEnabled = true
    private var restoreFailed = false
    private var submissionGeneration = 0L
    private var submission: Submission? = null

    fun restore(): Job = serialized { restoreLocked() }

    fun start(): Job = serialized {
        if (mutableState.value.isLoading || mutableState.value.step != OnboardingStep.WELCOME) return@serialized
        val current = record?.takeIf { it.origin == origin && !it.completed }
        val updated = (current ?: newDraft()).copy(step = OnboardingStep.HOUSEHOLD)
        publishAndPersist(updated)
    }

    fun advance(): Job = serialized {
        val current = record ?: return@serialized
        val next = when (mutableState.value.step) {
            OnboardingStep.WELCOME -> OnboardingStep.HOUSEHOLD
            OnboardingStep.HOUSEHOLD -> {
                if (current.householdSize !in ALLOWED_HOUSEHOLD_SIZES) return@serialized
                OnboardingStep.SAVING
            }
            OnboardingStep.SAVING -> {
                if (current.saveToday.isEmpty()) return@serialized
                OnboardingStep.DIET
            }
            OnboardingStep.DIET -> OnboardingStep.AUTH
            OnboardingStep.AUTH, OnboardingStep.COMPLETE -> return@serialized
        }
        val updated = current.copy(step = next)
        publishAndPersist(updated)
        if (next == OnboardingStep.AUTH) ensureSubmission(updated)
    }

    fun back(): Job = serialized {
        val current = record ?: return@serialized
        val previous = when (mutableState.value.step) {
            OnboardingStep.WELCOME -> return@serialized
            OnboardingStep.HOUSEHOLD -> OnboardingStep.WELCOME
            OnboardingStep.SAVING -> OnboardingStep.HOUSEHOLD
            OnboardingStep.DIET -> OnboardingStep.SAVING
            OnboardingStep.AUTH -> OnboardingStep.DIET
            OnboardingStep.COMPLETE -> return@serialized
        }
        publishAndPersist(current.copy(step = previous))
    }

    fun skip(): Job = complete()

    fun existingAccount(): Job = complete()

    fun updateHousehold(value: Int): Job = serialized {
        if (mutableState.value.step != OnboardingStep.HOUSEHOLD || value !in ALLOWED_HOUSEHOLD_SIZES) {
            return@serialized
        }
        val current = record ?: return@serialized
        publishAndPersist(current.copy(householdSize = value))
    }

    fun updateSaving(value: String): Job = serialized {
        if (mutableState.value.step != OnboardingStep.SAVING || value !in ALLOWED_SAVING_VALUES) {
            return@serialized
        }
        val current = record ?: return@serialized
        publishAndPersist(current.copy(saveToday = current.saveToday.toggled(value)))
    }

    fun updateDiet(value: String): Job = serialized {
        if (mutableState.value.step != OnboardingStep.DIET || value !in ALLOWED_DIET_VALUES) {
            return@serialized
        }
        val current = record ?: return@serialized
        publishAndPersist(current.copy(diet = current.diet.toggled(value)))
    }

    fun retryPersistence(): Job = serialized {
        if (!mutableState.value.canRetryPersistence) return@serialized
        if (restoreFailed) {
            restoreLocked()
        } else {
            record?.let { persist(it) }
        }
    }

    fun continueWithoutSaving(): Job = serialized {
        savingEnabled = false
        restoreFailed = false
        mutableState.value = mutableState.value.copy(
            isLoading = false,
            persistenceError = null,
            canRetryPersistence = false,
        )
    }

    fun complete(): Job = serialized { consumeLocked() }

    suspend fun prepareAuthentication(): String? {
        val prepared = operationMutex.withLock {
            val current = record?.takeIf {
                mutableState.value.step == OnboardingStep.AUTH && it.isCompleteDraft()
            } ?: return@withLock null
            val key = current.submissionKey()
            val job = synchronized(submissionLock) {
                val existing = submission?.takeIf { it.key == key }
                when (existing?.status) {
                    SubmissionStatus.RUNNING -> existing.job
                    SubmissionStatus.SUCCEEDED -> null
                    SubmissionStatus.FAILED, null -> startSubmissionLocked(current, key)
                }
            }
            PreparedAuthentication(current.deviceId, job)
        } ?: return null
        prepared.job?.join()
        return prepared.deviceId
    }

    suspend fun authenticationSucceeded() {
        operationMutex.withLock { consumeLocked() }
    }

    private suspend fun restoreLocked() {
        mutableState.value = mutableState.value.copy(isLoading = true)
        val restored = try {
            store.read()
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Throwable) {
            record = null
            restoreFailed = true
            mutableState.value = OnboardingState(
                isLoading = false,
                persistenceError = READ_ERROR,
                canRetryPersistence = true,
            )
            return
        }
        restoreFailed = false
        savingEnabled = true
        record = restored?.takeIf { it.origin == origin && it.isControllerValid() }
        mutableState.value = record?.toState() ?: OnboardingState(isLoading = false)
        record?.takeIf { it.step == OnboardingStep.AUTH && it.isCompleteDraft() }?.let(::ensureSubmission)
    }

    private suspend fun publishAndPersist(updated: OnboardingRecord) {
        record = updated
        mutableState.value = updated.toState(
            persistenceError = mutableState.value.persistenceError,
            canRetryPersistence = mutableState.value.canRetryPersistence,
        )
        persist(updated)
    }

    private suspend fun persist(updated: OnboardingRecord) {
        if (!savingEnabled) return
        try {
            store.write(updated)
            mutableState.value = mutableState.value.copy(
                persistenceError = null,
                canRetryPersistence = false,
            )
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Throwable) {
            mutableState.value = mutableState.value.copy(
                persistenceError = WRITE_ERROR,
                canRetryPersistence = true,
            )
        }
    }

    private suspend fun consumeLocked() {
        invalidateSubmission()
        val completed = completedRecord()
        record = completed
        mutableState.value = completed.toState(
            persistenceError = mutableState.value.persistenceError,
            canRetryPersistence = mutableState.value.canRetryPersistence,
        )
        persist(completed)
    }

    private fun ensureSubmission(current: OnboardingRecord) {
        if (!current.isCompleteDraft()) return
        val key = current.submissionKey()
        synchronized(submissionLock) {
            if (submission?.key == key) return
            submission?.job?.cancel()
            startSubmissionLocked(current, key)
        }
    }

    private fun startSubmissionLocked(current: OnboardingRecord, key: SubmissionKey): Job {
        val generation = ++submissionGeneration
        lateinit var launched: Job
        launched = scope.launch(start = CoroutineStart.LAZY) {
            val succeeded = try {
                withTimeoutOrNull(submissionTimeoutMillis) {
                    api.submitOnboarding(current.toRequest())
                    true
                } == true
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Throwable) {
                false
            }
            synchronized(submissionLock) {
                if (submissionGeneration == generation && submission?.job == launched) {
                    submission = Submission(
                        key = key,
                        status = if (succeeded) SubmissionStatus.SUCCEEDED else SubmissionStatus.FAILED,
                        job = launched,
                    )
                }
            }
        }
        submission = Submission(key, SubmissionStatus.RUNNING, launched)
        launched.start()
        return launched
    }

    private fun invalidateSubmission() {
        synchronized(submissionLock) {
            submissionGeneration++
            submission?.job?.cancel()
            submission = null
        }
    }

    private fun serialized(block: suspend () -> Unit): Job =
        scope.launch(start = CoroutineStart.UNDISPATCHED) { operationMutex.withLock { block() } }

    private fun newDraft() = OnboardingRecord(
        origin = origin,
        deviceId = uuidFactory(),
        step = OnboardingStep.WELCOME,
    )

    private fun completedRecord() = OnboardingRecord(
        origin = origin,
        deviceId = null,
        step = OnboardingStep.COMPLETE,
        completed = true,
    )

    private fun OnboardingRecord.toState(
        persistenceError: String? = null,
        canRetryPersistence: Boolean = false,
    ) = OnboardingState(
        isLoading = false,
        step = step,
        householdSize = householdSize,
        saveToday = saveToday,
        diet = diet,
        persistenceError = persistenceError,
        canRetryPersistence = canRetryPersistence,
    )

    private fun OnboardingRecord.isControllerValid(): Boolean =
        if (completed) {
            step == OnboardingStep.COMPLETE && deviceId == null && householdSize == null &&
                saveToday.isEmpty() && diet.isEmpty()
        } else {
            step != OnboardingStep.COMPLETE && deviceId != null &&
                householdSize?.let { it in ALLOWED_HOUSEHOLD_SIZES } != false &&
                saveToday.all { it in ALLOWED_SAVING_VALUES } &&
                diet.all { it in ALLOWED_DIET_VALUES }
        }

    private fun OnboardingRecord.isCompleteDraft(): Boolean =
        !completed && deviceId != null && householdSize in ALLOWED_HOUSEHOLD_SIZES && saveToday.isNotEmpty()

    private fun OnboardingRecord.submissionKey() = SubmissionKey(
        deviceId = requireNotNull(deviceId),
        householdSize = requireNotNull(householdSize),
        saveToday = saveToday,
        diet = diet,
    )

    private fun OnboardingRecord.toRequest() = OnboardingRequest(
        deviceId = requireNotNull(deviceId),
        answers = OnboardingAnswers(
            householdSize = householdSize,
            saveToday = saveToday,
            diet = diet,
        ),
    )

    private fun List<String>.toggled(value: String): List<String> =
        if (value in this) filterNot { it == value } else (this + value).distinct().sorted()

    private data class PreparedAuthentication(val deviceId: String?, val job: Job?)

    private data class SubmissionKey(
        val deviceId: String,
        val householdSize: Int,
        val saveToday: List<String>,
        val diet: List<String>,
    )

    private data class Submission(
        val key: SubmissionKey,
        val status: SubmissionStatus,
        val job: Job,
    )

    private enum class SubmissionStatus { RUNNING, SUCCEEDED, FAILED }

    private companion object {
        const val READ_ERROR = "Could not read saved onboarding progress"
        const val WRITE_ERROR = "Could not save onboarding progress"
    }
}
