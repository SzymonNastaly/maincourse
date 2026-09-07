package com.getmaincourse.app.features.session

import com.getmaincourse.app.data.cache.CachedRecipes
import com.getmaincourse.app.data.cache.CatalogStore
import com.getmaincourse.app.data.cache.RecipeScope
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeSummary
import com.getmaincourse.app.data.model.SessionResponse
import com.getmaincourse.app.data.network.MainCourseApi
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

    suspend fun refreshRecipes(session: SessionResponse, scope: RecipeScope): List<RecipeSummary> {
        val items = api.recipes(session.token, scope.cookbookId)
        currentCoroutineContext().ensureActive()
        writes.withLock {
            currentCoroutineContext().ensureActive()
            store.replaceRecipes(scope, items)
        }
        return items
    }

    suspend fun cachedDetail(scope: RecipeScope, recipeId: Long): RecipeDetail? = store.detail(scope, recipeId)

    suspend fun refreshDetail(session: SessionResponse, scope: RecipeScope, recipeId: Long): RecipeDetail {
        val detail = api.recipe(session.token, scope.cookbookId, recipeId)
        currentCoroutineContext().ensureActive()
        writes.withLock {
            currentCoroutineContext().ensureActive()
            store.saveDetail(scope, detail)
        }
        return detail
    }

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
