package com.getmaincourse.app.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getmaincourse.app.data.model.OnboardingRequest
import com.getmaincourse.app.data.onboarding.OnboardingPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class PreAuthViewModel internal constructor(
    hasCompleted: Boolean,
    hasReachedAuthentication: Boolean,
    private val deviceId: () -> String,
    private val prepareForAuthentication: (String) -> Unit,
    private val completeOnboarding: () -> Unit,
    private val submitOnboarding: suspend (OnboardingRequest) -> Unit,
) : ViewModel() {
    constructor(
        preferences: OnboardingPreferences,
        submitOnboarding: suspend (OnboardingRequest) -> Unit,
    ) : this(
        hasCompleted = preferences.hasCompleted,
        hasReachedAuthentication = preferences.hasReachedAuthentication,
        deviceId = preferences::deviceId,
        prepareForAuthentication = preferences::prepareForAuthentication,
        completeOnboarding = preferences::complete,
        submitOnboarding = submitOnboarding,
    )

    private val mutableState = MutableStateFlow(
        PreAuthUiState(
            step = when {
                hasCompleted || hasReachedAuthentication -> PreAuthStep.AUTH
                else -> PreAuthStep.WELCOME
            },
            onboarding = !hasCompleted,
        ),
    )
    val state = mutableState.asStateFlow()

    fun start() {
        if (mutableState.value.step == PreAuthStep.WELCOME) {
            mutableState.update { it.copy(step = PreAuthStep.HOUSEHOLD) }
        }
    }

    fun selectHousehold(household: HouseholdSize) {
        mutableState.update { it.copy(household = household) }
    }

    fun toggleSaveToday(option: SaveTodayOption) {
        mutableState.update { it.copy(saveToday = it.saveToday.toggle(option)) }
    }

    fun toggleDiet(option: DietOption) {
        mutableState.update { it.copy(diet = it.diet.toggle(option)) }
    }

    fun advance() {
        val current = mutableState.value
        if (!current.canAdvance) return
        when (current.step) {
            PreAuthStep.WELCOME -> start()
            PreAuthStep.HOUSEHOLD -> mutableState.update { it.copy(step = PreAuthStep.SAVE_TODAY) }
            PreAuthStep.SAVE_TODAY -> mutableState.update { it.copy(step = PreAuthStep.DIET) }
            PreAuthStep.DIET -> moveToAuthentication(current)
            PreAuthStep.AUTH -> Unit
        }
    }

    fun goBack() {
        mutableState.update { current ->
            current.copy(
                step = when (current.step) {
                    PreAuthStep.WELCOME -> PreAuthStep.WELCOME
                    PreAuthStep.HOUSEHOLD -> PreAuthStep.WELCOME
                    PreAuthStep.SAVE_TODAY -> PreAuthStep.HOUSEHOLD
                    PreAuthStep.DIET -> PreAuthStep.SAVE_TODAY
                    PreAuthStep.AUTH -> if (current.onboarding) PreAuthStep.DIET else PreAuthStep.AUTH
                },
            )
        }
    }

    fun skip() {
        completeOnboarding()
        mutableState.update { it.copy(step = PreAuthStep.AUTH, onboarding = false) }
    }

    fun authenticated() {
        if (mutableState.value.onboarding) {
            completeOnboarding()
            mutableState.update { it.copy(onboarding = false) }
        }
    }

    private fun moveToAuthentication(current: PreAuthUiState) {
        val id = deviceId()
        prepareForAuthentication(id)
        mutableState.update { it.copy(step = PreAuthStep.AUTH) }
        if (current.household == null && current.saveToday.isEmpty()) return
        val answers = buildJsonObject {
            current.household?.let { put("household_size", it.serverValue) }
            if (current.saveToday.isNotEmpty()) put("save_today", buildJsonArray {
                current.saveToday.map(SaveTodayOption::serverValue).sorted().forEach(::add)
            })
            put("diet", buildJsonArray {
                current.diet.map(DietOption::serverValue).sorted().forEach(::add)
            })
        }
        viewModelScope.launch {
            try {
                submitOnboarding(OnboardingRequest(id, answers))
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Throwable) {
                // Answers are product research, so onboarding and authentication remain usable offline.
            }
        }
    }
}

data class PreAuthUiState(
    val step: PreAuthStep,
    val onboarding: Boolean,
    val household: HouseholdSize? = null,
    val saveToday: Set<SaveTodayOption> = emptySet(),
    val diet: Set<DietOption> = emptySet(),
) {
    val canAdvance: Boolean
        get() = when (step) {
            PreAuthStep.WELCOME -> true
            PreAuthStep.HOUSEHOLD -> household != null
            PreAuthStep.SAVE_TODAY -> saveToday.isNotEmpty()
            PreAuthStep.DIET -> true
            PreAuthStep.AUTH -> false
        }
}

enum class PreAuthStep { WELCOME, HOUSEHOLD, SAVE_TODAY, DIET, AUTH }

enum class HouseholdSize(val serverValue: Int) { ONE(1), TWO(2), THREE_OR_FOUR(3), FIVE_OR_MORE(5) }

enum class SaveTodayOption(val serverValue: String) {
    SCREENSHOTS("screenshots"),
    BROWSER_BOOKMARKS("browser_bookmarks"),
    NOTES("notes"),
    RECIPE_APPS("recipe_apps"),
    COOKBOOKS("cookbooks"),
    DONT_SAVE("dont_save"),
}

enum class DietOption(val serverValue: String) {
    VEGETARIAN("vegetarian"),
    VEGAN("vegan"),
    GLUTEN_FREE("glutenFree"),
    PESCATARIAN("pescatarian"),
    HALAL("halal"),
    KOSHER("kosher"),
    LACTOSE_FREE("lactoseFree"),
}

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value
