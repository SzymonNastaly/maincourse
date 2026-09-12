package com.getmaincourse.app.features.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.getmaincourse.app.R
import com.getmaincourse.app.ui.theme.MainCourseColors
import com.getmaincourse.app.ui.theme.MainCourseShapes

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onSave: (name: String, remindersEnabled: Boolean) -> Unit,
    onDeleteAccount: () -> Unit,
    onSignOut: () -> Unit,
    onClearError: () -> Unit,
    onManageCookbooks: () -> Unit,
    notificationsEnabled: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    val user = state.user ?: return
    var name by rememberSaveable(user.id) { mutableStateOf(user.name.orEmpty()) }
    var remindersEnabled by rememberSaveable(user.id) {
        mutableStateOf(user.lifecycleNotificationsEnabled)
    }
    var confirmingDelete by rememberSaveable(user.id) { mutableStateOf(false) }
    val busy = state.saving || state.deleting
    val trimmedName = name.trim()
    val changed = state.pendingPersistence ||
        trimmedName != user.name.orEmpty() ||
        remindersEnabled != user.lifecycleNotificationsEnabled
    val valid = trimmedName.isNotEmpty() && trimmedName.length <= MAX_NAME_LENGTH

    LaunchedEffect(user.name, user.lifecycleNotificationsEnabled) {
        name = user.name.orEmpty()
        remindersEnabled = user.lifecycleNotificationsEnabled
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("screen_Settings"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Surface(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                shape = MainCourseShapes.Panel,
                color = MainCourseColors.Surface,
                border = BorderStroke(1.dp, MainCourseColors.Hairline),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(stringResource(R.string.account), style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            onClearError()
                        },
                        modifier = Modifier.fillMaxWidth().testTag("settings_name"),
                        enabled = !busy,
                        isError = name.trim().length > MAX_NAME_LENGTH,
                        label = { Text(stringResource(R.string.auth_name)) },
                        supportingText = {
                            Text(
                                if (name.trim().length > MAX_NAME_LENGTH) {
                                    stringResource(R.string.name_too_long)
                                } else {
                                    stringResource(R.string.edit_name_help)
                                },
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        shape = MainCourseShapes.Control,
                    )
                    OutlinedTextField(
                        value = user.email,
                        onValueChange = {},
                        modifier = Modifier.fillMaxWidth().testTag("settings_email"),
                        enabled = !busy,
                        readOnly = true,
                        label = { Text(stringResource(R.string.auth_email)) },
                        singleLine = true,
                        shape = MainCourseShapes.Control,
                    )
                    Row(
                        Modifier.fillMaxWidth()
                            .testTag("recipe_reminders")
                            .toggleable(
                                value = remindersEnabled,
                                enabled = !busy,
                                role = Role.Switch,
                                onValueChange = {
                                    remindersEnabled = it
                                    onClearError()
                                    if (it) onRequestNotificationPermission()
                                },
                            )
                            .semantics(mergeDescendants = true) {}
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.recipe_reminders), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.recipe_reminders_help),
                                style = MaterialTheme.typography.bodySmall,
                                color = MainCourseColors.Body,
                            )
                        }
                        Switch(checked = remindersEnabled, onCheckedChange = null, enabled = !busy)
                    }
                    if (user.lifecycleNotificationsEnabled && !notificationsEnabled) {
                        Text(
                            stringResource(R.string.notifications_disabled_device),
                            style = MaterialTheme.typography.bodySmall,
                            color = MainCourseColors.Body,
                        )
                        OutlinedButton(
                            onClick = onOpenNotificationSettings,
                            modifier = Modifier.fillMaxWidth().testTag("notification_settings"),
                            enabled = !busy,
                            shape = MainCourseShapes.Control,
                        ) {
                            Text(stringResource(R.string.open_notification_settings))
                        }
                    }
                    state.error?.let {
                        Text(
                            it,
                            color = MainCourseColors.Danger,
                            modifier = Modifier.testTag("settings_error").semantics {
                                liveRegion = LiveRegionMode.Polite
                            },
                        )
                    }
                    Button(
                        onClick = { onSave(trimmedName, remindersEnabled) },
                        modifier = Modifier.fillMaxWidth().testTag("settings_save"),
                        enabled = valid && changed && !busy,
                        shape = MainCourseShapes.Control,
                    ) {
                        if (state.saving) {
                            CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                        }
                        Text(stringResource(R.string.save))
                    }
                }
            }
        }
        item {
            Surface(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                shape = MainCourseShapes.Panel,
                color = MainCourseColors.Surface,
                border = BorderStroke(1.dp, MainCourseColors.Hairline),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.cookbook_manage), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.cookbook_create_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MainCourseColors.Body,
                    )
                    OutlinedButton(
                        onClick = onManageCookbooks,
                        modifier = Modifier.fillMaxWidth().testTag("settings_cookbooks"),
                        enabled = !busy,
                        shape = MainCourseShapes.Control,
                    ) {
                        Text(stringResource(R.string.cookbook_manage))
                    }
                }
            }
        }
        item {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth().testTag("settings_sign_out"),
                    enabled = !busy,
                    shape = MainCourseShapes.Control,
                ) {
                    Text(stringResource(R.string.sign_out))
                }
                OutlinedButton(
                    onClick = {
                        onClearError()
                        confirmingDelete = true
                    },
                    modifier = Modifier.fillMaxWidth().testTag("settings_delete"),
                    enabled = !busy,
                    shape = MainCourseShapes.Control,
                ) {
                    Text(stringResource(R.string.delete_account), color = MainCourseColors.Danger)
                }
            }
        }
    }

    if (confirmingDelete) {
        DeleteAccountDialog(
            deleting = state.deleting,
            error = state.error,
            onConfirm = onDeleteAccount,
            onDismiss = { confirmingDelete = false },
        )
    }
}

private const val MAX_NAME_LENGTH = 50
