package com.getmaincourse.app.features.session

import com.getmaincourse.app.data.cache.RecipeScope
import com.getmaincourse.app.data.model.RecipeSummary
import com.getmaincourse.app.data.model.SessionResponse
import kotlinx.coroutines.withTimeout

internal class RecipeHydrator(
    private val repository: CatalogRepository,
    private val timeoutMillis: Long = 120_000,
) {
    suspend fun hydrate(
        session: SessionResponse,
        scope: RecipeScope,
        knownRecipes: List<RecipeSummary>,
        canCommit: suspend () -> Boolean,
        onPageSaved: suspend () -> Unit,
    ) {
        val knownCompletedIds = knownRecipes
            .filter { it.importStatus == COMPLETED_IMPORT_STATUS }
            .mapTo(mutableSetOf(), RecipeSummary::id)
        repository.withRecipeRead { permit ->
            withTimeout(timeoutMillis) {
                var cursor: String? = null
                val seenCursors = mutableSetOf<String>()
                while (true) {
                    val page = repository.refreshRecipeDetailBatch(
                        session = session,
                        scope = scope,
                        cursor = cursor,
                        knownCompletedIds = knownCompletedIds,
                        canCommit = canCommit,
                        permit = permit,
                    )
                    if (page.recipes.isEmpty()) return@withTimeout
                    onPageSaved()
                    val next = page.nextCursor?.takeIf(String::isNotBlank)
                        ?: throw RecipeHydrationException("Search hydration is incomplete because cursor progress stopped")
                    if (!seenCursors.add(next)) {
                        throw RecipeHydrationException("Search hydration is incomplete because a cursor repeated")
                    }
                    cursor = next
                }
            }
        }
    }

    private companion object {
        const val COMPLETED_IMPORT_STATUS = "completed"
    }
}

internal class RecipeHydrationException(message: String) : IllegalStateException(message)
