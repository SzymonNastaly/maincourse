package com.getmaincourse.app

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasTestTag
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.CoverImages
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeSummary
import com.getmaincourse.app.data.model.SignInRequest
import com.getmaincourse.app.data.model.SignUpRequest
import com.getmaincourse.app.data.model.StructuredIngredient
import com.getmaincourse.app.data.model.User
import com.getmaincourse.app.features.auth.AuthenticationMethod
import com.getmaincourse.app.features.session.DetailStatus
import com.getmaincourse.app.features.session.LoadStatus
import com.getmaincourse.app.features.session.RecipeDetailState
import com.getmaincourse.app.features.session.SessionPhase
import com.getmaincourse.app.features.session.SessionState
import com.getmaincourse.app.features.onboarding.OnboardingState
import com.getmaincourse.app.features.onboarding.OnboardingStep
import com.getmaincourse.app.features.settings.AccountOperation
import com.getmaincourse.app.features.settings.AccountState
import com.getmaincourse.app.ui.theme.MainCourseTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainCourseAppTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainCourseTestActivity>()

    private lateinit var state: MutableState<SessionState>
    private lateinit var onboardingState: MutableState<OnboardingState>
    private lateinit var accountState: MutableState<AccountState>
    private lateinit var authenticationMethod: MutableState<AuthenticationMethod?>
    private lateinit var appleCanCancel: MutableState<Boolean>
    private lateinit var recorder: ActionRecorder

    @Before
    fun setUp() {
        state = mutableStateOf(readyState())
        onboardingState = mutableStateOf(OnboardingState(isLoading = false, step = OnboardingStep.COMPLETE))
        accountState = mutableStateOf(AccountState())
        authenticationMethod = mutableStateOf(null)
        appleCanCancel = mutableStateOf(false)
        recorder = ActionRecorder(state)
        compose.runOnIdle {
            MainCourseTestContent.content = {
                MainCourseTheme {
                    TestApp()
                }
            }
        }
    }

    @After
    fun tearDown() {
        compose.runOnIdle { MainCourseTestContent.content = {} }
    }

    @Test
    fun loginValidatesFieldsAndAcceptsAnExistingShortPassword() {
        show(SessionState(phase = SessionPhase.SIGNED_OUT))
        compose.onNodeWithTag("auth_submit").performScrollTo().performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.auth_email_required)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.auth_password_required)).performScrollTo().assertIsDisplayed()

        compose.onNodeWithTag("auth_email").performTextInput("reader@example.test")
        compose.onNodeWithTag("auth_password").performTextInput("short")
        compose.onNodeWithTag("auth_submit").performScrollTo().performClick()

        assertEquals("reader@example.test", recorder.signIn?.email)
        assertEquals("short", recorder.signIn?.password)
        assertNull(recorder.signUp)
    }

    @Test
    fun signupRequiresNameAndTwelveCharacterPasswordAndConfirmsTheSamePassword() {
        show(SessionState(phase = SessionPhase.SIGNED_OUT))
        compose.onNodeWithText(compose.activity.getString(R.string.auth_need_account)).performScrollTo().performClick()
        compose.onNodeWithTag("auth_email").performTextInput("reader@example.test")
        compose.onNodeWithTag("auth_password").performTextInput("too-short")
        compose.onNodeWithTag("auth_submit").performScrollTo().performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.auth_name_required)).performScrollTo().assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.auth_password_too_short)).performScrollTo().assertExists()

        compose.onNodeWithTag("auth_name").performTextInput("Reader")
        compose.onNodeWithTag("auth_password").performTextClearance()
        compose.onNodeWithTag("auth_password").performTextInput("long-password")
        compose.onNodeWithTag("auth_submit").performScrollTo().performClick()

        assertEquals("Reader", recorder.signUp?.name)
        assertEquals("long-password", recorder.signUp?.password)
        assertEquals("long-password", recorder.signUp?.passwordConfirmation)
    }

    @Test
    fun authenticationLoadingPreventsDuplicateSubmitAndShowsServerError() {
        show(SessionState(phase = SessionPhase.SIGNED_OUT))
        recorder.afterSignIn = {
            authenticationMethod.value = AuthenticationMethod.EMAIL
            state.value = SessionState(phase = SessionPhase.LOADING_COOKBOOKS, catalogStatus = LoadStatus.LOADING)
        }
        compose.onNodeWithTag("auth_email").performTextInput("reader@example.test")
        compose.onNodeWithTag("auth_password").performTextInput("short")
        compose.onNodeWithTag("auth_submit").performScrollTo().performClick()
        compose.onNodeWithTag("auth_submit").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("auth_email_progress").assertIsDisplayed()
        compose.onNodeWithTag("auth_google_progress").assertDoesNotExist()
        assertEquals(1, recorder.signInCount)

        show(SessionState(phase = SessionPhase.SIGNED_OUT, authError = "Invalid email or password"))
        compose.onNodeWithText("Invalid email or password").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun googleButtonPrecedesEmailAndBusyStateDisablesCompetingAuthentication() {
        show(SessionState(phase = SessionPhase.SIGNED_OUT))
        val google = compose.onNodeWithTag("auth_google").performScrollTo().assertIsEnabled()
        val googleBounds = google.fetchSemanticsNode().boundsInRoot
        val emailBounds = compose.onNodeWithTag("auth_email").fetchSemanticsNode().boundsInRoot
        assertEquals(true, googleBounds.bottom <= emailBounds.top)

        google.performClick()
        assertEquals(1, recorder.googleCount)
        compose.runOnIdle { authenticationMethod.value = AuthenticationMethod.GOOGLE }

        compose.onNodeWithTag("auth_google").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("auth_email").assertIsNotEnabled()
        compose.onNodeWithTag("auth_submit").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("auth_google_progress").assertIsDisplayed()
        compose.onNodeWithTag("auth_email_progress").assertDoesNotExist()
    }

    @Test
    fun appleButtonIsAccessibleFullWidthAndShowsOnlyItsOwnWaitingControls() {
        show(SessionState(phase = SessionPhase.SIGNED_OUT))
        val apple = compose.onNodeWithTag("auth_apple").performScrollTo().assertIsEnabled()
        assertEquals(
            compose.activity.getString(R.string.auth_continue_apple),
            apple.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription]
                .single(),
        )
        val googleWidth = compose.onNodeWithTag("auth_google").fetchSemanticsNode().boundsInRoot.width
        assertEquals(googleWidth, apple.fetchSemanticsNode().boundsInRoot.width, 1f)
        apple.performClick()
        assertEquals(1, recorder.appleCount)

        compose.runOnIdle {
            authenticationMethod.value = AuthenticationMethod.APPLE
            appleCanCancel.value = true
        }
        compose.onNodeWithTag("auth_apple").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("auth_apple_progress").assertIsDisplayed()
        compose.onNodeWithTag("auth_google_progress").assertDoesNotExist()
        compose.onNodeWithTag("auth_email").assertIsNotEnabled()
        compose.onNodeWithTag("auth_cancel_apple").performScrollTo().assertIsEnabled().performClick()
        assertEquals(1, recorder.cancelAppleCount)

        compose.runOnIdle { appleCanCancel.value = false }
        compose.onNodeWithTag("auth_cancel_apple").assertDoesNotExist()
    }

    @Test
    fun googleFailureKeepsEmailAndPasswordAvailableForPasswordGuidance() {
        show(SessionState(phase = SessionPhase.SIGNED_OUT))
        compose.onNodeWithTag("auth_email").performTextInput("reader@example.test")
        compose.onNodeWithTag("auth_password").performTextInput("password")

        show(
            SessionState(
                phase = SessionPhase.SIGNED_OUT,
                authError = "Sign in with your password to link this account",
            ),
        )

        compose.onNodeWithText("Sign in with your password to link this account").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("auth_email").assertTextContains("reader@example.test")
        compose.onNodeWithTag("auth_submit").performScrollTo().performClick()
        assertEquals("password", recorder.signIn?.password)
    }

    @Test
    fun loginFieldsSurviveSubmittingAndARejectedResponse() {
        show(SessionState(phase = SessionPhase.SIGNED_OUT))
        recorder.afterSignIn = {
            state.value = SessionState(phase = SessionPhase.LOADING_COOKBOOKS, catalogStatus = LoadStatus.LOADING)
        }
        compose.onNodeWithTag("auth_email").performTextInput("reader@example.test")
        compose.onNodeWithTag("auth_password").performTextInput("short")
        compose.onNodeWithTag("auth_submit").performScrollTo().performClick()
        show(SessionState(phase = SessionPhase.SIGNED_OUT, authError = "Invalid email or password"))

        compose.onNodeWithTag("auth_email").assertTextContains("reader@example.test")
        compose.onNodeWithTag("auth_submit").performScrollTo().performClick()
        assertEquals(2, recorder.signInCount)
        assertEquals("short", recorder.signIn?.password)
    }

    @Test
    fun signupModeAndFieldsSurviveSubmittingAndValidationErrors() {
        show(SessionState(phase = SessionPhase.SIGNED_OUT))
        recorder.afterSignUp = {
            state.value = SessionState(phase = SessionPhase.LOADING_COOKBOOKS, catalogStatus = LoadStatus.LOADING)
        }
        compose.onNodeWithText(compose.activity.getString(R.string.auth_need_account)).performScrollTo().performClick()
        compose.onNodeWithTag("auth_name").performTextInput("Reader")
        compose.onNodeWithTag("auth_email").performTextInput("reader@example.test")
        compose.onNodeWithTag("auth_password").performTextInput("long-password")
        compose.onNodeWithTag("auth_submit").performScrollTo().performClick()
        show(SessionState(phase = SessionPhase.SIGNED_OUT, authError = "Email is already registered"))

        compose.onNodeWithText("Email is already registered").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("auth_name").assertTextContains("Reader")
        compose.onNodeWithTag("auth_email").assertTextContains("reader@example.test")
        compose.onNodeWithTag("auth_submit").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("auth_submit").performScrollTo().performClick()
        assertEquals(2, recorder.signUpCount)
        assertEquals("long-password", recorder.signUp?.password)
    }

    @Test
    fun passwordImeActionUsesTheSameSubmitPathAsTheButton() {
        show(SessionState(phase = SessionPhase.SIGNED_OUT))
        compose.onNodeWithTag("auth_email").performTextInput("reader@example.test")
        compose.onNodeWithTag("auth_password").performTextInput("short")
        compose.onNodeWithTag("auth_password").performImeAction()

        assertEquals(1, recorder.signInCount)
        assertEquals("short", recorder.signIn?.password)
    }

    @Test
    fun authenticationFormIsWidthBoundedInsideTabletConstraints() {
        compose.runOnIdle {
            MainCourseTestContent.content = {
                MainCourseTheme {
                    Box(Modifier.requiredWidth(800.dp).requiredHeight(1000.dp)) {
                        MainCourseApp(
                            state = SessionState(phase = SessionPhase.SIGNED_OUT),
                            onboardingState = OnboardingState(isLoading = false, step = OnboardingStep.COMPLETE),
                            accountState = AccountState(),
                            authenticationMethod = null,
                            actions = recorder.actions,
                        )
                    }
                }
            }
        }

        val widthPixels = compose.onNodeWithTag("auth_form").fetchSemanticsNode().boundsInRoot.width
        val density = compose.activity.resources.displayMetrics.density
        assertEquals(true, widthPixels / density <= 440.5f)
    }

    @Test
    fun sessionRestoreAlwaysPrecedesFirstRunOnboardingAndAStoredUserBypassesIt() {
        onboardingState.value = OnboardingState(isLoading = false, step = OnboardingStep.WELCOME)
        show(SessionState(phase = SessionPhase.RESTORING))
        compose.onNodeWithText(compose.activity.getString(R.string.startup_loading)).assertIsDisplayed()
        compose.onAllNodesWithText(compose.activity.getString(R.string.onboarding_get_started)).assertCountEquals(0)

        show(
            SessionState(
                phase = SessionPhase.LOADING_COOKBOOKS,
                user = USER,
                catalogStatus = LoadStatus.ERROR,
            ),
        )
        compose.onNodeWithTag("screen_Recipes").assertIsDisplayed()
        compose.onAllNodesWithText(compose.activity.getString(R.string.onboarding_get_started)).assertCountEquals(0)
    }

    @Test
    fun completedOnboardingUsesStandaloneSignIn() {
        onboardingState.value = OnboardingState(isLoading = false, step = OnboardingStep.COMPLETE)
        show(SessionState(phase = SessionPhase.SIGNED_OUT))

        compose.onNodeWithTag("auth_email").assertIsDisplayed()
        compose.onNodeWithTag("auth_name").assertDoesNotExist()
    }

    @Test
    fun preparingAuthenticationKeepsTheEmbeddedFormWhenTheDraftChanges() {
        onboardingState.value = OnboardingState(isLoading = false, step = OnboardingStep.AUTH)
        show(SessionState(phase = SessionPhase.SIGNED_OUT))
        compose.onNodeWithTag("auth_email").performTextInput("reader@example.test")

        compose.runOnIdle {
            authenticationMethod.value = AuthenticationMethod.EMAIL
            onboardingState.value = OnboardingState(isLoading = false, step = OnboardingStep.COMPLETE)
        }

        compose.onNodeWithTag("auth_email").assertTextContains("reader@example.test")
        compose.onNodeWithTag("auth_name").assertIsDisplayed()
        compose.onNodeWithTag("auth_submit").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun cookbookSwitchUpdatesListAndDetailShowsSavedOfflineSections() {
        compose.onNodeWithTag("cookbook_picker").performClick()
        compose.onNodeWithText("Family cookbook").performClick()
        assertEquals(2L, recorder.switchedCookbook)
        show(readyState(cookbookId = 2L, recipes = listOf(SOUP)))

        compose.onNodeWithText("Vegetable soup").performClick()
        compose.waitForIdle()
        assertEquals(20L, recorder.openedRecipe)
        show(
            readyState(cookbookId = 2L, recipes = listOf(SOUP)).copy(
                detail = RecipeDetailState(20L, DetailStatus.SAVED_OFFLINE, SOUP_DETAIL, "Saved copy"),
            ),
        )

        compose.onNodeWithTag("recipe_detail").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.recipe_saved_offline)).assertIsDisplayed()
        compose.onNodeWithText("Ingredients").assertIsDisplayed()
        compose.onNodeWithText("1. Chop vegetables").assertIsDisplayed()
        compose.onNodeWithText("Freezes well").assertIsDisplayed()
    }

    @Test
    fun aRecipeCardStartsExactlyOneDetailLoad() {
        compose.onNodeWithText("Vegetable soup").performClick()
        compose.waitForIdle()
        assertEquals(1, recorder.openRecipeCount)
    }

    @Test
    fun aMissingCardImageIsDecorativeBecauseTheCardAlreadyNamesTheRecipe() {
        compose.onAllNodesWithContentDescription(
            "No photo for Vegetable soup",
        ).assertCountEquals(0)
    }

    @Test
    fun uncachedDetailOffersConnectionRetryWithoutInventingContent() {
        compose.onNodeWithText("Vegetable soup").performClick()
        show(
            readyState().copy(
                detail = RecipeDetailState(20L, DetailStatus.ERROR, message = "Connect to load this recipe"),
            ),
        )
        compose.onNodeWithTag("recipe_detail").assertIsDisplayed()
        compose.onNodeWithText("Recipe could not be loaded. Check your connection and retry.").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.retry)).performClick()
        assertEquals(20L, recorder.openedRecipe)
    }

    @Test
    fun restoredUnfetchedDetailRetryRefreshesListThenOpensReturnedRecipe() {
        compose.onNodeWithText("Vegetable soup").performClick()
        show(
            readyState(recipes = emptyList()).copy(
                recipesFetched = false,
                recipeStatus = LoadStatus.ERROR,
                detail = null,
            ),
        )

        compose.onNodeWithText("Recipe could not be loaded. Check your connection and retry.").assertIsDisplayed()
        recorder.afterRefresh = { state.value = readyState() }
        compose.onNodeWithText(compose.activity.getString(R.string.retry)).performClick()
        compose.waitForIdle()

        assertEquals(1, recorder.refreshCount)
        assertEquals(2, recorder.openRecipeCount)
    }

    @Test
    fun notReadyDetailUsesStatusCopyInsteadOfInternalMessages() {
        compose.onNodeWithText("Vegetable soup").performClick()
        show(
            readyState().copy(
                detail = RecipeDetailState(20L, DetailStatus.NOT_READY, message = "java.lang.IllegalStateException"),
            ),
        )

        compose.onNodeWithText(compose.activity.getString(R.string.recipe_processing)).assertIsDisplayed()
        compose.onAllNodesWithText("java.lang.IllegalStateException").assertCountEquals(0)
    }

    @Test
    fun removedRecipeKeepsItsUnavailableDestinationUntilBack() {
        compose.onNodeWithText("Vegetable soup").performClick()
        show(
            readyState(recipes = emptyList()).copy(
                detail = RecipeDetailState(20L, DetailStatus.UNAVAILABLE),
            ),
        )

        compose.onNodeWithTag("recipe_detail").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.recipe_unavailable)).assertIsDisplayed()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.back)).performClick()
        compose.onNodeWithTag("screen_Recipes").assertIsDisplayed()
    }

    @Test
    fun recipesRenderEmptyErrorAndPendingStates() {
        show(readyState(recipes = emptyList()).copy(recipesFetched = true))
        compose.onNodeWithText(compose.activity.getString(R.string.recipes_empty)).assertIsDisplayed()

        show(
            readyState(recipes = emptyList()).copy(
                recipesFetched = false,
                recipeStatus = LoadStatus.ERROR,
                message = "Could not refresh recipes",
                canRetry = true,
            ),
        )
        compose.onNodeWithText(compose.activity.getString(R.string.recipes_load_error)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.retry)).performClick()
        assertEquals(1, recorder.refreshCount)

        show(readyState(recipes = listOf(PENDING, FAILED)))
        compose.onNodeWithText(compose.activity.getString(R.string.recipe_processing)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.recipe_failed)).assertIsDisplayed()
        compose.onNodeWithText("Imported cake").performClick()
        assertNull(recorder.openedRecipe)
    }

    @Test
    fun coldCatalogDiscoveryShowsLoadingBeforeARealEmptyResult() {
        show(
            SessionState(
                phase = SessionPhase.LOADING_COOKBOOKS,
                user = USER,
                catalogStatus = LoadStatus.LOADING,
                recipeStatus = LoadStatus.IDLE,
            ),
        )
        compose.onNodeWithText(compose.activity.getString(R.string.startup_loading)).assertIsDisplayed()
        compose.onAllNodesWithText(compose.activity.getString(R.string.cookbooks_empty)).assertCountEquals(0)

        show(readyState(recipes = emptyList()).copy(cookbooks = emptyList(), activeCookbookId = null))
        compose.onNodeWithText(compose.activity.getString(R.string.cookbooks_empty)).assertIsDisplayed()
    }

    @Test
    fun degradedCookbookMembershipIsVisibleEvenWhenRecipesAreFresh() {
        show(readyState().copy(catalogStatus = LoadStatus.DEGRADED, recipeStatus = LoadStatus.FRESH))
        compose.onNodeWithText("Showing saved cookbooks. Connect and retry to check access.").assertIsDisplayed()
    }

    @Test
    fun recipeFailuresUseResourceCopyInsteadOfInternalControllerMessages() {
        show(
            readyState().copy(
                recipeStatus = LoadStatus.ERROR,
                message = "java.io.IOException: socket closed",
                canRetry = true,
            ),
        )

        compose.onNodeWithText(compose.activity.getString(R.string.recipes_load_error)).assertIsDisplayed()
        compose.onAllNodesWithText("java.io.IOException: socket closed").assertCountEquals(0)
    }

    @Test
    fun settingsShowsAccountGalleryAndLogoutClearsProtectedShell() {
        compose.onNodeWithTag("nav_Settings").performClick()
        compose.onNodeWithText("reader@example.test").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.explore_design)).performScrollTo().performClick()
        compose.onNodeWithTag("sample_note").performScrollTo().performTextInput("Extra lemon")
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.back)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.sign_out)).performClick()
        assertEquals(1, recorder.logoutCount)
        show(SessionState(phase = SessionPhase.SIGNED_OUT))
        compose.onNodeWithTag("auth_email").assertIsDisplayed()
    }

    @Test
    fun settingsEditsTrimmedNameAndKeepsDialogOpenForAnError() {
        compose.onNodeWithTag("nav_Settings").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.edit_name)).performClick()
        compose.onNodeWithTag("edit_name_input").performTextClearance()
        compose.onNodeWithTag("edit_name_input").performTextInput("  Ada  ")
        compose.onNodeWithText(compose.activity.getString(R.string.save)).performClick()

        assertEquals("Ada", recorder.updatedName)
        accountState.value = AccountState(error = "Could not update account")
        compose.onNodeWithText("Could not update account").assertIsDisplayed()
        compose.onNodeWithTag("edit_name_input").assertTextContains("  Ada  ")

        compose.onNodeWithTag("edit_name_input").performTextClearance()
        compose.onNodeWithTag("edit_name_input").performTextInput("x".repeat(51))
        compose.onNodeWithText(compose.activity.getString(R.string.save)).assertIsNotEnabled()
    }

    @Test
    fun remindersWaitForTheServerValueAndExplainAndroidDelivery() {
        compose.onNodeWithTag("nav_Settings").performClick()
        compose.onNodeWithTag("recipe_reminders")
            .assertTextContains(compose.activity.getString(R.string.recipe_reminders))
        compose.onNodeWithTag("recipe_reminders").performClick()
        assertEquals(true, recorder.updatedReminders)
        compose.onNodeWithTag("recipe_reminders").assertIsOff()
        compose.onNodeWithText(compose.activity.getString(R.string.recipe_reminders_help)).assertIsDisplayed()

        accountState.value = AccountState(error = "Could not update account")
        compose.onNodeWithText("Could not update account").assertIsDisplayed()
        compose.onNodeWithTag("recipe_reminders").assertIsOff()

        accountState.value = AccountState(operation = AccountOperation.SAVING)
        compose.onNodeWithTag("recipe_reminders").assertIsNotEnabled()
    }

    @Test
    fun acceptedAccountValueWithLocalPersistenceFailureOffersSaveRetry() {
        show(readyState().copy(user = USER.copy(lifecycleNotificationsEnabled = true)))
        accountState.value = AccountState(
            canRetryPersistence = true,
        )
        compose.onNodeWithTag("nav_Settings").performClick()

        compose.onNodeWithTag("recipe_reminders").assertIsOn()
        compose.onNodeWithTag("screen_Settings").performScrollToNode(hasTestTag("account_error"))
        compose.onNodeWithTag("account_retry", useUnmergedTree = true).performClick()
        assertEquals(1, recorder.retryAccountPersistenceCount)
    }

    @Test
    fun pendingLocalSaveStaysVisibleBesideADismissibleLaterOperationError() {
        compose.onNodeWithTag("nav_Settings").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.edit_name)).performClick()
        compose.onNodeWithTag("edit_name_input").performTextClearance()
        compose.onNodeWithTag("edit_name_input").performTextInput("  Ada draft  ")
        accountState.value = AccountState(
            error = "Network request failed",
            canRetryPersistence = true,
        )

        compose.onNodeWithText("Network request failed").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.account_save_pending)).assertIsDisplayed()
        compose.onNodeWithTag("edit_name_input").assertTextContains("  Ada draft  ")
        compose.onNodeWithText(compose.activity.getString(R.string.cancel)).performClick()
        compose.onNodeWithTag("screen_Settings").performScrollToNode(hasTestTag("account_error"))
        val clearsBeforeDismiss = recorder.clearAccountErrorCount
        compose.onNodeWithText(compose.activity.getString(R.string.dismiss)).performClick()
        assertEquals(clearsBeforeDismiss + 1, recorder.clearAccountErrorCount)

        accountState.value = accountState.value.copy(error = null)
        compose.onNodeWithText(compose.activity.getString(R.string.account_save_pending)).assertIsDisplayed()
        compose.onNodeWithTag("account_retry", useUnmergedTree = true).assertIsEnabled()
    }

    @Test
    fun namePersistenceFailureCanRetryInDialogAndRemainsReachableAfterDismissAndNavigation() {
        compose.onNodeWithTag("nav_Settings").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.edit_name)).performClick()
        val clearsBeforePersistenceFailure = recorder.clearAccountErrorCount
        accountState.value = AccountState(
            canRetryPersistence = true,
        )

        compose.onNodeWithTag("edit_name_retry").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.cancel)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.cancel)).performClick()
        assertEquals(clearsBeforePersistenceFailure, recorder.clearAccountErrorCount)
        compose.onNodeWithTag("screen_Settings").performScrollToNode(hasTestTag("account_error"))
        compose.onNodeWithTag("account_error", useUnmergedTree = true).assertIsDisplayed()

        compose.onNodeWithText(compose.activity.getString(R.string.manage_account)).performClick()
        assertEquals(clearsBeforePersistenceFailure, recorder.clearAccountErrorCount)
        pressBack()
        compose.onNodeWithTag("screen_Settings").performScrollToNode(hasTestTag("account_error"))
        compose.onNodeWithTag("account_retry", useUnmergedTree = true).performClick()
        assertEquals(1, recorder.retryAccountPersistenceCount)
    }

    @Test
    fun deleteRequiresExactPhraseAndNeverAutoSubmitsAfterRecreation() {
        compose.onNodeWithTag("nav_Settings").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.manage_account)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.delete_account)).performClick()
        compose.onNodeWithTag("delete_account_button").assertIsNotEnabled()
        compose.onNodeWithTag("delete_confirmation").performTextInput("delete")
        compose.onNodeWithTag("delete_confirmation")
            .assertTextContains(compose.activity.getString(R.string.delete_confirmation_prompt))
        compose.onNodeWithTag("delete_account_button").assertIsNotEnabled()
        compose.onNodeWithTag("delete_confirmation").performTextClearance()
        compose.onNodeWithTag("delete_confirmation").performTextInput("DELETE")
        compose.activityRule.scenario.recreate()
        assertEquals(0, recorder.deleteAccountCount)
        compose.onNodeWithTag("delete_account_button").assertIsEnabled().performClick()
        assertEquals(1, recorder.deleteAccountCount)

        accountState.value = AccountState(operation = AccountOperation.DELETING)
        compose.onNodeWithTag("delete_account_button").assertIsNotEnabled()
        accountState.value = AccountState(operation = AccountOperation.SAVING)
        compose.onNodeWithTag("delete_account_button").assertIsNotEnabled()
        accountState.value = AccountState(error = "Deletion could not be confirmed")
        compose.onNodeWithText("Deletion could not be confirmed").assertIsDisplayed()
        compose.onNodeWithTag("delete_account_button").assertIsEnabled().performClick()
        assertEquals(2, recorder.deleteAccountCount)
    }

    @Test
    fun busyAccountOperationDisablesPendingRetryAndManageDeleteEntries() {
        accountState.value = AccountState(
            operation = AccountOperation.SAVING,
            canRetryPersistence = true,
        )
        compose.onNodeWithTag("nav_Settings").performClick()
        compose.onNodeWithTag("screen_Settings").performScrollToNode(hasTestTag("account_error"))

        compose.onNodeWithTag("account_retry", useUnmergedTree = true).assertIsNotEnabled()
        compose.onNodeWithText(compose.activity.getString(R.string.manage_account)).assertIsNotEnabled()

        accountState.value = AccountState()
        compose.onNodeWithText(compose.activity.getString(R.string.manage_account)).performClick()
        accountState.value = AccountState(operation = AccountOperation.SAVING)
        compose.onNodeWithText(compose.activity.getString(R.string.delete_account)).assertIsNotEnabled()
    }

    @Test
    fun protectedRestoreAndCleanupFailuresOfferTheirRecoveryActions() {
        show(
            SessionState(
                phase = SessionPhase.RESTORE_FAILED,
                message = "Could not read the saved session",
                canRetry = true,
                canReset = true,
            ),
        )
        compose.onNodeWithText(compose.activity.getString(R.string.retry)).performClick()
        assertEquals(1, recorder.restoreCount)
        compose.onNodeWithText(compose.activity.getString(R.string.clear_local_data)).performClick()
        assertEquals(1, recorder.resetCount)

        show(SessionState(phase = SessionPhase.CLEANUP_FAILED, canRetry = true, canReset = true))
        compose.onNodeWithText(compose.activity.getString(R.string.retry_cleanup)).performClick()
        assertEquals(1, recorder.logoutCount)
    }

    @Test
    fun recoveryActionsRemainScrollableAtLargeTextInShortWindows() {
        val failed = SessionState(phase = SessionPhase.RESTORE_FAILED, canRetry = true, canReset = true)
        compose.runOnIdle {
            MainCourseTestContent.content = {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                    Box(Modifier.requiredWidth(500.dp).requiredHeight(150.dp)) {
                        MainCourseTheme {
                            MainCourseApp(
                                state = failed,
                                onboardingState = onboardingState.value,
                                accountState = accountState.value,
                                authenticationMethod = null,
                                actions = recorder.actions,
                            )
                        }
                    }
                }
            }
        }

        compose.onNodeWithText(compose.activity.getString(R.string.clear_local_data)).performScrollTo().performClick()
        assertEquals(1, recorder.resetCount)
    }

    @Test
    fun cookbookStartupFailureOffersRetryAndSignOut() {
        show(
            SessionState(
                phase = SessionPhase.LOADING_COOKBOOKS,
                user = USER,
                catalogStatus = LoadStatus.ERROR,
                canRetry = true,
                canReset = true,
            ),
        )
        compose.onNodeWithText(compose.activity.getString(R.string.cookbooks_load_error)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.retry)).performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.sign_out)).performClick()
        assertEquals(1, recorder.refreshCount)
        assertEquals(1, recorder.logoutCount)
    }

    @Test
    fun authenticationRecreationKeepsEmailButNotPassword() {
        show(SessionState(phase = SessionPhase.SIGNED_OUT))
        compose.onNodeWithTag("auth_email").performTextInput("reader@example.test")
        compose.onNodeWithTag("auth_password").performTextInput("do-not-save")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("auth_email").assertTextContains("reader@example.test")
        compose.onNodeWithTag("auth_submit").performScrollTo().performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.auth_password_required)).assertIsDisplayed()
        assertEquals(0, recorder.signInCount)
    }

    @Test
    fun systemBackReturnsFromAnotherTabToRecipes() {
        compose.onNodeWithTag("nav_Shopping").performClick()
        pressBack()
        compose.onNodeWithTag("screen_Recipes").assertIsDisplayed()
        compose.onNodeWithTag("nav_Recipes").assertIsSelected()
    }

    @Test
    fun detailBackAndAuthenticatedNavigationSurviveActivityRecreation() {
        compose.onNodeWithText("Vegetable soup").performClick()
        show(readyState().copy(detail = RecipeDetailState(20L, DetailStatus.FRESH, SOUP_DETAIL)))
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("recipe_detail").assertIsDisplayed()
        pressBack()
        compose.onNodeWithTag("screen_Recipes").assertIsDisplayed()
        compose.onNodeWithTag("nav_Recipes").assertIsSelected()

        compose.onNodeWithTag("nav_Settings").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.explore_design)).performScrollTo().performClick()
        compose.onNodeWithTag("sample_note").performScrollTo().performTextInput("Extra lemon")
        compose.onNodeWithTag("sample_note").assertTextContains("Extra lemon")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("sample_note").performScrollTo().assertTextContains("Extra lemon")
    }

    @Test
    fun restoredDetailWaitsForFreshViewModelCookbookResolutionAndKeepsTheMatchingScope() {
        val originalState = mutableStateOf(readyState())
        var viewModelState = originalState
        val actions = ActionRecorder(originalState)
        compose.runOnIdle {
            MainCourseTestContent.content = {
                MainCourseTheme {
                    MainCourseApp(
                        viewModelState.value,
                        onboardingState.value,
                        accountState.value,
                        null,
                        actions.actions,
                    )
                }
            }
        }
        compose.onNodeWithText("Vegetable soup").performClick()
        showIn(originalState, readyState().copy(detail = RecipeDetailState(20L, DetailStatus.FRESH, SOUP_DETAIL)))
        compose.onNodeWithTag("recipe_detail").assertIsDisplayed()

        val restoredState = mutableStateOf(
            SessionState(
                phase = SessionPhase.LOADING_COOKBOOKS,
                user = USER,
                catalogStatus = LoadStatus.LOADING,
            ),
        )
        compose.runOnIdle { viewModelState = restoredState }
        compose.activityRule.scenario.recreate()

        compose.onNodeWithTag("recipe_detail").assertIsDisplayed()
        showIn(
            restoredState,
            readyState().copy(detail = RecipeDetailState(20L, DetailStatus.FRESH, SOUP_DETAIL)),
        )
        compose.onNodeWithTag("recipe_detail").assertIsDisplayed()
    }

    @Test
    fun restoredDetailPopsAfterFreshViewModelResolvesADifferentCookbook() {
        val originalState = mutableStateOf(readyState())
        var viewModelState = originalState
        val actions = ActionRecorder(originalState)
        compose.runOnIdle {
            MainCourseTestContent.content = {
                MainCourseTheme {
                    MainCourseApp(
                        viewModelState.value,
                        onboardingState.value,
                        accountState.value,
                        null,
                        actions.actions,
                    )
                }
            }
        }
        compose.onNodeWithText("Vegetable soup").performClick()
        showIn(originalState, readyState().copy(detail = RecipeDetailState(20L, DetailStatus.FRESH, SOUP_DETAIL)))

        val restoredState = mutableStateOf(
            SessionState(
                phase = SessionPhase.LOADING_COOKBOOKS,
                user = USER,
                catalogStatus = LoadStatus.LOADING,
            ),
        )
        compose.runOnIdle { viewModelState = restoredState }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("recipe_detail").assertIsDisplayed()

        showIn(restoredState, readyState(cookbookId = 2L, recipes = listOf(SOUP)))
        compose.onNodeWithTag("screen_Recipes").assertIsDisplayed()
    }

    @Test
    fun restoredFailedImportShowsFailureAfterFreshViewModelResolvesItsScope() {
        val originalState = mutableStateOf(readyState())
        var viewModelState = originalState
        val actions = ActionRecorder(originalState)
        compose.runOnIdle {
            MainCourseTestContent.content = {
                MainCourseTheme {
                    MainCourseApp(
                        viewModelState.value,
                        onboardingState.value,
                        accountState.value,
                        null,
                        actions.actions,
                    )
                }
            }
        }
        compose.onNodeWithText("Vegetable soup").performClick()

        val restoredState = mutableStateOf(
            SessionState(
                phase = SessionPhase.LOADING_COOKBOOKS,
                user = USER,
                catalogStatus = LoadStatus.LOADING,
            ),
        )
        compose.runOnIdle { viewModelState = restoredState }
        compose.activityRule.scenario.recreate()
        showIn(
            restoredState,
            readyState(
                recipes = listOf(FAILED.copy(id = SOUP.id, name = SOUP.name)),
            ).copy(
                detail = RecipeDetailState(SOUP.id, DetailStatus.NOT_READY),
            ),
        )

        compose.onNodeWithTag("recipe_detail").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.recipe_failed)).assertIsDisplayed()
        compose.onAllNodesWithText(compose.activity.getString(R.string.recipe_processing)).assertCountEquals(0)
    }

    @Test
    fun sameCookbookRediscoveryKeepsDetailRouteButSwitchingScopeRemovesIt() {
        compose.onNodeWithText("Vegetable soup").performClick()
        show(readyState().copy(detail = RecipeDetailState(20L, DetailStatus.FRESH, SOUP_DETAIL)))
        compose.onNodeWithTag("recipe_detail").assertIsDisplayed()

        show(
            readyState(recipes = emptyList()).copy(
                recipesFetched = false,
                recipeStatus = LoadStatus.LOADING,
                detail = null,
            ),
        )
        compose.onNodeWithTag("recipe_detail").assertIsDisplayed()

        show(readyState(cookbookId = 2L, recipes = listOf(SOUP)))
        compose.onNodeWithTag("screen_Recipes").assertIsDisplayed()
    }

    @Test
    fun protectedNavigationSurvivesSameUserLoadingButResetsForAnotherUser() {
        compose.onNodeWithTag("nav_Settings").performClick()
        show(readyState().copy(phase = SessionPhase.LOADING_COOKBOOKS, catalogStatus = LoadStatus.LOADING))
        compose.onNodeWithTag("screen_Settings").assertIsDisplayed()

        show(readyState().copy(user = USER.copy(id = 8L, email = "other@example.test")))
        compose.onNodeWithTag("screen_Recipes").assertIsDisplayed()
    }

    @Test
    fun changingUserFromDetailDoesNotPopTheResetRecipesDestination() {
        compose.onNodeWithText("Vegetable soup").performClick()
        show(readyState().copy(detail = RecipeDetailState(20L, DetailStatus.FRESH, SOUP_DETAIL)))
        compose.onNodeWithTag("recipe_detail").assertIsDisplayed()

        show(
            readyState(cookbookId = 2L, recipes = emptyList()).copy(
                user = USER.copy(id = 8L, email = "other@example.test"),
            ),
        )

        compose.onNodeWithTag("screen_Recipes").assertIsDisplayed()
        compose.onNodeWithTag("nav_Recipes").assertIsSelected()
    }

    @Test
    fun changingUserFromDetailDoesNotOpenTheOldRouteWhenIdentifiersMatch() {
        compose.onNodeWithText("Vegetable soup").performClick()
        show(readyState().copy(detail = RecipeDetailState(20L, DetailStatus.FRESH, SOUP_DETAIL)))
        compose.onNodeWithTag("recipe_detail").assertIsDisplayed()
        assertEquals(1, recorder.openRecipeCount)

        show(readyState().copy(user = USER.copy(id = 8L, email = "other@example.test")))
        compose.waitForIdle()

        compose.onNodeWithTag("screen_Recipes").assertIsDisplayed()
        assertEquals(1, recorder.openRecipeCount)
    }

    @Test
    fun topLevelNavigationAndWidthAdaptationRemainAvailable() {
        listOf("Shopping", "Search", "Settings", "Recipes").forEach { destination ->
            compose.onNodeWithTag("nav_$destination").performClick().assertIsSelected()
            compose.onNodeWithTag("screen_$destination").assertIsDisplayed()
        }
        val tag = if (compose.activity.resources.configuration.screenWidthDp >= 600) {
            "navigation_rail"
        } else {
            "navigation_bar"
        }
        compose.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun show(value: SessionState) {
        compose.runOnIdle { state.value = value }
    }

    @androidx.compose.runtime.Composable
    private fun TestApp() {
        MainCourseApp(
            state = state.value,
            onboardingState = onboardingState.value,
            accountState = accountState.value,
            authenticationMethod = authenticationMethod.value,
            actions = recorder.actions,
            appleCanCancel = appleCanCancel.value,
        )
    }

    private fun showIn(target: MutableState<SessionState>, value: SessionState) {
        compose.runOnIdle { target.value = value }
    }

    private class ActionRecorder(private val state: MutableState<SessionState>) {
        var signIn: SignInRequest? = null
        var signUp: SignUpRequest? = null
        var signInCount = 0
        var signUpCount = 0
        var googleCount = 0
        var appleCount = 0
        var cancelAppleCount = 0
        var switchedCookbook: Long? = null
        var openedRecipe: Long? = null
        var openRecipeCount = 0
        var refreshCount = 0
        var logoutCount = 0
        var restoreCount = 0
        var resetCount = 0
        var updatedName: String? = null
        var updatedReminders: Boolean? = null
        var retryAccountPersistenceCount = 0
        var deleteAccountCount = 0
        var clearAccountErrorCount = 0
        var afterSignIn: () -> Unit = {}
        var afterSignUp: () -> Unit = {}
        var afterRefresh: () -> Unit = {}

        val actions = MainCourseActions(
            restore = { restoreCount++ },
            signIn = {
                signIn = it
                signInCount++
                afterSignIn()
            },
            signUp = {
                signUp = it
                signUpCount++
                afterSignUp()
            },
            googleSignIn = { googleCount++ },
            appleSignIn = { appleCount++ },
            cancelAppleSignIn = { cancelAppleCount++ },
            switchCookbook = { switchedCookbook = it },
            refresh = {
                refreshCount++
                afterRefresh()
            },
            openRecipe = {
                openedRecipe = it
                openRecipeCount++
            },
            closeRecipe = { state.value = state.value.copy(detail = null) },
            updateName = { updatedName = it },
            updateLifecycleNotifications = { updatedReminders = it },
            retryAccountPersistence = { retryAccountPersistenceCount++ },
            deleteAccount = { deleteAccountCount++ },
            clearAccountError = { clearAccountErrorCount++ },
            logout = { logoutCount++ },
            reset = { resetCount++ },
        )
    }

    private companion object {
        val USER = User(7L, "Reader", "reader@example.test", false)
        val PERSONAL = Cookbook(1L, "My cookbook", true, 1, emptyList())
        val FAMILY = Cookbook(2L, "Family cookbook", false, 1, emptyList())
        val SOUP = RecipeSummary(
            id = 20L,
            name = "Vegetable soup",
            prepTime = 10,
            cookTime = 30,
            favorite = false,
            coverImageUrl = null,
            coverImages = CoverImages(null, null, null),
            importStatus = "completed",
            errorMessage = null,
            updatedAt = "2026-09-07T10:00:00Z",
        )
        val PENDING = SOUP.copy(id = 21L, name = "Imported cake", importStatus = "pending")
        val FAILED = SOUP.copy(id = 22L, name = "Failed cake", importStatus = "failed", errorMessage = "Could not import")
        val SOUP_DETAIL = RecipeDetail(
            id = 20L,
            name = "Vegetable soup",
            prepTime = 10,
            cookTime = 30,
            servings = 4,
            favorite = false,
            ingredients = listOf("2 carrots"),
            structuredIngredients = listOf(
                StructuredIngredient(1L, 1, "2", null, null, "carrots", null, "2 carrots"),
            ),
            instructions = listOf("Chop vegetables", "Simmer until tender"),
            notes = "Freezes well",
            sourceUrl = null,
            tags = emptyList(),
            coverImageUrl = null,
            coverImages = CoverImages(null, null, null),
            createdAt = "2026-09-07T09:00:00Z",
            updatedAt = "2026-09-07T10:00:00Z",
        )

        fun readyState(
            cookbookId: Long = 1L,
            recipes: List<RecipeSummary> = listOf(SOUP),
        ) = SessionState(
            phase = SessionPhase.READY,
            user = USER,
            cookbooks = listOf(PERSONAL, FAMILY),
            activeCookbookId = cookbookId,
            recipes = recipes,
            recipesFetched = true,
            catalogStatus = LoadStatus.FRESH,
            recipeStatus = LoadStatus.FRESH,
            canReset = true,
        )
    }
}
