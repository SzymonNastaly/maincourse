package com.getmaincourse.app.features.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getmaincourse.app.data.model.SignInRequest
import com.getmaincourse.app.data.model.SignUpRequest
import com.getmaincourse.app.data.network.MainCourseApi
import com.getmaincourse.app.data.onboarding.OnboardingStore
import com.getmaincourse.app.data.session.SessionStore
import com.getmaincourse.app.features.onboarding.OnboardingController
import com.getmaincourse.app.features.onboarding.OnboardingState
import com.getmaincourse.app.features.onboarding.OnboardingStep
import java.time.Clock
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
) : ViewModel() {
    private val controller = SessionController(
        api = api,
        sessionStore = sessionStore,
        catalogRepository = catalogRepository,
        baseUrl = baseUrl,
        clock = clock,
        scope = viewModelScope,
        imageCleanup = imageCleanup,
    )
    private val onboarding = OnboardingController(
        store = onboardingStore,
        api = api,
        origin = baseUrl,
        scope = viewModelScope,
    )
    private val mutablePreparingAuthentication = MutableStateFlow(false)
    private val authenticationLock = Any()
    private var authenticationJob: Job? = null

    val state = controller.state
    val accountState = controller.accountState
    val onboardingState = onboarding.state
    val isPreparingAuthentication = mutablePreparingAuthentication.asStateFlow()

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
    fun logout() = controller.logout()
    fun reset() = controller.reset()
    fun checkExpiry() = controller.checkExpiry()

    private fun authenticateWithOnboarding(authenticate: (String?) -> Job): Job {
        synchronized(authenticationLock) {
            if (authenticationJob?.isActive == true) return viewModelScope.launch {}
            val startedDraft = onboardingState.value.authDraftIdentity()
            mutablePreparingAuthentication.value = true
            val launched = viewModelScope.launch {
                try {
                    val deviceId = onboarding.prepareAuthentication()
                    if (startedDraft != null && deviceId == null &&
                        onboardingState.value.authDraftIdentity() != startedDraft
                    ) {
                        return@launch
                    }
                    authenticate(deviceId).join()
                } finally {
                    mutablePreparingAuthentication.value = false
                }
            }
            authenticationJob = launched
            launched.invokeOnCompletion {
                synchronized(authenticationLock) {
                    if (authenticationJob == launched) authenticationJob = null
                }
            }
            return launched
        }
    }

    private fun ifPreparingAuthenticationIgnored(action: () -> Job): Job =
        if (mutablePreparingAuthentication.value) viewModelScope.launch {} else action()

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
