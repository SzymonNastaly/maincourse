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

    // ł has no combining mark to strip, and ß folds to "ss" as on iOS and web.
    // Documents are rebuilt when search opens, so changing this needs no migration.
    fun normalizeIndexedText(raw: String): String =
        Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .lowercase(Locale.ROOT)
            .replace("ł", "l")
            .replace("ß", "ss")
}

private val COMBINING_MARKS = Regex("\\p{M}+")
private val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")
