package com.getmaincourse.app.features.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreAuthViewModelTest {
    @Test
    fun demoMustFinishBeforeRecapAndSignupBackDoesNotReplayIt() {
        var completedDemo = false
        var prepared = false
        val model = model(recordDemo = { completedDemo = true }, prepare = { prepared = true })
        model.start()
        model.advance()
        assertEquals(PreAuthStep.DEMO, model.state.value.step)
        model.finishDemo()
        model.advance()
        assertTrue(completedDemo)
        assertEquals(PreAuthStep.FEATURES, model.state.value.step)
        model.signUp()
        assertTrue(prepared)
        assertEquals(PreAuthStep.AUTH, model.state.value.step)
        assertFalse(model.state.value.login)
        model.goBack()
        assertEquals(PreAuthStep.FEATURES, model.state.value.step)
        model.goBack()
        assertTrue(model.state.value.demoCompleted)
    }

    @Test
    fun existingAccountCanGoDirectlyToLoginAndBackToWelcome() {
        val model = model()
        model.logIn()
        assertTrue(model.state.value.login)
        assertEquals(PreAuthStep.AUTH, model.state.value.step)
        model.goBack()
        assertEquals(PreAuthStep.WELCOME, model.state.value.step)
    }

    @Test
    fun restoredSignupRemembersDemoAndAuthenticationCompletesFirstRun() {
        var completed = false
        val model = model(reachedAuth = true, demoCompleted = true, complete = { completed = true })
        model.goBack()
        assertEquals(PreAuthStep.FEATURES, model.state.value.step)
        model.authenticated()
        assertTrue(completed)
        assertFalse(model.state.value.onboarding)
        assertEquals(PreAuthStep.AUTH, model.state.value.step)
        model.goBack()
        assertEquals(PreAuthStep.AUTH, model.state.value.step)
    }

    @Test
    fun existingInstallationDoesNotRepeatOnboarding() {
        val model = model(completed = true)
        assertFalse(model.state.value.onboarding)
        assertEquals(PreAuthStep.AUTH, model.state.value.step)
    }

    private fun model(
        completed: Boolean = false,
        reachedAuth: Boolean = false,
        demoCompleted: Boolean = false,
        prepare: () -> Unit = {},
        complete: () -> Unit = {},
        recordDemo: () -> Unit = {},
    ) = PreAuthViewModel(completed, reachedAuth, demoCompleted, { prepare() }, complete, recordDemo)
}
