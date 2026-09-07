package com.getmaincourse.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AccountUpdateRequest(
    val user: AccountAttributes,
)

@Serializable
data class AccountAttributes(
    val name: String? = null,
    @SerialName("lifecycle_notifications_enabled")
    val lifecycleNotificationsEnabled: Boolean? = null,
)

@Serializable
data class AccountResponse(
    val user: User,
)
