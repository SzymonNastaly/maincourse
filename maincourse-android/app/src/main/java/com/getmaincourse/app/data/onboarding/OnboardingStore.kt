package com.getmaincourse.app.data.onboarding

import kotlinx.serialization.Serializable

@Serializable
enum class OnboardingStep {
    WELCOME,
    HOUSEHOLD,
    SAVING,
    DIET,
    AUTH,
    COMPLETE,
}

@Serializable
data class OnboardingRecord(
    val schemaVersion: Int = CURRENT_ONBOARDING_SCHEMA_VERSION,
    val origin: String,
    val deviceId: String?,
    val step: OnboardingStep,
    val householdSize: Int? = null,
    val saveToday: List<String> = emptyList(),
    val diet: List<String> = emptyList(),
    val completed: Boolean = false,
)

interface OnboardingStore {
    suspend fun read(): OnboardingRecord?

    suspend fun write(record: OnboardingRecord)
}

internal const val CURRENT_ONBOARDING_SCHEMA_VERSION = 1
internal val ALLOWED_HOUSEHOLD_SIZES = setOf(1, 2, 3, 5)
internal val ALLOWED_SAVING_VALUES = setOf(
    "screenshots",
    "browser_bookmarks",
    "notes",
    "recipe_apps",
    "cookbooks",
    "dont_save",
)
internal val ALLOWED_DIET_VALUES = setOf(
    "vegetarian",
    "vegan",
    "glutenFree",
    "pescatarian",
    "halal",
    "kosher",
    "lactoseFree",
)
