package com.getmaincourse.app.data

import androidx.room.withTransaction
import com.getmaincourse.app.data.cache.MainCourseDatabase
import com.getmaincourse.app.data.cache.RecipeEntity
import com.getmaincourse.app.data.cache.toDetail
import com.getmaincourse.app.data.cache.toEntity
import com.getmaincourse.app.data.cache.toJson
import com.getmaincourse.app.data.cache.toSummary
import com.getmaincourse.app.data.model.MoveRecipeRequest
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeImportResponse
import com.getmaincourse.app.data.model.RecipeSummary
import com.getmaincourse.app.data.model.RecipeTextImportRequest
import com.getmaincourse.app.data.model.RecipeUrlImportRequest
import com.getmaincourse.app.data.network.MainCourseService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

class RecipeRepository(
    private val database: MainCourseDatabase,
    private val service: MainCourseService,
    json: Json = Json,
) {
    private val dao = database.catalogDao()
    private val json = Json(json) { ignoreUnknownKeys = true }
    private val listWrites = Mutex()

    fun observeSummaries(userId: Long, cookbookId: Long): Flow<List<RecipeSummary>> =
        dao.observeRecipes(userId, cookbookId).map { entities ->
            entities.mapNotNull { it.toSummary(json) }
        }

    fun observeDetail(userId: Long, cookbookId: Long, recipeId: Long): Flow<RecipeDetail?> =
        dao.observeRecipe(userId, cookbookId, recipeId).map { it?.toDetail(json) }

    suspend fun refreshList(userId: Long, cookbookId: Long) = listWrites.withLock {
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

    suspend fun importUrl(
        userId: Long,
        cookbookId: Long,
        url: String,
    ): RecipeImportResponse = importAndRefresh(userId, cookbookId) {
        service.importRecipe(cookbookId, RecipeUrlImportRequest(url))
    }

    suspend fun importText(
        userId: Long,
        cookbookId: Long,
        text: String,
    ): RecipeImportResponse = importAndRefresh(userId, cookbookId) {
        service.importRecipeText(cookbookId, RecipeTextImportRequest(text))
    }

    suspend fun move(
        userId: Long,
        sourceCookbookId: Long,
        recipeId: Long,
        targetCookbookId: Long,
    ) {
        listWrites.withLock {
            val moved = service.moveRecipe(sourceCookbookId, recipeId, MoveRecipeRequest(targetCookbookId))
            database.withTransaction {
                val source = dao.recipe(userId, sourceCookbookId, recipeId)
                val target = dao.recipe(userId, targetCookbookId, recipeId)
                val previousSummary = source?.toSummary(json) ?: target?.toSummary(json)
                dao.upsertRecipes(
                    listOf(
                        RecipeEntity(
                            userId = userId,
                            cookbookId = targetCookbookId,
                            recipeId = recipeId,
                            listPosition = target?.listPosition
                                ?: dao.nextRecipePosition(userId, targetCookbookId),
                            summaryJson = json.encodeToString(moved.toSummary(previousSummary)),
                            detailJson = moved.toJson(json),
                        ),
                    ),
                )
                dao.removeRecipe(userId, sourceCookbookId, recipeId)
            }
        }

        try {
            refreshList(userId, targetCookbookId)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Throwable) {
            // The PATCH is already confirmed and locally reconciled.
        }
    }

    suspend fun delete(userId: Long, cookbookId: Long, recipeId: Long) = listWrites.withLock {
        service.deleteRecipe(cookbookId, recipeId)
        dao.removeRecipe(userId, cookbookId, recipeId)
    }

    private suspend fun importAndRefresh(
        userId: Long,
        cookbookId: Long,
        request: suspend () -> RecipeImportResponse,
    ): RecipeImportResponse {
        val response = request()
        try {
            refreshList(userId, cookbookId)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Throwable) {
            // The import is already accepted; a later refresh will reconcile the cache.
        }
        return response
    }

}
