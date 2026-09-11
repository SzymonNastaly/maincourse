package com.getmaincourse.app.features.recipes

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.getmaincourse.app.R
import com.getmaincourse.app.ui.theme.MainCourseColors
import com.getmaincourse.app.ui.theme.MainCourseMono
import com.getmaincourse.app.ui.theme.MainCourseShapes
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun RecipeEditScreen(
    state: RecipeEditUiState,
    imageLoader: ImageLoader?,
    resolveImage: (String?) -> String?,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onNameChange: (String) -> Unit,
    onPrepTimeChange: (String) -> Unit,
    onCookTimeChange: (String) -> Unit,
    onServingsChange: (String) -> Unit,
    onIngredientChange: (Long, String) -> Unit,
    onAddIngredient: () -> Unit,
    onRemoveIngredient: (Long) -> Unit,
    onMoveIngredient: (Long, Int) -> Unit,
    onInstructionChange: (Long, String) -> Unit,
    onAddInstruction: () -> Unit,
    onRemoveInstruction: (Long) -> Unit,
    onMoveInstruction: (Long, Int) -> Unit,
    onNotesChange: (String) -> Unit,
    onSourceUrlChange: (String) -> Unit,
    onImageSelected: (SharedImage) -> Unit,
    onImageError: (String) -> Unit,
    onClearError: () -> Unit,
) {
    var showDiscardDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val imageFailedMessage = stringResource(R.string.recipe_edit_image_failed)
    val imageReader = remember(context) { SharedImageReader(context) }
    val scope = rememberCoroutineScope()
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    onImageSelected(imageReader.read(uri.toString(), "image/*"))
                } catch (failure: SharedImageReadException) {
                    onImageError(failure.message ?: imageFailedMessage)
                }
            }
        }
    }
    val requestBack = {
        when {
            state.saving -> Unit
            state.dirty -> showDiscardDialog = true
            else -> onBack()
        }
    }

    BackHandler(enabled = state.saving || state.dirty) { requestBack() }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.recipe_edit_discard_title)) },
            text = { Text(stringResource(R.string.recipe_edit_discard_body)) },
            confirmButton = {
                TextButton(
                    onClick = onBack,
                    colors = ButtonDefaults.textButtonColors(contentColor = MainCourseColors.Danger),
                    modifier = Modifier.testTag("recipe_edit_discard"),
                ) {
                    Text(stringResource(R.string.discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.keep_editing))
                }
            },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("recipe_edit"),
        containerColor = MainCourseColors.Canvas,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recipe_edit_title)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MainCourseColors.Canvas),
                navigationIcon = {
                    IconButton(
                        onClick = requestBack,
                        enabled = !state.saving,
                        modifier = Modifier.testTag("recipe_edit_back"),
                    ) {
                        Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = onSave,
                        enabled = state.canSave && state.dirty,
                        modifier = Modifier.testTag("recipe_edit_save"),
                    ) {
                        if (state.saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.padding(end = 8.dp).size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        Text(stringResource(if (state.saving) R.string.saving else R.string.save))
                    }
                },
            )
        },
    ) { padding ->
        if (!state.loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            RecipeEditForm(
                state = state,
                imageLoader = imageLoader,
                resolveImage = resolveImage,
                contentPadding = padding,
                onChooseImage = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onNameChange = onNameChange,
                onPrepTimeChange = onPrepTimeChange,
                onCookTimeChange = onCookTimeChange,
                onServingsChange = onServingsChange,
                onIngredientChange = onIngredientChange,
                onAddIngredient = onAddIngredient,
                onRemoveIngredient = onRemoveIngredient,
                onMoveIngredient = onMoveIngredient,
                onInstructionChange = onInstructionChange,
                onAddInstruction = onAddInstruction,
                onRemoveInstruction = onRemoveInstruction,
                onMoveInstruction = onMoveInstruction,
                onNotesChange = onNotesChange,
                onSourceUrlChange = onSourceUrlChange,
                onClearError = onClearError,
            )
        }
    }
}

