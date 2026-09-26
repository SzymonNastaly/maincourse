package com.getmaincourse.app.data

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import android.text.Annotation
import android.text.Spanned
import com.getmaincourse.app.R
import com.getmaincourse.app.data.network.ApiErrorCode
import com.getmaincourse.app.data.network.ApiProblem
import com.getmaincourse.app.data.network.ApiStrings
import com.getmaincourse.app.data.network.ImportErrorCode
import com.getmaincourse.app.data.network.importFailureMessage
import com.getmaincourse.app.data.network.ValidationFieldCode
import com.getmaincourse.app.data.network.ValidationRuleCode
import com.getmaincourse.app.ui.UiMessage
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiErrorResourcesTest {
    @Test
    fun everyKnownErrorHasPackagedNativeResources() {
        val strings = strings("en")
        for (code in ApiErrorCode.entries) {
            assertTrue(ApiProblem(code.wire, ApiProblem.Parameters(50)).message(strings).isNotBlank())
        }
        for (code in ImportErrorCode.entries) {
            assertTrue(importFailureMessage(code.wire, strings).isNotBlank())
        }
        assertEquals("Recipe text exceeds the character limit (50).",
            ApiProblem("text_too_long", ApiProblem.Parameters(50)).message(strings))
    }

    @Test
    fun allCodesAndValidationCombinationsRenderInBothShippingTranslations() {
        val english = strings("en")
        for (locale in listOf("pl", "de")) {
            val translated = strings(locale)
            for (code in ApiErrorCode.entries) {
                val problem = ApiProblem(code.wire, ApiProblem.Parameters(50))
                assertNotEquals("$locale/${code.wire}", problem.message(english), problem.message(translated))
            }
            for (code in ImportErrorCode.entries) {
                assertNotEquals(importFailureMessage(code.wire, english), importFailureMessage(code.wire, translated))
            }
            for (field in ValidationFieldCode.entries) {
                for (rule in ValidationRuleCode.entries) {
                    val problem = ApiProblem("validation_failed", details = listOf(ApiProblem.Detail(field.wire, rule.wire, ApiProblem.Parameters(50))))
                    val message = problem.message(translated)
                    assertTrue(message.isNotBlank())
                    assertNotEquals(problem.message(english), message)
                    assertTrue(!message.contains("%1\$") && !message.contains("%2\$"))
                }
            }
        }
    }

    @Test
    fun retainedMessagesResolveUsingTheCurrentLanguageAndIgnoreServerProse() {
        val validation = UiMessage.Api(ApiProblem.parse("""{"error_code":"validation_failed","errors":["DO NOT DISPLAY"],"error_details":[{"field":"name","code":"too_long","params":{"count":50}}]}"""), 422)
        assertEquals("Maksymalna liczba znaków w polu „Nazwa” to 50.", validation.resolve(strings("pl")))
        assertEquals("Die maximale Zeichenanzahl für „Name“ beträgt 50.", validation.resolve(strings("de")))
        assertEquals("The maximum character count for Name is 50.", validation.resolve(strings("en")))
        for (locale in listOf("pl", "de", "en")) {
            val strings = strings(locale)
            for (body in listOf("<html>proxy error</html>", """{"error_code":"future","error":"DO NOT DISPLAY"}""", """{"error_code":"text_too_long","error_params":{"count":"bad"}}""")) {
                assertEquals(strings.text(R.string.api_error_server_unavailable), UiMessage.Api(ApiProblem.parse(body), 503).resolve(strings))
            }
            assertEquals(strings.text(R.string.error_connection_timeout), UiMessage.Resource(R.string.error_connection_timeout).resolve(strings))
        }
    }

    @Test
    fun styledTaglineKeepsItsEmphasisInEveryLanguage() {
        for ((language, word) in listOf("en" to "delicious", "pl" to "pysznego", "de" to "Leckeres")) {
            val text = context(language).getText(R.string.onboarding_tagline) as Spanned
            val span = text.getSpans(0, text.length, Annotation::class.java)
                .single { it.key == "emphasis" && it.value == "accent" }
            assertEquals(word, text.subSequence(text.getSpanStart(span), text.getSpanEnd(span)).toString())
        }
    }

    @Test
    fun polishPluralRulesAndUnsupportedLanguageFallbackUsePackagedResources() {
        val polish = context("pl").resources
        for ((count, expected) in listOf(0 to "0 przepisów", 1 to "1 przepis", 2 to "2 przepisy", 5 to "5 przepisów", 12 to "12 przepisów", 22 to "22 przepisy", 101 to "101 przepisów")) {
            assertEquals(expected, polish.getQuantityString(R.plurals.cookbook_recipes, count, count))
        }
        assertEquals("1 Portion", context("de").resources.getQuantityString(R.plurals.recipe_servings, 1, 1))
        assertEquals("2 Portionen", context("de").resources.getQuantityString(R.plurals.recipe_servings, 2, 2))
        assertEquals("Recipes", context("fr").getString(R.string.recipes))
        assertEquals(context("en").getString(R.string.app_name), context("pl").getString(R.string.app_name))
    }

    private fun context(language: String): Context {
        val app = ApplicationProvider.getApplicationContext<Context>()
        return app.createConfigurationContext(Configuration(app.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(language))
        })
    }

    private fun strings(language: String): ApiStrings {
        val context = context(language)
        return ApiStrings { resource, arguments -> context.getString(resource, *arguments) }
    }
}
