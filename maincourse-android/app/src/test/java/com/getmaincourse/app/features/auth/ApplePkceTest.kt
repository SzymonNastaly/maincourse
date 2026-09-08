package com.getmaincourse.app.features.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplePkceTest {
    @Test
    fun challengeMatchesTheRfc7636S256Vector() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            ApplePkce.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun generatedVerifierUsesTheRequiredUnpaddedUrlSafeAlphabet() {
        repeat(20) {
            val verifier = ApplePkce.generateVerifier()
            assertEquals(43, verifier.length)
            assertTrue(verifier.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        }
    }
}
