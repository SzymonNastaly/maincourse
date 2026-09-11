package com.getmaincourse.app.features.recipes

import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecipeShareIntentTest {
    @Test
    fun sendTextIntentExtractsSharedRecipeInput() {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "  https://example.com/soup  ")

        assertEquals("https://example.com/soup", intent.sharedRecipeInput()?.value)
    }

    @Test
    fun unrelatedOrBlankIntentsAreIgnored() {
        assertNull(Intent(Intent.ACTION_MAIN).putExtra(Intent.EXTRA_TEXT, "recipe").sharedRecipeInput())
        assertNull(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "  ").sharedRecipeInput())
        assertNull(Intent(Intent.ACTION_SEND).setType("image/jpeg").putExtra(Intent.EXTRA_TEXT, "recipe").sharedRecipeInput())
    }

    @Test
    fun imageIntentExtractsTheGrantedContentUri() {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("image/jpeg")
            .putExtra(Intent.EXTRA_STREAM, Uri.parse("content://images/recipe"))

        assertEquals(
            RecipeShareContent.Image("content://images/recipe", "image/jpeg"),
            intent.recipeShareContent(),
        )
    }

    @Test
    fun manifestRoutesTextHtmlAndImagesToTheCompactShareActivity() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val textIntent = Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(context.packageName)
        val htmlIntent = Intent(Intent.ACTION_SEND).setType("text/html").setPackage(context.packageName)
        val imageIntent = Intent(Intent.ACTION_SEND).setType("image/jpeg").setPackage(context.packageName)

        listOf(textIntent, htmlIntent, imageIntent).forEach { intent ->
            val matches = context.packageManager.queryIntentActivities(intent, 0)
            assertEquals(1, matches.size)
            assertEquals("com.getmaincourse.app.RecipeShareActivity", matches.single().activityInfo.name)
        }
    }
}
