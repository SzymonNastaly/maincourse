package com.getmaincourse.app.features.recipes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getmaincourse.app.data.CookbookRepository
import com.getmaincourse.app.data.CookbookSelection
import com.getmaincourse.app.data.RecipeRepository
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.RecipeImportResponse
import com.getmaincourse.app.data.network.userMessage
import java.net.URI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class RecipeImportMode { URL, TEXT }

data class RecipeImportUiState(
    val cookbooks: List<Cookbook> = emptyList(),
    val selectedCookbookId: Long? = null,
    val mode: RecipeImportMode = RecipeImportMode.URL,
    val url: String = "",
    val text: String = "",
    val importing: Boolean = false,
    val error: String? = null,
    val importedRecipeId: Long? = null,
) {
    val canSubmit: Boolean
        get() = selectedCookbookId != null && !importing && when (mode) {
            RecipeImportMode.URL -> url.isNotBlank()
            RecipeImportMode.TEXT -> text.isNotBlank()
        }
}

class RecipeImportViewModel internal constructor(
    observeCookbooks: () -> Flow<CookbookSelection>,
    private val refreshCookbooks: suspend () -> Unit,
    private val selectCookbook: suspend (Long) -> Unit,
    private val importUrl: suspend (Long, String) -> RecipeImportResponse,
    private val importText: suspend (Long, String) -> RecipeImportResponse,
) : ViewModel() {
    constructor(
        userId: Long,
        cookbookRepository: CookbookRepository,
        recipeRepository: RecipeRepository,
    ) : this(
        observeCookbooks = { cookbookRepository.observe(userId) },
        refreshCookbooks = { cookbookRepository.refresh(userId) },
        selectCookbook = { cookbookId -> cookbookRepository.select(userId, cookbookId) },
        importUrl = { cookbookId, url -> recipeRepository.importUrl(userId, cookbookId, url) },
        importText = { cookbookId, text -> recipeRepository.importText(userId, cookbookId, text) },
    )

    private val operation = MutableStateFlow(ImportOperationState())
    private val cookbookSelection = observeCookbooks().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = CookbookSelection(emptyList(), null),
    )

    val state = combine(cookbookSelection, operation) { selection, operation ->
        RecipeImportUiState(
            cookbooks = selection.cookbooks,
            selectedCookbookId = selection.selectedId,
            mode = operation.mode,
            url = operation.url,
            text = operation.text,
            importing = operation.importing,
            error = operation.error,
            importedRecipeId = operation.importedRecipeId,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = RecipeImportUiState(),
    )

    init {
        viewModelScope.launch {
            try {
                refreshCookbooks()
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Throwable) {
                operation.value = operation.value.copy(
                    error = failure.userMessage("Could not load cookbooks"),
                )
            }
        }
    }

    fun setMode(mode: RecipeImportMode) {
        operation.value = operation.value.copy(mode = mode, error = null)
    }

    fun updateUrl(url: String) {
        operation.value = operation.value.copy(url = url, error = null)
    }

    fun updateText(text: String) {
        operation.value = operation.value.copy(text = text, error = null)
    }

    fun acceptSharedInput(value: String) {
        val input = value.trim()
        operation.value = if (input.isHttpUrl()) {
            operation.value.copy(mode = RecipeImportMode.URL, url = input, error = null)
        } else {
            operation.value.copy(mode = RecipeImportMode.TEXT, text = input, error = null)
        }
    }

    fun selectCookbook(cookbookId: Long): Job = viewModelScope.launch {
        operation.value = operation.value.copy(error = null)
        try {
            selectCookbook.invoke(cookbookId)
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            operation.value = operation.value.copy(
                error = failure.userMessage("Could not select cookbook"),
            )
        }
    }

    fun submit(): Job = viewModelScope.launch {
        val snapshot = operation.value
        if (snapshot.importing) return@launch
        val cookbookId = cookbookSelection.value.selectedId
        val input = when (snapshot.mode) {
            RecipeImportMode.URL -> snapshot.url.trim()
            RecipeImportMode.TEXT -> snapshot.text.trim()
        }
        val validationError = when {
            cookbookId == null -> "Choose a cookbook first."
            input.isBlank() -> if (snapshot.mode == RecipeImportMode.URL) {
                "Enter a recipe link."
            } else {
                "Paste some recipe text."
            }
            snapshot.mode == RecipeImportMode.URL && !input.isHttpUrl() ->
                "Enter a valid http(s) recipe link."
            snapshot.mode == RecipeImportMode.TEXT && input.length > MAX_RECIPE_IMPORT_TEXT_LENGTH ->
                "Recipe text must be 50,000 characters or fewer."
            else -> null
        }
        if (validationError != null) {
            operation.value = operation.value.copy(error = validationError)
            return@launch
        }

        operation.value = operation.value.copy(importing = true, error = null, importedRecipeId = null)
        try {
            val response = when (snapshot.mode) {
                RecipeImportMode.URL -> importUrl(checkNotNull(cookbookId), input)
                RecipeImportMode.TEXT -> importText(checkNotNull(cookbookId), input)
            }
            operation.value = operation.value.copy(
                importing = false,
                importedRecipeId = response.id,
            )
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            operation.value = operation.value.copy(
                importing = false,
                error = failure.userMessage("Could not import recipe"),
            )
        }
    }

    fun acknowledgeImport() {
        operation.value = operation.value.copy(importedRecipeId = null)
    }

    private data class ImportOperationState(
        val mode: RecipeImportMode = RecipeImportMode.URL,
        val url: String = "",
        val text: String = "",
        val importing: Boolean = false,
        val error: String? = null,
        val importedRecipeId: Long? = null,
    )
}

internal const val MAX_RECIPE_IMPORT_TEXT_LENGTH = 50_000

private fun String.isHttpUrl(): Boolean {
    val uri = runCatching { URI(this) }.getOrNull() ?: return false
    return uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()
}
