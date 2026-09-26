package com.getmaincourse.app.features.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import com.getmaincourse.app.R
import com.getmaincourse.app.ui.localized
import com.getmaincourse.app.features.recipes.RecipeCard
import com.getmaincourse.app.ui.theme.MainCourseColors
import com.getmaincourse.app.ui.theme.MainCourseShapes

@Composable
fun SearchScreen(
    state: SearchUiState,
    imageLoader: ImageLoader?,
    resolveImage: (String?) -> String?,
    onQueryChange: (String) -> Unit,
    onRetry: () -> Unit,
    onOpenRecipe: (Long) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(220.dp),
        modifier = Modifier.fillMaxSize().testTag("screen_Search"),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth().testTag("search_input"),
                enabled = state.selectedCookbookId != null,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = {
                    Icon(painterResource(R.drawable.ic_search), contentDescription = null)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = MainCourseShapes.Card,
            )
        }

        state.error?.let { error ->
            item(span = { GridItemSpan(maxLineSpan) }) {
                ErrorPanel(error.localized(), onRetry)
            }
        }

        when {
            state.preparing || state.searching -> item(span = { GridItemSpan(maxLineSpan) }) {
                LoadingPanel()
            }
            state.selectedCookbookId == null -> item(span = { GridItemSpan(maxLineSpan) }) {
                SearchMessage(
                    title = stringResource(R.string.cookbooks_empty),
                    body = null,
                    showIcon = false,
                )
            }
            state.query.isBlank() -> item(span = { GridItemSpan(maxLineSpan) }) {
                SearchMessage(
                    title = stringResource(R.string.search_prompt),
                    body = null,
                    showIcon = true,
                )
            }
            state.results.isEmpty() && state.error == null -> item(span = { GridItemSpan(maxLineSpan) }) {
                SearchMessage(
                    title = stringResource(R.string.search_no_results),
                    body = stringResource(R.string.search_try_another),
                    showIcon = false,
                )
            }
            else -> items(state.results, key = { it.id }) { recipe ->
                RecipeCard(recipe, imageLoader, resolveImage, onOpenRecipe)
            }
        }
    }
}

@Composable
private fun SearchMessage(title: String, body: String?, showIcon: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showIcon) {
            Icon(
                painterResource(R.drawable.ic_search),
                contentDescription = null,
                tint = MainCourseColors.Muted,
                modifier = Modifier.size(48.dp),
            )
        }
        Text(title, style = MaterialTheme.typography.titleMedium, color = MainCourseColors.Ink)
        body?.let { Text(it, color = MainCourseColors.Body) }
    }
}

@Composable
private fun ErrorPanel(text: String, onRetry: () -> Unit) {
    Surface(
        shape = MainCourseShapes.Panel,
        color = MainCourseColors.Surface,
        border = BorderStroke(1.dp, MainCourseColors.Hairline),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text, color = MainCourseColors.Body)
            Button(onClick = onRetry, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@Composable
private fun LoadingPanel() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
    }
}
