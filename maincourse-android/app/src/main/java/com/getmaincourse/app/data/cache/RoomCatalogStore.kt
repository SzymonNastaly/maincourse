package com.getmaincourse.app.data.cache

import androidx.room.withTransaction
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeSummary
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class RoomCatalogStore(
    private val database: MainCourseDatabase,
    json: Json = Json,
) : CatalogStore {
    private val dao = database.catalogDao()
    private val json = Json(json) { ignoreUnknownKeys = true }

    override suspend fun cookbooks(userId: Long): List<Cookbook> = database.withTransaction {
        dao.cookbooks(userId).mapNotNull { entity ->
            try {
                json.decodeFromString<Cookbook>(entity.cookbookJson)
            } catch (_: SerializationException) {
                dao.removeCookbookIfInvalid(entity.userId, entity.cookbookId, entity.cookbookJson)
                null
            }
        }
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
        return database.withTransaction {
            val stored = dao.storedRecipes(scope.userId, scope.cookbookId)
            try {
                CachedRecipes(
                    items = stored.items.map {
                        json.decodeFromString<RecipeSummary>(it.summaryJson)
                    },
                    fetched = stored.fetched,
                )
            } catch (_: SerializationException) {
                dao.invalidateRecipes(scope.userId, scope.cookbookId)
                CachedRecipes(emptyList(), fetched = false)
            }
        }
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

    override suspend fun detail(scope: RecipeScope, recipeId: Long): RecipeDetail? = database.withTransaction {
        dao.recipe(scope.userId, scope.cookbookId, recipeId)?.detailJson?.let { detailJson ->
            try {
                json.decodeFromString<RecipeDetail>(detailJson)
            } catch (_: SerializationException) {
                dao.clearDetailIfInvalid(scope.userId, scope.cookbookId, recipeId, detailJson)
                null
            }
        }
    }

    override suspend fun saveDetail(scope: RecipeScope, detail: RecipeDetail) {
        dao.updateDetail(
            userId = scope.userId,
            cookbookId = scope.cookbookId,
            recipeId = detail.id,
            detailJson = json.encodeToString(detail),
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
