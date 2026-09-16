package com.getmaincourse.app.features.recipes

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.getmaincourse.app.MainCourseTestActivity
import com.getmaincourse.app.MainCourseTestContent
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.StructuredIngredient
import com.getmaincourse.app.ui.theme.MainCourseTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IngredientReviewDefaultsTest {
    @get:Rule val compose = createAndroidComposeRule<MainCourseTestActivity>()

    @After
    fun tearDown() {
        compose.runOnIdle { MainCourseTestContent.content = {} }
    }

    @Test
    fun mixedReviewExcludesStapleAndAllowsIncludingIt() {
        showReview(listOf(ingredient(1, "onions"), ingredient(2, "salt", false)))

        compose.onNodeWithText("Common staples are excluded by default. Include anything you need.")
            .assertExists()
        compose.onNodeWithTag("review_submit").assertIsEnabled().assertTextContains("Add 1 item")

        compose.onNodeWithTag("review_item_1").performClick()

        compose.onNodeWithTag("review_submit").assertTextContains("Add 2 items")
    }

    @Test
    fun allStapleReviewDisablesAdd() {
        showReview(listOf(ingredient(1, "salt", false), ingredient(2, "water", false)))

        compose.onNodeWithTag("review_submit").assertIsNotEnabled().assertTextContains("Add 0 items")
    }

    private fun showReview(ingredients: List<StructuredIngredient>) {
        compose.runOnIdle {
            MainCourseTestContent.content = {
                MainCourseTheme {
                    IngredientReviewScreen(
                        recipe = recipe(ingredients),
                        portions = 1,
                        actionState = RecipeActionUiState.Idle,
                        onBack = {},
                        onSubmit = { _, _ -> },
                    )
                }
            }
        }
    }

    private fun ingredient(id: Long, name: String, shoppingDefaultIncluded: Boolean? = null) =
        StructuredIngredient(
            id = id,
            position = id.toInt(),
            amount = null,
            amountMax = null,
            unit = null,
            name = name,
            note = null,
            raw = name,
            shoppingDefaultIncluded = shoppingDefaultIncluded,
        )

    private fun recipe(ingredients: List<StructuredIngredient>) = RecipeDetail(
        id = 20,
        name = "Soup",
        prepTime = null,
        cookTime = null,
        servings = 1,
        favorite = false,
        ingredients = ingredients.map(StructuredIngredient::raw),
        structuredIngredients = ingredients,
        instructions = emptyList(),
        notes = null,
        sourceUrl = null,
        tags = emptyList(),
        coverImageUrl = null,
        coverImages = null,
        createdAt = "then",
        updatedAt = "now",
    )
}
