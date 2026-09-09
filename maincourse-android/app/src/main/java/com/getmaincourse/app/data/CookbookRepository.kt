package com.getmaincourse.app.data

import androidx.room.withTransaction
import com.getmaincourse.app.data.cache.MainCourseDatabase
import com.getmaincourse.app.data.cache.SelectedCookbookEntity
import com.getmaincourse.app.data.cache.toCookbook
import com.getmaincourse.app.data.cache.toEntity
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.network.MainCourseService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.Json

data class CookbookSelection(
    val cookbooks: List<Cookbook>,
    val selectedId: Long?,
)

class CookbookRepository(
    private val database: MainCourseDatabase,
    private val service: MainCourseService,
    json: Json = Json,
) {
    private val dao = database.catalogDao()
    private val json = Json(json) { ignoreUnknownKeys = true }

    fun observe(userId: Long): Flow<CookbookSelection> = combine(
        dao.observeCookbooks(userId),
        dao.observeSelectedCookbookId(userId),
    ) { entities, selectedId ->
        val cookbooks = entities.mapNotNull { it.toCookbook(json) }
        CookbookSelection(
            cookbooks = cookbooks,
            selectedId = selectedId?.takeIf { id -> cookbooks.any { it.id == id } },
        )
    }

    suspend fun refresh(userId: Long) {
        val response = service.cookbooks()
        database.withTransaction {
            val selectedId = dao.selectedCookbookId(userId)
            dao.replaceCookbooks(
                userId,
                response.mapIndexed { index, cookbook -> cookbook.toEntity(userId, index, json) },
            )
            val nextSelection = selectedId?.takeIf { selected -> response.any { it.id == selected } }
                ?: response.firstOrNull()?.id
            if (nextSelection != null && nextSelection != selectedId) {
                dao.selectCookbook(SelectedCookbookEntity(userId, nextSelection))
            }
        }
    }

    suspend fun select(userId: Long, cookbookId: Long) {
        dao.selectCookbook(SelectedCookbookEntity(userId, cookbookId))
    }
}
