package com.getmaincourse.app.features.recipes

import android.os.PowerManager
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.getmaincourse.app.MainCourseTestActivity
import com.getmaincourse.app.MainCourseTestContent
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.ui.theme.MainCourseTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecipeScreenAwakeTest {
    @get:Rule val compose = createAndroidComposeRule<MainCourseTestActivity>()

    @After fun tearDown() {
        compose.runOnUiThread { MainCourseTestContent.content = {} }
    }

    @Test
    fun onlyLoadedForegroundRecipeKeepsScreenAwakeAndDisposalReleasesIt() {
        val state = mutableStateOf(RecipeDetailUiState())
        var displayedState: RecipeDetailUiState? = null
        lateinit var owner: ScreenLifecycle
        compose.runOnUiThread {
            owner = ScreenLifecycle()
            MainCourseTestContent.content = {
                CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                    MainCourseTheme {
                        RecipeDetailScreen(state.value, null, { null }, {})
                        SideEffect { displayedState = state.value }
                    }
                }
            }
        }
        compose.waitUntil(5_000) { displayedState == state.value }
        assertScreenAwake(false)
        compose.runOnUiThread { state.value = RecipeDetailUiState(recipe = recipe, loading = false) }
        assertScreenAwake(true)
        compose.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.STARTED }
        assertScreenAwake(false)
        compose.runOnUiThread { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        assertScreenAwake(true)
        compose.runOnUiThread { state.value = RecipeDetailUiState(loading = false, error = "Unavailable") }
        assertScreenAwake(false)
        compose.runOnUiThread { state.value = RecipeDetailUiState(recipe = recipe, loading = false) }
        assertScreenAwake(true)
        compose.runOnUiThread { MainCourseTestContent.content = {} }
        assertScreenAwake(false)
    }

    @Test
    fun outgoingRecipeDisposalDoesNotReleaseTheIncomingRecipe() {
        val firstVisible = mutableStateOf(true)
        val secondVisible = mutableStateOf(false)
        var displayedRecipes = 0
        compose.runOnUiThread {
            MainCourseTestContent.content = {
                MainCourseTheme {
                    if (firstVisible.value) RecipeDetailScreen(loaded, null, { null }, {})
                    if (secondVisible.value) RecipeDetailScreen(loaded, null, { null }, {})
                    SideEffect {
                        displayedRecipes = (if (firstVisible.value) 1 else 0) + (if (secondVisible.value) 1 else 0)
                    }
                }
            }
        }
        assertScreenAwake(true)
        compose.runOnUiThread { secondVisible.value = true }
        compose.waitUntil(5_000) { displayedRecipes == 2 }
        assertScreenAwake(true)
        compose.runOnUiThread { firstVisible.value = false }
        compose.waitUntil(5_000) { displayedRecipes == 1 }
        assertScreenAwake(true)
        compose.runOnUiThread { secondVisible.value = false }
        assertScreenAwake(false)
    }

    @Test
    fun batterySaverReleasesAndReacquiresWithoutReopeningTheRecipe() {
        val power = compose.activity.getSystemService(PowerManager::class.java)
        val originalPowerSaveMode = power.isPowerSaveMode
        var recipeDisplayed = false
        try {
            shell("dumpsys battery unplug")
            setPowerSaveMode(false)
            compose.runOnUiThread {
                MainCourseTestContent.content = {
                    MainCourseTheme {
                        RecipeDetailScreen(loaded, null, { null }, {})
                        SideEffect { recipeDisplayed = true }
                    }
                }
            }
            compose.waitUntil(5_000) { recipeDisplayed }
            assertScreenAwake(true)
            setPowerSaveMode(true)
            assertScreenAwake(false)
            setPowerSaveMode(false)
            assertScreenAwake(true)
        } finally {
            shell("cmd power set-mode ${if (originalPowerSaveMode) 1 else 0}")
            shell("dumpsys battery reset")
        }
    }

    private fun setPowerSaveMode(enabled: Boolean) {
        shell("cmd power set-mode ${if (enabled) 1 else 0}")
        compose.waitUntil(5_000) {
            compose.activity.getSystemService(PowerManager::class.java).isPowerSaveMode == enabled
        }
    }

    private fun assertScreenAwake(expected: Boolean) {
        compose.waitUntil(5_000) {
            var awake = false
            compose.runOnUiThread { awake = compose.activity.window.decorView.hasAwakeView() }
            awake == expected
        }
        compose.runOnUiThread { assertEquals(expected, compose.activity.window.decorView.hasAwakeView()) }
    }

    private fun View.hasAwakeView(): Boolean = keepScreenOn ||
        (this is ViewGroup && (0 until childCount).any { getChildAt(it).hasAwakeView() })

    private fun shell(command: String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
        }
    }

    private class ScreenLifecycle : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
    }

    private companion object {
        val recipe = RecipeDetail(
            20, "Onion soup", 10, 20, 4, false, listOf("2 onions"), emptyList(),
            listOf("Chop", "Cook"), null, null, emptyList(), null, null, "then", "now",
        )
        val loaded = RecipeDetailUiState(recipe = recipe, loading = false)
    }
}
