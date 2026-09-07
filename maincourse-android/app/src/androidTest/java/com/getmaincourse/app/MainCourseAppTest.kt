package com.getmaincourse.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.pressBack
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainCourseAppTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun topLevelNavigationSelectsEachDestination() {
        listOf("Shopping", "Search", "Settings", "Recipes").forEach { destination ->
            compose.onNodeWithTag("nav_$destination").performClick().assertIsSelected()
            compose.onNodeWithTag("screen_$destination").assertIsDisplayed()
        }
    }

    @Test
    fun galleryStateAndNavigationSurviveActivityRecreation() {
        compose.onNodeWithTag("nav_Settings").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.explore_design)).performScrollTo().performClick()
        compose.onNodeWithTag("sample_note").performScrollTo().performTextInput("Extra lemon")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("sample_note").performScrollTo().assertTextContains("Extra lemon")
        compose.onNodeWithTag("nav_Settings").assertIsSelected()
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.back)).performClick()
        compose.onNodeWithTag("screen_Settings").assertIsDisplayed()
    }

    @Test
    fun systemBackReturnsToTheStartDestination() {
        compose.onNodeWithTag("nav_Shopping").performClick()
        compose.onNodeWithTag("screen_Shopping").assertIsDisplayed()
        pressBack()
        compose.onNodeWithTag("screen_Recipes").assertIsDisplayed()
        compose.onNodeWithTag("nav_Recipes").assertIsSelected()
    }

    @Test
    fun navigationAdaptsToWindowWidth() {
        val tag = if (compose.activity.resources.configuration.screenWidthDp >= 600) {
            "navigation_rail"
        } else {
            "navigation_bar"
        }
        compose.onNodeWithTag(tag).assertIsDisplayed()
    }
}
