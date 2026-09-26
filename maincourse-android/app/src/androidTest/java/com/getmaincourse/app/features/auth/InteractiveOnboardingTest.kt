package com.getmaincourse.app.features.auth

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.getmaincourse.app.MainCourseTestActivity
import com.getmaincourse.app.MainCourseTestContent
import com.getmaincourse.app.ui.theme.MainCourseTheme
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InteractiveOnboardingTest {
    @get:Rule val compose = createAndroidComposeRule<MainCourseTestActivity>()

    @After fun tearDown() { compose.runOnIdle { MainCourseTestContent.content = {} } }

    @Test
    fun sharingExampleScalesIngredientsAndKeepLeadsToSignup() {
        var keep = false
        show(onKeep = { keep = true })
        compose.onNodeWithTag("onboarding_start").performClick()
        compose.onNodeWithTag("demo_share").performScrollTo().performClick()
        compose.onNodeWithTag("demo_share_to").performScrollTo().performClick()
        compose.onNodeWithTag("demo_maincourse").performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("demo_recipe").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("demo_increment").performScrollTo().performClick()
        compose.onNodeWithTag("demo_portions").assertTextEquals("3")
        compose.onNodeWithText("240 g orzo").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("demo_continue").performClick()
        compose.onNodeWithText("And once it’s in your cookbook…").assertIsDisplayed()
        assertFalse(keep)
        compose.onNodeWithTag("onboarding_keep").performClick()
        assertTrue(keep)
        compose.onNodeWithText("Create your account").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("And once it’s in your cookbook…").assertIsDisplayed()
    }

    @Test
    fun largeTextKeepsWelcomeActionsAndOptionalSignupAccessible() {
        var skipped = false
        show(largeText = true, completedDemo = true, onWithoutRecipe = { skipped = true })
        compose.onNodeWithTag("onboarding_start").assertIsDisplayed().performClick()
        compose.onNodeWithTag("demo_continue").assertIsDisplayed().performClick()
        compose.onNodeWithTag("onboarding_without_recipe").assertIsDisplayed().performClick()
        assertTrue(skipped)
        compose.onNodeWithTag("auth_name").assertExists()
    }

    @Test
    fun welcomeLoginBypassesDemo() {
        show()
        compose.onNodeWithTag("onboarding_login").performClick()
        compose.onNodeWithTag("auth_form").assertIsDisplayed()
        compose.onNodeWithTag("auth_name").assertDoesNotExist()
    }

    private fun show(
        largeText: Boolean = false,
        completedDemo: Boolean = false,
        onKeep: () -> Unit = {},
        onWithoutRecipe: () -> Unit = {},
    ) {
        compose.runOnIdle {
            val model = PreAuthViewModel(false, false, completedDemo, {}, {}, {})
            MainCourseTestContent.content = {
                MainCourseTheme {
                    val density = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density, if (largeText) 2f else 1f)) {
                        val state by model.state.collectAsState()
                        PreAuthScreen(
                            state = state, busy = false, error = null,
                            onStart = model::start, onDemoCompleted = model::finishDemo,
                            onAdvance = model::advance, onBack = model::goBack, onLogIn = model::logIn,
                            onKeep = { onKeep(); model.signUp() },
                            onContinueWithoutRecipe = { onWithoutRecipe(); model.signUp() },
                            onSignIn = { _, _ -> }, onSignUp = { _, _, _, _ -> },
                        )
                    }
                }
            }
        }
    }
}
