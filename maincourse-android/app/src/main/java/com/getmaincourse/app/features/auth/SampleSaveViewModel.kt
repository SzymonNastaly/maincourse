package com.getmaincourse.app.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getmaincourse.app.data.model.RecipeSaveRequest
import com.getmaincourse.app.data.model.RecipeSaveResponse
import com.getmaincourse.app.data.model.RecipeSaveSource
import com.getmaincourse.app.data.network.MainCourseService
import com.getmaincourse.app.data.onboarding.OnboardingPreferences
import com.getmaincourse.app.data.onboarding.SampleSaveIntent
import com.getmaincourse.app.data.session.SessionProvider
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class SampleSaveUiState(
    val userId: Long? = null,
    val saving: Boolean = false,
    val failed: Boolean = false,
    val saved: RecipeSaveResponse? = null,
    val demoDismissed: Boolean = true,
)

class SampleSaveViewModel internal constructor(
    users: Flow<Long?>,
    private val readIntent: () -> SampleSaveIntent?,
    private val writeIntent: (SampleSaveIntent?) -> Unit,
    private val personalCookbook: suspend () -> Long,
    private val save: suspend (SampleSaveIntent) -> RecipeSaveResponse,
    private val bindDismissal: (Long) -> Unit,
    private val isDismissed: (Long) -> Boolean,
    private val dismiss: (Long) -> Unit,
) : ViewModel() {
    constructor(preferences: OnboardingPreferences, service: MainCourseService, sessions: SessionProvider) : this(
        users = sessions.session.map { it?.user?.id }.distinctUntilChanged(),
        readIntent = preferences::saveIntent,
        writeIntent = preferences::writeSaveIntent,
        personalCookbook = { service.cookbooks().first { it.personal }.id },
        save = {
            val destination = checkNotNull(it.cookbookId)
            service.saveRecipe(destination, destination, RecipeSaveRequest(RecipeSaveSource("sample", it.sourceKey), it.requestId))
        },
        bindDismissal = preferences::bindDemoDismissal,
        isDismissed = preferences::isDemoDismissed,
        dismiss = preferences::dismissDemo,
    )

    private val mutableState = MutableStateFlow(SampleSaveUiState())
    val state = mutableState.asStateFlow()
    private var userId: Long? = null
    private var job: Job? = null
    private var generation = 0

    init {
        viewModelScope.launch {
            users.collect { next ->
                generation++
                job?.cancel()
                val previous = userId
                userId = next
                val intent = readIntent()
                if (intent?.userId != null && (previous != null || (next != null && intent.userId != next))) {
                    writeIntent(null)
                }
                mutableState.value = SampleSaveUiState()
                if (next != null) {
                    bindDismissal(next)
                    mutableState.value = SampleSaveUiState(userId = next, demoDismissed = isDismissed(next))
                    readIntent()?.let { pending ->
                        writeIntent(pending.copy(userId = next))
                        retry()
                    }
                }
            }
        }
    }

    /** Called only after session restoration has confirmed there is no account. */
    fun signedOut() {
        if (readIntent()?.userId != null) writeIntent(null)
    }

    fun keep() {
        if (state.value.saving) return
        writeIntent(SampleSaveIntent(UUID.randomUUID().toString(), userId = userId))
        userId?.let { dismissDemo(); retry() }
    }

    fun dismissDemo() {
        userId?.let(dismiss)
        mutableState.value = state.value.copy(demoDismissed = true)
    }

    fun continueWithoutRecipe() {
        generation++
        job?.cancel()
        writeIntent(null)
        mutableState.value = state.value.copy(saving = false, failed = false, saved = null)
    }

    fun acknowledgeSaved() { mutableState.value = state.value.copy(saved = null) }

    fun retry() {
        if (job?.isActive == true) return
        val owner = userId ?: return
        val pending = readIntent()?.takeIf { it.userId == owner } ?: return
        val attempt = generation
        mutableState.value = state.value.copy(saving = true, failed = false)
        job = viewModelScope.launch {
            try {
                val destination = pending.cookbookId ?: personalCookbook()
                currentCoroutineContext().ensureActive()
                if (attempt != generation || userId != owner) return@launch
                val bound = pending.copy(cookbookId = destination)
                writeIntent(bound)
                val result = save(bound)
                currentCoroutineContext().ensureActive()
                if (attempt != generation || userId != owner) return@launch
                writeIntent(null)
                mutableState.value = state.value.copy(saving = false, saved = result)
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Exception) {
                if (attempt == generation && userId == owner) {
                    mutableState.value = state.value.copy(saving = false, failed = true)
                }
            }
        }
    }
}
