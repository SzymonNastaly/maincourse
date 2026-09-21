package com.getmaincourse.app.data.cache

import java.text.Normalizer
import java.util.Locale

internal object RecipeSearchQuery {
    fun build(raw: String, column: String? = null): String? {
        val tokens = normalizeIndexedText(raw)
            .split(NON_ALPHANUMERIC)
            .filter(String::isNotBlank)
        if (tokens.isEmpty()) return null

        val prefix = column?.let { "$it:" }.orEmpty()
        return tokens.joinToString(" AND ") { token -> "$prefix$token*" }
    }

    fun normalizeIndexedText(raw: String): String =
        Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase(Locale.ROOT)
}

private val COMBINING_MARKS = Regex("\\p{M}+")
private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")
