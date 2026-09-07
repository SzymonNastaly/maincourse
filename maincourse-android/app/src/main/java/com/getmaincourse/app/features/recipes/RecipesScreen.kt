package com.getmaincourse.app.features.recipes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.getmaincourse.app.R
import com.getmaincourse.app.data.images.cardImagePath
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.RecipeSummary
import com.getmaincourse.app.features.session.LoadStatus
import com.getmaincourse.app.ui.theme.MainCourseColors
import com.getmaincourse.app.ui.theme.MainCourseMono
import com.getmaincourse.app.ui.theme.MainCourseShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(
    cookbooks: List<Cookbook>,
    activeCookbookId: Long?,
    recipes: List<RecipeSummary>,
    recipesFetched: Boolean,
    status: LoadStatus,
    message: String?,
    imageLoader: ImageLoader?,
    resolveImage: (String?) -> String?,
    onSwitchCookbook: (Long) -> Unit,
    onRefresh: () -> Unit,
    onOpenRecipe: (Long) -> Unit,
) {
    val refreshing = status == LoadStatus.LOADING && recipesFetched
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize().testTag("screen_Recipes"),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(220.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                CookbookPicker(cookbooks, activeCookbookId, onSwitchCookbook)
            }
            if (status == LoadStatus.DEGRADED) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    StatusPanel(stringResource(R.string.recipes_saved), onRefresh)
                }
            } else if (status == LoadStatus.ERROR) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    StatusPanel(
                        if (recipes.isEmpty()) stringResource(R.string.recipes_load_error)
                        else message ?: stringResource(R.string.recipes_load_error),
                        onRefresh,
                    )
                }
            }
            when {
                cookbooks.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyPanel(stringResource(R.string.cookbooks_empty))
                }
                status == LoadStatus.LOADING && recipes.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    LoadingPanel()
                }
                recipesFetched && recipes.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyPanel(stringResource(R.string.recipes_empty))
                }
                else -> items(recipes, key = { it.id }) { recipe ->
                    RecipeCard(recipe, imageLoader, resolveImage, onOpenRecipe)
                }
            }
        }
    }
}

@Composable
private fun CookbookPicker(cookbooks: List<Cookbook>, activeId: Long?, onSwitch: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val active = cookbooks.firstOrNull { it.id == activeId }
    Box {
        Surface(
            modifier = Modifier.fillMaxWidth().clip(MainCourseShapes.Control)
                .clickable(enabled = cookbooks.isNotEmpty()) { expanded = true }
                .testTag("cookbook_picker"),
            shape = MainCourseShapes.Control,
            border = BorderStroke(1.dp, MainCourseColors.Hairline),
            color = MainCourseColors.Surface,
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(stringResource(R.string.cookbook_picker), style = MaterialTheme.typography.labelSmall, color = MainCourseColors.Muted)
                Text(active?.name ?: stringResource(R.string.cookbooks_empty), style = MaterialTheme.typography.titleMedium)
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            cookbooks.forEach { cookbook ->
                DropdownMenuItem(
                    text = { Text(cookbook.name) },
                    onClick = {
                        expanded = false
                        if (cookbook.id != activeId) onSwitch(cookbook.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun RecipeCard(
    recipe: RecipeSummary,
    imageLoader: ImageLoader?,
    resolveImage: (String?) -> String?,
    onOpenRecipe: (Long) -> Unit,
) {
    val ready = recipe.importStatus.isBlank() || recipe.importStatus == "completed"
    Surface(
        modifier = Modifier.fillMaxWidth().clip(MainCourseShapes.Card)
            .clickable(enabled = ready) { onOpenRecipe(recipe.id) },
        shape = MainCourseShapes.Card,
        color = MainCourseColors.Surface,
        border = BorderStroke(1.dp, MainCourseColors.Hairline),
    ) {
        Column {
            RecipeImage(recipe, imageLoader, resolveImage)
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(recipe.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val total = listOfNotNull(recipe.prepTime, recipe.cookTime).sum()
                if (total > 0) {
                    Text(stringResource(R.string.recipe_time, total), fontFamily = MainCourseMono, color = MainCourseColors.Body)
                }
                when (recipe.importStatus) {
                    "pending", "processing" -> Text(stringResource(R.string.recipe_processing), color = MainCourseColors.Body)
                    "failed" -> Text(stringResource(R.string.recipe_failed), color = MainCourseColors.Danger)
                }
            }
        }
    }
}

@Composable
private fun RecipeImage(recipe: RecipeSummary, imageLoader: ImageLoader?, resolveImage: (String?) -> String?) {
    val model = resolveImage(recipe.cardImagePath())
    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_recipes),
            contentDescription = stringResource(R.string.recipe_image_placeholder, recipe.name),
            tint = MainCourseColors.Muted,
            modifier = Modifier.size(36.dp),
        )
        if (model != null && imageLoader != null) {
            AsyncImage(
                model = model,
                imageLoader = imageLoader,
                contentDescription = recipe.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun StatusPanel(text: String, onRetry: () -> Unit) {
    Surface(shape = MainCourseShapes.Panel, color = MainCourseColors.Surface, border = BorderStroke(1.dp, MainCourseColors.Hairline)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, modifier = Modifier.weight(1f), color = MainCourseColors.Body)
            Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

@Composable
private fun EmptyPanel(text: String) {
    Surface(shape = MainCourseShapes.Panel, color = MainCourseColors.Surface, border = BorderStroke(1.dp, MainCourseColors.Hairline)) {
        Text(text, Modifier.fillMaxWidth().padding(24.dp), color = MainCourseColors.Body)
    }
}

@Composable
private fun LoadingPanel() {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator()
        Text(stringResource(R.string.recipes_loading), color = MainCourseColors.Body)
    }
}
