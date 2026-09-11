package com.getmaincourse.app.features.cookbooks

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.getmaincourse.app.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InvitationLinkIntentTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun canonicalAndCustomInvitationLinksResolveToMainActivity() {
        listOf(
            "https://app.getmaincourse.com/invite/test-token",
            "https://cook.hauptgang.app/invite/test-token",
            "hauptgang://invite/test-token",
        ).forEach { url ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setPackage(context.packageName)
            val resolved = context.packageManager.resolveActivity(intent, 0)

            assertEquals(MainActivity::class.java.name, resolved?.activityInfo?.name)
        }
    }
}
