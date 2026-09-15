package com.getmaincourse.app.data

import com.getmaincourse.app.data.cache.MainCourseDatabase
import com.getmaincourse.app.data.cache.toEntity
import com.getmaincourse.app.data.cache.toShoppingItem
import com.getmaincourse.app.data.model.ShoppingItem
import com.getmaincourse.app.data.model.ShoppingItemRequest
import com.getmaincourse.app.data.model.ShoppingItemsRequest
import com.getmaincourse.app.data.model.ShoppingItemUpdateRequest
import com.getmaincourse.app.data.network.MainCourseService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

class ShoppingListRepository(
    database: MainCourseDatabase,
    private val service: MainCourseService,
    json: Json = Json,
) {
    private val dao = database.catalogDao()
    private val json = Json(json) { ignoreUnknownKeys = true }
    private val writes = Mutex()

    fun observeItems(userId: Long, cookbookId: Long): Flow<List<ShoppingItem>> =
        dao.observeShoppingItems(userId, cookbookId).map { entities ->
            entities.mapNotNull { it.toShoppingItem(json) }
        }

    suspend fun refresh(userId: Long, cookbookId: Long) = writes.withLock {
        val response = service.shoppingListItems(cookbookId)
        dao.replaceShoppingItems(
            userId,
            cookbookId,
            response.map { it.toEntity(userId, cookbookId, json) },
        )
        response
    }

    suspend fun create(
        userId: Long,
        cookbookId: Long,
        items: List<ShoppingItemRequest>,
        clearExisting: Boolean = false,
    ) = writes.withLock {
        val response = service.createShoppingItems(
            cookbookId,
            ShoppingItemsRequest(items, clearExisting = clearExisting),
        )
        val entities = response.map { it.toEntity(userId, cookbookId, json) }
        if (clearExisting) {
            dao.replaceShoppingItems(userId, cookbookId, entities)
        } else {
            dao.upsertShoppingItems(entities)
        }
    }

    suspend fun setChecked(
        userId: Long,
        cookbookId: Long,
        itemId: Long,
        checked: Boolean,
    ) = writes.withLock {
        val response = service.updateShoppingItem(
            cookbookId,
            itemId,
            ShoppingItemUpdateRequest(checked),
        )
        dao.upsertShoppingItems(listOf(response.toEntity(userId, cookbookId, json)))
    }

    suspend fun delete(userId: Long, cookbookId: Long, itemId: Long) = writes.withLock {
        service.deleteShoppingItem(cookbookId, itemId)
        dao.deleteShoppingItem(userId, cookbookId, itemId)
    }

    suspend fun clear(userId: Long, cookbookId: Long) = writes.withLock {
        service.clearShoppingItems(cookbookId)
        dao.deleteShoppingItems(userId, cookbookId)
    }
}
