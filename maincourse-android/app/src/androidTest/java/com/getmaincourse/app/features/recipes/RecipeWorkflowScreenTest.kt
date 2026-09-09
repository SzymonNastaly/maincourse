package com.getmaincourse.app.features.recipes

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.getmaincourse.app.MainCourseTestActivity
import com.getmaincourse.app.MainCourseTestContent
import com.getmaincourse.app.data.cache.RecipeScope
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.CoverImages
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeSummary
import com.getmaincourse.app.data.model.StructuredIngredient
import com.getmaincourse.app.features.search.RecipeSearchScreen
import com.getmaincourse.app.features.search.RecipeSearchState
import com.getmaincourse.app.ui.theme.MainCourseTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun searchShowsPromptThenScopedResultsAndOfflinePartialFeedback() {
        val scope = RecipeScope(7, 1)
        val state = mutableStateOf(RecipeSearchState(scope = scope))
        var query = ""
        var opened: Long? = null
        compose.runOnIdle {
            MainCourseTestContent.content = {
                MainCourseTheme {
                    RecipeSearchScreen(
                        scope = scope,
                        state = state.value,
                        imageLoader = null,
                        resolveImage = { it },
                        onQueryChange = { query = it },
                        onOpenRecipe = { opened = it },
                    )
                }
            }
        }
        compose.onNodeWithTag("search_prompt").assertIsDisplayed()
        compose.onNodeWithTag("search_query").performTextInput("onion")
        assertEquals("onion", query)

        compose.runOnIdle {
            state.value = RecipeSearchState(
                scope = scope,
                query = "onion",
                results = listOf(SUMMARY),
                hydrationStatus = com.getmaincourse.app.features.search.SearchHydrationStatus.INCOMPLETE,
                message = "Search may be incomplete offline",
            )
        }
        compose.onNodeWithText("Search may be incomplete offline").assertIsDisplayed()
        compose.onNodeWithText(SUMMARY.name).performClick()
        assertEquals(SUMMARY.id, opened)

        compose.runOnIdle { state.value = RecipeSearchState(scope = RecipeScope(8, 1), query = "onion", results = listOf(SUMMARY)) }
        compose.onNodeWithText(SUMMARY.name).assertDoesNotExist()
    }

    @Test
    fun searchDoesNotShowNoResultsWhileLoadingOrAfterACacheReadFailure() {
        val scope = RecipeScope(7, 1)
        val state = mutableStateOf(RecipeSearchState(scope = scope, query = "onion", isSearching = true))
        compose.runOnIdle {
            MainCourseTestContent.content = {
                MainCourseTheme {
                    RecipeSearchScreen(scope, state.value, null, { it }, {}, {})
                }
            }
        }

        compose.onNodeWithTag("search_loading").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(com.getmaincourse.app.R.string.search_empty)).assertDoesNotExist()

        compose.runOnIdle {
            state.value = state.value.copy(isSearching = false, searchError = "Search is unavailable. Try again.")
        }
        compose.onNodeWithTag("search_error").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(com.getmaincourse.app.R.string.search_empty)).assertDoesNotExist()
    }

    @Test
    fun editorValidatesFieldsAndPreservesRowOrderInSubmittedDraft() {
        var submitted: RecipeEditDraft? = null
        compose.runOnIdle {
            MainCourseTestContent.content = {
                MainCourseTheme {
                    RecipeEditScreen(
                        scope = RecipeScope(7, 1),
                        recipe = DETAIL,
                        actionState = RecipeActionState(),
                        imageState = RecipeImagePreparationState(),
                        onSave = { draft, _ -> submitted = draft },
                    )
                }
            }
        }
        compose.onNodeWithTag("editor_name").performClick()
        compose.onNodeWithTag("editor_name").performTextInput(" updated")
        compose.onNodeWithTag("ingredient_move_down_0").performClick()
        compose.onNodeWithTag("editor_list").performScrollToNode(hasTestTag("editor_save"))
        compose.onNodeWithTag("editor_save").assertIsEnabled().performClick()

        assertEquals(listOf("salt", "2 onions"), submitted?.values?.ingredients?.map { it.text })
    }

    @Test
    fun editorDraftAndInterruptedMarkerSurviveActivityRecreationWithoutSubmittingAgain() {
        var submissions = 0
        compose.runOnIdle {
            MainCourseTestContent.content = {
                MainCourseTheme {
                    RecipeEditScreen(
                        scope = RecipeScope(7, 1),
                        recipe = DETAIL,
                        actionState = RecipeActionState(),
                        imageState = RecipeImagePreparationState(),
                        onSave = { _, _ -> submissions++ },
                        editorId = "editor-restore",
                    )
                }
            }
        }
        compose.onNodeWithTag("editor_name").performTextInput(" restored")
        compose.onNodeWithTag("editor_list").performScrollToNode(hasTestTag("editor_save"))
        compose.onNodeWithTag("editor_save").performClick()
        assertEquals(1, submissions)

        compose.activityRule.scenario.recreate()

        compose.onNodeWithTag("editor_list").performScrollToNode(hasTestTag("editor_name"))
        compose.onNodeWithTag("editor_name").assertTextContains("restored", substring = true)
        compose.onNodeWithTag("editor_list").performScrollToNode(hasTestTag("editor_unconfirmed"))
        compose.onNodeWithTag("editor_unconfirmed").assertIsDisplayed()
        assertEquals(1, submissions)
    }

    @Test
    fun rotationDoesNotReleaseAStagedPhotoButExplicitCancelReportsItsExactOwner() {
        val image = com.getmaincourse.app.data.images.PreparedRecipeImage("/private/staged.jpg", 7, "staged")
        val releases = mutableListOf<RecipeEditorImageSelection>()
        compose.runOnIdle {
            MainCourseTestContent.content = {
                MainCourseTheme {
                    RecipeEditScreen(
                        scope = RecipeScope(7, 1),
                        recipe = DETAIL,
                        actionState = RecipeActionState(),
                        imageState = RecipeImagePreparationState(
                            status = RecipeImagePreparationStatus.READY,
                            scope = RecipeScope(7, 1),
                            image = image,
                            requestKey = "editor-a:pick-a",
                        ),
                        onSave = { _, _ -> },
                        editorId = "editor-a",
                        onLeaveEditor = { releases += it },
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.activityRule.scenario.recreate()
        assertTrue(releases.isEmpty())

        compose.onNodeWithTag("editor_list").performScrollToNode(hasTestTag("editor_cancel"))
        compose.onNodeWithTag("editor_cancel").performClick()

        assertEquals(listOf(RecipeEditorImageSelection("editor-a", image, "editor-a:pick-a")), releases)
    }

    @Test
    fun ingredientReviewStartsIncludedAndKeepsStablePayloadForSubmission() {
        var submitted: List<ShoppingItemInput>? = null
        compose.runOnIdle {
            MainCourseTestContent.content = {
                MainCourseTheme {
                    IngredientReviewScreen(
                        scope = RecipeScope(7, 1),
                        recipe = DETAIL,
                        portions = 4,
                        actionState = RecipeActionState(),
                        onSubmit = { submitted = it },
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
    fun ingredientReviewShowsOnlyIngredientActionMessages() {
        val state = mutableStateOf(
            RecipeActionState(
                operation = RecipeActionOperation.SAVING,
                outcome = RecipeActionOutcome.SUCCEEDED,
                scope = RecipeScope(7, 1),
                recipeId = DETAIL.id,
                message = "Recipe saved",
            ),
        )
        compose.runOnIdle {
            MainCourseTestContent.content = {
                MainCourseTheme {
                    IngredientReviewScreen(
                        scope = RecipeScope(7, 1),
                        recipe = DETAIL,
                        portions = 4,
                        actionState = state.value,
                        onSubmit = {},
                    )
                }
            }
        }

        compose.onNodeWithText("Recipe saved").assertDoesNotExist()
        compose.runOnIdle {
            state.value = state.value.copy(
                operation = RecipeActionOperation.ADDING_INGREDIENTS,
                message = "Added 2 items",
            )
        }
        compose.onNodeWithText("Added 2 items").assertIsDisplayed()
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
                            scope = RecipeScope(7, 1),
                            detailState = com.getmaincourse.app.features.session.RecipeDetailState(
                                DETAIL.id,
                                com.getmaincourse.app.features.session.DetailStatus.FRESH,
                                DETAIL.copy(sourceUrl = "https://example.test/recipe"),
                            ),
                            importFailed = false,
                            imageLoader = null,
                            resolveImage = { it },
                            onRetry = {},
                        )
                    }
                }
            }
        }

        compose.onNodeWithTag("recipe_detail").performScrollToNode(hasTestTag("recipe_source"))
        compose.onNodeWithTag("recipe_source").performClick()
        compose.onNodeWithTag("source_open_error").assertIsDisplayed()
    }

    private companion object {
        val SUMMARY = RecipeSummary(20, "Onion soup", 10, 20, false, null, CoverImages(null, null, null), "completed", null, "now")
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
