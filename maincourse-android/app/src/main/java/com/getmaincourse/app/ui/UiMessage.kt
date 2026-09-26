package com.getmaincourse.app.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import com.getmaincourse.app.data.network.ApiProblem
import com.getmaincourse.app.data.network.ApiStrings

/** Keep messages unresolved in retained view-model state so app-language changes apply. */
sealed interface UiMessage {
    data class Resource(@param:StringRes val id: Int) : UiMessage
    data class Api(val problem: ApiProblem, val status: Int?) : UiMessage

    fun resolve(strings: ApiStrings): String = when (this) {
        is Resource -> strings.text(id)
        is Api -> problem.message(strings, status)
    }
}

@Composable
fun UiMessage.localized(): String {
    val resources = LocalResources.current
    return resolve { id, arguments -> resources.getString(id, *arguments) }
}
