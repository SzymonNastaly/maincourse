package com.getmaincourse.app.features.recipes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.getmaincourse.app.R
import com.getmaincourse.app.data.cache.RecipeScope
import com.getmaincourse.app.data.images.PreparedRecipeImage
import com.getmaincourse.app.data.model.RecipeDetail
import com.getmaincourse.app.ui.theme.MainCourseColors
import java.util.UUID

@Composable
fun RecipeEditScreen(
    scope: RecipeScope,
    recipe: RecipeDetail,
    actionState: RecipeActionState,
    imageState: RecipeImagePreparationState,
    onSave: (RecipeEditDraft, PreparedRecipeImage?) -> Unit,
    onPrepareImage: (android.net.Uri, String) -> Unit = { _, _ -> },
    onDiscardImage: (PreparedRecipeImage) -> Unit = {},
    onCancelImageRequest: (String) -> Unit = {},
    onRetryPhoto: (PreparedRecipeImage?) -> Unit = { _ -> },
    onRetryReconciliation: () -> Unit = {},
    onCancel: () -> Unit = {},
) {
    val fresh = remember(scope, recipe.id, recipe.updatedAt) { RecipeEditorSavedState(
        userId = scope.userId,
        cookbookId = scope.cookbookId,
        recipeId = recipe.id,
        editorId = UUID.randomUUID().toString(),
        original = recipe.editValues(),
        values = recipe.editValues(),
    ) }
    var saved by rememberSaveable { mutableStateOf<String?>(null) }
    var payload = RecipeUiSavedStateCodec.decodeEditor(saved, scope, recipe.id) ?: fresh
    SideEffect { if (saved == null || RecipeUiSavedStateCodec.decodeEditor(saved, scope, recipe.id) == null) saved = RecipeUiSavedStateCodec.encodeEditor(payload) }
    fun update(transform: (RecipeEditorSavedState) -> RecipeEditorSavedState) {
        payload = transform(payload)
        saved = RecipeUiSavedStateCodec.encodeEditor(payload)
    }

    val matchingAction = actionState.scope == scope && actionState.recipeId == recipe.id
    val savingHere = matchingAction && actionState.isBusy
    LaunchedEffect(imageState) {
        if (imageState.scope == scope && imageState.requestKey == payload.imageRequestKey && imageState.image != null) {
            update { it.copy(stagedImage = imageState.image) }
        } else if (imageState.scope == scope && imageState.image != null &&
            imageState.requestKey?.substringBefore(':') != payload.editorId
        ) {
            onDiscardImage(imageState.image)
        }
    }
    LaunchedEffect(actionState.outcome, actionState.isBusy) {
        if (matchingAction && payload.operationInterrupted && !actionState.isBusy && actionState.outcome != RecipeActionOutcome.IDLE) {
            update { it.copy(operationInterrupted = false) }
        }
    }
    LaunchedEffect(actionState.needsPhotoSelection, actionState.outcome) {
        if (matchingAction && actionState.needsPhotoSelection && payload.stagedImage != null) {
            val rejected = payload.stagedImage ?: return@LaunchedEffect
            update { it.copy(stagedImage = null, imageRequestKey = null) }
            onDiscardImage(rejected)
        }
    }
    val activity = LocalActivity.current
    val latestPayload by rememberUpdatedState(payload)
    DisposableEffect(payload.editorId) {
        onDispose {
            if (activity?.isChangingConfigurations != true) {
                latestPayload.imageRequestKey?.let(onCancelImageRequest)
                if (latestPayload.imageRequestKey == null) latestPayload.stagedImage?.let(onDiscardImage)
            }
        }
    }
    val photoPicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) {
            val requestKey = "${payload.editorId}:${UUID.randomUUID()}"
            update { it.copy(imageRequestKey = requestKey) }
            onPrepareImage(uri, requestKey)
        }
    }
    val draft = RecipeEditDraft(scope, recipe.id, payload.original, payload.values)
    val validation = draft.validate()
    val requiresRecovery = matchingAction && (actionState.canRetryPhoto || actionState.needsPhotoSelection || actionState.canRetryReconciliation)
    val canSave = (draft.hasChanges() || payload.stagedImage != null) && validation is RecipeDraftValidation.Valid &&
        !actionState.isBusy && !payload.operationInterrupted && !requiresRecovery

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("editor_list"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { EditorField(R.string.recipe_name, payload.values.name, "editor_name") { value -> updateValues(payload, { transform -> update(transform) }) { it.copy(name = value) } } }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                EditorField(R.string.recipe_prep_minutes, payload.values.prepMinutes, "editor_prep", keyboardType = KeyboardType.Number) { value -> updateValues(payload, { transform -> update(transform) }) { it.copy(prepMinutes = value) } }
                EditorField(R.string.recipe_cook_minutes, payload.values.cookMinutes, "editor_cook", keyboardType = KeyboardType.Number) { value -> updateValues(payload, { transform -> update(transform) }) { it.copy(cookMinutes = value) } }
                EditorField(R.string.servings, payload.values.servings, "editor_servings", keyboardType = KeyboardType.Number) { value -> updateValues(payload, { transform -> update(transform) }) { it.copy(servings = value) } }
            }
        }
        item { EditRows(R.string.recipe_ingredients, "ingredient", payload.values.ingredients) { rows -> updateValues(payload, { transform -> update(transform) }) { it.copy(ingredients = rows) } } }
        item { EditRows(R.string.recipe_steps, "instruction", payload.values.instructions) { rows -> updateValues(payload, { transform -> update(transform) }) { it.copy(instructions = rows) } } }
        item { EditorField(R.string.recipe_notes, payload.values.notes, "editor_notes") { value -> updateValues(payload, { transform -> update(transform) }) { it.copy(notes = value) } } }
        item { EditorField(R.string.recipe_source_url, payload.values.sourceUrl, "editor_source", keyboardType = KeyboardType.Uri) { value -> updateValues(payload, { transform -> update(transform) }) { it.copy(sourceUrl = value) } } }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !actionState.isBusy && imageState.status != RecipeImagePreparationStatus.PREPARING,
                    onClick = { photoPicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.testTag("editor_photo"),
                ) { Text(stringResource(if (payload.stagedImage == null) R.string.recipe_choose_photo else R.string.recipe_replace_photo)) }
                if (imageState.requestKey == payload.imageRequestKey && imageState.status == RecipeImagePreparationStatus.PREPARING) CircularProgressIndicator()
                if (imageState.requestKey == payload.imageRequestKey && imageState.message != null) Text(imageState.message, color = MainCourseColors.Danger)
            }
        }
        if (validation is RecipeDraftValidation.Invalid) item { Text(validation.error.message(), color = MainCourseColors.Danger) }
        if (payload.operationInterrupted && !savingHere && actionState.outcome == RecipeActionOutcome.IDLE) {
            item {
                Column {
                    Text(stringResource(R.string.recipe_save_unconfirmed), color = MainCourseColors.Danger, modifier = Modifier.testTag("editor_unconfirmed"))
                    OutlinedButton(onClick = { update { it.copy(operationInterrupted = false) } }) {
                        Text(stringResource(R.string.recipe_continue_editing))
                    }
                }
            }
        }
        if (matchingAction && actionState.message != null) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(actionState.message, color = if (actionState.outcome == RecipeActionOutcome.SUCCEEDED) MainCourseColors.Accent else MainCourseColors.Danger)
                if (actionState.canRetryPhoto || (actionState.needsPhotoSelection && payload.stagedImage != null)) {
                    Button(enabled = !actionState.isBusy, onClick = { onRetryPhoto(payload.stagedImage) }) { Text(stringResource(R.string.recipe_retry_photo)) }
                }
                if (actionState.canRetryReconciliation) Button(enabled = !actionState.isBusy, onClick = onRetryReconciliation) { Text(stringResource(R.string.recipe_refresh_data)) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        update { it.copy(operationInterrupted = true) }
                        onSave(RecipeEditDraft(scope, recipe.id, payload.original, payload.values), payload.stagedImage)
                    },
                    enabled = canSave,
                    modifier = Modifier.testTag("editor_save"),
                ) {
                    if (savingHere) CircularProgressIndicator() else Text(stringResource(R.string.save))
                }
                OutlinedButton(enabled = !savingHere, onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}

