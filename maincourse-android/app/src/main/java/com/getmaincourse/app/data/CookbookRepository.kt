package com.getmaincourse.app.data

import androidx.room.withTransaction
import com.getmaincourse.app.data.cache.MainCourseDatabase
import com.getmaincourse.app.data.cache.SelectedCookbookEntity
import com.getmaincourse.app.data.cache.toCookbook
import com.getmaincourse.app.data.cache.toEntity
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.CookbookInvitation
import com.getmaincourse.app.data.model.CookbookInvitationAcceptance
import com.getmaincourse.app.data.model.CookbookInvitationPreview
import com.getmaincourse.app.data.model.CreateCookbookRequest
import com.getmaincourse.app.data.network.MainCourseService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.CancellationException
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

    suspend fun createShared(userId: Long, name: String, movePersonalRecipes: Boolean): Cookbook {
        val created = service.createCookbook(CreateCookbookRequest(name, movePersonalRecipes))
        database.withTransaction {
            val existing = dao.cookbooks(userId)
            val position = existing.maxOfOrNull { it.listPosition }?.plus(1) ?: 0
            val updatedPersonal = if (movePersonalRecipes) {
                existing.firstNotNullOfOrNull { entity ->
                    entity.toCookbook(json)?.takeIf(Cookbook::personal)?.copy(recipeCount = 0)
                        ?.toEntity(userId, entity.listPosition, json)
                }
            } else {
                null
            }
            dao.upsertCookbooks(listOfNotNull(updatedPersonal, created.toEntity(userId, position, json)))
            dao.selectCookbook(SelectedCookbookEntity(userId, created.id))
        }
        return created
    }

    suspend fun deleteShared(userId: Long, cookbookId: Long) {
        service.deleteCookbook(cookbookId)
        removeCookbookFromCache(userId, cookbookId)
    }

    suspend fun leaveShared(userId: Long, cookbookId: Long) {
        service.leaveCookbook(cookbookId)
        removeCookbookFromCache(userId, cookbookId)
    }

    suspend fun createInvitation(cookbookId: Long): CookbookInvitation =
        service.createCookbookInvitation(cookbookId)

    suspend fun invitation(token: String): CookbookInvitationPreview =
        service.cookbookInvitation(token)

    suspend fun acceptInvitation(userId: Long, token: String): CookbookInvitationAcceptance {
        val acceptance = service.acceptCookbookInvitation(token)
        try {
            database.withTransaction {
                val existing = dao.cookbooks(userId)
                if (existing.none { it.cookbookId == acceptance.cookbookId }) {
                    val position = existing.maxOfOrNull { it.listPosition }?.plus(1) ?: 0
                    val joined = Cookbook(
                        id = acceptance.cookbookId,
                        name = acceptance.cookbookName,
                        personal = false,
                        recipeCount = 0,
                        members = emptyList(),
                    )
                    dao.upsertCookbooks(listOf(joined.toEntity(userId, position, json)))
                }
                dao.selectCookbook(SelectedCookbookEntity(userId, acceptance.cookbookId))
            }
            refresh(userId)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Throwable) {
            // Acceptance is already acknowledged. A later cookbook refresh will reconcile the placeholder.
        }
        return acceptance
    }

    suspend fun rejectInvitation(token: String) {
        service.rejectCookbookInvitation(token)
    }

    private suspend fun removeCookbookFromCache(userId: Long, cookbookId: Long) {
        database.withTransaction {
            dao.deleteCookbook(userId, cookbookId)
            val remaining = dao.cookbooks(userId)
            val next = remaining.firstOrNull { it.toCookbook(json)?.personal == true }
                ?: remaining.firstOrNull()
            next?.let { dao.selectCookbook(SelectedCookbookEntity(userId, it.cookbookId)) }
        }
    }
}
