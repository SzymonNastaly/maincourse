package com.getmaincourse.app.features.auth

import com.getmaincourse.app.data.model.OnboardingRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PreAuthViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun freshInstallCollectsAnswersAndMovesToEmbeddedSignup() = runTest(dispatcher) {
        var preparedId: String? = null
        var request: OnboardingRequest? = null
        val viewModel = viewModel(
            prepare = { preparedId = it },
            submit = { request = it },
        )

        assertEquals(PreAuthStep.WELCOME, viewModel.state.value.step)
        viewModel.start()
        assertEquals(PreAuthStep.HOUSEHOLD, viewModel.state.value.step)
        assertFalse(viewModel.state.value.canAdvance)

        viewModel.selectHousehold(HouseholdSize.THREE_OR_FOUR)
        viewModel.advance()
        viewModel.toggleSaveToday(SaveTodayOption.SCREENSHOTS)
        viewModel.toggleSaveToday(SaveTodayOption.COOKBOOKS)
        viewModel.advance()
        viewModel.toggleDiet(DietOption.VEGETARIAN)
        viewModel.advance()
        runCurrent()

        assertEquals(PreAuthStep.AUTH, viewModel.state.value.step)
        assertTrue(viewModel.state.value.onboarding)
        assertEquals("android-device", preparedId)
        assertEquals("android-device", request?.deviceId)
        assertEquals("3", request?.answers?.get("household_size").toString())
        assertEquals("[\"cookbooks\",\"screenshots\"]", request?.answers?.get("save_today").toString())
        assertEquals("[\"vegetarian\"]", request?.answers?.get("diet").toString())
    }

    @Test
    fun optionalDietCanBeEmptyAndNetworkFailureDoesNotBlockSignup() = runTest(dispatcher) {
        val viewModel = viewModel(submit = { error("offline") })
        viewModel.start()
        viewModel.selectHousehold(HouseholdSize.ONE)
        viewModel.advance()
        viewModel.toggleSaveToday(SaveTodayOption.DONT_SAVE)
        viewModel.advance()
        assertTrue(viewModel.state.value.canAdvance)

        viewModel.advance()
        runCurrent()

        assertEquals(PreAuthStep.AUTH, viewModel.state.value.step)
    }

    @Test
    fun skipAndSuccessfulAuthenticationCompleteFirstRun() {
        var completions = 0
        val skipped = viewModel(complete = { completions += 1 })

        skipped.skip()

        assertEquals(1, completions)
        assertEquals(PreAuthUiState(PreAuthStep.AUTH, onboarding = false), skipped.state.value)

        val authenticating = viewModel(
            hasReachedAuthentication = true,
            complete = { completions += 1 },
        )
        assertEquals(PreAuthStep.AUTH, authenticating.state.value.step)
        assertTrue(authenticating.state.value.onboarding)

        authenticating.authenticated()
        assertEquals(2, completions)
        assertFalse(authenticating.state.value.onboarding)
    }

    @Test
    fun completedFirstRunStartsAtStandaloneLogin() {
        val viewModel = viewModel(hasCompleted = true)

        assertEquals(PreAuthUiState(PreAuthStep.AUTH, onboarding = false), viewModel.state.value)
    }

    private fun viewModel(
        hasCompleted: Boolean = false,
        hasReachedAuthentication: Boolean = false,
        prepare: (String) -> Unit = {},
        complete: () -> Unit = {},
        submit: suspend (OnboardingRequest) -> Unit = {},
    ) = PreAuthViewModel(
        hasCompleted = hasCompleted,
        hasReachedAuthentication = hasReachedAuthentication,
        deviceId = { "android-device" },
        prepareForAuthentication = prepare,
        completeOnboarding = complete,
        submitOnboarding = submit,
    )
}
