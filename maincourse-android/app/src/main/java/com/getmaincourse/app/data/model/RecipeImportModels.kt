package com.getmaincourse.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RecipeUrlImportRequest(val url: String)

@Serializable
data class RecipeTextImportRequest(val text: String)

@Serializable
data class RecipePageContent(
    val url: String,
    val jsonLd: List<String> = emptyList(),
    val metaTags: Map<String, String> = emptyMap(),
    val coverImageCandidates: List<String> = emptyList(),
    val html: String = "",
)

@Serializable
data class RecipeContentImportRequest(
    val url: String,
    @SerialName("json_ld")
    val jsonLd: List<String>,
    @SerialName("meta_tags")
    val metaTags: Map<String, String>,
    @SerialName("cover_image_candidates")
    val coverImageCandidates: List<String>,
    val html: String,
) {
    constructor(content: RecipePageContent) : this(
        url = content.url,
        jsonLd = content.jsonLd,
        metaTags = content.metaTags,
        coverImageCandidates = content.coverImageCandidates,
        html = content.html,
    )
}

@Serializable
data class RecipeImportResponse(
    val id: Long,
    @SerialName("import_status")
    val importStatus: String,
)
