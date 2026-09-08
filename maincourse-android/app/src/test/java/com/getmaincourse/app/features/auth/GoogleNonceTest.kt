package com.getmaincourse.app.features.auth

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleNonceTest {
    @Test
    fun generatesFresh32ByteUrlSafeUnpaddedValues() {
        val first = GoogleNonce.generate()
        val second = GoogleNonce.generate()

        assertEquals(32, Base64.getUrlDecoder().decode(first).size)
        assertTrue(first.matches(Regex("[A-Za-z0-9_-]+")))
        assertFalse(first.contains('='))
        assertNotEquals(first, second)
    }
}
