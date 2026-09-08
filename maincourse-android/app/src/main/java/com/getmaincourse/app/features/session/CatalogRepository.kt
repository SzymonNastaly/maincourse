package com.getmaincourse.app.features.session

import com.getmaincourse.app.data.cache.CachedRecipes
import com.getmaincourse.app.data.cache.CatalogStore
import com.getmaincourse.app.data.cache.RecipeScope
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.RecipeBatchResponse
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeSummary
import com.getmaincourse.app.data.model.SessionResponse
import com.getmaincourse.app.data.network.MainCourseApi
import com.getmaincourse.app.features.search.RecipeSearchDocument
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class CatalogRepository(
    private val api: MainCourseApi,
    private val store: CatalogStore,
) {
    private val writes = Mutex()
    private val recipeReads = RecipeReadMutationBarrier()

    suspend fun cachedCookbooks(userId: Long): List<Cookbook> = store.cookbooks(userId)

    suspend fun selectedCookbookId(userId: Long): Long? = store.selectedCookbookId(userId)

    suspend fun discoverCookbooks(
        session: SessionResponse,
        excludingCookbookId: Long? = null,
        canCommit: () -> Boolean = { true },
    ): List<Cookbook> {
        val items = api.cookbooks(session.token).filterNot { it.id == excludingCookbookId }
        currentCoroutineContext().ensureActive()
        writes.withLock {
            currentCoroutineContext().ensureActive()
            if (!canCommit()) throw CancellationException("Catalog discovery was replaced")
            store.replaceCookbooks(session.user.id, items)
        }
        return items
    }

    suspend fun selectCookbook(userId: Long, cookbookId: Long) = writes.withLock {
        currentCoroutineContext().ensureActive()
        store.selectCookbook(userId, cookbookId)
    }

    suspend fun cachedRecipes(scope: RecipeScope): CachedRecipes = store.recipes(scope)

    suspend fun refreshRecipes(
        session: SessionResponse,
        scope: RecipeScope,
        canCommit: suspend () -> Boolean = { true },
    ): List<RecipeSummary> {
        val permit = recipeReads.beginRead()
        try {
            if (!permit.admitted) throw CancellationException("A recipe mutation is active")
            val items = api.recipes(session.token, scope.cookbookId)
            currentCoroutineContext().ensureActive()
            writes.withLock {
                currentCoroutineContext().ensureActive()
                if (!recipeReads.canCommit(permit) || !canCommit()) {
                    throw CancellationException("Recipe refresh was replaced")
                }
                store.replaceRecipes(scope, items)
            }
            return items
        } finally {
            recipeReads.endRead(permit)
        }
    }

    suspend fun cachedDetail(scope: RecipeScope, recipeId: Long): RecipeDetail? = store.detail(scope, recipeId)

    suspend fun refreshDetail(
        session: SessionResponse,
        scope: RecipeScope,
        recipeId: Long,
        canCommit: suspend () -> Boolean = { true },
    ): RecipeDetail {
        val permit = recipeReads.beginRead()
        try {
            if (!permit.admitted) throw CancellationException("A recipe mutation is active")
            val detail = api.recipe(session.token, scope.cookbookId, recipeId)
            currentCoroutineContext().ensureActive()
            writes.withLock {
                currentCoroutineContext().ensureActive()
                if (!recipeReads.canCommit(permit) || !canCommit()) {
                    throw CancellationException("Recipe detail was replaced")
                }
                store.saveRecipeDetails(scope, listOf(detail))
            }
            return detail
        } finally {
            recipeReads.endRead(permit)
        }
    }

    suspend fun searchDocuments(scope: RecipeScope): List<RecipeSearchDocument> = store.searchDocuments(scope)

    internal suspend fun refreshRecipeDetailBatch(
        session: SessionResponse,
        scope: RecipeScope,
        cursor: String?,
        knownCompletedIds: Set<Long>,
        canCommit: suspend () -> Boolean,
    ): RecipeBatchResponse {
        val permit = recipeReads.beginRead()
        try {
            if (!permit.admitted) throw CancellationException("A recipe mutation is active")
            val page = api.recipeBatch(session.token, scope.cookbookId, cursor)
            val eligible = page.recipes.filter { it.id in knownCompletedIds }
            currentCoroutineContext().ensureActive()
            writes.withLock {
                currentCoroutineContext().ensureActive()
                if (!recipeReads.canCommit(permit) || !canCommit()) {
                    throw CancellationException("Recipe hydration was replaced")
                }
                store.saveRecipeDetails(scope, eligible)
            }
            return page
        } finally {
            recipeReads.endRead(permit)
        }
    }

    internal suspend fun invalidateAndJoinRecipeReads() = recipeReads.invalidateAndJoinReads()

    internal suspend fun beginRecipeMutation(): RecipeMutationPermit = recipeReads.beginMutation()

    internal suspend fun <T> withRecipeMutation(block: suspend (RecipeMutationPermit) -> T): T {
        val permit = beginRecipeMutation()
        return try {
            block(permit)
        } finally {
            endRecipeMutation(permit)
        }
    }

    internal suspend fun commitRecipeDetails(
        permit: RecipeMutationPermit,
        scope: RecipeScope,
        details: List<RecipeDetail>,
    ) = writes.withLock {
        recipeReads.requireMutationCanCommit(permit)
        store.saveRecipeDetails(scope, details)
    }

    internal suspend fun commitPartialRecipe(
        permit: RecipeMutationPermit,
        scope: RecipeScope,
        knownSummary: RecipeSummary,
        detail: RecipeDetail,
    ) = writes.withLock {
        recipeReads.requireMutationCanCommit(permit)
        store.upsertPartialRecipe(scope, knownSummary, detail)
    }

    internal suspend fun commitRecipeRemoval(
        permit: RecipeMutationPermit,
        scope: RecipeScope,
        recipeId: Long,
    ) = writes.withLock {
        recipeReads.requireMutationCanCommit(permit)
        store.removeRecipe(scope, recipeId)
    }

    internal fun endRecipeMutation(permit: RecipeMutationPermit) = recipeReads.endMutation(permit)

    suspend fun removeRecipe(scope: RecipeScope, recipeId: Long) = writes.withLock {
        currentCoroutineContext().ensureActive()
        store.removeRecipe(scope, recipeId)
    }

    suspend fun removeCookbook(scope: RecipeScope) = writes.withLock {
        currentCoroutineContext().ensureActive()
        store.removeCookbook(scope)
    }

    suspend fun clear() = writes.withLock { store.clear() }
}
