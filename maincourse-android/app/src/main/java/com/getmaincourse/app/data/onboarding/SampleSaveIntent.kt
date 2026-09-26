package com.getmaincourse.app.data.onboarding

import kotlinx.serialization.Serializable

/** One explicit Keep, not an import queue. Retries retain the same source and destination. */
@Serializable
data class SampleSaveIntent(
    val requestId: String,
    val sourceKey: String = "tomato-orzo-v1",
    val userId: Long? = null,
    val cookbookId: Long? = null,
)
