package com.getmaincourse.app.features.cookbooks

import com.getmaincourse.app.data.CookbookSelection
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.CookbookInvitationAcceptance
import com.getmaincourse.app.data.model.CookbookInvitationPreview
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InvitationViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun loadsAndAcceptsAvailableInvitation() = runTest(dispatcher) {
        var acceptedToken: String? = null
        val viewModel = viewModel(
            accept = { _, token ->
                acceptedToken = token
                CookbookInvitationAcceptance(11, "Family")
            },
        )
        advanceUntilIdle()

        viewModel.accept()
        advanceUntilIdle()

        assertEquals("invite-token", acceptedToken)
        assertEquals("Family", viewModel.state.value.acceptance?.cookbookName)
        assertFalse(viewModel.state.value.accepting)
    }

    @Test
    fun existingSharedCookbookPreventsAcceptance() = runTest(dispatcher) {
        var acceptCalls = 0
        val viewModel = viewModel(
            cookbooks = listOf(Cookbook(11, "Other", false, 0, emptyList())),
            accept = { _, _ -> acceptCalls += 1; CookbookInvitationAcceptance(12, "Family") },
        )
        advanceUntilIdle()

        viewModel.accept()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.hasSharedCookbook)
        assertEquals(0, acceptCalls)
    }

    @Test
    fun expiredInvitationIsUnavailable() = runTest(dispatcher) {
        val viewModel = viewModel(
            preview = PREVIEW.copy(expiresAt = "2026-09-10T00:00:00Z"),
        )
        advanceUntilIdle()

        assertEquals("This invitation has expired.", viewModel.state.value.error)
    }

    private fun viewModel(
        cookbooks: List<Cookbook> = emptyList(),
        preview: CookbookInvitationPreview = PREVIEW,
        accept: suspend (Long, String) -> CookbookInvitationAcceptance = { _, _ ->
            CookbookInvitationAcceptance(11, "Family")
        },
    ) = InvitationViewModel(
        userId = 7,
        token = "invite-token",
        observeCookbooks = { MutableStateFlow(CookbookSelection(cookbooks, cookbooks.firstOrNull()?.id)) },
        loadInvitation = { preview },
        acceptInvitation = accept,
        rejectInvitation = {},
        clock = Clock.fixed(Instant.parse("2026-09-11T12:00:00Z"), ZoneOffset.UTC),
    )

    private companion object {
        val PREVIEW = CookbookInvitationPreview(
            cookbookName = "Family",
            inviterEmail = "owner@example.test",
            expiresAt = "2026-09-18T12:00:00Z",
            status = "pending",
        )
    }
}
