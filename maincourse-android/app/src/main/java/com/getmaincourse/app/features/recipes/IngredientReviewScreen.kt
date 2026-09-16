package com.getmaincourse.app.features.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import com.getmaincourse.app.R
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.ui.theme.MainCourseColors
import kotlinx.serialization.json.Json

@Composable
fun IngredientReviewScreen(
    recipe: RecipeDetail,
    portions: Int,
    actionState: RecipeActionUiState,
    shoppingListReviewReady: Boolean = true,
    shoppingListNeedsReview: () -> Boolean = { false },
    onBack: () -> Unit,
    onSubmit: (List<ShoppingItemInput>, Boolean) -> Unit,
) {
    var review by rememberSaveable(recipe.id, portions, stateSaver = IngredientReviewSaver) {
        mutableStateOf(IngredientReview.create(recipe.id, recipe.structuredForReview(), portions, recipe.servings))
    }
    val selected = review.includedPayload()
    val running = actionState is RecipeActionUiState.Running
    var showFreshListConfirmation by rememberSaveable { mutableStateOf(false) }

    if (showFreshListConfirmation) {
        AlertDialog(
            onDismissRequest = { showFreshListConfirmation = false },
            title = { Text(stringResource(R.string.shopping_fresh_start_title)) },
            text = { Text(stringResource(R.string.shopping_fresh_start_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showFreshListConfirmation = false
                        onSubmit(selected, true)
                    },
                    modifier = Modifier.testTag("review_clear_and_add"),
                ) {
                    Text(stringResource(R.string.shopping_clear_and_add), color = MainCourseColors.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFreshListConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
                TextButton(
                    onClick = {
                        showFreshListConfirmation = false
                        onSubmit(selected, false)
                    },
                    modifier = Modifier.testTag("review_keep_and_add"),
                ) {
                    Text(stringResource(R.string.shopping_keep_and_add))
                }
            },
        )
    }

    if (actionState is RecipeActionUiState.Succeeded) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(actionState.message) },
            confirmButton = {
                Button(onClick = onBack, modifier = Modifier.testTag("review_success_confirm")) {
                    Text(stringResource(R.string.done))
                }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("ingredient_review"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Button(onClick = onBack, enabled = !running, modifier = Modifier.testTag("review_back")) {
                Text(stringResource(R.string.back))
            }
        }
        item { Text(stringResource(R.string.recipe_review_help), color = MainCourseColors.Body) }
        item { Text(stringResource(R.string.recipe_review_staples_help), color = MainCourseColors.Body) }
        itemsIndexed(review.items, key = { _, item -> item.clientId }) { index, item ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable(enabled = !running) {
                    review = review.withIncluded(item.clientId, !item.included)
                }.padding(vertical = 8.dp).testTag("review_item_$index"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = item.included,
                    enabled = !running,
                    onCheckedChange = { included -> review = review.withIncluded(item.clientId, included) },
                )
                Column(Modifier.weight(1f)) {
                    Text(item.name)
                    item.details?.let { Text(it, color = MainCourseColors.Body) }
                }
            }
        }
        if (actionState is RecipeActionUiState.Failed) {
            item { Text(actionState.message, color = MainCourseColors.Danger, modifier = Modifier.testTag("review_error")) }
        }
        item {
            Button(
                enabled = !running && selected.isNotEmpty() && shoppingListReviewReady,
                onClick = {
                    if (shoppingListNeedsReview()) {
                        showFreshListConfirmation = true
                    } else {
                        onSubmit(selected, false)
                    }
                },
                modifier = Modifier.testTag("review_submit"),
            ) {
                if (running) {
                    CircularProgressIndicator()
                } else {
                    Text(pluralStringResource(R.plurals.add_items, selected.size, selected.size))
                }
            }
        }
    }
}

private val IngredientReviewSaver = Saver<IngredientReview, String>(
    save = { review -> Json.encodeToString(review) },
    restore = { encoded -> runCatching { Json.decodeFromString<IngredientReview>(encoded) }.getOrNull() },
)

private fun RecipeDetail.structuredForReview() = if (structuredIngredients.isNotEmpty()) {
    structuredIngredients
} else {
    ingredients.mapIndexed { index, raw -> com.getmaincourse.app.data.model.StructuredIngredient(index.toLong(), index, null, null, null, null, null, raw) }
}
