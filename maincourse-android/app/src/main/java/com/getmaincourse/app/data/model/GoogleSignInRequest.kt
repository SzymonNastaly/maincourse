package com.getmaincourse.app.data.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GoogleSignInRequest(
    @SerialName("id_token") val idToken: String,
    val nonce: String,
    @SerialName("device_name") val deviceName: String,
    @SerialName("onboarding_device_id") val onboardingDeviceId: String? = null,
) {
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val provider: String = "google"
}
