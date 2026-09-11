package com.getmaincourse.app.features.cookbooks

import com.getmaincourse.app.data.CookbookSelection
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.CookbookInvitation
import com.getmaincourse.app.data.model.CookbookMember
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CookbookManagementViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun createsSharedCookbookWithNormalizedDraft() = runTest(dispatcher) {
        val selection = MutableStateFlow(CookbookSelection(listOf(PERSONAL), PERSONAL.id))
        var request: Triple<Long, String, Boolean>? = null
        val viewModel = viewModel(
            selection = selection,
            create = { userId, name, move ->
                request = Triple(userId, name, move)
                SHARED
            },
        )
        advanceUntilIdle()

        viewModel.create("  Family  ", true)
        advanceUntilIdle()

        assertEquals(Triple(7L, "Family", true), request)
        assertFalse(viewModel.state.value.working)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun invitationAndLeaveUseTheSharedCookbook() = runTest(dispatcher) {
        val selection = MutableStateFlow(CookbookSelection(listOf(PERSONAL, SHARED), SHARED.id))
        var invitedId: Long? = null
        var leftId: Long? = null
        val invitation = CookbookInvitation(1, "token", "https://example.test/invite/token", "2099-01-01T00:00:00Z")
        val viewModel = viewModel(
            selection = selection,
            invite = { cookbookId -> invitedId = cookbookId; invitation },
            leave = { _, cookbookId -> leftId = cookbookId },
        )
        advanceUntilIdle()

        viewModel.generateInvitation()
        advanceUntilIdle()
        viewModel.leave()
        advanceUntilIdle()

        assertEquals(SHARED.id, invitedId)
        assertEquals(invitation, viewModel.state.value.invitation)
        assertEquals(SHARED.id, leftId)
    }

    @Test
    fun mutationFailureKeepsCachedCookbooksAndShowsOfflineState() = runTest(dispatcher) {
        val selection = MutableStateFlow(CookbookSelection(listOf(PERSONAL, SHARED), SHARED.id))
        val viewModel = viewModel(
            selection = selection,
            delete = { _, _ -> throw IOException("offline") },
        )
        advanceUntilIdle()

        viewModel.delete()
        advanceUntilIdle()

        assertEquals(listOf(PERSONAL, SHARED), viewModel.state.value.cookbooks)
        assertEquals("You're offline", viewModel.state.value.error)
    }

    private fun viewModel(
        selection: MutableStateFlow<CookbookSelection>,
        create: suspend (Long, String, Boolean) -> Cookbook = { _, _, _ -> SHARED },
        delete: suspend (Long, Long) -> Unit = { _, _ -> },
        leave: suspend (Long, Long) -> Unit = { _, _ -> },
        invite: suspend (Long) -> CookbookInvitation = { error("Unexpected invite") },
    ) = CookbookManagementViewModel(
        userId = 7,
        observeCookbooks = { selection },
        refreshCookbooks = {},
        createSharedCookbook = create,
        deleteSharedCookbook = delete,
        leaveSharedCookbook = leave,
        createCookbookInvitation = invite,
    )

    private companion object {
        val PERSONAL = Cookbook(10, "Mine", true, 2, emptyList())
        val SHARED = Cookbook(
            11,
            "Family",
            false,
            3,
            listOf(CookbookMember(7, "owner@example.test", "owner")),
        )
    }
}
