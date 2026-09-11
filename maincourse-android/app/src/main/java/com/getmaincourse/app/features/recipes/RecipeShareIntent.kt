package com.getmaincourse.app.features.recipes

import android.content.Intent

data class SharedRecipeInput(val value: String)

internal fun Intent.sharedRecipeInput(): SharedRecipeInput? {
    if (action != Intent.ACTION_SEND || type?.substringBefore(';')?.startsWith("text/") != true) return null
    val value = getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.trim().orEmpty()
    return value.takeIf(String::isNotBlank)?.let(::SharedRecipeInput)
}
