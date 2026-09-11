package com.getmaincourse.app.features.shopping

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.getmaincourse.app.R
import com.getmaincourse.app.data.model.ShoppingItem
import com.getmaincourse.app.ui.theme.MainCourseColors
import com.getmaincourse.app.ui.theme.MainCourseMono
import com.getmaincourse.app.ui.theme.MainCourseShapes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingListScreen(
    state: ShoppingListUiState,
    onRefresh: () -> Unit,
    onDraftChange: (String) -> Unit,
    onAdd: () -> Unit,
    onToggle: (ShoppingItem) -> Unit,
    onDelete: (ShoppingItem) -> Unit,
    onClear: () -> Unit,
    onClearError: () -> Unit,
) {
    var checkedExpanded by rememberSaveable { mutableStateOf(true) }
    var showClearConfirmation by rememberSaveable { mutableStateOf(false) }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            title = { Text(stringResource(R.string.shopping_clear_title)) },
            text = { Text(stringResource(R.string.shopping_clear_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirmation = false
                        onClear()
                    },
                    modifier = Modifier.testTag("shopping_clear_confirm"),
                ) {
                    Text(stringResource(R.string.shopping_remove_all), color = MainCourseColors.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize().testTag("screen_Shopping"),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AddItemBar(
                    value = state.draft,
                    enabled = state.selectedCookbookId != null && !state.busy,
                    adding = state.action == ShoppingAction.ADD,
                    onValueChange = onDraftChange,
                    onAdd = onAdd,
                )
            }
            state.error?.let { error ->
                item {
                    ErrorPanel(error, onClearError)
                }
            }
            if (state.initialLoading) {
                item { LoadingPanel() }
            } else if (state.cookbooks.isEmpty()) {
                item { EmptyPanel(stringResource(R.string.cookbooks_empty)) }
            } else {
                item {
                    SectionHeader(
                        title = stringResource(R.string.shopping_to_buy),
                        count = state.uncheckedItems.size,
                    ) {
                        if (state.items.isNotEmpty()) {
                            TextButton(
                                onClick = { showClearConfirmation = true },
                                enabled = !state.busy,
                                modifier = Modifier.testTag("shopping_clear"),
                            ) {
                                if (state.action == ShoppingAction.CLEAR) {
                                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(
                                        stringResource(R.string.shopping_remove_all),
                                        color = MainCourseColors.Danger,
                                    )
                                }
                            }
                        }
                    }
                }
                if (state.uncheckedItems.isEmpty() && state.checkedItems.isEmpty()) {
                    item {
                        EmptyPanel(
                            message = stringResource(R.string.shopping_empty),
                            help = stringResource(R.string.shopping_empty_help),
                        )
                    }
                } else {
                    items(state.uncheckedItems, key = { it.id }) { item ->
                        ShoppingItemCard(
                            item = item,
                            busy = state.actionItemId == item.id,
                            enabled = !state.busy,
                            onToggle = { onToggle(item) },
                            onDelete = { onDelete(item) },
                        )
                    }
                }
                if (state.checkedItems.isNotEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth().clip(MainCourseShapes.Control)
                                .clickable { checkedExpanded = !checkedExpanded }
                                .testTag("shopping_checked_header"),
                            color = MainCourseColors.Canvas,
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.shopping_already_got),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MainCourseColors.Muted,
                                )
                                Text(
                                    state.checkedItems.size.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = MainCourseMono,
                                    color = MainCourseColors.Muted,
                                )
                                Text(
                                    stringResource(
                                        if (checkedExpanded) R.string.shopping_hide else R.string.shopping_show,
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MainCourseColors.Body,
                                )
                            }
                        }
                    }
                    if (checkedExpanded) {
                        items(state.checkedItems, key = { it.id }) { item ->
                            ShoppingItemCard(
                                item = item,
                                busy = state.actionItemId == item.id,
                                enabled = !state.busy,
                                onToggle = { onToggle(item) },
                                onDelete = { onDelete(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddItemBar(
    value: String,
    enabled: Boolean,
    adding: Boolean,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).testTag("shopping_add_input"),
            enabled = enabled,
            placeholder = { Text(stringResource(R.string.shopping_add_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (value.isNotBlank()) onAdd() }),
            shape = MainCourseShapes.Card,
        )
        Button(
            onClick = onAdd,
            enabled = enabled && value.isNotBlank(),
            modifier = Modifier.testTag("shopping_add"),
        ) {
            if (adding) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.shopping_add))
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MainCourseColors.Muted,
            )
            Text(
                count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = MainCourseMono,
                color = MainCourseColors.Muted,
            )
        }
        trailing()
    }
}

@Composable
private fun ShoppingItemCard(
    item: ShoppingItem,
    busy: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    val checked = item.checkedAt != null
    Surface(
        modifier = Modifier.fillMaxWidth().clip(MainCourseShapes.Card)
            .clickable(enabled = enabled) { onToggle() }
            .testTag("shopping_item_${item.id}"),
        shape = MainCourseShapes.Card,
        color = if (checked) MainCourseColors.Sunken else MainCourseColors.Surface,
        border = BorderStroke(1.dp, MainCourseColors.Hairline),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (busy) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            } else {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { onToggle() },
                    enabled = enabled,
                    modifier = Modifier.testTag("shopping_toggle_${item.id}"),
                )
            }
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (checked) MainCourseColors.Muted else MainCourseColors.Ink,
                )
                item.details?.trim()?.takeIf(String::isNotEmpty)?.let { details ->
                    Text(
                        details,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MainCourseColors.Body,
                    )
                }
            }
            TextButton(
                onClick = onDelete,
                enabled = enabled,
                modifier = Modifier.testTag("shopping_delete_${item.id}"),
            ) {
                Text(stringResource(R.string.recipe_delete), color = MainCourseColors.Danger)
            }
        }
    }
}

@Composable
private fun ErrorPanel(message: String, onDismiss: () -> Unit) {
    Surface(
        shape = MainCourseShapes.Panel,
        color = MainCourseColors.DangerTint,
        border = BorderStroke(1.dp, MainCourseColors.DangerLine),
        modifier = Modifier.testTag("shopping_error"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(message, modifier = Modifier.weight(1f), color = MainCourseColors.Danger)
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
        }
    }
}

@Composable
private fun EmptyPanel(message: String, help: String? = null) {
    Surface(
        shape = MainCourseShapes.Panel,
        color = MainCourseColors.Surface,
        border = BorderStroke(1.dp, MainCourseColors.Hairline),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(message, style = MaterialTheme.typography.titleMedium)
            help?.let { Text(it, color = MainCourseColors.Body) }
        }
    }
}

@Composable
private fun LoadingPanel() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator()
        Text(stringResource(R.string.shopping_loading), color = MainCourseColors.Body)
    }
}