@Composable
private fun RecipeEditForm(
    state: RecipeEditUiState,
    imageLoader: ImageLoader?,
    resolveImage: (String?) -> String?,
    contentPadding: PaddingValues,
    onChooseImage: () -> Unit,
    onNameChange: (String) -> Unit,
    onPrepTimeChange: (String) -> Unit,
    onCookTimeChange: (String) -> Unit,
    onServingsChange: (String) -> Unit,
    onIngredientChange: (Long, String) -> Unit,
    onAddIngredient: () -> Unit,
    onRemoveIngredient: (Long) -> Unit,
    onMoveIngredient: (Long, Int) -> Unit,
    onInstructionChange: (Long, String) -> Unit,
    onAddInstruction: () -> Unit,
    onRemoveInstruction: (Long) -> Unit,
    onMoveInstruction: (Long, Int) -> Unit,
    onNotesChange: (String) -> Unit,
    onSourceUrlChange: (String) -> Unit,
    onClearError: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item("cover") {
            SectionCard(title = stringResource(R.string.recipe_edit_cover)) {
                val imageModel: Any? = state.selectedImage?.bytes
                    ?: resolveImage(state.coverImagePath)
                Surface(
                    modifier = Modifier.fillMaxWidth().height(190.dp),
                    shape = MainCourseShapes.Card,
                    color = MainCourseColors.Sunken,
                    border = BorderStroke(1.dp, MainCourseColors.Hairline),
                ) {
                    if (imageModel != null && imageLoader != null) {
                        AsyncImage(
                            model = imageModel,
                            imageLoader = imageLoader,
                            contentDescription = stringResource(R.string.recipe_edit_cover),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(R.string.recipe_edit_no_photo),
                                color = MainCourseColors.Muted,
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = onChooseImage,
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth().testTag("recipe_edit_photo"),
                ) {
                    Text(
                        stringResource(
                            if (imageModel == null) R.string.recipe_edit_add_photo else R.string.recipe_edit_change_photo,
                        ),
                    )
                }
            }
        }
        state.error?.let { error ->
            item("error") {
                Surface(
                    modifier = Modifier.fillMaxWidth().testTag("recipe_edit_error"),
                    shape = MainCourseShapes.Panel,
                    color = MainCourseColors.DangerTint,
                    border = BorderStroke(1.dp, MainCourseColors.DangerLine),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 14.dp, top = 8.dp, end = 6.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(error, Modifier.weight(1f), color = MainCourseColors.Danger)
                        TextButton(onClick = onClearError) { Text(stringResource(R.string.dismiss)) }
                    }
                }
            }
        }
        item("basics") {
            SectionCard(title = stringResource(R.string.recipe_edit_basics)) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth().testTag("recipe_edit_name"),
                    enabled = !state.saving,
                    singleLine = true,
                    isError = state.nameInvalid,
                    label = { Text(stringResource(R.string.recipe_edit_name)) },
                    supportingText = if (state.nameInvalid) {
                        { Text(stringResource(R.string.recipe_edit_name_required)) }
                    } else {
                        null
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(
                        value = state.prepTime,
                        onValueChange = onPrepTimeChange,
                        label = stringResource(R.string.recipe_prep),
                        modifier = Modifier.weight(1f).testTag("recipe_edit_prep"),
                        enabled = !state.saving,
                        suffix = stringResource(R.string.minutes_short),
                    )
                    NumberField(
                        value = state.cookTime,
                        onValueChange = onCookTimeChange,
                        label = stringResource(R.string.recipe_cook),
                        modifier = Modifier.weight(1f).testTag("recipe_edit_cook"),
                        enabled = !state.saving,
                        suffix = stringResource(R.string.minutes_short),
                    )
                    NumberField(
                        value = state.servings,
                        onValueChange = onServingsChange,
                        label = stringResource(R.string.recipe_edit_servings),
                        modifier = Modifier.weight(1f).testTag("recipe_edit_servings"),
                        enabled = !state.saving,
                    )
                }
            }
        }
        item("ingredients-header") { SectionTitle(stringResource(R.string.recipe_ingredients)) }
        items(state.ingredients, key = { "ingredient-${it.id}" }) { row ->
            EditRow(
                row = row,
                index = state.ingredients.indexOf(row),
                count = state.ingredients.size,
                label = stringResource(R.string.recipe_edit_ingredient),
                enabled = !state.saving,
                onChange = { onIngredientChange(row.id, it) },
                onMove = { onMoveIngredient(row.id, it) },
                onRemove = { onRemoveIngredient(row.id) },
                testTag = "recipe_edit_ingredient_${row.id}",
            )
        }
        item("ingredient-add") {
            OutlinedButton(
                onClick = onAddIngredient,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth().testTag("recipe_edit_add_ingredient"),
            ) {
                Text(stringResource(R.string.recipe_edit_add_ingredient))
            }
        }
        item("instructions-header") { SectionTitle(stringResource(R.string.recipe_steps)) }
        items(state.instructions, key = { "instruction-${it.id}" }) { row ->
            val index = state.instructions.indexOf(row)
            EditRow(
                row = row,
                index = index,
                count = state.instructions.size,
                label = stringResource(R.string.recipe_edit_step, index + 1),
                enabled = !state.saving,
                singleLine = false,
                onChange = { onInstructionChange(row.id, it) },
                onMove = { onMoveInstruction(row.id, it) },
                onRemove = { onRemoveInstruction(row.id) },
                testTag = "recipe_edit_instruction_${row.id}",
            )
        }
        item("instruction-add") {
            OutlinedButton(
                onClick = onAddInstruction,
                enabled = !state.saving,
                modifier = Modifier.fillMaxWidth().testTag("recipe_edit_add_instruction"),
            ) {
                Text(stringResource(R.string.recipe_edit_add_step))
            }
        }
        item("notes") {
            SectionCard(title = stringResource(R.string.recipe_notes)) {
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = onNotesChange,
                    modifier = Modifier.fillMaxWidth().testTag("recipe_edit_notes"),
                    enabled = !state.saving,
                    minLines = 3,
                    maxLines = 8,
                    label = { Text(stringResource(R.string.recipe_notes)) },
                )
            }
        }
        item("source") {
            SectionCard(title = stringResource(R.string.recipe_edit_source)) {
                OutlinedTextField(
                    value = state.sourceUrl,
                    onValueChange = onSourceUrlChange,
                    modifier = Modifier.fillMaxWidth().testTag("recipe_edit_source"),
                    enabled = !state.saving,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    label = { Text(stringResource(R.string.recipe_edit_source)) },
                    placeholder = { Text("https://…") },
                )
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MainCourseShapes.Panel,
        color = MainCourseColors.Surface,
        border = BorderStroke(1.dp, MainCourseColors.Hairline),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(title)
            content()
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MainCourseColors.Ink)
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier,
    enabled: Boolean,
    suffix: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        label = { Text(label) },
        suffix = suffix?.let { value -> { Text(value, fontFamily = MainCourseMono) } },
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = MainCourseMono),
    )
}

@Composable
private fun EditRow(
    row: RecipeEditRow,
    index: Int,
    count: Int,
    label: String,
    enabled: Boolean,
    singleLine: Boolean = true,
    onChange: (String) -> Unit,
    onMove: (Int) -> Unit,
    onRemove: () -> Unit,
    testTag: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MainCourseShapes.Card,
        color = MainCourseColors.Surface,
        border = BorderStroke(1.dp, MainCourseColors.Hairline),
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            OutlinedTextField(
                value = row.text,
                onValueChange = onChange,
                modifier = Modifier.fillMaxWidth().testTag(testTag),
                enabled = enabled,
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 2,
                maxLines = if (singleLine) 1 else 5,
                label = { Text(label) },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { onMove(-1) }, enabled = enabled && index > 0) {
                    Text(stringResource(R.string.move_up))
                }
                TextButton(onClick = { onMove(1) }, enabled = enabled && index < count - 1) {
                    Text(stringResource(R.string.move_down))
                }
                TextButton(
                    onClick = onRemove,
                    enabled = enabled,
                    colors = ButtonDefaults.textButtonColors(contentColor = MainCourseColors.Danger),
                ) {
                    Text(stringResource(R.string.remove))
                }
            }
        }
    }
}
