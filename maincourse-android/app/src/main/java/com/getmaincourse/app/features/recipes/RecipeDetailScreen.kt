package com.getmaincourse.app.features.recipes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.getmaincourse.app.R
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
    imageLoader: ImageLoader?,
    resolveImage: (String?) -> String?,
    onRetry: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("recipe_detail"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        val recipe = detailState?.recipe
        if (recipe != null) {
            item { DetailHeader(recipe, imageLoader, resolveImage) }
            if (detailState.status == DetailStatus.SAVED_OFFLINE) {
                item { Feedback(stringResource(R.string.recipe_saved_offline)) }
            }
            val ingredients = recipe.structuredIngredients.takeIf { it.isNotEmpty() }?.map { it.raw }
                ?: recipe.ingredients
            if (ingredients.isNotEmpty()) {
                item { Section(R.string.recipe_ingredients, ingredients) }
            }
            if (recipe.instructions.isNotEmpty()) {
                item {
                    Section(
                        R.string.recipe_steps,
                        recipe.instructions.mapIndexed { index, step -> "${index + 1}. $step" },
                    )
                }
            }
            recipe.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                item { Section(R.string.recipe_notes, listOf(notes)) }
            }
        } else {
            item {
                when (detailState?.status) {
                    DetailStatus.ERROR -> RetryState(stringResource(R.string.recipe_load_error), onRetry)
                    DetailStatus.UNAVAILABLE -> RetryState(stringResource(R.string.recipe_unavailable), onRetry)
                    DetailStatus.NOT_READY -> Feedback(stringResource(R.string.recipe_processing))
                    else -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailHeader(recipe: RecipeDetail, imageLoader: ImageLoader?, resolveImage: (String?) -> String?) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
        val facts = buildList {
            recipe.prepTime?.takeIf { it > 0 }?.let { add(stringResource(R.string.recipe_prep) to stringResource(R.string.recipe_time, it)) }
            recipe.cookTime?.takeIf { it > 0 }?.let { add(stringResource(R.string.recipe_cook) to stringResource(R.string.recipe_time, it)) }
            recipe.servings?.takeIf { it > 0 }?.let {
                add("" to pluralStringResource(R.plurals.recipe_servings, it, it))
            }
        }
        if (facts.isNotEmpty()) {
            Surface(shape = MainCourseShapes.Panel, color = MainCourseColors.Surface, border = BorderStroke(1.dp, MainCourseColors.Hairline)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    facts.forEach { (label, value) ->
                        Column(Modifier.weight(1f)) {
                            if (label.isNotEmpty()) Text(label, style = MaterialTheme.typography.labelSmall, color = MainCourseColors.Muted)
                            Text(value, fontFamily = MainCourseMono)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: Int, lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
        lines.filter { it.isNotBlank() }.forEach { Text(it, style = MaterialTheme.typography.bodyLarge) }
    }
}

@Composable
private fun Feedback(text: String) {
    Surface(shape = MainCourseShapes.Panel, color = MainCourseColors.AccentTint, border = BorderStroke(1.dp, MainCourseColors.AccentLine)) {
        Text(text, Modifier.fillMaxWidth().padding(16.dp), color = MainCourseColors.Body)
    }
}

@Composable
private fun RetryState(text: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text, color = MainCourseColors.Body)
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}
