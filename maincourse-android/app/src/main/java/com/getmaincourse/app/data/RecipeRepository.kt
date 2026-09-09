package com.getmaincourse.app.data

import com.getmaincourse.app.data.cache.MainCourseDatabase
import com.getmaincourse.app.data.cache.toDetail
import com.getmaincourse.app.data.cache.toEntity
import com.getmaincourse.app.data.cache.toJson
import com.getmaincourse.app.data.cache.toSummary
import com.getmaincourse.app.data.model.MoveRecipeRequest
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeSummary
import com.getmaincourse.app.data.model.ShoppingItemsRequest
import com.getmaincourse.app.data.network.MainCourseService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class RecipeRepository(
    private val database: MainCourseDatabase,
    private val service: MainCourseService,
    json: Json = Json,
) {
    private val dao = database.catalogDao()
    private val json = Json(json) { ignoreUnknownKeys = true }

    fun observeSummaries(userId: Long, cookbookId: Long): Flow<List<RecipeSummary>> =
        dao.observeRecipes(userId, cookbookId).map { entities ->
            entities.mapNotNull { it.toSummary(json) }
        }

    fun observeDetail(userId: Long, cookbookId: Long, recipeId: Long): Flow<RecipeDetail?> =
        dao.observeRecipe(userId, cookbookId, recipeId).map { it?.toDetail(json) }

    suspend fun refreshList(userId: Long, cookbookId: Long) {
        val response = service.recipes(cookbookId)
        dao.replaceRecipes(
            userId,
            cookbookId,
            response.mapIndexed { index, summary -> summary.toEntity(userId, cookbookId, index, json) },
        )
    }

    suspend fun refreshDetail(userId: Long, cookbookId: Long, recipeId: Long) {
        val response = service.recipe(cookbookId, recipeId)
        dao.updateDetail(userId, cookbookId, recipeId, response.toJson(json))
    }

    suspend fun move(
        userId: Long,
        sourceCookbookId: Long,
        recipeId: Long,
        targetCookbookId: Long,
    ) {
        service.moveRecipe(sourceCookbookId, recipeId, MoveRecipeRequest(targetCookbookId))
        dao.removeRecipe(userId, sourceCookbookId, recipeId)
        refreshList(userId, targetCookbookId)
    }

    suspend fun delete(userId: Long, cookbookId: Long, recipeId: Long) {
        service.deleteRecipe(cookbookId, recipeId)
        dao.removeRecipe(userId, cookbookId, recipeId)
    }

    suspend fun addIngredients(cookbookId: Long, request: ShoppingItemsRequest) {
        service.addRecipeIngredients(cookbookId, request)
    }
}
