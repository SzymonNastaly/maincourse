package com.getmaincourse.app

import android.app.LocaleManager
import android.os.LocaleList
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.filters.SdkSuppress
import com.getmaincourse.app.data.network.ApiProblem
import com.getmaincourse.app.features.recipes.RecipeShareSheet
import com.getmaincourse.app.features.recipes.RecipeShareStatus
import com.getmaincourse.app.features.recipes.RecipeShareUiState
import com.getmaincourse.app.ui.UiMessage
import com.getmaincourse.app.ui.localized
import com.getmaincourse.app.ui.theme.MainCourseTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test

@SdkSuppress(minSdkVersion = 33)
class LocalizationScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainCourseTestActivity>()
    private var originalLocales: LocaleList? = null

    @After
    fun restoreLanguage() {
        compose.runOnIdle {
            MainCourseTestContent.content = {}
            originalLocales?.let { compose.activity.getSystemService(LocaleManager::class.java).applicationLocales = it }
        }
        compose.waitForIdle()
    }

    @Test
    fun systemAppLanguageChangesRerenderRetainedErrorsAndShareUi() {
        // The same immutable state survives every locale-triggered activity recreation.
        val error = UiMessage.Api(ApiProblem("invalid_credentials"), 401)
        val shareState = RecipeShareUiState("Family / Rodzina", RecipeShareStatus.Failed(error))
        compose.runOnIdle {
            originalLocales = compose.activity.getSystemService(LocaleManager::class.java).applicationLocales
            MainCourseTestContent.content = {
                MainCourseTheme {
                    Column {
                        Text(stringResource(R.string.recipes))
                        Text(UiMessage.Resource(R.string.error_connection_timeout).localized())
                        RecipeShareSheet(shareState, onRetry = {}, onDismiss = {})
                    }
                }
            }
        }
        for ((language, title, credentials, destination) in listOf(
            listOf("pl", "Przepisy", "Nieprawidłowy adres e-mail lub hasło", "Zapisywanie w: Family / Rodzina"),
            listOf("de", "Rezepte", "Ungültige E-Mail-Adresse oder ungültiges Passwort", "Wird in „Family / Rodzina“ gespeichert"),
            listOf("en", "Recipes", "Invalid email or password", "Saving to Family / Rodzina"),
        )) {
            compose.runOnIdle {
                compose.activity.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(language)
            }
            compose.waitUntil(10_000) {
                compose.onAllNodes(androidx.compose.ui.test.hasText(title)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(title).assertIsDisplayed()
            compose.onNodeWithText(credentials).assertIsDisplayed()
            compose.onNodeWithText(destination).assertIsDisplayed()
        }
    }
}
