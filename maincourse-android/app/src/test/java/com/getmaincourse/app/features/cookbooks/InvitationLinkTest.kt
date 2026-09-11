package com.getmaincourse.app.features.cookbooks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InvitationLinkTest {
    @Test
    fun acceptsCanonicalLegacyAndCustomInvitationLinks() {
        assertEquals("abc123", invitationToken("https://app.getmaincourse.com/invite/abc123"))
        assertEquals("legacy", invitationToken("http://cook.hauptgang.app/invite/legacy"))
        assertEquals("custom", invitationToken("hauptgang://invite/custom"))
        assertEquals("query", invitationToken("hauptgang://invite?token=query"))
    }

    @Test
    fun rejectsForeignMalformedAndUnrelatedLinks() {
        assertNull(invitationToken("https://evil.example/invite/abc123"))
        assertNull(invitationToken("https://app.getmaincourse.com/recipes/abc123"))
        assertNull(invitationToken("https://app.getmaincourse.com/invite/abc123/extra"))
        assertNull(invitationToken("ftp://app.getmaincourse.com/invite/abc123"))
        assertNull(invitationToken("not a valid URL"))
        assertNull(invitationToken(null))
    }
}
