package com.getmaincourse.app.features.recipes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.getmaincourse.app.R
import com.getmaincourse.app.ui.localized
import com.getmaincourse.app.ui.theme.MainCourseColors
import com.getmaincourse.app.ui.theme.MainCourseMono
import com.getmaincourse.app.ui.theme.MainCourseShapes

@Composable
fun RecipeImportScreen(
    state: RecipeImportUiState,
    onSelectCookbook: (Long) -> Unit,
    onModeChange: (RecipeImportMode) -> Unit,
    onUrlChange: (String) -> Unit,
    onTextChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("screen_RecipeImport"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.recipe_import_help),
            style = MaterialTheme.typography.bodyLarge,
            color = MainCourseColors.Body,
        )
        CookbookPicker(
            state.cookbooks,
            state.selectedCookbookId,
            onSelectCookbook,
            enabled = !state.importing,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.recipe_import_source),
                style = MaterialTheme.typography.labelMedium,
                color = MainCourseColors.Body,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.mode == RecipeImportMode.URL,
                    onClick = { onModeChange(RecipeImportMode.URL) },
                    label = { Text(stringResource(R.string.recipe_import_url)) },
                    modifier = Modifier.testTag("import_mode_url"),
                    enabled = !state.importing,
                )
                FilterChip(
                    selected = state.mode == RecipeImportMode.TEXT,
                    onClick = { onModeChange(RecipeImportMode.TEXT) },
                    label = { Text(stringResource(R.string.recipe_import_text)) },
                    modifier = Modifier.testTag("import_mode_text"),
                    enabled = !state.importing,
                )
            }
        }
        when (state.mode) {
            RecipeImportMode.URL -> OutlinedTextField(
                value = state.url,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth().testTag("import_url"),
                enabled = !state.importing,
                label = { Text(stringResource(R.string.recipe_import_url_label)) },
                placeholder = { Text(stringResource(R.string.recipe_import_url_placeholder)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { if (state.canSubmit) onSubmit() }),
            )
            RecipeImportMode.TEXT -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = state.text,
                    onValueChange = onTextChange,
                    modifier = Modifier.fillMaxWidth().testTag("import_text"),
                    enabled = !state.importing,
                    label = { Text(stringResource(R.string.recipe_import_text_label)) },
                    placeholder = { Text(stringResource(R.string.recipe_import_text_placeholder)) },
                    minLines = 9,
                )
                Text(
                    stringResource(
                        R.string.recipe_import_character_count,
                        state.text.length,
                        MAX_RECIPE_IMPORT_TEXT_LENGTH,
                    ),
                    modifier = Modifier.align(Alignment.End),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = MainCourseMono,
                    color = if (state.text.length > MAX_RECIPE_IMPORT_TEXT_LENGTH) {
                        MainCourseColors.Danger
                    } else {
                        MainCourseColors.Muted
                    },
                )
            }
        }
        state.error?.let { error ->
            Surface(
                modifier = Modifier.fillMaxWidth().testTag("import_error"),
                shape = MainCourseShapes.Panel,
                color = MainCourseColors.DangerTint,
                border = BorderStroke(1.dp, MainCourseColors.DangerLine),
            ) {
                Text(error.localized(), Modifier.padding(14.dp), color = MainCourseColors.Danger)
            }
        }
        Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth().testTag("import_submit"),
            enabled = state.canSubmit,
        ) {
            if (state.importing) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(end = 10.dp).size(18.dp),
                    strokeWidth = 2.dp,
                )
            }
            Text(
                if (state.importing) {
                    stringResource(R.string.recipe_import_starting)
                } else {
                    stringResource(R.string.recipe_import_action)
                },
            )
        }
        Text(
            stringResource(R.string.recipe_import_background_help),
            style = MaterialTheme.typography.bodySmall,
            color = MainCourseColors.Muted,
        )
    }
}
