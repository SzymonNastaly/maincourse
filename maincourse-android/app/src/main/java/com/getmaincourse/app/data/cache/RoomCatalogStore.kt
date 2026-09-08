package com.getmaincourse.app.data.cache

import androidx.room.withTransaction
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeSummary
import com.getmaincourse.app.features.search.RecipeSearchDocument
import java.time.Instant
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

    override suspend fun saveRecipeDetails(scope: RecipeScope, details: List<RecipeDetail>) {
        database.withTransaction {
            requireMembership(scope)
            details.forEach { detail ->
                val existing = dao.recipe(scope.userId, scope.cookbookId, detail.id) ?: return@forEach
                val summary = decodeSummary(existing) ?: return@withTransaction
                if (summary.importStatus != COMPLETED_IMPORT_STATUS) return@forEach
                val savedDetail = existing.detailJson?.let { encoded ->
                    runCatching { json.decodeFromString<RecipeDetail>(encoded) }.getOrNull()
                }
                if (savedDetail != null && detail.isOlderThan(savedDetail.updatedAt)) return@forEach
                dao.upsertRecipes(
                    listOf(
                        existing.copy(
                            summaryJson = json.encodeToString(
                                if (detail.isOlderThan(summary.updatedAt)) summary else summary.updatedFrom(detail),
                            ),
                            detailJson = json.encodeToString(detail),
                        ),
                    ),
                )
            }
        }
    }

    override suspend fun searchDocuments(scope: RecipeScope): List<RecipeSearchDocument> = database.withTransaction {
        val entities = dao.recipes(scope.userId, scope.cookbookId)
        val documents = mutableListOf<RecipeSearchDocument>()
        for (entity in entities) {
            val summary = decodeSummary(entity) ?: return@withTransaction emptyList()
            val detail = entity.detailJson?.let { encoded ->
                try {
                    json.decodeFromString<RecipeDetail>(encoded)
                } catch (_: SerializationException) {
                    dao.clearDetailIfInvalid(scope.userId, scope.cookbookId, entity.recipeId, encoded)
                    null
                }
            }
            documents += RecipeSearchDocument(summary, detail)
        }
        documents
    }

    override suspend fun upsertPartialRecipe(
        scope: RecipeScope,
        knownSummary: RecipeSummary,
        detail: RecipeDetail,
    ) {
        require(knownSummary.id == detail.id) { "Summary and detail must identify the same recipe" }
        require(knownSummary.importStatus == COMPLETED_IMPORT_STATUS) { "Only completed recipes can be moved" }
        database.withTransaction {
            requireMembership(scope)
            val existing = dao.recipe(scope.userId, scope.cookbookId, detail.id)
            val existingSummary = if (existing == null) {
                null
            } else {
                decodeSummary(existing) ?: return@withTransaction
            }
            val savedDetail = existing?.detailJson?.let { encoded ->
                runCatching { json.decodeFromString<RecipeDetail>(encoded) }.getOrNull()
            }
            if ((savedDetail != null && detail.isOlderThan(savedDetail.updatedAt)) ||
                (existingSummary != null && detail.isOlderThan(existingSummary.updatedAt))
            ) {
                return@withTransaction
            }
            dao.upsertRecipes(
                listOf(
                    RecipeEntity(
                        userId = scope.userId,
                        cookbookId = scope.cookbookId,
                        recipeId = detail.id,
                        listPosition = existing?.listPosition
                            ?: dao.nextRecipePosition(scope.userId, scope.cookbookId),
                        summaryJson = json.encodeToString(
                            knownSummary.updatedFrom(detail),
                        ),
                        detailJson = json.encodeToString(detail),
                    ),
                ),
            )
        }
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

    private suspend fun requireMembership(scope: RecipeScope) {
        require(dao.hasCookbook(scope.userId, scope.cookbookId)) {
            "Cookbook membership must be stored before recipe details"
        }
    }

    private suspend fun decodeSummary(entity: RecipeEntity): RecipeSummary? = try {
        json.decodeFromString<RecipeSummary>(entity.summaryJson)
    } catch (_: SerializationException) {
        dao.invalidateRecipes(entity.userId, entity.cookbookId)
        null
    }

    private fun RecipeSummary.updatedFrom(detail: RecipeDetail): RecipeSummary = copy(
        name = detail.name,
        prepTime = detail.prepTime,
        cookTime = detail.cookTime,
        favorite = detail.favorite,
        coverImageUrl = detail.coverImageUrl,
        coverImages = detail.coverImages,
        updatedAt = detail.updatedAt,
    )

    private fun RecipeDetail.isOlderThan(revision: String): Boolean {
        val detailRevision = runCatching { Instant.parse(updatedAt) }.getOrNull()
        val summaryRevision = runCatching { Instant.parse(revision) }.getOrNull()
        return if (detailRevision != null && summaryRevision != null) {
            detailRevision.isBefore(summaryRevision)
        } else {
            updatedAt < revision
        }
    }

    private companion object {
        const val COMPLETED_IMPORT_STATUS = "completed"
    }
}
