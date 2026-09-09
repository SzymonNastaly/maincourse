package com.getmaincourse.app.features.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getmaincourse.app.data.cache.MainCourseDatabase
import com.getmaincourse.app.data.images.SessionImages
import com.getmaincourse.app.data.model.SessionResponse
import com.getmaincourse.app.data.model.SignInRequest
import com.getmaincourse.app.data.model.SignUpRequest
import com.getmaincourse.app.data.network.MainCourseService
import com.getmaincourse.app.data.network.SessionEvents
import com.getmaincourse.app.data.network.userMessage
import com.getmaincourse.app.data.session.SessionProvider
import com.getmaincourse.app.data.session.SessionStore
import com.getmaincourse.app.data.session.StoredSession
import coil3.ImageLoader
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class SessionViewModel internal constructor(
    private val service: MainCourseService,
    private val sessionStore: SessionStore,
    private val sessionProvider: SessionProvider,
    sessionEvents: SessionEvents,
    private val baseUrl: String,
    private val clock: Clock,
    private val prepareImages: suspend (Long) -> ImageLoader?,
    private val clearDatabase: suspend () -> Unit,
    private val clearImages: suspend () -> Unit,
) : ViewModel() {
    constructor(
        service: MainCourseService,
        sessionStore: SessionStore,
        sessionProvider: SessionProvider,
        sessionEvents: SessionEvents,
        database: MainCourseDatabase,
        images: SessionImages,
        baseUrl: String,
        clock: Clock = Clock.systemUTC(),
    ) : this(
        service = service,
        sessionStore = sessionStore,
        sessionProvider = sessionProvider,
        sessionEvents = sessionEvents,
        baseUrl = baseUrl,
        clock = clock,
        prepareImages = images::prepare,
        clearDatabase = database::clearAllTables,
        clearImages = images::clear,
    )

    private val mutableState = MutableStateFlow<SessionUiState>(SessionUiState.Restoring)
    private var foregroundAction: Job? = null

    val state = mutableState.asStateFlow()
    var imageLoader: ImageLoader? = null
        private set

    init {
        viewModelScope.launch {
            sessionEvents.expired.collect {
                if (mutableState.value is SessionUiState.SignedIn) {
                    mutableState.value = SessionUiState.Restoring
                    foregroundAction?.cancelAndJoin()
                    launchForeground { hideAndClear() }.join()
                }
            }
        }
    }

    fun restore(): Job = launchForeground {
        mutableState.value = SessionUiState.Restoring
        val stored = try {
            sessionStore.read()
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            mutableState.value = SessionUiState.RestoreError(
                failure.userMessage("Could not read the saved session"),
            )
            return@launchForeground
        }

        if (stored == null || stored.baseUrl != baseUrl || stored.response.isExpired()) {
            hideAndClear()
            return@launchForeground
        }

        try {
            publish(stored.response)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            sessionProvider.clear()
            imageLoader = null
            mutableState.value = SessionUiState.RestoreError(
                failure.userMessage("Could not restore the saved session"),
            )
        }
    }

    fun signIn(email: String, password: String): Job = authenticate("Could not sign in") {
        val normalizedEmail = email.trim()
        when {
            !normalizedEmail.isValidEmail() -> "Enter a valid email address."
            password.isEmpty() -> "Enter your password."
            else -> null
        }?.let {
            mutableState.value = SessionUiState.SignedOut(it)
            return@authenticate null
        }
        service.signIn(SignInRequest(normalizedEmail, password, DEVICE_NAME))
    }

    fun signUp(
        name: String?,
        email: String,
        password: String,
        confirmation: String,
    ): Job = authenticate("Could not create account") {
        val normalizedEmail = email.trim()
        when {
            !normalizedEmail.isValidEmail() -> "Enter a valid email address."
            password.length < MINIMUM_PASSWORD_LENGTH -> "Use at least 12 characters."
            password != confirmation -> "Passwords do not match."
            else -> null
        }?.let {
            mutableState.value = SessionUiState.SignedOut(it)
            return@authenticate null
        }
        service.signUp(
            SignUpRequest(
                name = name?.trim()?.takeIf(String::isNotEmpty),
                email = normalizedEmail,
                password = password,
                passwordConfirmation = confirmation,
                deviceName = DEVICE_NAME,
            ),
        )
    }

    fun signOut(): Job {
        if (mutableState.value !is SessionUiState.SignedIn) return completedJob()
        return launchForeground {
            mutableState.value = SessionUiState.Restoring
            try {
                withTimeout(SIGN_OUT_TIMEOUT_MILLIS) { service.signOut() }
            } catch (_: TimeoutCancellationException) {
                // Remote revocation is best effort; local credentials are authoritative.
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Throwable) {
                // Remote revocation is best effort; local credentials are authoritative.
            }
            hideAndClear()
        }
    }

    fun deleteAccount(): Job {
        if (mutableState.value !is SessionUiState.SignedIn) return completedJob()
        return launchForeground {
            try {
                service.deleteAccount()
                hideAndClear()
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Throwable) {
                // The account remains signed in until deletion is confirmed.
            }
        }
    }

    private fun authenticate(
        fallback: String,
        request: suspend () -> SessionResponse?,
    ): Job {
        if (mutableState.value !is SessionUiState.SignedOut) return completedJob()
        return launchForeground {
            mutableState.value = SessionUiState.SignedOut(busy = true)
            val response = try {
                request() ?: return@launchForeground
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                mutableState.value = SessionUiState.SignedOut(failure.userMessage(fallback))
                return@launchForeground
            }

            if (response.isExpired()) {
                mutableState.value = SessionUiState.SignedOut(fallback)
                return@launchForeground
            }

            try {
                sessionStore.write(StoredSession(baseUrl, response))
                publish(response)
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                hideAndClear(SessionUiState.SignedOut(failure.userMessage("Could not save the session")))
            }
        }
    }

    private suspend fun publish(session: SessionResponse) {
        val loader = prepareImages(session.user.id)
        sessionProvider.set(session)
        imageLoader = loader
        mutableState.value = SessionUiState.SignedIn(session)
    }

    private suspend fun hideAndClear(success: SessionUiState = SessionUiState.SignedOut()) {
        mutableState.value = SessionUiState.Restoring
        imageLoader = null
        sessionProvider.clear()
        var firstFailure: Throwable? = null
        suspend fun clear(action: suspend () -> Unit) {
            try {
                action()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                if (firstFailure == null) firstFailure = failure
            }
        }
        clear(sessionStore::clear)
        clear(clearDatabase)
        clear(clearImages)
        mutableState.value = if (firstFailure == null) {
            success
        } else {
            SessionUiState.RestoreError("Could not clear local data")
        }
    }

    private fun launchForeground(block: suspend () -> Unit): Job {
        foregroundAction?.takeIf(Job::isActive)?.let { return it }
        lateinit var launched: Job
        launched = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                block()
            } finally {
                val current = mutableState.value
                if (current is SessionUiState.SignedOut && current.busy) {
                    mutableState.value = current.copy(busy = false)
                }
                if (foregroundAction === launched) foregroundAction = null
            }
        }
        foregroundAction = launched
        launched.start()
        return launched
    }

    private fun completedJob(): Job = Job().apply { complete() }

    private fun SessionResponse.isExpired(): Boolean = try {
        !Instant.parse(expiresAt).isAfter(clock.instant())
    } catch (_: DateTimeParseException) {
        true
    }

    private fun String.isValidEmail(): Boolean =
        contains('@') && !startsWith('@') && !endsWith('@')

    private companion object {
        const val DEVICE_NAME = "Android"
        const val MINIMUM_PASSWORD_LENGTH = 12
        const val SIGN_OUT_TIMEOUT_MILLIS = 5_000L
    }
}
