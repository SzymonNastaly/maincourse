package com.getmaincourse.app.features.recipes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
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
import com.getmaincourse.app.data.cache.RecipeScope
import com.getmaincourse.app.data.images.heroImagePath
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.features.session.DetailStatus
import com.getmaincourse.app.features.session.RecipeDetailState
import com.getmaincourse.app.ui.theme.MainCourseColors
import com.getmaincourse.app.ui.theme.MainCourseMono
import com.getmaincourse.app.ui.theme.MainCourseShapes

@Composable
fun RecipeDetailScreen(
    detailState: RecipeDetailState?,
    importFailed: Boolean,
    imageLoader: ImageLoader?,
    resolveImage: (String?) -> String?,
    onRetry: () -> Unit,
    scope: RecipeScope = RecipeScope(0, 0),
    actionState: RecipeActionState = RecipeActionState(),
    onEdit: () -> Unit = {},
    onMove: () -> Unit = {},
    onDelete: () -> Unit = {},
    onReviewIngredients: (Int) -> Unit = { _ -> },
    onCookingChanged: (Boolean) -> Unit = {},
    onRetryPhoto: () -> Unit = {},
    onRetryReconciliation: () -> Unit = {},
    onDismissAction: () -> Unit = {},
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("recipe_detail"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        val recipe = detailState?.recipe
        if (recipe != null) {
            if (detailState.status == DetailStatus.SAVED_OFFLINE) item(key = "offline-feedback") { Feedback(stringResource(R.string.recipe_saved_offline)) }
            item(key = "detail-${scope.userId}-${scope.cookbookId}-${recipe.id}") { DetailContent(
                scope, recipe, imageLoader, resolveImage, actionState, onEdit, onMove, onDelete,
                onReviewIngredients, onCookingChanged, onRetryPhoto, onRetryReconciliation,
                onDismissAction,
            ) }
        } else {
            item {
                when (detailState?.status) {
                    DetailStatus.ERROR -> RetryState(stringResource(R.string.recipe_load_error), onRetry)
                    DetailStatus.UNAVAILABLE -> RetryState(stringResource(R.string.recipe_unavailable), onRetry)
                    DetailStatus.NOT_READY -> Feedback(stringResource(if (importFailed) R.string.recipe_failed else R.string.recipe_processing))
                    else -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
            }
        }
    }
}

@Composable
private fun DetailContent(
    scope: RecipeScope,
    recipe: RecipeDetail,
    imageLoader: ImageLoader?,
    resolveImage: (String?) -> String?,
    actionState: RecipeActionState,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onReviewIngredients: (Int) -> Unit,
    onCookingChanged: (Boolean) -> Unit,
    onRetryPhoto: () -> Unit,
    onRetryReconciliation: () -> Unit,
    onDismissAction: () -> Unit,
) {
    val base = recipe.servings?.takeIf { it > 0 }
    val initialPortions = base?.coerceIn(1, 64) ?: 1
    var saved by rememberSaveable { mutableStateOf<String?>(null) }
    var savedState = RecipeUiSavedStateCodec.decodeDetail(saved, scope, recipe.id)
        ?: RecipeDetailSavedState(scope.userId, scope.cookbookId, recipe.id, initialPortions, false)
    SideEffect { if (saved == null || RecipeUiSavedStateCodec.decodeDetail(saved, scope, recipe.id) == null) saved = RecipeUiSavedStateCodec.encodeDetail(savedState) }
    fun update(value: RecipeDetailSavedState) { savedState = value; saved = RecipeUiSavedStateCodec.encodeDetail(value) }
    LaunchedEffect(savedState.cooking) { onCookingChanged(savedState.cooking) }
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    var sourceOpenFailed by remember(scope, recipe.id) { mutableStateOf(false) }
    val matchingAction = actionState.scope == scope && actionState.recipeId == recipe.id

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        val image = resolveImage(recipe.heroImagePath())
        if (image != null && imageLoader != null) {
            AsyncImage(model = image, imageLoader = imageLoader, contentDescription = recipe.name,
                modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 360.dp), contentScale = ContentScale.Crop)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(recipe.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium)
            Box {
                TextButton(enabled = !actionState.isBusy, onClick = { menuExpanded = true }, modifier = Modifier.testTag("detail_actions")) {
                    Text(stringResource(R.string.recipe_actions))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.recipe_edit)) }, onClick = { menuExpanded = false; onEdit() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.recipe_move)) }, onClick = { menuExpanded = false; onMove() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.recipe_delete)) }, onClick = { menuExpanded = false; onDelete() })
                }
            }
        }
        val facts = buildList {
            recipe.prepTime?.takeIf { it > 0 }?.let { add(stringResource(R.string.recipe_prep) to stringResource(R.string.recipe_time, it)) }
            recipe.cookTime?.takeIf { it > 0 }?.let { add(stringResource(R.string.recipe_cook) to stringResource(R.string.recipe_time, it)) }
            base?.let { add("" to pluralStringResource(R.plurals.recipe_servings, it, it)) }
        }
        if (facts.isNotEmpty()) {
            Surface(shape = MainCourseShapes.Panel, color = MainCourseColors.Surface, border = BorderStroke(1.dp, MainCourseColors.Hairline)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    facts.forEach { (label, value) -> Column {
                        if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.labelSmall, color = MainCourseColors.Muted)
                        Text(value, fontFamily = MainCourseMono)
                    } }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.recipe_portions))
            OutlinedButton(enabled = base != null && savedState.portions > 1, onClick = { update(savedState.copy(portions = savedState.portions - 1)) }) { Text("−") }
            Text(savedState.portions.toString(), fontFamily = MainCourseMono, modifier = Modifier.testTag("recipe_portions"))
            OutlinedButton(
                enabled = base != null && savedState.portions < 64,
                onClick = { update(savedState.copy(portions = savedState.portions + 1)) },
                modifier = Modifier.testTag("portion_increment"),
            ) { Text("+") }
        }
        val ingredientLines = if (recipe.structuredIngredients.isNotEmpty()) {
            recipe.structuredIngredients.sortedBy { it.position }.map {
                if (base == null) it.raw else IngredientFormatter.formatIngredient(it, savedState.portions, base)
            }
        } else recipe.ingredients
        if (ingredientLines.isNotEmpty()) Section(R.string.recipe_ingredients, ingredientLines)
        Button(enabled = ingredientLines.isNotEmpty() && !actionState.isBusy, onClick = { onReviewIngredients(savedState.portions) }) { Text(stringResource(R.string.recipe_add_to_shopping)) }
        if (recipe.instructions.isNotEmpty()) Section(R.string.recipe_steps, recipe.instructions.mapIndexed { index, step -> "${index + 1}. $step" })
        recipe.notes?.takeIf { it.isNotBlank() }?.let { Section(R.string.recipe_notes, listOf(it)) }
        recipe.sourceUrl?.takeIf(::isSafeRecipeSourceUrl)?.let { source ->
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.recipe_cooking_mode), modifier = Modifier.weight(1f))
            Switch(checked = savedState.cooking, onCheckedChange = { update(savedState.copy(cooking = it)) }, modifier = Modifier.testTag("cooking_mode"))
        }
        if (matchingAction && actionState.message != null) {
            Text(actionState.message, color = if (actionState.outcome == RecipeActionOutcome.SUCCEEDED) MainCourseColors.Accent else MainCourseColors.Danger)
            if (actionState.canRetryPhoto) Button(enabled = !actionState.isBusy, onClick = onRetryPhoto) { Text(stringResource(R.string.recipe_retry_photo)) }
            if (actionState.canRetryReconciliation) Button(enabled = !actionState.isBusy, onClick = onRetryReconciliation) { Text(stringResource(R.string.recipe_refresh_data)) }
            TextButton(enabled = !actionState.isBusy, onClick = onDismissAction) { Text(stringResource(R.string.dismiss)) }
        }
    }
}

@Composable private fun Section(title: Int, lines: List<String>) = Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
    lines.filter { it.isNotBlank() }.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
}

@Composable private fun Feedback(text: String) {
    Surface(shape = MainCourseShapes.Panel, color = MainCourseColors.AccentTint, border = BorderStroke(1.dp, MainCourseColors.AccentLine)) {
        Text(text, Modifier.fillMaxWidth().padding(16.dp), color = MainCourseColors.Body)
    }
}

@Composable private fun RetryState(text: String, onRetry: () -> Unit) = Column(
    Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp),
) {
    Text(text, color = MainCourseColors.Body)
    Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
}
