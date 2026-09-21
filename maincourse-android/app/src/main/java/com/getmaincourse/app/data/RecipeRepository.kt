package com.getmaincourse.app.data

import androidx.room.withTransaction
import com.getmaincourse.app.data.cache.MainCourseDatabase
import com.getmaincourse.app.data.cache.RecipeDetailSyncEntity
import com.getmaincourse.app.data.cache.RecipeEntity
import com.getmaincourse.app.data.cache.RecipeSearchDocumentEntity
import com.getmaincourse.app.data.cache.RecipeSearchQuery
import com.getmaincourse.app.data.cache.toDetail
import com.getmaincourse.app.data.cache.toEntity
import com.getmaincourse.app.data.cache.toJson
import com.getmaincourse.app.data.cache.toSummary
import com.getmaincourse.app.data.model.MoveRecipeRequest
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeImportResponse
import com.getmaincourse.app.data.model.RecipeContentImportRequest
import com.getmaincourse.app.data.model.RecipePageContent
import com.getmaincourse.app.data.model.RecipeSummary
import com.getmaincourse.app.data.model.RecipeTextImportRequest
import com.getmaincourse.app.data.model.RecipeUpdateRequest
import com.getmaincourse.app.data.model.RecipeUrlImportRequest
import com.getmaincourse.app.data.network.MainCourseService
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class RecipeRepository(
    private val database: MainCourseDatabase,
    private val service: MainCourseService,
    json: Json = Json,
) {
    private val dao = database.catalogDao()
    private val json = Json(json) { ignoreUnknownKeys = true }
    private val listWrites = Mutex()
    private val unsettledImportCookbooks = MutableStateFlow(emptySet<Long>())

    fun observeSummaries(userId: Long, cookbookId: Long): Flow<List<RecipeSummary>> =
        dao.observeRecipes(userId, cookbookId).map { entities ->
            entities.mapNotNull { it.toSummary(json) }
        }

    fun observeDetail(userId: Long, cookbookId: Long, recipeId: Long): Flow<RecipeDetail?> =
        dao.observeRecipe(userId, cookbookId, recipeId).map { it?.toDetail(json) }

    fun searchSummaries(
        userId: Long,
        cookbookId: Long,
        rawQuery: String,
        limit: Int = SEARCH_RESULT_LIMIT,
    ): Flow<List<RecipeSummary>> {
        val query = RecipeSearchQuery.build(rawQuery) ?: return flowOf(emptyList())
        val nameQuery = checkNotNull(RecipeSearchQuery.build(rawQuery, "name"))
        val ingredientQuery = checkNotNull(RecipeSearchQuery.build(rawQuery, "ingredients"))

        fun results() = dao.observeRecipeSearchResults(
            userId = userId,
            cookbookId = cookbookId,
            query = query,
            nameQuery = nameQuery,
            ingredientQuery = ingredientQuery,
            limit = limit.coerceAtLeast(1),
        ).map { entities -> entities.mapNotNull { it.toSummary(json) } }

        return results().catch { failure ->
            if (failure is CancellationException) throw failure
            rebuildSearchIndex(userId, cookbookId)
            emitAll(results())
        }
    }

    suspend fun rebuildSearchIndex(userId: Long, cookbookId: Long) {
        database.withTransaction {
            dao.deleteRecipeSearchDocuments(userId, cookbookId)
            val documents = dao.recipes(userId, cookbookId).mapNotNull { it.toSearchDocument() }
            if (documents.isNotEmpty()) dao.upsertRecipeSearchDocuments(documents)
        }
    }

    suspend fun refreshList(userId: Long, cookbookId: Long) = listWrites.withLock {
        val response = service.recipes(cookbookId)
        database.withTransaction {
            dao.replaceRecipes(
                userId,
                cookbookId,
                response.mapIndexed { index, summary -> summary.toEntity(userId, cookbookId, index, json) },
            )
            val documents = dao.recipes(userId, cookbookId).mapNotNull { it.toSearchDocument() }
            if (documents.isNotEmpty()) dao.upsertRecipeSearchDocuments(documents)
        }
    }

    suspend fun refreshDetail(userId: Long, cookbookId: Long, recipeId: Long) {
        val response = service.recipe(cookbookId, recipeId)
        saveRecipeDetails(userId, cookbookId, listOf(response))
    }

    suspend fun syncDetails(userId: Long, cookbookId: Long) {
        var cursor = dao.recipeDetailSyncCursor(userId, cookbookId)
        val seenCursors = cursor?.let { mutableSetOf(it) } ?: mutableSetOf()

        while (true) {
            val response = service.recipeDetails(cookbookId, cursor, DETAIL_BATCH_SIZE)
            if (response.recipes.isEmpty()) return

            val nextCursor = response.nextCursor?.takeIf(String::isNotBlank)
                ?: error("Recipe detail sync returned a non-empty page without a cursor")
            check(seenCursors.add(nextCursor)) {
                "Recipe detail sync returned a repeated cursor"
            }

            saveRecipeDetails(userId, cookbookId, response.recipes, nextCursor)
            cursor = nextCursor
        }
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

    suspend fun importContent(
        userId: Long,
        cookbookId: Long,
        content: RecipePageContent,
    ): RecipeImportResponse = importAndRefresh(userId, cookbookId) {
        service.importRecipeContent(cookbookId, RecipeContentImportRequest(content))
    }

    suspend fun importImage(
        userId: Long,
        cookbookId: Long,
        bytes: ByteArray,
        mimeType: String,
    ): RecipeImportResponse = importAndRefresh(userId, cookbookId) {
        val extension = when (mimeType.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/heic", "image/heif" -> "heic"
            else -> "jpg"
        }
        val body = bytes.toRequestBody(mimeType.toMediaType())
        val part = MultipartBody.Part.createFormData("image", "shared-recipe.$extension", body)
        service.importRecipeImage(cookbookId, part)
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
                updateSearchDocuments(
                    userId,
                    targetCookbookId,
                    listOfNotNull(dao.recipe(userId, targetCookbookId, recipeId)),
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

    suspend fun update(
        userId: Long,
        cookbookId: Long,
        recipeId: Long,
        request: RecipeUpdateRequest,
        coverImage: ByteArray? = null,
        coverImageMimeType: String? = null,
    ) = listWrites.withLock {
        val updated = service.updateRecipe(cookbookId, recipeId, request)
        saveRecipeDetails(userId, cookbookId, listOf(updated))

        if (coverImage != null && coverImageMimeType != null) {
            val extension = imageExtension(coverImageMimeType)
            val body = coverImage.toRequestBody(coverImageMimeType.toMediaType())
            val part = MultipartBody.Part.createFormData("cover_image", "cover.$extension", body)
            val withImage = service.updateRecipeCoverImage(cookbookId, recipeId, part)
            saveRecipeDetails(userId, cookbookId, listOf(withImage))
        }
    }

    suspend fun delete(userId: Long, cookbookId: Long, recipeId: Long) = listWrites.withLock {
        service.deleteRecipe(cookbookId, recipeId)
        dao.removeRecipe(userId, cookbookId, recipeId)
    }

    fun hasUnsettledImport(cookbookId: Long): Boolean = cookbookId in unsettledImportCookbooks.value

    fun markImportSettled(cookbookId: Long) {
        unsettledImportCookbooks.update { it - cookbookId }
    }

    private suspend fun importAndRefresh(
        userId: Long,
        cookbookId: Long,
        request: suspend () -> RecipeImportResponse,
    ): RecipeImportResponse {
        val response = request()
        unsettledImportCookbooks.update { it + cookbookId }
        try {
            refreshList(userId, cookbookId)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Throwable) {
            // The import is already accepted; a later refresh will reconcile the cache.
        }
        return response
    }

    private fun imageExtension(mimeType: String): String = when (mimeType.lowercase()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/heic", "image/heif" -> "heic"
        else -> "jpg"
    }

    private suspend fun saveRecipeDetails(
        userId: Long,
        cookbookId: Long,
        details: List<RecipeDetail>,
        nextCursor: String? = null,
    ) {
        database.withTransaction {
            val updates = details.mapNotNull { detail ->
                val existing = dao.recipe(userId, cookbookId, detail.id) ?: return@mapNotNull null
                val summary = existing.toSummary(json) ?: return@mapNotNull null
                if (summary.importStatus != COMPLETED_IMPORT_STATUS) return@mapNotNull null

                val cachedDetail = existing.toDetail(json)
                if (detail.isOlderThan(summary.updatedAt) ||
                    cachedDetail?.let { detail.isOlderThan(it.updatedAt) } == true
                ) {
                    return@mapNotNull null
                }

                existing.copy(
                    summaryJson = json.encodeToString(detail.toSummary(summary)),
                    detailJson = detail.toJson(json),
                )
            }
            if (updates.isNotEmpty()) dao.upsertRecipes(updates)
            updateSearchDocuments(userId, cookbookId, updates)
            if (nextCursor != null) {
                dao.upsertRecipeDetailSync(RecipeDetailSyncEntity(userId, cookbookId, nextCursor))
            }
        }
    }

    private fun RecipeDetail.isOlderThan(otherUpdatedAt: String): Boolean {
        val incoming = runCatching { Instant.parse(updatedAt) }.getOrNull() ?: return true
        val existing = runCatching { Instant.parse(otherUpdatedAt) }.getOrNull() ?: return false
        return incoming.isBefore(existing)
    }

    private suspend fun updateSearchDocuments(
        userId: Long,
        cookbookId: Long,
        recipes: List<RecipeEntity>,
    ) {
        if (recipes.isEmpty()) return
        val existing = dao.recipeSearchDocuments(userId, cookbookId).associateBy { it.recipeId }
        val documents = recipes.mapNotNull { recipe ->
            recipe.toSearchDocument(existing[recipe.recipeId]?.rowId ?: 0)
        }
        val indexedIds = documents.map(RecipeSearchDocumentEntity::recipeId).toSet()
        val excludedIds = recipes.map(RecipeEntity::recipeId) - indexedIds
        if (excludedIds.isNotEmpty()) {
            dao.deleteRecipeSearchDocuments(userId, cookbookId, excludedIds)
        }
        if (documents.isNotEmpty()) dao.upsertRecipeSearchDocuments(documents)
    }

    private fun RecipeEntity.toSearchDocument(rowId: Long = 0): RecipeSearchDocumentEntity? {
        val summary = toSummary(json) ?: return null
        if (summary.importStatus == FAILED_IMPORT_STATUS) return null
        val detail = toDetail(json)
        val ingredients = detail?.structuredIngredients
            ?.map { ingredient -> ingredient.name?.takeIf(String::isNotBlank) ?: ingredient.raw }
            ?.takeIf { it.isNotEmpty() }
            ?: detail?.ingredients.orEmpty()
        return RecipeSearchDocumentEntity(
            rowId = rowId,
            userId = userId,
            cookbookId = cookbookId,
            recipeId = recipeId,
            name = RecipeSearchQuery.normalizeIndexedText(summary.name),
            ingredients = RecipeSearchQuery.normalizeIndexedText(ingredients.joinToString("\n")),
            instructions = RecipeSearchQuery.normalizeIndexedText(
                detail?.instructions.orEmpty().joinToString("\n"),
            ),
            updatedAt = summary.updatedAt,
        )
    }

}

private const val COMPLETED_IMPORT_STATUS = "completed"
private const val FAILED_IMPORT_STATUS = "failed"
private const val DETAIL_BATCH_SIZE = 100
private const val SEARCH_RESULT_LIMIT = 50
