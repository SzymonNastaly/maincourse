package com.getmaincourse.app.features.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object ApplePkce {
    private val random = SecureRandom()

    fun generateVerifier(): String = ByteArray(32).also(random::nextBytes).base64Url()

    fun challenge(verifier: String): String =
        MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)).base64Url()

    private fun ByteArray.base64Url(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(this)
}
