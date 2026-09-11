package com.getmaincourse.app.features.cookbooks

import java.net.URI
import java.net.URISyntaxException

internal fun invitationToken(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val uri = try {
        URI(url)
    } catch (_: URISyntaxException) {
        return null
    }
    val pathParts = uri.path.orEmpty().split('/').filter(String::isNotEmpty)
    return when {
        uri.scheme == "hauptgang" && uri.host == "invite" && pathParts.size == 1 -> pathParts.single()
        uri.scheme == "hauptgang" && uri.host == "invite" && pathParts.isEmpty() ->
            uri.rawQuery.orEmpty().split('&').firstNotNullOfOrNull { part ->
                part.split('=', limit = 2).takeIf { it.size == 2 && it[0] == "token" }?.get(1)
            }
        uri.scheme in setOf("http", "https") &&
            uri.host in INVITATION_HOSTS &&
            pathParts.size == 2 &&
            pathParts.first() == "invite" -> pathParts.last()
        else -> null
    }?.takeIf { it.isNotBlank() }
}

private val INVITATION_HOSTS = setOf("app.getmaincourse.com", "cook.hauptgang.app")
