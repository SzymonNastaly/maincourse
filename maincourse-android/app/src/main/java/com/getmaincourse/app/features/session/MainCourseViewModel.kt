package com.getmaincourse.app.features.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getmaincourse.app.data.model.GoogleSignInRequest
import com.getmaincourse.app.data.model.SignInRequest
import com.getmaincourse.app.data.model.SignUpRequest
import com.getmaincourse.app.data.network.MainCourseApi
import com.getmaincourse.app.data.onboarding.OnboardingStore
import com.getmaincourse.app.data.session.SessionStore
import com.getmaincourse.app.features.auth.AuthenticationMethod
import com.getmaincourse.app.features.auth.GoogleNonce
import com.getmaincourse.app.features.auth.GoogleSignInException
import com.getmaincourse.app.features.onboarding.OnboardingController
import com.getmaincourse.app.features.onboarding.OnboardingState
import com.getmaincourse.app.features.onboarding.OnboardingStep
import java.time.Clock
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainCourseViewModel(
    api: MainCourseApi,
    sessionStore: SessionStore,
    catalogRepository: CatalogRepository,
    onboardingStore: OnboardingStore,
    baseUrl: String,
    clock: Clock,
    imageCleanup: suspend () -> Unit,
    credentialStateCleanup: suspend () -> Unit = {},
) : ViewModel() {
    private val controller = SessionController(
        api = api,
        sessionStore = sessionStore,
        catalogRepository = catalogRepository,
        baseUrl = baseUrl,
        clock = clock,
        scope = viewModelScope,
        imageCleanup = imageCleanup,
        credentialStateCleanup = credentialStateCleanup,
    )
    private val onboarding = OnboardingController(
        store = onboardingStore,
        api = api,
        origin = baseUrl,
        scope = viewModelScope,
    )
    private val mutableAuthenticationMethod = MutableStateFlow<AuthenticationMethod?>(null)
    private val authenticationLock = Any()
    private var authenticationJob: Job? = null
    private var googleAttempt: GoogleAuthenticationAttempt? = null

    val state = controller.state
    val accountState = controller.accountState
    val onboardingState = onboarding.state
    val authenticationMethod = mutableAuthenticationMethod.asStateFlow()

    init {
        onboarding.restore()
        controller.restore()
        viewModelScope.launch {
            state.filter { it.user != null }.first()
            onboarding.authenticationSucceeded()
        }
    }

    fun restore() = controller.restore()
    fun signIn(request: SignInRequest) = authenticateWithOnboarding {
        controller.signIn(request.copy(onboardingDeviceId = it))
    }
    fun signUp(request: SignUpRequest) = authenticateWithOnboarding {
        controller.signUp(request.copy(onboardingDeviceId = it))
    }
    internal fun beginGoogleAuthentication(): GoogleAuthenticationAttempt? = synchronized(authenticationLock) {
        if (mutableAuthenticationMethod.value != null || state.value.phase != SessionPhase.SIGNED_OUT) return null
        controller.clearAuthenticationError()
        GoogleAuthenticationAttempt(GoogleNonce.generate()).also {
            googleAttempt = it
            mutableAuthenticationMethod.value = AuthenticationMethod.GOOGLE
        }
    }

    internal fun finishGoogleAuthentication(attempt: GoogleAuthenticationAttempt, idToken: String): Boolean =
        synchronized(authenticationLock) {
            if (googleAttempt != attempt || mutableAuthenticationMethod.value != AuthenticationMethod.GOOGLE) return false
            if (idToken.isBlank()) {
                googleAttempt = null
                mutableAuthenticationMethod.value = null
                controller.reportAuthenticationFailure(GoogleSignInException.USER_MESSAGE)
                return false
            }
            googleAttempt = null
            launchAuthentication { deviceId ->
                controller.signInWithGoogle(
                    GoogleSignInRequest(
                        idToken = idToken,
                        nonce = attempt.nonce,
                        deviceName = "Android",
                        onboardingDeviceId = deviceId,
                    ),
                )
            }
            true
        }

    internal fun cancelGoogleAuthentication(
        attempt: GoogleAuthenticationAttempt,
        error: String? = null,
    ): Boolean = synchronized(authenticationLock) {
        if (googleAttempt != attempt) return false
        googleAttempt = null
        mutableAuthenticationMethod.value = null
        if (error != null) controller.reportAuthenticationFailure(error)
        true
    }
    fun startOnboarding() = onboarding.start()
    fun advanceOnboarding() = onboarding.advance()
    fun backOnboarding() = ifPreparingAuthenticationIgnored { onboarding.back() }
    fun skipOnboarding() = onboarding.skip()
    fun useExistingAccount() = onboarding.existingAccount()
    fun updateOnboardingHousehold(value: Int) = ifPreparingAuthenticationIgnored {
        onboarding.updateHousehold(value)
    }
    fun updateOnboardingSaving(value: String) = ifPreparingAuthenticationIgnored {
        onboarding.updateSaving(value)
    }
    fun updateOnboardingDiet(value: String) = ifPreparingAuthenticationIgnored {
        onboarding.updateDiet(value)
    }
    fun retryOnboardingPersistence() = onboarding.retryPersistence()
    fun continueOnboardingWithoutSaving() = onboarding.continueWithoutSaving()
    fun switchCookbook(id: Long) = controller.switchCookbook(id)
    fun refresh() = controller.refresh()
    fun openRecipe(id: Long) = controller.openRecipe(id)
    fun closeRecipe() = controller.closeRecipe()
    fun updateName(name: String) = controller.updateName(name)
    fun updateLifecycleNotifications(enabled: Boolean) = controller.updateLifecycleNotifications(enabled)
    fun retryAccountPersistence() = controller.retryAccountPersistence()
    fun deleteAccount() = controller.deleteAccount()
    fun clearAccountError() = controller.clearAccountError()
    fun logout(): Job {
        cancelAuthenticationForCleanup()
        return controller.logout()
    }
    fun reset(): Job {
        cancelAuthenticationForCleanup()
        return controller.reset()
    }
    fun checkExpiry() = controller.checkExpiry()

    private fun authenticateWithOnboarding(authenticate: (String?) -> Job): Job {
        synchronized(authenticationLock) {
            if (mutableAuthenticationMethod.value != null) return viewModelScope.launch {}
            controller.clearAuthenticationError()
            mutableAuthenticationMethod.value = AuthenticationMethod.EMAIL
            return launchAuthentication(authenticate)
        }
    }

    private fun launchAuthentication(authenticate: (String?) -> Job): Job {
        val startedDraft = onboardingState.value.authDraftIdentity()
        lateinit var launched: Job
        launched = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val deviceId = onboarding.prepareAuthentication()
                if (startedDraft != null && deviceId == null &&
                    onboardingState.value.authDraftIdentity() != startedDraft
                ) {
                    return@launch
                }
                authenticate(deviceId).join()
            } finally {
                finishAuthenticationJob(launched)
            }
        }
        authenticationJob = launched
        launched.start()
        return launched
    }

    private fun finishAuthenticationJob(job: Job) {
        synchronized(authenticationLock) {
            if (authenticationJob == job) {
                authenticationJob = null
                mutableAuthenticationMethod.value = null
            }
        }
    }

    private fun cancelAuthenticationForCleanup() {
        val job = synchronized(authenticationLock) {
            googleAttempt = null
            mutableAuthenticationMethod.value = null
            authenticationJob.also { authenticationJob = null }
        }
        job?.cancel()
    }

    private fun ifPreparingAuthenticationIgnored(action: () -> Job): Job =
        if (mutableAuthenticationMethod.value != null) viewModelScope.launch {} else action()

    private fun OnboardingState.authDraftIdentity(): AuthDraftIdentity? =
        takeIf { it.step == OnboardingStep.AUTH }?.let {
            AuthDraftIdentity(it.householdSize, it.saveToday, it.diet)
        }

    private data class AuthDraftIdentity(
        val householdSize: Int?,
        val saveToday: List<String>,
        val diet: List<String>,
    )
}

internal class GoogleAuthenticationAttempt internal constructor(
    internal val nonce: String,
)
