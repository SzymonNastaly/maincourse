package com.getmaincourse.app.features.recipes
import com.getmaincourse.app.R
import com.getmaincourse.app.ui.UiMessage

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.getmaincourse.app.MainCourseTestActivity
import com.getmaincourse.app.MainCourseTestContent
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.CoverImages
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.StructuredIngredient
import com.getmaincourse.app.ui.theme.MainCourseTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecipeWorkflowScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainCourseTestActivity>()

    @Before fun setUp() = Unit
    @After fun tearDown() { compose.runOnIdle { MainCourseTestContent.content = {} } }

    @Test
    fun ingredientReviewStartsIncludedAndKeepsStablePayloadForSubmission() {
        var submitted: List<ShoppingItemInput>? = null
        compose.runOnIdle {
            MainCourseTestContent.content = {
                MainCourseTheme {
                    IngredientReviewScreen(
                        recipe = DETAIL,
                        portions = 4,
                        actionState = RecipeActionUiState.Idle,
                        onBack = {},
                        onSubmit = { items, _ -> submitted = items },
                    )
                }
            }
        }
        compose.onNodeWithTag("review_item_0").performClick()
        compose.onNodeWithTag("review_submit").assertIsEnabled().performClick()
        assertEquals(1, submitted?.size)
        assertEquals("salt", submitted?.single()?.name)
    }

    @Test
    fun ingredientReviewShowsActionFailureAndAllowsRetry() {
        val state = mutableStateOf<RecipeActionUiState>(RecipeActionUiState.Failed(UiMessage.Resource(R.string.error_connection_timeout)))
        var submissions = 0
        compose.runOnIdle {
            MainCourseTestContent.content = {
                MainCourseTheme {
                    IngredientReviewScreen(
                        recipe = DETAIL,
                        portions = 4,
                        actionState = state.value,
                        onBack = {},
                        onSubmit = { _, _ -> submissions++ },
                    )
                }
            }
        }

        compose.onNodeWithTag("review_error").assertTextContains("Connection timed out. Please try again.")
        compose.onNodeWithTag("review_submit").performClick()
        assertEquals(1, submissions)
    }

    @Test
    fun oldShoppingListRequiresAChoiceBeforeIngredientsAreSubmitted() {
        var submission: Pair<List<ShoppingItemInput>, Boolean>? = null
        compose.runOnIdle {
            MainCourseTestContent.content = {
                MainCourseTheme {
                    IngredientReviewScreen(
                        recipe = DETAIL,
                        portions = 4,
                        actionState = RecipeActionUiState.Idle,
                        shoppingListNeedsReview = { true },
                        onBack = {},
                        onSubmit = { items, clearExisting -> submission = items to clearExisting },
                    )
                }
            }
        }

        compose.onNodeWithTag("review_submit").performClick()

        compose.onNodeWithText("Start a fresh shopping list?").assertIsDisplayed()
        assertEquals(null, submission)

        compose.onNodeWithTag("review_clear_and_add").performClick()
        compose.waitUntil(5_000) { submission != null }
        assertEquals(true, submission?.second)
    }

    @Test
    fun ingredientReviewSuccessWaitsForAcknowledgementBeforeReturning() {
        val action = mutableStateOf<RecipeActionUiState>(RecipeActionUiState.Idle)
        var submissions = 0
        var backs = 0
        compose.runOnIdle {
            MainCourseTestContent.content = {
                MainCourseTheme {
                    IngredientReviewScreen(
                        recipe = DETAIL,
                        portions = 4,
                        actionState = action.value,
                        onBack = { backs++ },
                        onSubmit = { _, _ ->
                            submissions++
                            action.value = RecipeActionUiState.Succeeded(UiMessage.Resource(R.string.ingredients_added))
                        },
                    )
                }
            }
        }

        compose.onNodeWithTag("review_submit").performClick()
        compose.onNodeWithText("Ingredients added").assertIsDisplayed()
        assertEquals(0, backs)
        compose.onNodeWithTag("review_success_confirm").performClick()
        compose.waitUntil(5_000) { backs == 1 }
        assertEquals(1, submissions)
    }

    @Test
    fun ingredientReviewKeepsCheckboxesAndStableRowsAcrossRecreationAndRetry() {
        val action = mutableStateOf<RecipeActionUiState>(RecipeActionUiState.Idle)
        val submissions = mutableListOf<List<ShoppingItemInput>>()
        compose.runOnIdle {
            MainCourseTestContent.content = {
                MainCourseTheme {
                    IngredientReviewScreen(
                        recipe = DETAIL,
                        portions = 4,
                        actionState = action.value,
                        onBack = {},
                        onSubmit = { items, _ ->
                            submissions += items
                            action.value = RecipeActionUiState.Failed(UiMessage.Resource(R.string.error_connection_timeout))
                        },
                    )
                }
            }
        }
        compose.onNodeWithTag("review_item_0").performClick()

        compose.onNodeWithTag("review_submit").performClick()
        compose.waitUntil(5_000) { submissions.size == 1 }

        compose.activityRule.scenario.recreate()

        compose.onNodeWithTag("review_item_0").assertIsDisplayed()
        compose.onNodeWithTag("review_submit").performClick()
        compose.waitUntil(5_000) { submissions.size == 2 }
        assertEquals(submissions.first(), submissions.last())
        assertEquals(submissions.first().map { it.clientId }, submissions.last().map { it.clientId })
    }

    @Test
    fun recipeMoveRequiresChoosingAnotherCookbook() {
        var movedTo: Long? = null
        showEditor(
            cookbooks = listOf(cookbook(1, "Home"), cookbook(2, "Shared")),
            onMove = { movedTo = it },
        )

        compose.onNodeWithTag("recipe_move").performClick()
        compose.onNodeWithTag("move_target_2").performClick()

        assertEquals(2L, movedTo)
    }

    @Test
    fun recipeDeleteRequiresConfirmation() {
        var deletes = 0
        showEditor(onDelete = { deletes++ })

        compose.onNodeWithTag("recipe_delete").performClick()
        assertEquals(0, deletes)
        compose.onNodeWithTag("confirm_delete").performClick()
        assertEquals(1, deletes)
    }

    private fun showEditor(
        cookbooks: List<Cookbook> = listOf(cookbook(1, "Home")),
        onMove: (Long) -> Unit = {},
        onDelete: () -> Unit = {},
    ) {
        compose.runOnIdle {
            MainCourseTestContent.content = {
                MainCourseTheme {
                    RecipeEditScreen(
                        state = RecipeEditUiState(loaded = true, name = DETAIL.name),
                        imageLoader = null,
                        resolveImage = { it },
                        onBack = {},
                        onSave = {},
                        onNameChange = {},
                        onPrepTimeChange = {},
                        onCookTimeChange = {},
                        onServingsChange = {},
                        onIngredientChange = { _, _ -> },
                        onAddIngredient = {},
                        onRemoveIngredient = {},
                        onMoveIngredient = { _, _ -> },
                        onInstructionChange = { _, _ -> },
                        onAddInstruction = {},
                        onRemoveInstruction = {},
                        onMoveInstruction = { _, _ -> },
                        onNotesChange = {},
                        onSourceUrlChange = {},
                        onImageSelected = {},
                        onImageError = {},
                        onClearError = {},
                        cookbooks = cookbooks,
                        cookbookId = 1,
                        onMove = onMove,
                        onDelete = onDelete,
                    )
                }
            }
        }
    }

    @Test
    fun sourceOpenFailureShowsSafeFeedbackInsteadOfCrashing() {
        compose.runOnIdle {
            MainCourseTestContent.content = {
                MainCourseTheme {
                    CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                        override fun openUri(uri: String) = error("no handler")
                    }) {
                        RecipeDetailScreen(
                            state = RecipeDetailUiState(
                                recipe = DETAIL.copy(sourceUrl = "https://www.example.test/recipe"),
                                loading = false,
                            ),
                            imageLoader = null,
                            resolveImage = { it },
                            onRefresh = {},
                        )
                    }
                }
            }
        }

        compose.onNodeWithText("example.test").assertIsDisplayed()
        compose.onNodeWithText("https://www.example.test/recipe").assertDoesNotExist()
        compose.onNodeWithTag("recipe_source").performClick()
        compose.onNodeWithTag("source_open_error").assertIsDisplayed()
    }

    private companion object {
        fun cookbook(id: Long, name: String) = Cookbook(id, name, id == 1L, 1, emptyList())

        val DETAIL = RecipeDetail(
            20, "Onion soup", 10, 20, 4, false,
            listOf("2 onions", "salt"),
            listOf(
                StructuredIngredient(1, 0, "2", null, null, "onions", null, "2 onions"),
                StructuredIngredient(2, 1, null, null, null, null, null, "salt"),
            ),
            listOf("Chop", "Cook"), null, null, emptyList(), null, null, "then", "now",
        )
    }
}
