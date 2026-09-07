package com.getmaincourse.app.features.onboarding

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.getmaincourse.app.MainCourseTestActivity
import com.getmaincourse.app.MainCourseTestContent
import com.getmaincourse.app.R
import com.getmaincourse.app.data.model.SignUpRequest
import com.getmaincourse.app.data.model.SignInRequest
import com.getmaincourse.app.ui.theme.MainCourseTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainCourseTestActivity>()

    private val state = mutableStateOf(OnboardingState(isLoading = false))
    private val recorder = Recorder()

    @Before
    fun setUp() = show()

    @After
    fun tearDown() {
        compose.runOnIdle { MainCourseTestContent.content = {} }
    }

    @Test
    fun welcomeStartsQuestionsOrOpensExistingAccountSignIn() {
        compose.onNodeWithText(text(R.string.onboarding_get_started)).performClick()
        assertEquals(1, recorder.startCount)

        compose.onNodeWithText(text(R.string.onboarding_existing_account)).performClick()
        assertEquals(1, recorder.existingAccountCount)
    }

    @Test
    fun requiredQuestionsExposeSelectionAndNavigationSemantics() {
        update(OnboardingState(isLoading = false, step = OnboardingStep.HOUSEHOLD))
        compose.onNodeWithTag("onboarding_continue").assertIsNotEnabled()
        compose.onNodeWithText("3–4").performClick().assertIsSelected()
        assertEquals(3, recorder.household)
        update(state.value.copy(householdSize = 3))
        compose.onNodeWithTag("onboarding_continue").assertIsEnabled().performClick()

        update(OnboardingState(isLoading = false, step = OnboardingStep.SAVING))
        compose.onNodeWithTag("onboarding_continue").assertIsNotEnabled()
        compose.onNodeWithText("Screenshots").performClick().assertIsSelected()
        assertEquals("screenshots", recorder.saving)
        compose.onNodeWithText(text(R.string.skip)).performClick()
        compose.onNodeWithContentDescription(text(R.string.back)).performClick()

        assertEquals(1, recorder.advanceCount)
        assertEquals(1, recorder.skipCount)
        assertEquals(1, recorder.backCount)
    }

    @Test
    fun dietaryQuestionCanContinueWithoutASelection() {
        update(OnboardingState(isLoading = false, step = OnboardingStep.DIET))
        compose.onNodeWithTag("onboarding_continue").assertIsEnabled().performClick()
        assertEquals(1, recorder.advanceCount)
    }

    @Test
    fun embeddedAuthenticationStartsInSignupAndRetainsFieldsAcrossFailure() {
        update(OnboardingState(isLoading = false, step = OnboardingStep.AUTH))
        compose.onNodeWithText(text(R.string.auth_have_account)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("auth_name").performTextInput("Reader")
        compose.onNodeWithTag("auth_email").performTextInput("reader@example.test")
        compose.onNodeWithTag("auth_password").performTextInput("long-password")
        compose.onNodeWithTag("auth_submit").performScrollTo().performClick()
        assertEquals("Reader", recorder.signUp?.name)

        show(authError = "Email is already registered")
        compose.onNodeWithText("Email is already registered").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("auth_name").assertTextContains("Reader")
        compose.onNodeWithTag("auth_email").assertTextContains("reader@example.test")
    }

    @Test
    fun authPreparationKeepsTheFormAndDisablesBack() {
        update(OnboardingState(isLoading = false, step = OnboardingStep.AUTH))
        compose.onNodeWithTag("auth_email").performTextInput("reader@example.test")
        show(isPreparing = true)

        compose.onNodeWithTag("auth_email").assertTextContains("reader@example.test")
        compose.onNodeWithTag("auth_submit").assertIsNotEnabled()
        compose.onNodeWithContentDescription(text(R.string.back)).assertIsNotEnabled()
    }

    @Test
    fun persistenceFailureOffersRetryAndContinueWithoutBlockingTheFlow() {
        update(
            OnboardingState(
                isLoading = false,
                step = OnboardingStep.HOUSEHOLD,
                persistenceError = "Could not save onboarding progress",
                canRetryPersistence = true,
            ),
        )
        compose.onNodeWithText(text(R.string.retry)).performClick()
        compose.onNodeWithText(text(R.string.onboarding_continue_without_saving)).performClick()
        assertEquals(1, recorder.retryCount)
        assertEquals(1, recorder.continueWithoutSavingCount)
    }

    @Test
    fun actionsRemainReachableAtLargeTextInAShortWindow() {
        compose.runOnIdle {
            MainCourseTestContent.content = {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                    Box(Modifier.requiredWidth(400.dp).requiredHeight(180.dp)) {
                        MainCourseTheme { TestContent() }
                    }
                }
            }
        }
        compose.onNodeWithText(text(R.string.onboarding_existing_account)).performScrollTo().performClick()
        assertEquals(1, recorder.existingAccountCount)
    }

    private fun update(value: OnboardingState) {
        compose.runOnIdle { state.value = value }
    }

    private fun show(authError: String? = null, isPreparing: Boolean = false) {
        compose.runOnIdle {
            MainCourseTestContent.content = { MainCourseTheme { TestContent(authError, isPreparing) } }
        }
    }

    @androidx.compose.runtime.Composable
    private fun TestContent(authError: String? = null, isPreparing: Boolean = false) {
        OnboardingScreen(
            state = state.value,
            isPreparingAuthentication = isPreparing,
            authError = authError,
            onStart = { recorder.startCount++ },
            onBack = { recorder.backCount++ },
            onSkip = { recorder.skipCount++ },
            onExistingAccount = { recorder.existingAccountCount++ },
            onAdvance = { recorder.advanceCount++ },
            onHouseholdChanged = {
                recorder.household = it
                state.value = state.value.copy(householdSize = it)
            },
            onSavingChanged = {
                recorder.saving = it
                state.value = state.value.copy(saveToday = listOf(it))
            },
            onDietChanged = {
                recorder.diet = it
                state.value = state.value.copy(diet = listOf(it))
            },
            onRetryPersistence = { recorder.retryCount++ },
            onContinueWithoutSaving = { recorder.continueWithoutSavingCount++ },
            onSignIn = { recorder.signIn = it },
            onSignUp = { recorder.signUp = it },
        )
    }

    private fun text(id: Int) = compose.activity.getString(id)

    private class Recorder {
        var startCount = 0
        var backCount = 0
        var skipCount = 0
        var existingAccountCount = 0
        var advanceCount = 0
        var retryCount = 0
        var continueWithoutSavingCount = 0
        var household: Int? = null
        var saving: String? = null
        var diet: String? = null
        var signUp: SignUpRequest? = null
        var signIn: SignInRequest? = null
    }
}
