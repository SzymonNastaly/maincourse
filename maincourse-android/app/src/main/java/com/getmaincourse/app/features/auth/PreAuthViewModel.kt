package com.getmaincourse.app.features.auth

import androidx.lifecycle.ViewModel
import com.getmaincourse.app.data.onboarding.OnboardingPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class PreAuthViewModel internal constructor(
    hasCompleted: Boolean,
    hasReachedAuthentication: Boolean,
    demoCompleted: Boolean,
    private val prepareForAuthentication: (Boolean) -> Unit,
    private val completeOnboarding: () -> Unit,
    private val recordDemoCompleted: () -> Unit,
    initialLogin: Boolean = false,
) : ViewModel() {
    constructor(preferences: OnboardingPreferences) : this(
        preferences.hasCompleted, preferences.hasReachedAuthentication, preferences.demoCompleted,
        preferences::prepareForAuthentication, preferences::complete, preferences::finishDemo,
        preferences.authLogin,
    )

    private val mutableState = MutableStateFlow(
        PreAuthUiState(
            step = if (hasCompleted || hasReachedAuthentication) PreAuthStep.AUTH else PreAuthStep.WELCOME,
            onboarding = !hasCompleted,
            demoCompleted = demoCompleted,
            login = hasCompleted || initialLogin,
        ),
    )
    val state = mutableState.asStateFlow()

    fun start() { mutableState.update { it.copy(step = PreAuthStep.DEMO) } }

    fun finishDemo() {
        recordDemoCompleted()
        mutableState.update { it.copy(demoCompleted = true) }
    }

    fun advance() {
        if (state.value.step == PreAuthStep.DEMO && state.value.demoCompleted) {
            mutableState.update { it.copy(step = PreAuthStep.FEATURES) }
        }
    }

    fun signUp() {
        prepareForAuthentication(false)
        mutableState.update { it.copy(step = PreAuthStep.AUTH, login = false) }
    }

    fun logIn() {
        prepareForAuthentication(true)
        mutableState.update { it.copy(step = PreAuthStep.AUTH, login = true) }
    }

    fun goBack() {
        mutableState.update {
            it.copy(step = when (it.step) {
                PreAuthStep.WELCOME -> PreAuthStep.WELCOME
                PreAuthStep.DEMO -> PreAuthStep.WELCOME
                PreAuthStep.FEATURES -> PreAuthStep.DEMO
                PreAuthStep.AUTH -> when {
                    !it.onboarding -> PreAuthStep.AUTH
                    it.demoCompleted -> PreAuthStep.FEATURES
                    else -> PreAuthStep.WELCOME
                }
            })
        }
    }

    fun authenticated() {
        completeOnboarding()
        mutableState.update { it.copy(step = PreAuthStep.AUTH, onboarding = false, login = true) }
    }
}

data class PreAuthUiState(
    val step: PreAuthStep,
    val onboarding: Boolean,
    val demoCompleted: Boolean = false,
    val login: Boolean = false,
)

enum class PreAuthStep { WELCOME, DEMO, FEATURES, AUTH }
