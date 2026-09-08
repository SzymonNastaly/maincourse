package com.getmaincourse.app

import android.content.Intent
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppleIntentRoutingTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun installedDebugManifestResolvesTheActualCallbackToMainActivity() {
        val intent = Intent(Intent.ACTION_VIEW, DEBUG_CALLBACK.toUri())
            .addCategory(Intent.CATEGORY_BROWSABLE)

        val activity = intent.resolveActivity(context.packageManager)

        assertNotNull(activity)
        assertEquals(MainActivity::class.java.name, activity?.className)
    }

    @Test
    fun installedDebugManifestDoesNotResolveTheCallbackPathWithoutAQuery() {
        val intent = Intent(Intent.ACTION_VIEW, "com.getmaincourse.app.debug:/oauth/apple".toUri())
            .addCategory(Intent.CATEGORY_BROWSABLE)

        assertNull(intent.resolveActivity(context.packageManager))
    }

    @Test
    fun browserLaunchIntentUsesANewTaskAndLeavesCallbackOwnershipWithMainActivity() {
        val intent = appleBrowserIntent("https://example.test/android/apple/sign_in?transaction_id=$HANDLE")

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https", intent.data?.scheme)
        assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals(null, intent.component)
        assertEquals(null, intent.`package`)
    }

    private companion object {
        const val HANDLE = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        const val CODE = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"
        const val DEBUG_CALLBACK =
            "com.getmaincourse.app.debug:/oauth/apple?transaction_id=$HANDLE&exchange_code=$CODE"
    }
}
