package com.getmaincourse.app.features.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.getmaincourse.app.data.CookbookRepository
import com.getmaincourse.app.data.CookbookSelection
import com.getmaincourse.app.data.RecipeRepository
import com.getmaincourse.app.data.model.RecipeSummary
import com.getmaincourse.app.data.network.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

data class SearchUiState(
    val query: String = "",
    val selectedCookbookId: Long? = null,
    val selectedCookbookName: String? = null,
    val results: List<RecipeSummary> = emptyList(),
    val preparing: Boolean = true,
    val searching: Boolean = false,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel internal constructor(
    observeCookbooks: () -> Flow<CookbookSelection>,
    private val prepareSearchIndex: suspend (Long) -> Unit,
    private val searchRecipes: (Long, String) -> Flow<List<RecipeSummary>>,
) : ViewModel() {
    constructor(
        userId: Long,
        cookbookRepository: CookbookRepository,
        recipeRepository: RecipeRepository,
    ) : this(
        observeCookbooks = { cookbookRepository.observe(userId) },
        prepareSearchIndex = { cookbookId -> recipeRepository.rebuildSearchIndex(userId, cookbookId) },
        searchRecipes = { cookbookId, query -> recipeRepository.searchSummaries(userId, cookbookId, query) },
    )

    private val query = MutableStateFlow("")
    private val retryGeneration = MutableStateFlow(0)

    private val content = combine(
        observeCookbooks().distinctUntilChanged(),
        retryGeneration,
    ) { selection, _ -> selection }.flatMapLatest { selection ->
        val cookbookId = selection.selectedId
        val cookbookName = selection.cookbooks.firstOrNull { it.id == cookbookId }?.name
        if (cookbookId == null) {
            flowOf(SearchContent(cookbookId = null, cookbookName = null))
        } else {
            flow {
                emit(SearchContent(cookbookId, cookbookName, preparing = true))
                prepareSearchIndex(cookbookId)
                emitAll(
                    query.flatMapLatest { rawQuery ->
                        val trimmed = rawQuery.trim()
                        if (trimmed.isEmpty()) {
                            flowOf(SearchContent(cookbookId, cookbookName))
                        } else {
                            searchRecipes(cookbookId, trimmed)
                                .map { results -> SearchContent(cookbookId, cookbookName, results = results) }
                                .onStart {
                                    emit(SearchContent(cookbookId, cookbookName, searching = true))
                                }
                                .catch { failure ->
                                    if (failure is CancellationException) throw failure
                                    emit(
                                        SearchContent(
                                            cookbookId,
                                            cookbookName,
                                            error = failure.userMessage("Could not search recipes"),
                                        ),
                                    )
                                }
                        }
                    },
                )
            }.catch { failure ->
                if (failure is CancellationException) throw failure
                emit(
                    SearchContent(
                        cookbookId,
                        cookbookName,
                        error = failure.userMessage("Could not prepare recipe search"),
                    ),
                )
            }
        }
    }

    val state = combine(query, content) { currentQuery, currentContent ->
        SearchUiState(
            query = currentQuery,
            selectedCookbookId = currentContent.cookbookId,
            selectedCookbookName = currentContent.cookbookName,
            results = currentContent.results,
            preparing = currentContent.preparing,
            searching = currentContent.searching,
            error = currentContent.error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchUiState(),
    )

    fun updateQuery(value: String) {
        query.value = value.take(MAX_QUERY_LENGTH)
    }

    fun retry() {
        retryGeneration.update { it + 1 }
    }
}

private data class SearchContent(
    val cookbookId: Long?,
    val cookbookName: String?,
    val results: List<RecipeSummary> = emptyList(),
    val preparing: Boolean = false,
    val searching: Boolean = false,
    val error: String? = null,
)

private const val MAX_QUERY_LENGTH = 200
