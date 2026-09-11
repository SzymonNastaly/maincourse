package com.getmaincourse.app.features.recipes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.getmaincourse.app.R
import com.getmaincourse.app.data.images.heroImagePath
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.ui.theme.MainCourseColors
import com.getmaincourse.app.ui.theme.MainCourseMono
import com.getmaincourse.app.ui.theme.MainCourseShapes
import java.net.URI

@Composable
fun RecipeDetailScreen(
    state: RecipeDetailUiState,
    imageLoader: ImageLoader?,
    resolveImage: (String?) -> String?,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    cookbookId: Long = 0,
    actionState: RecipeActionUiState = RecipeActionUiState.Idle,
    onMove: (Long) -> Unit = {},
    onDelete: () -> Unit = {},
    onEdit: () -> Unit = {},
    onAddIngredients: (Int) -> Unit = {},
    onActionSucceeded: () -> Unit = {},
) {
    LaunchedEffect(actionState) {
        if (actionState is RecipeActionUiState.Succeeded) onActionSucceeded()
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("recipe_detail"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            OutlinedButton(onClick = onBack, modifier = Modifier.testTag("recipe_back")) {
                Text(stringResource(R.string.back))
            }
        }
        val recipe = state.recipe
        if (recipe == null) {
            item {
                when {
                    state.loading -> LoadingState()
                    state.error != null -> RetryState(state.error, onRefresh)
                    else -> RetryState(stringResource(R.string.recipe_unavailable), onRefresh)
                }
            }
        } else {
            state.error?.let { error -> item { ErrorBanner(error, onRefresh) } }
            item(key = "detail-${recipe.id}") {
                DetailContent(
                    recipe = recipe,
                    imageLoader = imageLoader,
                    resolveImage = resolveImage,
                    refreshing = state.refreshing,
                    onRefresh = onRefresh,
                    onMove = onMove,
                    onDelete = onDelete,
                    onEdit = onEdit,
                    onAddIngredients = onAddIngredients,
                    cookbooks = state.cookbooks,
                    cookbookId = cookbookId,
                    actionState = actionState,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun DetailContent(
    recipe: RecipeDetail,
    imageLoader: ImageLoader?,
    resolveImage: (String?) -> String?,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onMove: (Long) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onAddIngredients: (Int) -> Unit,
    cookbooks: List<Cookbook>,
    cookbookId: Long,
    actionState: RecipeActionUiState,
) {
    val baseServings = recipe.servings?.takeIf { it > 0 }
    var portions by rememberSaveable(recipe.id) { mutableIntStateOf(baseServings?.coerceIn(1, 64) ?: 1) }
    var sourceOpenFailed by remember(recipe.id) { mutableStateOf(false) }
    var showMove by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    val actionRunning = actionState is RecipeActionUiState.Running
    val moveTargets = cookbooks.filter { it.id != cookbookId }

    if (showMove) {
        AlertDialog(
            onDismissRequest = { if (!actionRunning) showMove = false },
            title = { Text(stringResource(R.string.recipe_move_title, recipe.name)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    moveTargets.forEach { cookbook ->
                        OutlinedButton(
                            onClick = {
                                showMove = false
                                onMove(cookbook.id)
                            },
                            enabled = !actionRunning,
                            modifier = Modifier.fillMaxWidth().testTag("move_target_${cookbook.id}"),
                        ) {
                            Text(stringResource(R.string.recipe_move_target, cookbook.name))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                OutlinedButton(onClick = { showMove = false }, enabled = !actionRunning) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { if (!actionRunning) showDelete = false },
            title = { Text(stringResource(R.string.recipe_delete_title, recipe.name)) },
            text = { Text(stringResource(R.string.recipe_delete_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDelete = false
                        onDelete()
                    },
                    enabled = !actionRunning,
                    modifier = Modifier.testTag("confirm_delete"),
                ) { Text(stringResource(R.string.recipe_delete)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDelete = false }, enabled = !actionRunning) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        val image = resolveImage(recipe.heroImagePath())
        if (image != null && imageLoader != null) {
            AsyncImage(
                model = image,
                imageLoader = imageLoader,
                contentDescription = recipe.name,
                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 360.dp),
                contentScale = ContentScale.Crop,
            )
        }
        Text(recipe.name, style = MaterialTheme.typography.headlineMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onRefresh, enabled = !refreshing) {
                Text(stringResource(R.string.retry))
            }
            OutlinedButton(
                onClick = onEdit,
                enabled = !actionRunning,
                modifier = Modifier.testTag("recipe_edit_open"),
            ) {
                Text(stringResource(R.string.recipe_edit))
            }
            OutlinedButton(
                onClick = { showMove = true },
                enabled = !actionRunning && moveTargets.isNotEmpty(),
                modifier = Modifier.testTag("recipe_move"),
            ) {
                Text(stringResource(R.string.recipe_move))
            }
            OutlinedButton(
                onClick = { showDelete = true },
                enabled = !actionRunning,
                modifier = Modifier.testTag("recipe_delete"),
            ) {
                Text(stringResource(R.string.recipe_delete))
            }
        }
        if (actionState is RecipeActionUiState.Failed) {
            Text(actionState.message, color = MainCourseColors.Danger, modifier = Modifier.testTag("recipe_action_error"))
        }
        if (actionRunning) CircularProgressIndicator(Modifier.testTag("recipe_action_running"))
        if (refreshing) CircularProgressIndicator(Modifier.testTag("recipe_refreshing"))
        val facts = buildList {
            recipe.prepTime?.takeIf { it > 0 }?.let { add(stringResource(R.string.recipe_prep) to stringResource(R.string.recipe_time, it)) }
            recipe.cookTime?.takeIf { it > 0 }?.let { add(stringResource(R.string.recipe_cook) to stringResource(R.string.recipe_time, it)) }
            baseServings?.let { add("" to pluralStringResource(R.plurals.recipe_servings, it, it)) }
        }
        if (facts.isNotEmpty()) {
            Surface(shape = MainCourseShapes.Panel, color = MainCourseColors.Surface, border = BorderStroke(1.dp, MainCourseColors.Hairline)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    facts.forEach { (label, value) ->
                        Column {
                            if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.labelSmall, color = MainCourseColors.Muted)
                            Text(value, fontFamily = MainCourseMono)
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.recipe_portions))
            OutlinedButton(enabled = baseServings != null && portions > 1, onClick = { portions-- }) { Text("−") }
            Text(portions.toString(), fontFamily = MainCourseMono, modifier = Modifier.testTag("recipe_portions"))
            OutlinedButton(
                enabled = baseServings != null && portions < 64,
                onClick = { portions++ },
                modifier = Modifier.testTag("portion_increment"),
            ) { Text("+") }
        }
        val ingredientLines = if (recipe.structuredIngredients.isNotEmpty()) {
            recipe.structuredIngredients.sortedBy { it.position }.map {
                if (baseServings == null) it.raw else IngredientFormatter.formatIngredient(it, portions, baseServings)
            }
        } else {
            recipe.ingredients
        }
        if (ingredientLines.isNotEmpty()) Section(R.string.recipe_ingredients, ingredientLines)
        Button(
            enabled = !actionRunning && ingredientLines.isNotEmpty(),
            onClick = { onAddIngredients(portions) },
            modifier = Modifier.testTag("recipe_add_ingredients"),
        ) {
            Text(stringResource(R.string.recipe_add_to_shopping))
        }
        if (recipe.instructions.isNotEmpty()) {
            Section(R.string.recipe_steps, recipe.instructions.mapIndexed { index, step -> "${index + 1}. $step" })
        }
        recipe.notes?.takeIf(String::isNotBlank)?.let { Section(R.string.recipe_notes, listOf(it)) }
        recipe.sourceUrl?.takeIf(::isSafeDetailSourceUrl)?.let { source ->
            val uriHandler = LocalUriHandler.current
            OutlinedButton(
                onClick = {
                    sourceOpenFailed = try {
                        uriHandler.openUri(source)
                        false
                    } catch (_: RuntimeException) {
                        true
                    }
                },
                modifier = Modifier.testTag("recipe_source"),
            ) { Text(source) }
            if (sourceOpenFailed) {
                Text(
                    stringResource(R.string.recipe_source_open_failed),
                    color = MainCourseColors.Danger,
                    modifier = Modifier.testTag("source_open_error"),
                )
            }
        }
    }
}

@Composable
private fun Section(title: Int, lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
        lines.filter(String::isNotBlank).forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun RetryState(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(message, color = MainCourseColors.Body)
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Surface(shape = MainCourseShapes.Panel, color = MainCourseColors.DangerTint) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, modifier = Modifier.weight(1f), color = MainCourseColors.Danger)
            Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

private fun isSafeDetailSourceUrl(value: String): Boolean = try {
    val uri = URI(value)
    uri.scheme in setOf("http", "https") && uri.host != null && uri.userInfo == null
} catch (_: IllegalArgumentException) {
    false
}
