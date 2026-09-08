package com.getmaincourse.app.features.auth

import androidx.credentials.exceptions.ClearCredentialUnknownException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GoogleCredentialSessionCleanerTest {
    @Test
    fun sdkFailureIsNonfatal() = runTest {
        GoogleCredentialSessionCleaner { throw ClearCredentialUnknownException() }.clear()
    }

    @Test
    fun clearHasFiniteTwoSecondBudget() = runTest {
        var completed = false
        GoogleCredentialSessionCleaner {
            delay(10_000)
            completed = true
        }.clear()

        assertEquals(2_000, testScheduler.currentTime)
        assertEquals(false, completed)
    }
}
