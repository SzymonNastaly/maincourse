package com.getmaincourse.app.features.recipes

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun manifestRegistersTextButNotImageSharing() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val textIntent = Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(context.packageName)
        val imageIntent = Intent(Intent.ACTION_SEND).setType("image/jpeg").setPackage(context.packageName)

        assertTrue(context.packageManager.queryIntentActivities(textIntent, 0).isNotEmpty())
        assertFalse(context.packageManager.queryIntentActivities(imageIntent, 0).isNotEmpty())
    }
}
