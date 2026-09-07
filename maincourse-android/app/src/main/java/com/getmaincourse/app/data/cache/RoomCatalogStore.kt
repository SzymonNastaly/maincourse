package com.getmaincourse.app.data.cache

import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeSummary
import kotlinx.serialization.json.Json

class RoomCatalogStore(
    database: MainCourseDatabase,
    private val json: Json = Json,
) : CatalogStore {
    private val dao = database.catalogDao()

    override suspend fun cookbooks(userId: Long): List<Cookbook> = dao.cookbooks(userId).map {
        json.decodeFromString<Cookbook>(it.cookbookJson)
    }

    override suspend fun replaceCookbooks(userId: Long, items: List<Cookbook>) {
        dao.replaceCookbooks(
            userId,
            items.mapIndexed { index, cookbook ->
                CookbookEntity(
                    userId = userId,
                    cookbookId = cookbook.id,
                    listPosition = index,
                    cookbookJson = json.encodeToString(cookbook),
                )
            },
        )
    }

    override suspend fun selectedCookbookId(userId: Long): Long? = dao.selectedCookbookId(userId)

    override suspend fun selectCookbook(userId: Long, cookbookId: Long) {
        dao.selectCookbook(SelectedCookbookEntity(userId, cookbookId))
    }

    override suspend fun recipes(scope: RecipeScope): CachedRecipes {
        val stored = dao.storedRecipes(scope.userId, scope.cookbookId)
        return CachedRecipes(
            items = stored.items.map {
                json.decodeFromString<RecipeSummary>(it.summaryJson)
            },
            fetched = stored.fetched,
        )
    }

    override suspend fun replaceRecipes(scope: RecipeScope, items: List<RecipeSummary>) {
        dao.replaceRecipes(
            userId = scope.userId,
            cookbookId = scope.cookbookId,
            items = items.mapIndexed { index, summary ->
                RecipeEntity(
                    userId = scope.userId,
                    cookbookId = scope.cookbookId,
                    recipeId = summary.id,
                    listPosition = index,
                    summaryJson = json.encodeToString(summary),
                )
            },
        )
    }

    override suspend fun detail(scope: RecipeScope, recipeId: Long): RecipeDetail? =
        dao.recipe(scope.userId, scope.cookbookId, recipeId)?.detailJson?.let {
            json.decodeFromString<RecipeDetail>(it)
        }

    override suspend fun saveDetail(scope: RecipeScope, detail: RecipeDetail) {
        dao.updateDetail(
            userId = scope.userId,
            cookbookId = scope.cookbookId,
            recipeId = detail.id,
            detailJson = json.encodeToString(detail),
            detailUpdatedAt = detail.updatedAt,
        )
    }

    override suspend fun removeRecipe(scope: RecipeScope, recipeId: Long) {
        dao.removeRecipe(scope.userId, scope.cookbookId, recipeId)
    }

    override suspend fun removeCookbook(scope: RecipeScope) {
        dao.removeCookbook(scope.userId, scope.cookbookId)
    }

    override suspend fun clear() {
        dao.clear()
    }
}
