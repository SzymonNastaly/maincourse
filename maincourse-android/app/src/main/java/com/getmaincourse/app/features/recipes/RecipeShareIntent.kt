package com.getmaincourse.app.features.recipes

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import java.net.URI

data class SharedRecipeInput(val value: String)

sealed interface RecipeShareContent {
    data class Url(val value: String) : RecipeShareContent

    data class Text(val value: String) : RecipeShareContent

    data class Image(
        val uri: String,
        val mimeType: String,
    ) : RecipeShareContent
}

internal fun Intent.sharedRecipeInput(): SharedRecipeInput? {
    val content = recipeShareContent()
    val value = when (content) {
        is RecipeShareContent.Text -> content.value
        is RecipeShareContent.Url -> content.value
        is RecipeShareContent.Image, null -> return null
    }
    return SharedRecipeInput(value)
}

internal fun Intent.recipeShareContent(): RecipeShareContent? {
    if (action != Intent.ACTION_SEND) return null
    val mimeType = type?.substringBefore(';')?.lowercase() ?: return null

    if (mimeType.startsWith("image/")) {
        val uri = IntentCompat.getParcelableExtra(this, Intent.EXTRA_STREAM, Uri::class.java)
            ?: clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        return uri?.let { RecipeShareContent.Image(it.toString(), mimeType) }
    }

    if (!mimeType.startsWith("text/")) return null
    val value = getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim().orEmpty()
    if (value.isBlank()) return null
    return if (value.isHttpUrl()) {
        RecipeShareContent.Url(value)
    } else {
        RecipeShareContent.Text(value)
    }
}

internal fun String.isHttpUrl(): Boolean {
    val uri = runCatching { URI(this) }.getOrNull() ?: return false
    return uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
}
