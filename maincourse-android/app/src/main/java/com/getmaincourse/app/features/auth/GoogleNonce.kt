package com.getmaincourse.app.features.auth

import java.security.SecureRandom
import java.util.Base64

object GoogleNonce {
    private val secureRandom = SecureRandom()

    fun generate(): String = ByteArray(32)
        .also(secureRandom::nextBytes)
        .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
}
