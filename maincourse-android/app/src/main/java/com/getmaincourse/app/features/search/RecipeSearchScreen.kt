package com.getmaincourse.app.features.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import com.getmaincourse.app.R
import com.getmaincourse.app.data.cache.RecipeScope
import com.getmaincourse.app.features.recipes.RecipeCard
import com.getmaincourse.app.features.recipes.RecipeActionFeedback
import com.getmaincourse.app.features.recipes.RecipeActionState
import com.getmaincourse.app.ui.theme.MainCourseColors

@Composable
fun RecipeSearchScreen(
    scope: RecipeScope,
    state: RecipeSearchState,
    imageLoader: ImageLoader?,
    resolveImage: (String?) -> String?,
    onQueryChange: (String) -> Unit,
    onOpenRecipe: (Long) -> Unit,
    onEditRecipe: (Long) -> Unit = {},
    onMoveRecipe: (Long) -> Unit = {},
    onDeleteRecipe: (Long) -> Unit = {},
    actionsEnabled: Boolean = true,
    actionState: RecipeActionState = RecipeActionState(),
    onRefresh: () -> Unit = {},
    onClearAction: () -> Unit = {},
) {
    val scoped = state.takeIf { it.scope == scope } ?: RecipeSearchState(scope = scope)
    var savedUser by rememberSaveable { mutableStateOf<Long?>(null) }
    var savedCookbook by rememberSaveable { mutableStateOf<Long?>(null) }
    var savedQuery by rememberSaveable { mutableStateOf("") }
    val restoredForScope = savedUser == scope.userId && savedCookbook == scope.cookbookId
    if (!restoredForScope) {
        SideEffect {
            savedUser = scope.userId
            savedCookbook = scope.cookbookId
            savedQuery = scoped.query
        }
    }
    val query = if (restoredForScope) savedQuery else scoped.query
    LaunchedEffect(scope, restoredForScope) {
        if (restoredForScope && query != scoped.query) onQueryChange(query)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("screen_Search"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { savedQuery = it; onQueryChange(it) },
                modifier = Modifier.fillMaxWidth().testTag("search_query"),
                label = { Text(stringResource(R.string.search_recipes)) },
                singleLine = true,
            )
        }
        if (scoped.hydrationStatus == SearchHydrationStatus.INCOMPLETE) {
            item { Text(scoped.message ?: stringResource(R.string.search_incomplete), color = MainCourseColors.Body) }
        }
        if (actionState.message != null && actionState.scope == scope) {
            item { RecipeActionFeedback(actionState, onRefresh, onClearAction) }
        }
        when {
            query.isBlank() -> item {
                Text(stringResource(R.string.search_prompt), modifier = Modifier.testTag("search_prompt"), color = MainCourseColors.Body)
            }
            scoped.results.isEmpty() -> item { Text(stringResource(R.string.search_empty), color = MainCourseColors.Body) }
            else -> items(scoped.results, key = { it.id }) { recipe ->
                RecipeCard(recipe, imageLoader, resolveImage, onOpenRecipe, onEditRecipe, onMoveRecipe, onDeleteRecipe, actionsEnabled)
            }
        }
    }
}
