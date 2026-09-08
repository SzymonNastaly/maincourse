package com.getmaincourse.app.features.search

import com.getmaincourse.app.data.cache.RecipeScope
import com.getmaincourse.app.data.model.RecipeSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SearchHydrationStatus { IDLE, HYDRATING, COMPLETE, INCOMPLETE }

data class RecipeSearchState(
    val query: String = "",
    val results: List<RecipeSummary> = emptyList(),
    val hydrationStatus: SearchHydrationStatus = SearchHydrationStatus.IDLE,
    val message: String? = null,
)

internal class RecipeSearchCoordinator(
    private val scope: CoroutineScope,
    private val loadDocuments: suspend (RecipeScope) -> List<RecipeSearchDocument>,
    private val engine: RecipeSearchEngine = RecipeSearchEngine(),
    private val searchDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    private val lock = Any()
    private val queries = mutableMapOf<RecipeScope, String>()
    private val mutableState = MutableStateFlow(RecipeSearchState())
    val state: StateFlow<RecipeSearchState> = mutableState.asStateFlow()
    private var activeScope: RecipeScope? = null
    private var generation = 0L
    private var searchJob: Job? = null

    fun activate(recipeScope: RecipeScope?): Job {
        val query = synchronized(lock) {
            generation++
            searchJob?.cancel()
            searchJob = null
            activeScope = recipeScope
            val saved = recipeScope?.let { queries[it] }.orEmpty()
            mutableState.value = RecipeSearchState(query = saved)
            saved
        }
        return if (recipeScope != null && query.isNotBlank()) launchSearch(recipeScope, query) else done()
    }

    fun updateQuery(query: String): Job {
        val recipeScope = synchronized(lock) {
            val current = activeScope
            if (current != null) queries[current] = query
            generation++
            searchJob?.cancel()
            searchJob = null
            mutableState.value = mutableState.value.copy(query = query, results = emptyList())
            current
        }
        return if (recipeScope != null && query.isNotBlank()) launchSearch(recipeScope, query) else done()
    }

    fun documentsChanged(recipeScope: RecipeScope): Job {
        val query = synchronized(lock) {
            if (activeScope != recipeScope) return done()
            mutableState.value.query
        }
        return if (query.isBlank()) done() else launchSearch(recipeScope, query)
    }

    fun setHydrationStatus(recipeScope: RecipeScope, status: SearchHydrationStatus, message: String? = null) {
        synchronized(lock) {
            if (activeScope == recipeScope) {
                mutableState.value = mutableState.value.copy(hydrationStatus = status, message = message)
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            generation++
            searchJob?.cancel()
            searchJob = null
            activeScope = null
            queries.clear()
            mutableState.value = RecipeSearchState()
        }
    }

    private fun launchSearch(recipeScope: RecipeScope, query: String): Job {
        lateinit var launched: Job
        val version = synchronized(lock) {
            generation++
            searchJob?.cancel()
            generation
        }
        launched = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val documents = loadDocuments(recipeScope)
                val results = withContext(searchDispatcher) { engine.search(documents, query) }
                synchronized(lock) {
                    if (generation == version && activeScope == recipeScope && mutableState.value.query == query) {
                        mutableState.value = mutableState.value.copy(results = results)
                    }
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (_: Throwable) {
                synchronized(lock) {
                    if (generation == version && activeScope == recipeScope && mutableState.value.query == query) {
                        mutableState.value = mutableState.value.copy(results = emptyList())
                    }
                }
            } finally {
                synchronized(lock) { if (searchJob == launched) searchJob = null }
            }
        }
        synchronized(lock) {
            if (generation == version && activeScope == recipeScope) searchJob = launched else launched.cancel()
        }
        launched.start()
        return launched
    }

    private fun done(): Job = Job().apply { complete() }
}
