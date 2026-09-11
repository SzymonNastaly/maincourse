package com.getmaincourse.app.features.recipes

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.getmaincourse.app.MainCourseTestActivity
import com.getmaincourse.app.MainCourseTestContent
import com.getmaincourse.app.ui.theme.MainCourseTheme
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule

@RunWith(AndroidJUnit4::class)
class RenderedRecipePageTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainCourseTestActivity>()

    @After
    fun tearDown() {
        compose.runOnIdle { MainCourseTestContent.content = {} }
    }

    @Test
    fun webViewExtractsJsonLdMetadataAndCleanedHtml() {
        val result = AtomicReference<com.getmaincourse.app.data.model.RecipePageContent?>()
        val html = """
            <html>
              <head>
                <meta property="og:title" content="Tomato soup">
                <script type="application/ld+json">{"@type":"Recipe","name":"Tomato soup"}</script>
                <style>.hidden { display: none; }</style>
              </head>
              <body><nav>Menu</nav><main class="recipe"><h1>Tomato soup</h1></main></body>
            </html>
        """.trimIndent()
        val url = "data:text/html;charset=utf-8,${Uri.encode(html)}"

        compose.runOnIdle {
            MainCourseTestContent.content = {
                MainCourseTheme {
                    RenderedRecipePage(
                        request = RecipeShareStatus.ReadingPage(1, url),
                        onFinished = { _, content -> result.set(content) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        compose.waitUntil(8_000) { result.get() != null }
        val content = checkNotNull(result.get())
        assertEquals("Tomato soup", content.metaTags["og:title"])
        assertTrue(content.jsonLd.single().contains("\"@type\":\"Recipe\""))
        assertTrue(content.html.contains("Tomato soup"))
        assertTrue(!content.html.contains("<nav"))
        assertTrue(!content.html.contains("class="))
    }
}
