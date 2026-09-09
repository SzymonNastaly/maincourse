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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import com.getmaincourse.app.R
import com.getmaincourse.app.data.cache.RecipeScope
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.ui.theme.MainCourseColors

@Composable
fun IngredientReviewScreen(
    scope: RecipeScope,
    recipe: RecipeDetail,
    portions: Int,
    actionState: RecipeActionState,
    onSubmit: (List<ShoppingItemInput>) -> Unit,
) {
    val initial = IngredientReview.create(recipe.id, recipe.structuredForReview(), portions, recipe.servings)
    var saved by rememberSaveable { mutableStateOf<String?>(null) }
    var payload = RecipeUiSavedStateCodec.decodeDetail(saved, scope, recipe.id)
        ?: RecipeDetailSavedState(scope.userId, scope.cookbookId, recipe.id, portions, false, initial)
    SideEffect { if (saved == null || RecipeUiSavedStateCodec.decodeDetail(saved, scope, recipe.id) == null) saved = RecipeUiSavedStateCodec.encodeDetail(payload) }
    fun update(review: IngredientReview) {
        payload = payload.copy(review = review)
        saved = RecipeUiSavedStateCodec.encodeDetail(payload)
    }
    val matching = actionState.scope == scope && actionState.recipeId == recipe.id
    val frozen = matching && actionState.outcome == RecipeActionOutcome.AMBIGUOUS && actionState.frozenShoppingItems.isNotEmpty()
    LaunchedEffect(actionState.outcome, actionState.isBusy) {
        if (payload.reviewSubmissionInterrupted && matching && !actionState.isBusy &&
            actionState.outcome != RecipeActionOutcome.IDLE && actionState.outcome != RecipeActionOutcome.AMBIGUOUS
        ) {
            payload = payload.copy(reviewSubmissionInterrupted = false)
            saved = RecipeUiSavedStateCodec.encodeDetail(payload)
        }
    }
    val review = payload.review ?: initial
    val selected = review.includedPayload()
    val retryingUnconfirmed = frozen || payload.reviewSubmissionInterrupted

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("ingredient_review"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text(stringResource(R.string.recipe_review_help), color = MainCourseColors.Body) }
        itemsIndexed(review.items, key = { _, item -> item.clientId }) { index, item ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable(enabled = !actionState.isBusy && !retryingUnconfirmed) {
                    update(review.withIncluded(item.clientId, !item.included))
                }.padding(vertical = 8.dp).testTag("review_item_$index"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = item.included,
                    enabled = !actionState.isBusy && !retryingUnconfirmed,
                    onCheckedChange = { included -> update(review.withIncluded(item.clientId, included)) },
                )
                Column(Modifier.weight(1f)) {
                    Text(item.name)
                    item.details?.let { Text(it, color = MainCourseColors.Body) }
                }
            }
        }
        if (matching && actionState.message != null) item { Text(actionState.message, color = if (actionState.outcome == RecipeActionOutcome.SUCCEEDED) MainCourseColors.Accent else MainCourseColors.Danger) }
        if (payload.reviewSubmissionInterrupted && (!matching || actionState.outcome == RecipeActionOutcome.IDLE)) {
            item { Text(stringResource(R.string.recipe_add_unconfirmed), color = MainCourseColors.Danger) }
        }
        item {
            Button(
                enabled = !actionState.isBusy && (selected.isNotEmpty() || frozen),
                onClick = {
                    payload = payload.copy(reviewSubmissionInterrupted = true)
                    saved = RecipeUiSavedStateCodec.encodeDetail(payload)
                    onSubmit(if (frozen) actionState.frozenShoppingItems else selected)
                },
                modifier = Modifier.testTag("review_submit"),
            ) {
                if (matching && actionState.isBusy && actionState.operation == RecipeActionOperation.ADDING_INGREDIENTS) {
                    CircularProgressIndicator()
                } else {
                    Text(if (retryingUnconfirmed) stringResource(R.string.retry_same_items) else pluralStringResource(R.plurals.add_items, selected.size, selected.size))
                }
            }
        }
    }
}

private fun RecipeDetail.structuredForReview() = if (structuredIngredients.isNotEmpty()) {
    structuredIngredients
} else {
    ingredients.mapIndexed { index, raw -> com.getmaincourse.app.data.model.StructuredIngredient(index.toLong(), index, null, null, null, null, null, raw) }
}
