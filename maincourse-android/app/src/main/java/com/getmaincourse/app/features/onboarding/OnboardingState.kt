package com.getmaincourse.app.features.onboarding

typealias OnboardingStep = com.getmaincourse.app.data.onboarding.OnboardingStep

data class OnboardingState(
    val isLoading: Boolean = true,
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val householdSize: Int? = null,
    val saveToday: List<String> = emptyList(),
    val diet: List<String> = emptyList(),
    val persistenceError: String? = null,
    val canRetryPersistence: Boolean = false,
)
