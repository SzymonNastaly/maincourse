package com.getmaincourse.app.features.settings

enum class AccountOperation { IDLE, SAVING, DELETING }

data class AccountState(
    val operation: AccountOperation = AccountOperation.IDLE,
    val error: String? = null,
    val canRetryPersistence: Boolean = false,
)