@Composable
private fun EditorField(
    label: Int,
    value: String,
    tag: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth().testTag(tag),
        label = { Text(stringResource(label)) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

@Composable
private fun EditRows(label: Int, prefix: String, rows: List<RecipeEditRow>, onChange: (List<RecipeEditRow>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(label))
        rows.forEachIndexed { index, row ->
            Column {
                OutlinedTextField(
                    value = row.text,
                    onValueChange = { text -> onChange(rows.toMutableList().also { it[index] = row.copy(text = text) }) },
                    modifier = Modifier.fillMaxWidth().testTag("${prefix}_row_$index"),
                    label = { Text(stringResource(R.string.recipe_row_number, index + 1)) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedButton(enabled = index > 0, onClick = { onChange(rows.moved(index, index - 1)) }, modifier = Modifier.testTag("${prefix}_move_up_$index")) { Text(stringResource(R.string.move_up)) }
                    OutlinedButton(enabled = index < rows.lastIndex, onClick = { onChange(rows.moved(index, index + 1)) }, modifier = Modifier.testTag("${prefix}_move_down_$index")) { Text(stringResource(R.string.move_down)) }
                    OutlinedButton(onClick = { onChange(rows.filterNot { it.id == row.id }) }) { Text(stringResource(R.string.remove)) }
                }
            }
        }
        OutlinedButton(onClick = { onChange(rows + RecipeEditRow(UUID.randomUUID().toString(), "")) }) { Text(stringResource(R.string.add_row)) }
    }
}

private fun updateValues(
    payload: RecipeEditorSavedState,
    update: ((RecipeEditorSavedState) -> RecipeEditorSavedState) -> Unit,
    transform: (RecipeEditValues) -> RecipeEditValues,
) = update { it.copy(values = transform(payload.values)) }

private fun List<RecipeEditRow>.moved(from: Int, to: Int): List<RecipeEditRow> = toMutableList().apply { add(to, removeAt(from)) }

private fun RecipeDetail.editValues() = RecipeEditValues(
    name = name,
    prepMinutes = prepTime?.toString().orEmpty(),
    cookMinutes = cookTime?.toString().orEmpty(),
    servings = servings?.toString().orEmpty(),
    ingredients = (structuredIngredients.takeIf { it.isNotEmpty() }?.sortedBy { it.position }?.map { it.raw } ?: ingredients)
        .map { RecipeEditRow(UUID.randomUUID().toString(), it) },
    instructions = instructions.map { RecipeEditRow(UUID.randomUUID().toString(), it) },
    notes = notes.orEmpty(),
    sourceUrl = sourceUrl.orEmpty(),
)

@Composable
private fun RecipeDraftError.message(): String = stringResource(
    when (this) {
        RecipeDraftError.NAME_REQUIRED -> R.string.recipe_name_required
        RecipeDraftError.PREP_MINUTES_INVALID -> R.string.recipe_prep_invalid
        RecipeDraftError.COOK_MINUTES_INVALID -> R.string.recipe_cook_invalid
        RecipeDraftError.SERVINGS_INVALID -> R.string.recipe_servings_invalid
        RecipeDraftError.SOURCE_URL_INVALID -> R.string.recipe_source_invalid
    },
)
