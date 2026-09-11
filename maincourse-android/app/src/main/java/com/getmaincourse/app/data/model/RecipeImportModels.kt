package com.getmaincourse.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RecipeUrlImportRequest(val url: String)

@Serializable
data class RecipeTextImportRequest(val text: String)

@Serializable
data class RecipeImportResponse(
    val id: Long,
    @SerialName("import_status")
    val importStatus: String,
)
