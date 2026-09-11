package com.getmaincourse.app.features.recipes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.getmaincourse.app.MainCourseTestActivity
import com.getmaincourse.app.MainCourseTestContent
import com.getmaincourse.app.ui.theme.MainCourseTheme
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecipeShareScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainCourseTestActivity>()

    @After
    fun tearDown() {
        compose.runOnIdle { MainCourseTestContent.content = {} }
    }

    @Test
    fun compactShareSheetShowsDestinationAndProgress() {
        show(RecipeShareUiState("Home", RecipeShareStatus.ReadingPage(1, "https://example.com")))

        compose.onNodeWithTag("recipe_share_sheet").assertIsDisplayed()
        compose.onNodeWithText("Saving to Home").assertIsDisplayed()
        compose.onNodeWithText("Reading this page…").assertIsDisplayed()
    }

    @Test
    fun failedShareCanRetryWithoutOpeningTheMainApp() {
        val retried = AtomicBoolean(false)
        compose.runOnIdle {
            MainCourseTestContent.content = {
                MainCourseTheme {
                    RecipeShareSheet(
                        state = RecipeShareUiState("Home", RecipeShareStatus.Failed("You're offline")),
                        onRetry = { retried.set(true) },
                        onDismiss = {},
                    )
                }
            }
        }

        compose.onNodeWithText("You're offline").assertIsDisplayed()
        compose.onNodeWithTag("share_retry").performClick()
        assertTrue(retried.get())
        compose.onNodeWithTag("share_open_app").assertDoesNotExist()
    }

    @Test
    fun acceptedShareExplainsThatTheUserCanReturn() {
        show(RecipeShareUiState("Home", RecipeShareStatus.Success))

        compose.onNodeWithTag("share_success").assertIsDisplayed()
        compose.onNodeWithText("Import started").assertIsDisplayed()
        compose.onNodeWithText("You can return to what you were doing.", substring = true).assertIsDisplayed()
    }

    private fun show(state: RecipeShareUiState) {
        compose.runOnIdle {
            MainCourseTestContent.content = {
                MainCourseTheme {
                    RecipeShareSheet(state = state, onRetry = {}, onDismiss = {})
                }
            }
        }
    }
}
