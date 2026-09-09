package com.getmaincourse.app.features.recipes

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.lifecycleScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.getmaincourse.app.MainCourseTestActivity
import com.getmaincourse.app.MainCourseTestContent
import com.getmaincourse.app.data.cache.CachedRecipes
import com.getmaincourse.app.data.cache.CatalogStore
import com.getmaincourse.app.data.cache.RecipeScope
import com.getmaincourse.app.data.model.AccountUpdateRequest
import com.getmaincourse.app.data.model.AppleAuthenticationExchangeRequest
import com.getmaincourse.app.data.model.AppleAuthenticationStartRequest
import com.getmaincourse.app.data.model.AppleAuthenticationStartResponse
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.CoverImages
import com.getmaincourse.app.data.model.GoogleSignInRequest
import com.getmaincourse.app.data.model.OnboardingRequest
import com.getmaincourse.app.data.model.OnboardingResponse
import com.getmaincourse.app.data.model.RecipeBatchResponse
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeSummary
import com.getmaincourse.app.data.model.RecipeUpdateRequest
import com.getmaincourse.app.data.model.SessionResponse
import com.getmaincourse.app.data.model.ShoppingItem
import com.getmaincourse.app.data.model.ShoppingItemsRequest
import com.getmaincourse.app.data.model.SignInRequest
import com.getmaincourse.app.data.model.SignUpRequest
import com.getmaincourse.app.data.model.StructuredIngredient
import com.getmaincourse.app.data.model.User
import com.getmaincourse.app.data.network.MainCourseApi
import com.getmaincourse.app.features.search.RecipeSearchScreen
import com.getmaincourse.app.features.search.RecipeSearchDocument
import com.getmaincourse.app.features.search.RecipeSearchState
import com.getmaincourse.app.features.session.CatalogRepository
import com.getmaincourse.app.ui.theme.MainCourseTheme
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
    }

    @Test
    fun ingredientReviewShowsRealControllerSuccessAndClearsItsInterruptedMarker() {
        val api = IngredientApi()
        val controller = ingredientController(api)
        compose.runOnIdle {
            MainCourseTestContent.content = {
                val actionState by controller.state.collectAsState()
                MainCourseTheme {
                    IngredientReviewScreen(
                        scope = RecipeScope(7, 1),
                        recipe = DETAIL,
                        portions = 4,
                        actionState = actionState,
                        onSubmit = { controller.addReviewedIngredients(DETAIL.id, it) },
                    )
                }
            }
        }

        compose.onNodeWithTag("review_submit").performClick()
        compose.waitUntil(5_000) {
            controller.state.value.outcome == RecipeActionOutcome.SUCCEEDED && !controller.state.value.isBusy
        }

        compose.onNodeWithText("Added 2 items").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(com.getmaincourse.app.R.string.recipe_add_unconfirmed)).assertDoesNotExist()
        compose.onNodeWithText(compose.activity.getString(com.getmaincourse.app.R.string.retry_same_items)).assertDoesNotExist()
        assertEquals(1, api.shoppingRequests.size)

        compose.runOnIdle { controller.clearRecipeAction() }
        compose.onNodeWithText(compose.activity.getString(com.getmaincourse.app.R.string.recipe_add_unconfirmed)).assertDoesNotExist()
    }

    @Test
    fun ingredientReviewRetriesTheRealControllersFrozenAmbiguousPayloadOnlyAfterATap() {
        val api = IngredientApi().apply { shoppingFailure = IOException("disconnected") }
        val controller = ingredientController(api)
        compose.runOnIdle {
            MainCourseTestContent.content = {
                val actionState by controller.state.collectAsState()
                MainCourseTheme {
                    IngredientReviewScreen(
                        scope = RecipeScope(7, 1),
                        recipe = DETAIL,
                        portions = 4,
                        actionState = actionState,
                        onSubmit = { controller.addReviewedIngredients(DETAIL.id, it) },
                    )
                }
            }
        }
        compose.onNodeWithTag("review_item_0").performClick()

        compose.onNodeWithTag("review_submit").performClick()
        compose.waitUntil(5_000) {
            controller.state.value.outcome == RecipeActionOutcome.AMBIGUOUS && !controller.state.value.isBusy
        }

        assertEquals(1, api.shoppingRequests.size)
        compose.onNodeWithText(compose.activity.getString(com.getmaincourse.app.R.string.retry_same_items)).assertIsDisplayed()
        val first = api.shoppingRequests.single()

        api.shoppingFailure = null
        compose.onNodeWithTag("review_submit").performClick()
        compose.waitUntil(5_000) {
            controller.state.value.outcome == RecipeActionOutcome.SUCCEEDED && !controller.state.value.isBusy
        }

        assertEquals(2, api.shoppingRequests.size)
        assertEquals(first, api.shoppingRequests.last())
        assertEquals(first.items.map { it.clientId }, api.shoppingRequests.last().items.map { it.clientId })
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
                                recipe = DETAIL.copy(sourceUrl = "https://example.test/recipe"),
                                loading = false,
                            ),
                            imageLoader = null,
                            resolveImage = { it },
                            onRefresh = {},
                            onBack = {},
                        )
                    }
                }
            }
        }

        compose.onNodeWithTag("recipe_detail").performScrollToNode(hasTestTag("recipe_source"))
        compose.onNodeWithTag("recipe_source").performClick()
        compose.onNodeWithTag("source_open_error").assertIsDisplayed()
    }

    private fun ingredientController(api: IngredientApi): RecipeActionController = RecipeActionController(
        api = api,
        repository = CatalogRepository(api, EmptyCatalogStore()),
        host = IngredientActionHost(compose.activity.lifecycleScope),
    ) { _, _ -> error("unused") }

    private class IngredientActionHost(private val scope: CoroutineScope) : RecipeActionHost {
        override fun launch(block: suspend (RecipeActionContext) -> Unit): Job = scope.launch {
            block(RecipeActionContext(1, 7, "fixture-token", RecipeScope(7, 1), listOf(SUMMARY), setOf(1)))
        }

        override suspend fun prepareMutation(context: RecipeActionContext) = true
        override suspend fun canCommit(context: RecipeActionContext) = true
        override suspend fun canPublish(context: RecipeActionContext) = true
        override suspend fun publishDetails(context: RecipeActionContext, scope: RecipeScope, recipeId: Long) = Unit
        override suspend fun publishRemoval(context: RecipeActionContext, scope: RecipeScope, recipeId: Long) = Unit
        override suspend fun publishRemovalCount(context: RecipeActionContext, targetCookbookId: Long?) = Unit
        override suspend fun retainPendingRemoval(
            context: RecipeActionContext,
            scope: RecipeScope,
            recipeId: Long,
            failure: Throwable,
        ) = Unit
        override suspend fun settleRecipeReads(context: RecipeActionContext) = true
        override suspend fun handleAuthorizationFailure(context: RecipeActionContext, failure: com.getmaincourse.app.data.network.ApiFailure) = true
    }

    private class IngredientApi : MainCourseApi {
        val shoppingRequests = mutableListOf<ShoppingItemsRequest>()
        var shoppingFailure: Throwable? = null

        override suspend fun addRecipeIngredients(
            token: String,
            cookbookId: Long,
            request: ShoppingItemsRequest,
        ): List<ShoppingItem> {
            shoppingRequests += request
            shoppingFailure?.let { throw it }
            return request.items.mapIndexed { index, item ->
                ShoppingItem(index.toLong(), item.clientId, item.name, item.details, item.checkedAt, item.sourceRecipeId, "now", "now")
            }
        }

        override suspend fun signIn(request: SignInRequest): SessionResponse = error("unused")
        override suspend fun signInWithGoogle(request: GoogleSignInRequest): SessionResponse = error("unused")
        override suspend fun startAppleAuthentication(request: AppleAuthenticationStartRequest): AppleAuthenticationStartResponse = error("unused")
        override suspend fun exchangeAppleAuthentication(request: AppleAuthenticationExchangeRequest): SessionResponse = error("unused")
        override suspend fun signUp(request: SignUpRequest): SessionResponse = error("unused")
        override suspend fun signOut(token: String) = Unit
        override suspend fun updateAccount(token: String, request: AccountUpdateRequest): User = error("unused")
        override suspend fun deleteAccount(token: String) = Unit
        override suspend fun submitOnboarding(request: OnboardingRequest): OnboardingResponse = error("unused")
        override suspend fun cookbooks(token: String) = emptyList<Cookbook>()
        override suspend fun recipes(token: String, cookbookId: Long) = emptyList<RecipeSummary>()
        override suspend fun recipe(token: String, cookbookId: Long, recipeId: Long): RecipeDetail = error("unused")
        override suspend fun recipeBatch(token: String, cookbookId: Long, cursor: String?) = RecipeBatchResponse(emptyList(), null)
        override suspend fun updateRecipe(token: String, cookbookId: Long, recipeId: Long, request: RecipeUpdateRequest): RecipeDetail = error("unused")
        override suspend fun updateRecipeCover(token: String, cookbookId: Long, recipeId: Long, image: File): RecipeDetail = error("unused")
        override suspend fun moveRecipe(token: String, sourceCookbookId: Long, recipeId: Long, targetCookbookId: Long): RecipeDetail = error("unused")
        override suspend fun deleteRecipe(token: String, cookbookId: Long, recipeId: Long) = error("unused")
    }

    private class EmptyCatalogStore : CatalogStore {
        override suspend fun cookbooks(userId: Long) = emptyList<Cookbook>()
        override suspend fun replaceCookbooks(userId: Long, items: List<Cookbook>) = Unit
        override suspend fun selectedCookbookId(userId: Long): Long? = null
        override suspend fun selectCookbook(userId: Long, cookbookId: Long) = Unit
        override suspend fun recipes(scope: RecipeScope) = CachedRecipes(emptyList(), false)
        override suspend fun replaceRecipes(scope: RecipeScope, items: List<RecipeSummary>) = Unit
        override suspend fun detail(scope: RecipeScope, recipeId: Long): RecipeDetail? = null
        override suspend fun saveRecipeDetails(scope: RecipeScope, details: List<RecipeDetail>) = Unit
        override suspend fun searchDocuments(scope: RecipeScope) = emptyList<RecipeSearchDocument>()
        override suspend fun upsertPartialRecipe(scope: RecipeScope, knownSummary: RecipeSummary, detail: RecipeDetail) = Unit
        override suspend fun removeRecipe(scope: RecipeScope, recipeId: Long) = Unit
        override suspend fun removeCookbook(scope: RecipeScope) = Unit
        override suspend fun clear() = Unit
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
