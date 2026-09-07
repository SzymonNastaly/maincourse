package com.getmaincourse.app.features.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.getmaincourse.app.R
import com.getmaincourse.app.ui.theme.MainCourseColors
import com.getmaincourse.app.ui.theme.MainCourseMono
import com.getmaincourse.app.ui.theme.MainCourseShapes
import kotlinx.coroutines.launch

@Composable
fun DesignSystemScreen() {
    var note by rememberSaveable { mutableStateOf("") }
    var quickMeals by rememberSaveable { mutableStateOf(false) }
    var reminder by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val feedback = stringResource(R.string.sample_feedback)
    val dismiss = stringResource(R.string.sample_feedback_dismiss)
    val reminderLabel = stringResource(R.string.sample_notification)

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth().testTag("design_gallery"),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.gallery_intro), style = MaterialTheme.typography.headlineLarge)
                    Text(stringResource(R.string.gallery_description), color = MainCourseColors.Body)
                }
            }
            item {
                Surface(
                    shape = MainCourseShapes.Panel,
                    color = MainCourseColors.Surface,
                    border = BorderStroke(1.dp, MainCourseColors.Hairline),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.sample_recipe), style = MaterialTheme.typography.labelMedium, color = MainCourseColors.Body)
                        Text(stringResource(R.string.sample_recipe_name), style = MaterialTheme.typography.titleLarge)
                        Text(stringResource(R.string.sample_recipe_description), color = MainCourseColors.Body)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.sample_numbers), fontFamily = MainCourseMono)
                                Text(stringResource(R.string.minutes), color = MainCourseColors.Body)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.sample_servings), fontFamily = MainCourseMono)
                                Text(stringResource(R.string.servings), color = MainCourseColors.Body)
                            }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.controls), style = MaterialTheme.typography.titleLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(false to R.string.all_recipes, true to R.string.quick_meals).forEach { (quick, label) ->
                            FilterChip(
                                selected = quickMeals == quick,
                                onClick = { quickMeals = quick },
                                label = { Text(stringResource(label)) },
                                shape = MainCourseShapes.Control,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MainCourseColors.Ink,
                                    selectedLabelColor = MainCourseColors.Surface,
                                ),
                            )
                        }
                    }
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text(stringResource(R.string.sample_note)) },
                        placeholder = { Text(stringResource(R.string.sample_note_placeholder)) },
                        modifier = Modifier.fillMaxWidth().testTag("sample_note"),
                        shape = MainCourseShapes.Card,
                        minLines = 2,
                        maxLines = 4,
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(reminderLabel, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.sample_notification_description), style = MaterialTheme.typography.bodyMedium, color = MainCourseColors.Body)
                    }
                    Switch(
                        checked = reminder,
                        onCheckedChange = { reminder = it },
                        modifier = Modifier.testTag("sample_reminder").semantics { contentDescription = reminderLabel },
                    )
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        shape = MainCourseShapes.Control,
                        onClick = { scope.launch { snackbar.showSnackbar(feedback, dismiss) } },
                    ) { Text(stringResource(R.string.try_action)) }
                    TextButton(onClick = { note = ""; quickMeals = false; reminder = false }) {
                        Text(stringResource(R.string.reset_samples))
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.color_roles), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.color_roles_description), color = MainCourseColors.Body)
                    Surface(
                        color = MainCourseColors.DangerTint,
                        shape = MainCourseShapes.Card,
                        border = BorderStroke(1.dp, MainCourseColors.DangerLine),
                    ) {
                        Text(stringResource(R.string.sample_error), color = MainCourseColors.Danger, modifier = Modifier.padding(16.dp))
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}
