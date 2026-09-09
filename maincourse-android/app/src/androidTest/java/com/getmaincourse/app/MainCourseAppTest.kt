package com.getmaincourse.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.getmaincourse.app.data.CookbookSelection
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeSummary
import com.getmaincourse.app.data.model.SessionResponse
import com.getmaincourse.app.data.model.User
import com.getmaincourse.app.features.recipes.RecipeDetailViewModel
import com.getmaincourse.app.features.recipes.RecipesViewModel
import com.getmaincourse.app.features.session.SessionUiState
import com.getmaincourse.app.ui.theme.MainCourseTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainCourseAppTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainCourseTestActivity>()

    @After
    fun tearDown() {
        compose.runOnIdle { MainCourseTestContent.content = {} }
    }

    @Test
    fun signedOutSessionShowsEmailAuthentication() {
        show(SessionUiState.SignedOut())

        compose.onNodeWithTag("auth_form").assertIsDisplayed()
        compose.onNodeWithTag("auth_email").assertIsDisplayed()
        compose.onNodeWithTag("auth_password").assertIsDisplayed()
    }

    @Test
    fun signedInSessionNavigatesFromRecipesToDetailAndBack() {
        show(SessionUiState.SignedIn(SESSION))

        compose.onNodeWithTag("screen_Recipes").assertIsDisplayed()
        compose.onNodeWithText(SUMMARY.name).performClick()
        compose.onNodeWithTag("recipe_detail").assertIsDisplayed()
        compose.onNodeWithText(DETAIL.name).assertIsDisplayed()

        compose.onNodeWithTag("navigate_back").performClick()
        compose.onNodeWithTag("screen_Recipes").assertIsDisplayed()
    }

    @Test
    fun bottomNavigationShowsShoppingSearchAndSettingsAtEveryWidth() {
        show(SessionUiState.SignedIn(SESSION))

        compose.onNodeWithTag("navigation_bar").assertIsDisplayed()
        compose.onNodeWithTag("nav_Shopping").performClick()
        compose.onNodeWithTag("screen_Shopping").assertIsDisplayed()
        compose.onNodeWithTag("nav_Search").performClick()
        compose.onNodeWithTag("screen_Search").assertIsDisplayed()
        compose.onNodeWithTag("nav_Settings").performClick()
        compose.onNodeWithTag("screen_Settings").assertIsDisplayed()
        compose.onNodeWithTag("navigation_bar").assertIsDisplayed()
    }

    private fun show(state: SessionUiState) {
        val selection = MutableStateFlow(CookbookSelection(listOf(COOKBOOK), COOKBOOK.id))
        val recipes = MutableStateFlow(listOf(SUMMARY))
        val detail = MutableStateFlow<RecipeDetail?>(DETAIL)
        val factories = BrowsingViewModelFactories(
            recipes = {
                simpleViewModelFactory {
                    RecipesViewModel(
                        observeCookbooks = { selection },
                        observeRecipes = { recipes },
                        refreshCookbooks = {},
                        refreshRecipes = {},
                        selectCookbook = {},
                    )
                }
            },
            detail = { _, _, _ ->
                simpleViewModelFactory {
                    RecipeDetailViewModel(
                        observeDetail = { detail },
                        refreshDetail = {},
                    )
                }
            },
        )
        compose.runOnIdle {
            MainCourseTestContent.content = {
                MainCourseTheme {
                    MainCourseAppContent(state = state, factories = factories)
                }
            }
        }
        compose.waitForIdle()
    }

    private companion object {
        val USER = User(1, "Reader", "reader@example.test", true)
        val SESSION = SessionResponse("token", "2099-01-01T00:00:00Z", USER)
        val COOKBOOK = Cookbook(10, "Home", true, 1, emptyList())
        val SUMMARY = RecipeSummary(
            id = 7,
            name = "Tomato soup",
            prepTime = 5,
            cookTime = 20,
            favorite = false,
            coverImageUrl = null,
            coverImages = null,
            importStatus = "completed",
            errorMessage = null,
            updatedAt = "2026-09-09T00:00:00Z",
        )
        val DETAIL = RecipeDetail(
            id = 7,
            name = "Tomato soup",
            prepTime = 5,
            cookTime = 20,
            servings = 2,
            favorite = false,
            ingredients = listOf("Tomatoes"),
            structuredIngredients = emptyList(),
            instructions = listOf("Cook"),
            notes = "Serve warm",
            sourceUrl = null,
            tags = emptyList(),
            coverImageUrl = null,
            coverImages = null,
            createdAt = "2026-09-09T00:00:00Z",
            updatedAt = "2026-09-09T00:00:00Z",
        )
    }
}
