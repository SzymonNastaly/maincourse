package com.getmaincourse.app.features.search

import com.getmaincourse.app.data.model.RecipeSummary
import java.text.Normalizer
import java.util.Locale

class RecipeSearchEngine {
    fun search(
        documents: List<RecipeSearchDocument>,
        query: String,
        limit: Int = RESULT_LIMIT,
    ): List<RecipeSummary> {
        if (limit <= 0) return emptyList()
        val terms = normalize(query).split(' ').filter(String::isNotBlank)
        if (terms.isEmpty()) return emptyList()
        val searchable = documents.filterNot { it.summary.importStatus.equals("failed", ignoreCase = true) }
        val direct = searchable.mapNotNull { it.score(terms, MatchMode.DIRECT) }
        val matches = if (direct.isNotEmpty()) direct else searchable.mapNotNull { it.score(terms, MatchMode.FALLBACK) }
        return matches.sortedWith(
            compareByDescending<ScoredRecipe> { it.score }
                .thenBy { normalize(it.summary.name) }
                .thenBy { it.summary.id },
        ).take(limit.coerceAtMost(RESULT_LIMIT)).map(ScoredRecipe::summary)
    }

    private fun RecipeSearchDocument.score(terms: List<String>, mode: MatchMode): ScoredRecipe? {
        val fields = buildList {
            add(SearchField(normalize(summary.name), NAME_WEIGHT))
            if (summary.importStatus.equals("completed", ignoreCase = true)) {
                detail?.let { recipe ->
                    recipe.ingredients.forEach { add(SearchField(normalize(it), INGREDIENT_WEIGHT)) }
                    recipe.structuredIngredients.forEach { ingredient ->
                        add(SearchField(normalize(ingredient.name ?: ingredient.raw), INGREDIENT_WEIGHT))
                    }
                    recipe.instructions.forEach { add(SearchField(normalize(it), INSTRUCTION_WEIGHT)) }
                }
            }
        }.filterNot { it.text.isBlank() }

        var score = 0
        for (term in terms) {
            val best = fields.maxOfOrNull { field ->
                val quality = when (mode) {
                    MatchMode.DIRECT -> field.directQuality(term)
                    MatchMode.FALLBACK -> field.fallbackQuality(term)
                }
                if (quality == 0) 0 else field.weight + quality
            } ?: 0
            if (best == 0) return null
            score += best
        }
        if (normalize(summary.name).contains(terms.joinToString(" "))) score += PHRASE_BONUS
        return ScoredRecipe(summary, score)
    }

    private fun SearchField.directQuality(term: String): Int = when {
        text == term -> 30
        tokens.any { it == term } -> 20
        else -> 0
    }

    private fun SearchField.fallbackQuality(term: String): Int {
        if (directQuality(term) > 0) return directQuality(term)
        if (tokens.any { it.startsWith(term) }) return 10
        val distance = typoDistance(term)
        return if (distance != null && tokens.any { boundedDistance(it, term, distance) }) 1 else 0
    }

    private fun typoDistance(term: String): Int? = when {
        term.length >= 8 -> 2
        term.length >= 4 -> 1
        else -> null
    }

    private fun boundedDistance(left: String, right: String, maximum: Int): Boolean {
        if (kotlin.math.abs(left.length - right.length) > maximum) return false
        var previous = IntArray(right.length + 1) { it }
        for (leftIndex in left.indices) {
            val current = IntArray(right.length + 1)
            current[0] = leftIndex + 1
            var rowMinimum = current[0]
            for (rightIndex in right.indices) {
                current[rightIndex + 1] = minOf(
                    current[rightIndex] + 1,
                    previous[rightIndex + 1] + 1,
                    previous[rightIndex] + if (left[leftIndex] == right[rightIndex]) 0 else 1,
                )
                rowMinimum = minOf(rowMinimum, current[rightIndex + 1])
            }
            if (rowMinimum > maximum) return false
            previous = current
        }
        return previous[right.length] <= maximum
    }

    private fun normalize(value: String): String {
        val expanded = buildString {
            value.lowercase(Locale.ROOT).forEach { character ->
                append(
                    when (character) {
                        'ł' -> "l"
                        'ø' -> "o"
                        'đ', 'ð' -> "d"
                        'þ' -> "th"
                        'æ' -> "ae"
                        'œ' -> "oe"
                        'ß' -> "ss"
                        'ı' -> "i"
                        'ħ' -> "h"
                        'ŋ' -> "n"
                        'ŧ' -> "t"
                        else -> character
                    },
                )
            }
        }
        return Normalizer.normalize(expanded, Normalizer.Form.NFD)
            .replace(COMBINING_MARKS, "")
            .replace(NON_ALPHANUMERIC, " ")
            .trim()
            .replace(WHITESPACE, " ")
    }

    private data class SearchField(val text: String, val weight: Int) {
        val tokens = text.split(' ').filter(String::isNotBlank)
    }

    private data class ScoredRecipe(val summary: RecipeSummary, val score: Int)

    private enum class MatchMode { DIRECT, FALLBACK }

    private companion object {
        const val NAME_WEIGHT = 300
        const val INGREDIENT_WEIGHT = 200
        const val INSTRUCTION_WEIGHT = 100
        const val PHRASE_BONUS = 50
        const val RESULT_LIMIT = 50
        val COMBINING_MARKS = Regex("\\p{M}+")
        val NON_ALPHANUMERIC = Regex("[^\\p{L}\\p{N}]+")
        val WHITESPACE = Regex("\\s+")
    }
}
