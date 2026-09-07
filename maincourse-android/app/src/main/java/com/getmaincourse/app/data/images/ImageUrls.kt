package com.getmaincourse.app.data.images

import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeSummary
import okhttp3.HttpUrl.Companion.toHttpUrl

fun resolveImageUrl(apiBaseUrl: String, path: String?): String? {
    val value = path?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (value.startsWith("http://") || value.startsWith("https://")) return value
    val origin = apiBaseUrl.toHttpUrl().newBuilder()
        .encodedPath("/")
        .query(null)
        .fragment(null)
        .build()
    return origin.resolve(value.removePrefix("/"))?.toString()
}

fun RecipeSummary.cardImagePath(): String? =
    coverImages?.card ?: coverImageUrl ?: coverImages?.hero ?: coverImages?.thumb

fun RecipeDetail.heroImagePath(): String? =
    coverImages?.hero ?: coverImageUrl ?: coverImages?.card ?: coverImages?.thumb
