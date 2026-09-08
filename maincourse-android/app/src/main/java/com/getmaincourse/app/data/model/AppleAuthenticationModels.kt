package com.getmaincourse.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppleAuthenticationStartRequest(
    @SerialName("code_challenge")
    val codeChallenge: String,
    val callback: String,
)

@Serializable
data class AppleAuthenticationStartResponse(
    @SerialName("transaction_id")
    val transactionId: String,
    @SerialName("browser_url")
    val browserUrl: String,
    @SerialName("expires_at")
    val expiresAt: String,
)

@Serializable
data class AppleAuthenticationExchangeRequest(
    @SerialName("transaction_id")
    val transactionId: String,
    @SerialName("exchange_code")
    val exchangeCode: String,
    @SerialName("code_verifier")
    val codeVerifier: String,
    @SerialName("device_name")
    val deviceName: String,
    @SerialName("onboarding_device_id")
    val onboardingDeviceId: String? = null,
)
