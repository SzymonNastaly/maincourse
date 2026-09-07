package com.getmaincourse.app

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.CoverImages
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.data.model.RecipeSummary
import com.getmaincourse.app.data.model.SignInRequest
import com.getmaincourse.app.data.model.SignUpRequest
import com.getmaincourse.app.data.model.StructuredIngredient
import com.getmaincourse.app.data.model.User
import com.getmaincourse.app.features.session.DetailStatus
import com.getmaincourse.app.features.session.LoadStatus
import com.getmaincourse.app.features.session.RecipeDetailState
import com.getmaincourse.app.features.session.SessionPhase
import com.getmaincourse.app.features.session.SessionState
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
    private lateinit var recorder: ActionRecorder

    @Before
    fun setUp() {
        state = mutableStateOf(readyState())
        recorder = ActionRecorder(state)
        compose.runOnIdle {
            MainCourseTestContent.content = {
                MainCourseTheme {
                    MainCourseApp(state.value, recorder.actions)
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
        compose.onNodeWithTag("auth_submit").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.auth_email_required)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.auth_password_required)).assertIsDisplayed()

        compose.onNodeWithTag("auth_email").performTextInput("reader@example.test")
        compose.onNodeWithTag("auth_password").performTextInput("short")
        compose.onNodeWithTag("auth_submit").performClick()

        assertEquals("reader@example.test", recorder.signIn?.email)
        assertEquals("short", recorder.signIn?.password)
        assertNull(recorder.signUp)
    }

    @Test
    fun signupRequiresNameAndTwelveCharacterPasswordAndConfirmsTheSamePassword() {
        show(SessionState(phase = SessionPhase.SIGNED_OUT))
        compose.onNodeWithText(compose.activity.getString(R.string.auth_need_account)).performClick()
        compose.onNodeWithTag("auth_email").performTextInput("reader@example.test")
        compose.onNodeWithTag("auth_password").performTextInput("too-short")
        compose.onNodeWithTag("auth_submit").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.auth_name_required)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.auth_password_too_short)).assertIsDisplayed()

        compose.onNodeWithTag("auth_name").performTextInput("Reader")
        compose.onNodeWithTag("auth_password").performTextClearance()
        compose.onNodeWithTag("auth_password").performTextInput("long-password")
        compose.onNodeWithTag("auth_submit").performClick()

        assertEquals("Reader", recorder.signUp?.name)
        assertEquals("long-password", recorder.signUp?.password)
        assertEquals("long-password", recorder.signUp?.passwordConfirmation)
    }

    @Test
    fun authenticationLoadingPreventsDuplicateSubmitAndShowsServerError() {
        show(SessionState(phase = SessionPhase.SIGNED_OUT))
        recorder.afterSignIn = {
            state.value = SessionState(phase = SessionPhase.LOADING_COOKBOOKS, catalogStatus = LoadStatus.LOADING)
        }
        compose.onNodeWithTag("auth_email").performTextInput("reader@example.test")
        compose.onNodeWithTag("auth_password").performTextInput("short")
        compose.onNodeWithTag("auth_submit").performClick()
        compose.onNodeWithTag("auth_submit").assertIsNotEnabled()
        assertEquals(1, recorder.signInCount)

        show(SessionState(phase = SessionPhase.SIGNED_OUT, authError = "Invalid email or password"))
        compose.onNodeWithText("Invalid email or password").assertIsDisplayed()
    }

    @Test
    fun cookbookSwitchUpdatesListAndDetailShowsSavedOfflineSections() {
        compose.onNodeWithTag("cookbook_picker").performClick()
        compose.onNodeWithText("Family cookbook").performClick()
        assertEquals(2L, recorder.switchedCookbook)
        show(readyState(cookbookId = 2L, recipes = listOf(SOUP)))

        compose.onNodeWithText("Vegetable soup").performClick()
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
    fun uncachedDetailOffersConnectionRetryWithoutInventingContent() {
        compose.onNodeWithText("Vegetable soup").performClick()
        show(
            readyState().copy(
                detail = RecipeDetailState(20L, DetailStatus.ERROR, message = "Connect to load this recipe"),
            ),
        )
        compose.onNodeWithTag("recipe_detail").assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.recipe_connect_to_load)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(R.string.retry)).performClick()
        assertEquals(20L, recorder.openedRecipe)
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
        compose.onNodeWithTag("auth_submit").performClick()
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
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("sample_note").performScrollTo().assertTextContains("Extra lemon")
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

    private class ActionRecorder(private val state: MutableState<SessionState>) {
        var signIn: SignInRequest? = null
        var signUp: SignUpRequest? = null
        var signInCount = 0
        var switchedCookbook: Long? = null
        var openedRecipe: Long? = null
        var refreshCount = 0
        var logoutCount = 0
        var restoreCount = 0
        var resetCount = 0
        var afterSignIn: () -> Unit = {}

        val actions = MainCourseActions(
            restore = { restoreCount++ },
            signIn = {
                signIn = it
                signInCount++
                afterSignIn()
            },
            signUp = { signUp = it },
            switchCookbook = { switchedCookbook = it },
            refresh = { refreshCount++ },
            openRecipe = { openedRecipe = it },
            closeRecipe = { state.value = state.value.copy(detail = null) },
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
