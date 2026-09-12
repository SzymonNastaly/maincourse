package com.getmaincourse.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceTokenRequest(
    val token: String,
    val provider: String,
    val environment: String,
    @SerialName("time_zone")
    val timeZone: String,
)

@Serializable
data class DeviceTokenResponse(
    val id: Long,
    val token: String,
    val provider: String,
    val environment: String,
)

@Serializable
data class NotificationOpenedRequest(
    @SerialName("action_taken")
    val actionTaken: String? = null,
)
