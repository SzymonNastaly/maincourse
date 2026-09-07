@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.getmaincourse.app.data.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OnboardingAnswers(
    @SerialName("household_size")
    val householdSize: Int? = null,
    @SerialName("save_today")
    val saveToday: List<String> = emptyList(),
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val diet: List<String> = emptyList(),
)

@Serializable
data class OnboardingRequest(
    @SerialName("device_id")
    val deviceId: String,
    val answers: OnboardingAnswers,
)

@Serializable
data class OnboardingResponse(
    val id: Long,
    @SerialName("device_id")
    val deviceId: String,
    val answers: OnboardingAnswers,
)
