package com.getmaincourse.app

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.getmaincourse.app.data.CookbookSelection
import com.getmaincourse.app.data.model.AccountResponse
import com.getmaincourse.app.data.model.AccountUpdateRequest
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.CookbookInvitation
import com.getmaincourse.app.data.model.CookbookInvitationAcceptance
import com.getmaincourse.app.data.model.CookbookInvitationPreview
import com.getmaincourse.app.data.model.CreateCookbookRequest
import com.getmaincourse.app.data.model.DeviceTokenRequest
import com.getmaincourse.app.data.model.DeviceTokenResponse
import com.getmaincourse.app.data.model.NotificationOpenedRequest
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeDetailBatchResponse
import com.getmaincourse.app.data.model.RecipeImportResponse
import com.getmaincourse.app.data.model.RecipeContentImportRequest
import com.getmaincourse.app.data.model.RecipeSummary
import com.getmaincourse.app.data.model.RecipeTextImportRequest
import com.getmaincourse.app.data.model.RecipeUpdateRequest
import com.getmaincourse.app.data.model.RecipeUrlImportRequest
import okhttp3.MultipartBody
import com.getmaincourse.app.data.model.SessionResponse
import com.getmaincourse.app.data.model.StructuredIngredient
import com.getmaincourse.app.data.model.User
import com.getmaincourse.app.data.model.MoveRecipeRequest
import com.getmaincourse.app.data.model.OnboardingRequest
import com.getmaincourse.app.data.model.OnboardingResponse
import com.getmaincourse.app.data.model.ShoppingItem
import com.getmaincourse.app.data.model.ShoppingItemsRequest
import com.getmaincourse.app.data.model.ShoppingItemRequest
import com.getmaincourse.app.data.model.ShoppingItemUpdateRequest
import com.getmaincourse.app.data.model.SignInRequest
import com.getmaincourse.app.data.model.SignUpRequest
import com.getmaincourse.app.data.network.MainCourseService
import com.getmaincourse.app.data.session.SessionProvider
import com.getmaincourse.app.data.session.SessionStore
import com.getmaincourse.app.data.session.StoredSession
import com.getmaincourse.app.features.recipes.RecipeDetailViewModel
import com.getmaincourse.app.features.auth.HouseholdSize
import com.getmaincourse.app.features.auth.PreAuthStep
import com.getmaincourse.app.features.auth.PreAuthUiState
import com.getmaincourse.app.features.cookbooks.CookbookManagementViewModel
import com.getmaincourse.app.features.cookbooks.InvitationViewModel
import com.getmaincourse.app.features.recipes.RecipeEditViewModel
import com.getmaincourse.app.features.recipes.RecipeImportViewModel
import com.getmaincourse.app.features.recipes.RecipesViewModel
import com.getmaincourse.app.features.recipes.SharedRecipeInput
import com.getmaincourse.app.features.search.SearchViewModel
import com.getmaincourse.app.features.session.SessionUiState
import com.getmaincourse.app.features.shopping.ShoppingListViewModel
import com.getmaincourse.app.features.settings.SettingsViewModel
import com.getmaincourse.app.ui.theme.MainCourseTheme
import coil3.ImageLoader
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun freshInstallStartsOnWelcomeAndUsesMaterialQuestionFlow() {
        val preAuth: MutableState<PreAuthUiState> = mutableStateOf(
            PreAuthUiState(PreAuthStep.WELCOME, onboarding = true),
        )
        compose.runOnIdle {
            MainCourseTestContent.content = {
                MainCourseTheme {
                    MainCourseAppContent(
                        state = SessionUiState.SignedOut(),
                        preAuthState = preAuth.value,
                        onStartOnboarding = {
                            preAuth.value = preAuth.value.copy(step = PreAuthStep.HOUSEHOLD)
                        },
                        onSelectHousehold = {
                            preAuth.value = preAuth.value.copy(household = it)
                        },
                    )
                }
            }
        }

        compose.onNodeWithTag("onboarding_logo").assertIsDisplayed()
        compose.onNodeWithTag("onboarding_start").performClick()
        compose.onNodeWithText("How big is your household?").assertIsDisplayed()
        compose.onNodeWithTag("onboarding_continue").assertIsNotEnabled()
        compose.onNodeWithTag("onboarding_household_one").performClick()
        compose.onNodeWithTag("onboarding_continue").assertIsEnabled()
        assertEquals(HouseholdSize.ONE, preAuth.value.household)
    }

    @Test
    fun onboardingAuthenticationReusesBrandedSignupScreen() {
        compose.runOnIdle {
            MainCourseTestContent.content = {
                MainCourseTheme {
                    MainCourseAppContent(
                        state = SessionUiState.SignedOut(),
                        preAuthState = PreAuthUiState(PreAuthStep.AUTH, onboarding = true),
                    )
                }
            }
        }

        compose.onNodeWithText("Create your account").assertIsDisplayed()
        compose.onNodeWithTag("auth_name").assertIsDisplayed()
        compose.onNodeWithTag("auth_logo").assertIsDisplayed()
    }

    @Test
    fun authBusyAndErrorUpdatesDoNotRequireARouteChange() {
        val state: MutableState<SessionUiState> = mutableStateOf(SessionUiState.SignedOut())
        compose.runOnIdle {
            MainCourseTestContent.content = {
                MainCourseTheme { MainCourseAppContent(state = state.value) }
            }
        }
        compose.onNodeWithTag("auth_form").assertIsDisplayed()

        compose.runOnIdle {
            state.value = SessionUiState.SignedOut(authError = "Try again", busy = true)
        }

        compose.onNodeWithText("Try again").assertIsDisplayed()
        compose.onNodeWithTag("auth_email_progress").assertIsDisplayed()
    }

    @Test
    fun signedInSessionNavigatesFromRecipesToDetailAndBack() {
        show(SessionUiState.SignedIn(SESSION))

        compose.onNodeWithTag("screen_Recipes").assertIsDisplayed()
        compose.onNodeWithText(SUMMARY.name).performClick()
        compose.onNodeWithTag("recipe_detail").assertIsDisplayed()
        compose.onNodeWithText(DETAIL.name).assertIsDisplayed()
        compose.onNodeWithTag("recipe_back").assertDoesNotExist()
        compose.onNodeWithTag("recipe_move").assertDoesNotExist()
        compose.onNodeWithTag("recipe_delete").assertDoesNotExist()

        compose.onNodeWithTag("navigate_back").performClick()
        compose.onNodeWithTag("screen_Recipes").assertIsDisplayed()
    }

    @Test
    fun recipeCanBeEditedFromDetailAndReturnsWithAcknowledgedChanges() {
        val updated = AtomicReference<RecipeUpdateRequest?>(null)
        show(
            SessionUiState.SignedIn(SESSION),
            factories = factories(onRecipeUpdated = updated::set),
        )

        compose.onNodeWithText(SUMMARY.name).performClick()
        compose.onNodeWithTag("recipe_edit_open").performClick()
        compose.onNodeWithTag("recipe_edit").assertIsDisplayed()
        compose.onNodeWithTag("navigation_bar").assertDoesNotExist()
        compose.onNodeWithTag("recipe_edit_name").performTextReplacement("Roasted tomato soup")
        compose.onNodeWithTag("recipe_edit_save").assertIsEnabled().performClick()

        compose.onNodeWithTag("recipe_detail").assertIsDisplayed()
        compose.onNodeWithText("Roasted tomato soup").assertIsDisplayed()
        compose.onNodeWithText("Recipe updated").assertIsDisplayed()
        assertEquals("Roasted tomato soup", updated.get()?.name)
    }

    @Test
    fun leavingADirtyRecipeEditorRequiresDiscardConfirmation() {
        show(SessionUiState.SignedIn(SESSION))

        compose.onNodeWithText(SUMMARY.name).performClick()
        compose.onNodeWithTag("recipe_edit_open").performClick()
        compose.onNodeWithTag("recipe_edit_name").performTextReplacement("Unpublished soup")
        compose.onNodeWithTag("recipe_edit_back").performClick()

        compose.onNodeWithText("Discard changes?").assertIsDisplayed()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithTag("recipe_edit").assertIsDisplayed()

        compose.onNodeWithTag("recipe_edit_back").performClick()
        compose.onNodeWithTag("recipe_edit_discard").performClick()
        compose.onNodeWithTag("recipe_detail").assertIsDisplayed()
        compose.onNodeWithText(SUMMARY.name).assertIsDisplayed()
    }

    @Test
    fun signedInSessionImportsARecipeUrlIntoTheSelectedCookbook() {
        val imported = AtomicReference<Pair<Long, String>?>(null)
        show(
            SessionUiState.SignedIn(SESSION),
            factories = factories(
                importUrl = { cookbookId, url ->
                    imported.set(cookbookId to url)
                    RecipeImportResponse(11, "pending")
                },
            ),
        )

        compose.onNodeWithTag("open_recipe_import").performClick()
        compose.onNodeWithTag("screen_RecipeImport").assertIsDisplayed()
        compose.onNodeWithTag("import_url").performTextInput("https://example.com/soup")
        compose.onNodeWithTag("import_submit").assertIsEnabled().performClick()

        compose.onNodeWithTag("screen_Recipes").assertIsDisplayed()
        compose.onNodeWithText("Import started").assertIsDisplayed()
        assertEquals(COOKBOOK.id to "https://example.com/soup", imported.get())
    }

    @Test
    fun activeCookbookCanBeSelectedFromEveryCookbookScopedTopLevelScreen() {
        show(
            SessionUiState.SignedIn(SESSION),
            factories = factories(cookbooks = listOf(COOKBOOK, SHARED_COOKBOOK)),
        )

        compose.onNodeWithTag("cookbook_picker").assertTextContains(COOKBOOK.name).performClick()
        compose.onNodeWithText(SHARED_COOKBOOK.name).performClick()

        compose.onNodeWithTag("cookbook_picker").assertTextContains(SHARED_COOKBOOK.name)

        compose.onNodeWithTag("nav_Shopping").performClick()
        compose.onNodeWithTag("cookbook_picker").assertTextContains(SHARED_COOKBOOK.name).performClick()
        compose.onNodeWithText(COOKBOOK.name).performClick()

        compose.onNodeWithTag("nav_Search").performClick()
        compose.onNodeWithTag("cookbook_picker").assertTextContains(COOKBOOK.name)
    }

    @Test
    fun sharedRecipeTextOpensThePrefilledTextImporter() {
        val consumed = AtomicBoolean(false)
        show(
            state = SessionUiState.SignedIn(SESSION),
            sharedRecipeInput = SharedRecipeInput("Soup\n1 onion"),
            onSharedRecipeInputConsumed = { consumed.set(true) },
        )

        compose.onNodeWithTag("screen_RecipeImport").assertIsDisplayed()
        compose.onNodeWithTag("import_mode_text").assertIsDisplayed()
        compose.onNodeWithTag("import_text").assertTextContains("Soup\n1 onion")
        assertTrue(consumed.get())
    }

    @Test
    fun acceptedShareImportClearsAnEarlierOfflineRefreshError() {
        show(
            state = SessionUiState.SignedIn(SESSION),
            factories = factories(cookbookRefresh = { throw IOException("offline") }),
            sharedRecipeInput = SharedRecipeInput("https://example.com/soup"),
        )

        compose.onNodeWithTag("screen_RecipeImport").assertIsDisplayed()
        compose.onNodeWithTag("import_submit").assertIsEnabled().performClick()

        compose.onNodeWithTag("screen_Recipes").assertIsDisplayed()
        compose.onNodeWithText("You're offline").assertDoesNotExist()
    }

    @Test
    fun sharedRecipeWaitsForAuthenticationBeforeOpeningTheImporter() {
        val state: MutableState<SessionUiState> = mutableStateOf(SessionUiState.SignedOut())
        val consumed = AtomicBoolean(false)
        val appFactories = factories()
        compose.runOnIdle {
            MainCourseTestContent.content = {
                MainCourseTheme {
                    MainCourseAppContent(
                        state = state.value,
                        factories = appFactories,
                        sharedRecipeInput = SharedRecipeInput("https://example.com/soup"),
                        onSharedRecipeInputConsumed = { consumed.set(true) },
                    )
                }
            }
        }

        compose.onNodeWithTag("auth_form").assertIsDisplayed()
        assertFalse(consumed.get())

        compose.runOnIdle { state.value = SessionUiState.SignedIn(SESSION) }

        compose.onNodeWithTag("screen_RecipeImport").assertIsDisplayed()
        compose.onNodeWithTag("import_url").assertTextContains("https://example.com/soup")
        assertTrue(consumed.get())
    }

    @Test
    fun ingredientReviewPreservesSelectedServingsAcrossRecreationThenReturns() {
        val detail = DETAIL.copy(
            structuredIngredients = listOf(
                StructuredIngredient(1, 0, "2", null, null, "Tomatoes", null, "2 Tomatoes"),
            ),
        )
        show(SessionUiState.SignedIn(SESSION), factories = factories(detail = detail))
        compose.onNodeWithText(SUMMARY.name).performClick()

        compose.onNodeWithTag("portion_increment").performScrollTo().performClick()
        compose.onNodeWithTag("recipe_add_ingredients").performScrollTo().performClick()
        compose.onNodeWithTag("ingredient_review").assertIsDisplayed()
        compose.onNodeWithText("3").assertIsDisplayed()

        compose.activityRule.scenario.recreate()

        compose.onNodeWithTag("ingredient_review").assertIsDisplayed()
        compose.onNodeWithText("3").assertIsDisplayed()
        compose.onNodeWithTag("review_submit").performClick()
        compose.onNodeWithText("Ingredients added").assertIsDisplayed()
        compose.onNodeWithTag("review_success_confirm").performClick()

        compose.onNodeWithTag("recipe_detail").assertIsDisplayed()
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
        compose.onNodeWithText(USER.email).assertIsDisplayed()
        compose.onNodeWithTag("settings_name").assertIsDisplayed()
        compose.onNodeWithTag("settings_email").assertIsDisplayed()
        compose.onNodeWithTag("settings_save").assertIsDisplayed()
        compose.onNodeWithTag("screen_Settings").performScrollToNode(hasTestTag("settings_delete"))
        compose.onNodeWithTag("settings_delete").assertIsDisplayed()
        compose.onNodeWithTag("settings_sign_out").assertIsDisplayed()
        compose.onNodeWithTag("navigation_bar").assertIsDisplayed()
    }

    @Test
    fun settingsOpensCookbookManagementAndCreatesASharedCookbook() {
        show(SessionUiState.SignedIn(SESSION))

        compose.onNodeWithTag("nav_Settings").performClick()
        compose.onNodeWithTag("settings_cookbooks").performClick()

        compose.onNodeWithTag("screen_Cookbooks").assertIsDisplayed()
        compose.onNodeWithText(COOKBOOK.name).assertIsDisplayed()
        compose.onNodeWithTag("cookbook_name").performTextInput("Family recipes")
        compose.onNodeWithTag("cookbook_move_recipes").performClick()
        compose.onNodeWithTag("cookbook_create").assertIsEnabled().performClick()
    }

    @Test
    fun invitationWaitsForAuthenticationThenCanBeAccepted() {
        val state: MutableState<SessionUiState> = mutableStateOf(SessionUiState.SignedOut())
        val consumed = AtomicBoolean(false)
        val appFactories = factories()
        compose.runOnIdle {
            MainCourseTestContent.content = {
                MainCourseTheme {
                    MainCourseAppContent(
                        state = state.value,
                        factories = appFactories,
                        invitationToken = "invite-token",
                        onInvitationConsumed = { consumed.set(true) },
                    )
                }
            }
        }

        compose.onNodeWithTag("auth_form").assertIsDisplayed()
        assertFalse(consumed.get())

        compose.runOnIdle { state.value = SessionUiState.SignedIn(SESSION) }
        compose.onNodeWithTag("screen_Invitation").assertIsDisplayed()
        compose.onNodeWithText("Family").assertIsDisplayed()
        assertTrue(consumed.get())
        compose.onNodeWithTag("invitation_accept").performClick()
        compose.onNodeWithText("You’re in!").assertIsDisplayed()
        compose.onNodeWithTag("invitation_done").performClick()
        compose.onNodeWithTag("screen_Recipes").assertIsDisplayed()
    }

    @Test
    fun recipeSearchOpensAResultAndReturnsToTheQuery() {
        show(SessionUiState.SignedIn(SESSION))

        compose.onNodeWithTag("nav_Search").performClick()
        compose.onNodeWithTag("search_input").performTextInput("Tomato")
        compose.onNodeWithTag("recipe_${SUMMARY.id}").assertIsDisplayed().performClick()

        compose.onNodeWithTag("recipe_detail").assertIsDisplayed()
        compose.onNodeWithTag("navigate_back").performClick()
        compose.onNodeWithTag("screen_Search").assertIsDisplayed()
        compose.onNodeWithTag("search_input").assertTextContains("Tomato")
    }

    @Test
    fun accountDeletionFailureStaysInConfirmationAndCanBeRetried() {
        val deletionCalls = AtomicInteger()
        show(
            SessionUiState.SignedIn(SESSION),
            factories = factories(onDeleteAccount = deletionCalls::incrementAndGet),
        )
        compose.onNodeWithTag("nav_Settings").performClick()
        compose.onNodeWithTag("screen_Settings").performScrollToNode(hasTestTag("settings_delete"))
        compose.onNodeWithTag("settings_delete").performClick()
        compose.onNodeWithTag("delete_confirmation").performTextInput("DELETE")

        compose.onNodeWithTag("delete_account_button").performClick()

        compose.onNodeWithTag("delete_account_error")
            .assertIsDisplayed()
            .assertTextEquals("Could not delete account")
        compose.onNodeWithText("Retry deletion").assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { deletionCalls.get() == 2 }
    }

    @Test
    fun leavingSettingsDiscardsItsDraftAndAllowsRevertingPendingChange() {
        val pending = SESSION.copy(user = USER.copy(name = "Server accepted name", lifecycleNotificationsEnabled = false))
        show(SessionUiState.SignedIn(SESSION), factories = factories(pendingSettingsSession = pending))
        compose.onNodeWithTag("nav_Settings").performClick()
        compose.onNodeWithTag("settings_name").performTextReplacement("Unpublished name")

        compose.onNodeWithTag("nav_Recipes").performClick()
        compose.onNodeWithTag("nav_Settings").performClick()

        compose.onNodeWithTag("settings_name").assertTextContains(USER.name!!)
        compose.onNodeWithTag("settings_save").assertIsEnabled()
    }

    @Test
    fun matchingPendingSaveSurvivesSettingsTabRecreationWithoutNetwork() {
        val pending = SESSION.copy(user = USER.copy(name = "Server accepted name", lifecycleNotificationsEnabled = false))
        show(SessionUiState.SignedIn(SESSION), factories = factories(pendingSettingsSession = pending))
        compose.onNodeWithTag("nav_Settings").performClick()
        compose.onNodeWithTag("nav_Recipes").performClick()
        compose.onNodeWithTag("nav_Settings").performClick()

        compose.onNodeWithTag("settings_name").performTextReplacement("Server accepted name")
        compose.onNodeWithTag("recipe_reminders").performClick()
        compose.onNodeWithTag("settings_save").performClick()

        compose.onNodeWithTag("settings_error").assertDoesNotExist()
        compose.onNodeWithTag("settings_save").assertIsNotEnabled()
    }

    @Test
    fun removingDetailEntryCancelsItsViewModelWork() {
        val refreshStarted = CompletableDeferred<Unit>()
        val refreshCancelled = AtomicBoolean(false)
        val factories = factories(
            detail = null,
            detailRefresh = {
                refreshStarted.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    refreshCancelled.set(true)
                }
            },
        )
        show(SessionUiState.SignedIn(SESSION), factories)

        compose.onNodeWithText(SUMMARY.name).performClick()
        compose.waitUntil(5_000) { refreshStarted.isCompleted }
        compose.onNodeWithTag("navigate_back").performClick()

        compose.waitUntil(5_000) { refreshCancelled.get() }
    }

    @Test
    fun removingProtectedShellCancelsItsViewModelWork() {
        val refreshStarted = CompletableDeferred<Unit>()
        val refreshCancelled = AtomicBoolean(false)
        val state: MutableState<SessionUiState> = mutableStateOf(SessionUiState.SignedIn(SESSION))
        val factories = factories(
            cookbookRefresh = {
                refreshStarted.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    refreshCancelled.set(true)
                }
            },
        )
        compose.runOnIdle {
            MainCourseTestContent.content = {
                MainCourseTheme {
                    MainCourseAppContent(state = state.value, factories = factories)
                }
            }
        }
        compose.waitUntil(5_000) { refreshStarted.isCompleted }

        compose.runOnIdle { state.value = SessionUiState.SignedOut() }

        compose.waitUntil(5_000) { refreshCancelled.get() }
    }

    @Test
    fun restoringImmediatelyRemovesProtectedContent() {
        val state: MutableState<SessionUiState> = mutableStateOf(SessionUiState.SignedIn(SESSION))
        val factories = factories()
        compose.runOnIdle {
            MainCourseTestContent.content = {
                MainCourseTheme {
                    MainCourseAppContent(state = state.value, factories = factories)
                }
            }
        }
        compose.onNodeWithTag("screen_Recipes").assertIsDisplayed()

        compose.runOnIdle { state.value = SessionUiState.Restoring }

        compose.onNodeWithTag("session_loading").assertIsDisplayed()
        compose.onNodeWithTag("screen_Recipes").assertDoesNotExist()
        compose.onNodeWithTag("navigation_bar").assertDoesNotExist()
    }

    @Test
    fun recipeImageAppearsWhenPreparedLoaderIsPublished() {
        val loader = MutableStateFlow<ImageLoader?>(null)
        val recipe = SUMMARY.copy(coverImageUrl = "/rails/active_storage/image.jpg")
        val recipesCreated = AtomicInteger()
        show(
            state = SessionUiState.SignedIn(SESSION),
            factories = factories(summary = recipe, onRecipesCreated = recipesCreated::incrementAndGet),
            imageLoader = loader,
        )
        compose.onNodeWithTag("screen_Recipes").assertIsDisplayed()
        compose.onNodeWithTag("recipe_image_${recipe.id}", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(1, recipesCreated.get())

        compose.runOnIdle { loader.value = ImageLoader.Builder(compose.activity).build() }

        compose.onNodeWithTag("screen_Recipes").assertIsDisplayed()
        compose.onNodeWithTag("recipe_image_${recipe.id}", useUnmergedTree = true).assertExists()
        assertEquals(1, recipesCreated.get())
    }

    @Test
    fun detailViewModelAndSelectedPortionsSurviveActivityRecreation() {
        val detailsCreated = AtomicInteger()
        show(
            state = SessionUiState.SignedIn(SESSION),
            factories = factories(onDetailCreated = detailsCreated::incrementAndGet),
        )
        compose.onNodeWithText(SUMMARY.name).performClick()
        compose.onNodeWithTag("portion_increment").performScrollTo().performClick()
        compose.onNodeWithTag("recipe_portions").assertTextEquals("3")
        assertEquals(1, detailsCreated.get())

        compose.activityRule.scenario.recreate()

        compose.onNodeWithTag("recipe_detail").assertIsDisplayed()
        compose.onNodeWithTag("recipe_portions").assertTextEquals("3")
        assertEquals(1, detailsCreated.get())
    }

    private fun show(
        state: SessionUiState,
        factories: BrowsingViewModelFactories = factories(),
        imageLoader: MutableStateFlow<ImageLoader?> = MutableStateFlow(null),
        sharedRecipeInput: SharedRecipeInput? = null,
        onSharedRecipeInputConsumed: () -> Unit = {},
        invitationToken: String? = null,
        onInvitationConsumed: () -> Unit = {},
    ) {
        compose.runOnIdle {
            MainCourseTestContent.content = {
                MainCourseTheme {
                    MainCourseAppContent(
                        state = state,
                        factories = factories,
                        imageLoader = imageLoader,
                        sharedRecipeInput = sharedRecipeInput,
                        onSharedRecipeInputConsumed = onSharedRecipeInputConsumed,
                        invitationToken = invitationToken,
                        onInvitationConsumed = onInvitationConsumed,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun factories(
        cookbookRefresh: suspend () -> Unit = {},
        cookbooks: List<Cookbook> = listOf(COOKBOOK),
        detail: RecipeDetail? = DETAIL,
        detailRefresh: suspend () -> Unit = {},
        summary: RecipeSummary = SUMMARY,
        onRecipesCreated: () -> Unit = {},
        onDetailCreated: () -> Unit = {},
        onDeleteAccount: () -> Unit = {},
        pendingSettingsSession: SessionResponse? = null,
        importUrl: suspend (Long, String) -> RecipeImportResponse = { _, _ ->
            RecipeImportResponse(11, "pending")
        },
        importText: suspend (Long, String) -> RecipeImportResponse = { _, _ ->
            RecipeImportResponse(12, "pending")
        },
        onRecipeUpdated: (RecipeUpdateRequest) -> Unit = {},
    ): BrowsingViewModelFactories {
        val selection = MutableStateFlow(CookbookSelection(cookbooks, cookbooks.firstOrNull()?.id))
        val recipes = MutableStateFlow(listOf(summary))
        val detailState = MutableStateFlow(detail)
        val shoppingItems = MutableStateFlow(emptyList<ShoppingItem>())
        val settingsProvider = SessionProvider().apply {
            set(SESSION)
            pendingSettingsSession?.let(::setPendingAcceptedSession)
        }
        val settingsStore = InMemorySessionStore(StoredSession(BASE_URL, SESSION))
        return BrowsingViewModelFactories(
            recipes = {
                simpleViewModelFactory {
                    onRecipesCreated()
                    RecipesViewModel(
                        observeCookbooks = { selection },
                        observeRecipes = { recipes },
                        refreshCookbooks = cookbookRefresh,
                        refreshRecipes = {},
                        selectCookbook = { cookbookId ->
                            selection.value = selection.value.copy(selectedId = cookbookId)
                        },
                    )
                }
            },
            import = {
                simpleViewModelFactory {
                    RecipeImportViewModel(
                        observeCookbooks = { selection },
                        refreshCookbooks = {},
                        selectCookbook = { cookbookId ->
                            selection.value = selection.value.copy(selectedId = cookbookId)
                        },
                        importUrl = importUrl,
                        importText = importText,
                    )
                }
            },
            detail = { _, _, _ ->
                simpleViewModelFactory {
                    onDetailCreated()
                    RecipeDetailViewModel(
                        observeDetail = { detailState },
                        refreshDetail = detailRefresh,
                    )
                }
            },
            edit = { _, _, _ ->
                simpleViewModelFactory {
                    RecipeEditViewModel(
                        observeRecipe = { detailState },
                        updateRecipe = { request, _ ->
                            onRecipeUpdated(request)
                            detailState.value = detailState.value?.copy(
                                name = request.name,
                                prepTime = request.prepTime,
                                cookTime = request.cookTime,
                                servings = request.servings,
                                ingredients = request.ingredients,
                                structuredIngredients = emptyList(),
                                instructions = request.instructions,
                                notes = request.notes,
                                sourceUrl = request.sourceUrl,
                                updatedAt = "2026-09-11T12:00:00Z",
                            )
                        },
                    )
                }
            },
            shopping = {
                simpleViewModelFactory {
                    ShoppingListViewModel(
                        observeCookbooks = { selection },
                        observeItems = { shoppingItems },
                        refreshCookbooks = {},
                        refreshItems = {},
                        createItem = { _, _: ShoppingItemRequest -> },
                        setItemChecked = { _, _, _ -> },
                        deleteItem = { _, _ -> },
                        clearItems = {},
                    )
                }
            },
            search = {
                simpleViewModelFactory {
                    SearchViewModel(
                        observeCookbooks = { selection },
                        prepareSearchIndex = {},
                        searchRecipes = { _, query ->
                            recipes.map { items ->
                                items.filter { recipe -> recipe.name.contains(query, ignoreCase = true) }
                            }
                        },
                    )
                }
            },
            settings = {
                simpleViewModelFactory {
                    SettingsViewModel(
                        service = UnusedService,
                        sessionStore = settingsStore,
                        sessionProvider = settingsProvider,
                        deleteAccount = { completedJob().also { onDeleteAccount() } },
                        signOut = ::completedJob,
                    )
                }
            },
            cookbooks = {
                simpleViewModelFactory {
                    CookbookManagementViewModel(
                        userId = USER.id,
                        observeCookbooks = { selection },
                        refreshCookbooks = {},
                        createSharedCookbook = { _, _, _ -> SHARED_COOKBOOK },
                        deleteSharedCookbook = { _, _ -> },
                        leaveSharedCookbook = { _, _ -> },
                        createCookbookInvitation = {
                            CookbookInvitation(1, "token", "$BASE_URL/invite/token", "2099-01-01T00:00:00Z")
                        },
                    )
                }
            },
            invitation = { _, token ->
                simpleViewModelFactory {
                    InvitationViewModel(
                        userId = USER.id,
                        token = token,
                        observeCookbooks = { selection },
                        loadInvitation = {
                            CookbookInvitationPreview("Family", "owner@example.test", "2099-01-01T00:00:00Z", "pending")
                        },
                        acceptInvitation = { _, _ -> CookbookInvitationAcceptance(SHARED_COOKBOOK.id, SHARED_COOKBOOK.name) },
                        rejectInvitation = {},
                    )
                }
            },
        )
    }

    private class InMemorySessionStore(private var value: StoredSession?) : SessionStore {
        override suspend fun read(): StoredSession? = value
        override suspend fun write(session: StoredSession) {
            value = session
        }
        override suspend fun clear() {
            value = null
        }
    }

    private object UnusedService : MainCourseService {
        override suspend fun registerDeviceToken(request: DeviceTokenRequest): DeviceTokenResponse = error("Not used")
        override suspend fun deleteDeviceToken(token: String, provider: String) = error("Not used")
        override suspend fun markNotificationOpened(id: Long, request: NotificationOpenedRequest) = error("Not used")
        override suspend fun signIn(request: SignInRequest): SessionResponse = error("Not used")
        override suspend fun signUp(request: SignUpRequest): SessionResponse = error("Not used")
        override suspend fun submitOnboarding(request: OnboardingRequest): OnboardingResponse = error("Not used")
        override suspend fun signOut() = error("Not used")
        override suspend fun cookbooks(): List<Cookbook> = error("Not used")
        override suspend fun createCookbook(request: CreateCookbookRequest): Cookbook = error("Not used")
        override suspend fun deleteCookbook(cookbookId: Long) = error("Not used")
        override suspend fun leaveCookbook(cookbookId: Long) = error("Not used")
        override suspend fun createCookbookInvitation(cookbookId: Long): CookbookInvitation = error("Not used")
        override suspend fun cookbookInvitation(token: String): CookbookInvitationPreview = error("Not used")
        override suspend fun acceptCookbookInvitation(token: String): CookbookInvitationAcceptance = error("Not used")
        override suspend fun rejectCookbookInvitation(token: String) = error("Not used")
        override suspend fun recipes(cookbookId: Long): List<RecipeSummary> = error("Not used")
        override suspend fun recipe(cookbookId: Long, recipeId: Long): RecipeDetail = error("Not used")
        override suspend fun recipeDetails(
            cookbookId: Long,
            cursor: String?,
            limit: Int,
        ): RecipeDetailBatchResponse = error("Not used")
        override suspend fun importRecipe(
            cookbookId: Long,
            request: RecipeUrlImportRequest,
        ): RecipeImportResponse = error("Not used")
        override suspend fun importRecipeContent(
            cookbookId: Long,
            request: RecipeContentImportRequest,
        ): RecipeImportResponse = error("Not used")
        override suspend fun importRecipeText(
            cookbookId: Long,
            request: RecipeTextImportRequest,
        ): RecipeImportResponse = error("Not used")
        override suspend fun importRecipeImage(
            cookbookId: Long,
            image: MultipartBody.Part,
        ): RecipeImportResponse = error("Not used")
        override suspend fun moveRecipe(
            cookbookId: Long,
            recipeId: Long,
            request: MoveRecipeRequest,
        ): RecipeDetail = error("Not used")
        override suspend fun updateRecipe(
            cookbookId: Long,
            recipeId: Long,
            request: RecipeUpdateRequest,
        ): RecipeDetail = error("Not used")
        override suspend fun updateRecipeCoverImage(
            cookbookId: Long,
            recipeId: Long,
            coverImage: MultipartBody.Part,
        ): RecipeDetail = error("Not used")
        override suspend fun deleteRecipe(cookbookId: Long, recipeId: Long) = error("Not used")
        override suspend fun shoppingListItems(cookbookId: Long): List<ShoppingItem> = error("Not used")
        override suspend fun createShoppingItems(
            cookbookId: Long,
            request: ShoppingItemsRequest,
        ): List<ShoppingItem> = error("Not used")
        override suspend fun updateShoppingItem(
            cookbookId: Long,
            itemId: Long,
            request: ShoppingItemUpdateRequest,
        ): ShoppingItem = error("Not used")
        override suspend fun deleteShoppingItem(cookbookId: Long, itemId: Long) = error("Not used")
        override suspend fun clearShoppingItems(cookbookId: Long) = error("Not used")
        override suspend fun updateAccount(request: AccountUpdateRequest): AccountResponse = error("Not used")
        override suspend fun deleteAccount() = error("Not used")
    }

    private fun completedJob() = Job().apply { complete() }

    private companion object {
        const val BASE_URL = "https://app.getmaincourse.com/"
        val USER = User(1, "Reader", "reader@example.test", true)
        val SESSION = SessionResponse("token", "2099-01-01T00:00:00Z", USER)
        val COOKBOOK = Cookbook(10, "Home", true, 1, emptyList())
        val SHARED_COOKBOOK = Cookbook(11, "Family", false, 3, emptyList())
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
