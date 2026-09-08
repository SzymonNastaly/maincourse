package com.getmaincourse.app.features.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import com.getmaincourse.app.BuildConfig
import com.getmaincourse.app.R
import com.getmaincourse.app.data.model.User
import com.getmaincourse.app.ui.theme.MainCourseColors
import com.getmaincourse.app.ui.theme.MainCourseMono
import com.getmaincourse.app.ui.theme.MainCourseShapes

@Composable
fun SettingsScreen(
    user: User,
    accountState: AccountState,
    onUpdateName: (String) -> Unit,
    onUpdateLifecycleNotifications: (Boolean) -> Unit,
    onRetryAccountPersistence: () -> Unit,
    onClearAccountError: () -> Unit,
    onOpenManageAccount: () -> Unit,
    onOpenDesignSystem: () -> Unit,
    onLogout: () -> Unit,
) {
    var editingName by rememberSaveable(user.id) { mutableStateOf(false) }
    val accountBusy = accountState.operation != AccountOperation.IDLE
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
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.account), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.signed_in_as), style = MaterialTheme.typography.labelMedium, color = MainCourseColors.Muted)
                    Text(user.email, color = MainCourseColors.Body)
                    SettingsRow(
                        title = stringResource(R.string.edit_name),
                        body = user.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.name_not_set),
                        enabled = !accountBusy,
                        onClick = {
                            if (!accountState.canRetryPersistence) onClearAccountError()
                            editingName = true
                        },
                    )
                    Row(
                        Modifier.fillMaxWidth()
                            .testTag("recipe_reminders")
                            .toggleable(
                                value = user.lifecycleNotificationsEnabled,
                                enabled = !accountBusy,
                                role = Role.Switch,
                                onValueChange = onUpdateLifecycleNotifications,
                            )
                            .semantics(mergeDescendants = true) {},
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
                        Switch(
                            checked = user.lifecycleNotificationsEnabled,
                            onCheckedChange = null,
                            enabled = !accountBusy,
                        )
                    }
                    SettingsRow(
                        title = stringResource(R.string.manage_account),
                        body = stringResource(R.string.manage_account_help),
                        enabled = !accountBusy,
                        onClick = onOpenManageAccount,
                    )
                    Button(onClick = onLogout, enabled = !accountBusy, shape = MainCourseShapes.Control) {
                        Text(stringResource(R.string.sign_out))
                    }
                }
            }
        }
        if (accountState.error != null || accountState.canRetryPersistence) {
            item {
                AccountError(
                    error = accountState.error,
                    canRetryPersistence = accountState.canRetryPersistence,
                    enabled = !accountBusy,
                    onRetry = onRetryAccountPersistence,
                    onDismiss = onClearAccountError,
                )
            }
        }
        item {
            OutlinedButton(onClick = onOpenDesignSystem, shape = MainCourseShapes.Control) {
                Text(stringResource(R.string.explore_design))
            }
        }
        item {
            Text(stringResource(R.string.preview_version), style = MaterialTheme.typography.labelMedium)
            Text(BuildConfig.VERSION_NAME, fontFamily = MainCourseMono, color = MainCourseColors.Body)
        }
    }
    if (editingName) {
        EditNameDialog(
            user = user,
            accountState = accountState,
            onSave = onUpdateName,
            onRetryPersistence = onRetryAccountPersistence,
            onDismiss = {
                if (!accountState.canRetryPersistence) onClearAccountError()
                editingName = false
            },
        )
    }
}

@Composable
private fun SettingsRow(title: String, body: String, enabled: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodySmall, color = MainCourseColors.Body)
    }
}

@Composable
internal fun AccountError(
    error: String?,
    canRetryPersistence: Boolean,
    enabled: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().testTag("account_error"),
        color = MainCourseColors.DangerTint,
        shape = MainCourseShapes.Card,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            error?.let { Text(it, color = MainCourseColors.Danger) }
            if (canRetryPersistence) {
                Text(stringResource(R.string.account_save_pending), color = MainCourseColors.Danger)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canRetryPersistence) {
                    Button(onClick = onRetry, enabled = enabled, modifier = Modifier.testTag("account_retry")) {
                        Text(stringResource(R.string.retry_save))
                    }
                }
                if (error != null) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) }
                }
            }
        }
    }
}
