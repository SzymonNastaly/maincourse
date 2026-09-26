package com.getmaincourse.app.features.recipes

import androidx.lifecycle.ViewModel
import com.getmaincourse.app.R
import com.getmaincourse.app.ui.UiMessage
import androidx.lifecycle.viewModelScope
import com.getmaincourse.app.data.CookbookSelection
import com.getmaincourse.app.data.model.RecipeImportResponse
import com.getmaincourse.app.data.model.RecipePageContent
import com.getmaincourse.app.data.network.ApiFailure
import com.getmaincourse.app.data.network.userMessage
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

sealed interface RecipeShareStatus {
    data object Preparing : RecipeShareStatus

    data class ReadingPage(
        val requestId: Long,
        val url: String,
    ) : RecipeShareStatus

    data object Sending : RecipeShareStatus

    data object Success : RecipeShareStatus

    data class Failed(val message: UiMessage) : RecipeShareStatus
}

data class RecipeShareUiState(
    val destinationName: String? = null,
    val status: RecipeShareStatus = RecipeShareStatus.Preparing,
)

class RecipeShareViewModel(
    private val content: RecipeShareContent,
    observeCookbooks: () -> Flow<CookbookSelection>,
    private val refreshCookbooks: suspend () -> Unit,
    private val importUrl: suspend (Long, String) -> RecipeImportResponse,
    private val importContent: suspend (Long, RecipePageContent) -> RecipeImportResponse,
    private val importText: suspend (Long, String) -> RecipeImportResponse,
    private val importImage: suspend (Long, String, String) -> RecipeImportResponse,
) : ViewModel() {
    private val mutableState = MutableStateFlow(RecipeShareUiState())
    private var cookbookSelection = CookbookSelection(emptyList(), null)
    private var importStarted = false
    private var importJob: Job? = null
    private var nextPageRequestId = 0L

    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            observeCookbooks().collect { selection ->
                cookbookSelection = selection
                mutableState.value = mutableState.value.copy(
                    destinationName = selection.cookbooks.firstOrNull { it.id == selection.selectedId }?.name,
                )
                maybeStartImport()
            }
        }
        refreshSelection()
    }

    fun pageExtractionFinished(requestId: Long, pageContent: RecipePageContent?) {
        val reading = mutableState.value.status as? RecipeShareStatus.ReadingPage ?: return
        if (reading.requestId != requestId || importJob?.isActive == true) return

        importJob = viewModelScope.launch {
            val usefulContent = pageContent?.takeIf {
                it.url.isHttpUrl() && (it.jsonLd.isNotEmpty() || it.html.isNotBlank())
            }
            if (usefulContent == null) {
                sendImport { importUrl(requireCookbookId(), reading.url) }
                return@launch
            }

            mutableState.value = mutableState.value.copy(status = RecipeShareStatus.Sending)
            try {
                importContent(requireCookbookId(), usefulContent)
                mutableState.value = mutableState.value.copy(status = RecipeShareStatus.Success)
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                if (failure.isPayloadTooLarge()) {
                    sendImport { importUrl(requireCookbookId(), reading.url) }
                } else {
                    fail(failure)
                }
            }
        }
    }

    fun retry() {
        if (mutableState.value.status !is RecipeShareStatus.Failed) return
        importJob?.cancel()
        importJob = null
        importStarted = false
        mutableState.value = mutableState.value.copy(status = RecipeShareStatus.Preparing)
        if (cookbookSelection.selectedId == null) refreshSelection()
        maybeStartImport()
    }

    private fun refreshSelection() {
        viewModelScope.launch {
            try {
                refreshCookbooks()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                if (cookbookSelection.selectedId == null && !importStarted) fail(failure, UiMessage.Resource(R.string.error_load_cookbooks))
            }
        }
    }

    private fun maybeStartImport() {
        val cookbookId = cookbookSelection.selectedId ?: return
        if (importStarted) return
        importStarted = true

        when (val shared = content) {
            is RecipeShareContent.Url -> {
                if (shouldRenderSharedPage(shared.value)) {
                    nextPageRequestId += 1
                    mutableState.value = mutableState.value.copy(
                        status = RecipeShareStatus.ReadingPage(nextPageRequestId, shared.value),
                    )
                } else {
                    importJob = viewModelScope.launch {
                        sendImport { importUrl(cookbookId, shared.value) }
                    }
                }
            }
            is RecipeShareContent.Text -> {
                importJob = viewModelScope.launch {
                    sendImport { importText(cookbookId, shared.value) }
                }
            }
            is RecipeShareContent.Image -> {
                importJob = viewModelScope.launch {
                    sendImport { importImage(cookbookId, shared.uri, shared.mimeType) }
                }
            }
        }
    }

    private suspend fun sendImport(request: suspend () -> RecipeImportResponse) {
        mutableState.value = mutableState.value.copy(status = RecipeShareStatus.Sending)
        try {
            request()
            mutableState.value = mutableState.value.copy(status = RecipeShareStatus.Success)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            fail(failure)
        }
    }

    private fun fail(failure: Throwable, fallback: UiMessage = UiMessage.Resource(R.string.error_import_recipe)) {
        val message = failure.userMessage(fallback)
        mutableState.value = mutableState.value.copy(status = RecipeShareStatus.Failed(message))
    }

    private fun requireCookbookId(): Long = checkNotNull(cookbookSelection.selectedId)
}

internal fun shouldRenderSharedPage(url: String): Boolean {
    val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return false
    return BACKEND_ONLY_DOMAINS.none { domain -> host == domain || host.endsWith(".$domain") }
}

private fun Throwable.isPayloadTooLarge(): Boolean =
    (this is HttpException && code() == 413) || (this is ApiFailure && status == 413)

private val BACKEND_ONLY_DOMAINS = setOf(
    "instagram.com",
    "tiktok.com",
    "youtube.com",
    "youtu.be",
)
