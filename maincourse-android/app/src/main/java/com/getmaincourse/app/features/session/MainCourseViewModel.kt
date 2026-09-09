package com.getmaincourse.app.features.session

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getmaincourse.app.BuildConfig
import com.getmaincourse.app.data.images.PreparedRecipeImage
import com.getmaincourse.app.data.images.PreparedRecipeImageUnavailable
import com.getmaincourse.app.data.model.AppleAuthenticationExchangeRequest
import com.getmaincourse.app.data.model.AppleAuthenticationStartRequest
import com.getmaincourse.app.data.model.GoogleSignInRequest
import com.getmaincourse.app.data.model.SignInRequest
import com.getmaincourse.app.data.model.SignUpRequest
import com.getmaincourse.app.data.network.MainCourseApi
import com.getmaincourse.app.data.onboarding.OnboardingStore
import com.getmaincourse.app.data.session.SessionStore
import com.getmaincourse.app.features.auth.AuthenticationMethod
import com.getmaincourse.app.features.auth.AppleAuthenticationCallback
import com.getmaincourse.app.features.auth.AppleAuthenticationError
import com.getmaincourse.app.features.auth.ApplePkce
import com.getmaincourse.app.features.auth.GoogleNonce
import com.getmaincourse.app.features.auth.GoogleSignInException
import com.getmaincourse.app.features.onboarding.OnboardingController
import com.getmaincourse.app.features.onboarding.OnboardingState
import com.getmaincourse.app.features.onboarding.OnboardingStep
import com.getmaincourse.app.features.recipes.RecipeEditDraft
import com.getmaincourse.app.features.recipes.ShoppingItemInput
import com.getmaincourse.app.features.recipes.RecipeEditorImageSelection
import java.io.File
import java.time.Clock
import java.time.Instant
import java.net.URI
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainCourseViewModel(
    private val api: MainCourseApi,
    sessionStore: SessionStore,
    catalogRepository: CatalogRepository,
    onboardingStore: OnboardingStore,
    baseUrl: String,
    private val clock: Clock,
    imageCleanup: suspend () -> Unit,
    resolvePreparedImage: suspend (PreparedRecipeImage, Long) -> File = { _, _ ->
        throw PreparedRecipeImageUnavailable("Choose the photo again")
    },
    prepareRecipeImage: suspend (Long, String) -> PreparedRecipeImage = { _, _ ->
        throw PreparedRecipeImageUnavailable("Choose the photo again")
    },
    discardRecipeImage: suspend (PreparedRecipeImage) -> Unit = {},
    credentialStateCleanup: suspend () -> Unit = {},
    private val appleWaitingTimeoutMillis: Long = 300_000,
    private val appleCallback: String = appleCallbackFor(baseUrl, BuildConfig.DEBUG),
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
        resolvePreparedImage = resolvePreparedImage,
        prepareRecipeImage = prepareRecipeImage,
        discardRecipeImage = discardRecipeImage,
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
    private var appleAttempt: PendingAppleAuthentication? = null
    private var appleStartJob: Job? = null
    private var appleDeadlineJob: Job? = null
    private val appleAttemptIds = AtomicLong()
    private val mutableAppleBrowserLaunch = MutableStateFlow<AppleBrowserLaunchCommand?>(null)
    private val mutableAppleCanCancel = MutableStateFlow(false)

    val state = controller.state
    val searchState = controller.searchState
    val recipeActionState = controller.recipeActionState
    val recipeImagePreparationState = controller.recipeImagePreparationState
    val accountState = controller.accountState
    val onboardingState = onboarding.state
    val authenticationMethod = mutableAuthenticationMethod.asStateFlow()
    val appleBrowserLaunch = mutableAppleBrowserLaunch.asStateFlow()
    val appleCanCancel = mutableAppleCanCancel.asStateFlow()

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

    internal fun beginAppleAuthentication(): Boolean {
        val pending = synchronized(authenticationLock) {
            if (mutableAuthenticationMethod.value != null || state.value.phase != SessionPhase.SIGNED_OUT) return false
            controller.clearAuthenticationError()
            PendingAppleAuthentication(
                id = appleAttemptIds.incrementAndGet(),
                verifier = ApplePkce.generateVerifier(),
            ).also {
                appleAttempt = it
                mutableAuthenticationMethod.value = AuthenticationMethod.APPLE
                mutableAppleCanCancel.value = false
            }
        }
        lateinit var launched: Job
        launched = viewModelScope.launch(start = CoroutineStart.LAZY) {
            try {
                val response = api.startAppleAuthentication(
                    AppleAuthenticationStartRequest(
                        codeChallenge = ApplePkce.challenge(pending.verifier),
                        callback = appleCallback,
                    ),
                )
                try {
                    Instant.parse(response.expiresAt)
                } catch (_: Throwable) {
                    throw IllegalArgumentException("Invalid Apple authentication response")
                }
                val accepted = synchronized(authenticationLock) {
                    if (appleAttempt != pending || mutableAuthenticationMethod.value != AuthenticationMethod.APPLE) {
                        false
                    } else {
                        appleAttempt = pending.copy(
                            transactionId = response.transactionId,
                            waiting = true,
                        )
                        mutableAppleBrowserLaunch.value = AppleBrowserLaunchCommand(pending.id, response.browserUrl)
                        mutableAppleCanCancel.value = true
                        true
                    }
                }
                if (accepted) scheduleAppleDeadline(pending.id, appleWaitingTimeoutMillis)
            } catch (failure: kotlinx.coroutines.CancellationException) {
                throw failure
            } catch (_: Throwable) {
                failAppleAttempt(pending.id, "Apple sign-in is unavailable. Please try again.")
            } finally {
                synchronized(authenticationLock) {
                    if (appleStartJob == launched) appleStartJob = null
                }
            }
        }
        synchronized(authenticationLock) {
            if (appleAttempt != pending) return false
            appleStartJob = launched
        }
        launched.start()
        return true
    }

    internal fun consumeAppleBrowserLaunch(command: AppleBrowserLaunchCommand): String? =
        synchronized(authenticationLock) {
            val pending = appleAttempt
            if (mutableAppleBrowserLaunch.value != command || pending?.id != command.id || !pending.waiting) return null
            mutableAppleBrowserLaunch.value = null
            command.url
        }

    internal fun appleBrowserLaunchFailed(command: AppleBrowserLaunchCommand) {
        failAppleAttempt(command.id, "No browser is available to continue with Apple.")
    }

    internal fun cancelAppleAuthentication(): Boolean {
        val deadline = synchronized(authenticationLock) {
            val pending = appleAttempt ?: return false
            if (!pending.waiting || mutableAuthenticationMethod.value != AuthenticationMethod.APPLE) return false
            appleAttempt = null
            mutableAppleBrowserLaunch.value = null
            mutableAppleCanCancel.value = false
            mutableAuthenticationMethod.value = null
            appleDeadlineJob.also { appleDeadlineJob = null }
        }
        deadline?.cancel()
        return true
    }

    internal fun handleAppleAuthenticationCallback(callback: AppleAuthenticationCallback): Boolean {
        var deadline: Job? = null
        val accepted = synchronized(authenticationLock) {
            val pending = appleAttempt
            if (pending == null || !pending.waiting || pending.transactionId != callback.transactionId ||
                mutableAuthenticationMethod.value != AuthenticationMethod.APPLE
            ) {
                return false
            }
            appleAttempt = null
            mutableAppleBrowserLaunch.value = null
            mutableAppleCanCancel.value = false
            deadline = appleDeadlineJob.also { appleDeadlineJob = null }
            when (callback) {
                is AppleAuthenticationCallback.Error -> {
                    mutableAuthenticationMethod.value = null
                    callback.error.userMessage()?.let(controller::reportAuthenticationFailure)
                }
                is AppleAuthenticationCallback.Success -> launchAuthentication { deviceId ->
                    controller.signInWithApple(
                        AppleAuthenticationExchangeRequest(
                            transactionId = callback.transactionId,
                            exchangeCode = callback.exchangeCode,
                            codeVerifier = pending.verifier,
                            deviceName = "Android",
                            onboardingDeviceId = deviceId,
                        ),
                    )
                }
            }
            true
        }
        deadline?.cancel()
        return accepted
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
    fun updateSearchQuery(query: String) = controller.updateSearchQuery(query)
    fun saveRecipe(draft: RecipeEditDraft, image: PreparedRecipeImage?) = controller.saveRecipe(draft, image)
    fun retryRecipePhoto(image: PreparedRecipeImage? = null) = controller.retryRecipePhoto(image)
    fun moveRecipe(recipeId: Long, targetId: Long) = controller.moveRecipe(recipeId, targetId)
    fun deleteRecipe(recipeId: Long) = controller.deleteRecipe(recipeId)
    fun addReviewedIngredients(recipeId: Long, items: List<ShoppingItemInput>) =
        controller.addReviewedIngredients(recipeId, items)
    fun retryRecipeReconciliation() = controller.retryRecipeReconciliation()
    fun clearRecipeAction() = controller.clearRecipeAction()
    fun prepareRecipeImage(uri: Uri, requestKey: String = uri.toString()) =
        controller.prepareRecipeImage(uri.toString(), requestKey)
    fun releaseRecipeEditorImage(selection: RecipeEditorImageSelection) = controller.releaseRecipeEditorImage(selection)
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
        val jobs = synchronized(authenticationLock) {
            googleAttempt = null
            appleAttempt = null
            mutableAppleBrowserLaunch.value = null
            mutableAppleCanCancel.value = false
            mutableAuthenticationMethod.value = null
            listOfNotNull(authenticationJob, appleStartJob, appleDeadlineJob).also {
                authenticationJob = null
                appleStartJob = null
                appleDeadlineJob = null
            }
        }
        jobs.forEach(Job::cancel)
    }

    private fun scheduleAppleDeadline(attemptId: Long, timeoutMillis: Long) {
        val deadline = viewModelScope.launch {
            delay(timeoutMillis)
            failAppleAttempt(attemptId, "Apple sign-in expired. Start again.")
        }
        synchronized(authenticationLock) {
            if (appleAttempt?.id == attemptId) {
                appleDeadlineJob?.cancel()
                appleDeadlineJob = deadline
            } else {
                deadline.cancel()
            }
        }
    }

    private fun failAppleAttempt(attemptId: Long, message: String) {
        val deadline = synchronized(authenticationLock) {
            if (appleAttempt?.id != attemptId || mutableAuthenticationMethod.value != AuthenticationMethod.APPLE) return
            appleAttempt = null
            mutableAppleBrowserLaunch.value = null
            mutableAppleCanCancel.value = false
            mutableAuthenticationMethod.value = null
            appleDeadlineJob.also { appleDeadlineJob = null }
        }
        deadline?.cancel()
        controller.reportAuthenticationFailure(message)
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

    private data class PendingAppleAuthentication(
        val id: Long,
        val verifier: String,
        val transactionId: String? = null,
        val waiting: Boolean = false,
    )
}

internal class GoogleAuthenticationAttempt internal constructor(
    internal val nonce: String,
)

class AppleBrowserLaunchCommand internal constructor(
    internal val id: Long,
    val url: String,
)

internal fun appleCallbackFor(baseUrl: String, isDebugBuild: Boolean): String {
    val uri = try {
        URI(baseUrl)
    } catch (_: Exception) {
        return "release"
    }
    return if (isDebugBuild && uri.scheme == "http" && uri.host in setOf("10.0.2.2", "localhost", "127.0.0.1")) {
        "debug"
    } else {
        "release"
    }
}

private fun AppleAuthenticationError.userMessage(): String? = when (this) {
    AppleAuthenticationError.CANCELLED -> null
    AppleAuthenticationError.AUTHENTICATION_FAILED -> "Could not sign in with Apple. Please try again."
    AppleAuthenticationError.TRANSACTION_EXPIRED -> "Apple sign-in expired. Start again."
    AppleAuthenticationError.TRANSACTION_UNAVAILABLE -> "That Apple sign-in can no longer be used. Start again."
    AppleAuthenticationError.ACCOUNT_LINK_REQUIRED -> "Sign in with your existing method to link this Apple account."
    AppleAuthenticationError.PROVIDER_UNAVAILABLE -> "Apple sign-in is unavailable. Please try again."
}
