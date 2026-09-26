package com.getmaincourse.app.data

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.getmaincourse.app.data.network.ApiErrorCode
import com.getmaincourse.app.data.network.ApiProblem
import com.getmaincourse.app.data.network.ApiStrings
import com.getmaincourse.app.data.network.ImportErrorCode
import com.getmaincourse.app.data.network.importFailureMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiErrorResourcesTest {
    @Test
    fun everyKnownErrorHasPackagedNativeResources() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val strings = ApiStrings { resource, arguments -> context.getString(resource, *arguments) }
        for (code in ApiErrorCode.entries) {
            assertTrue(ApiProblem(code.wire, ApiProblem.Parameters(50)).message(strings).isNotBlank())
        }
        for (code in ImportErrorCode.entries) {
            assertTrue(importFailureMessage(code.wire, strings).isNotBlank())
        }
        assertEquals("Recipe text exceeds the character limit (50).",
            ApiProblem("text_too_long", ApiProblem.Parameters(50)).message(strings))
    }
}
