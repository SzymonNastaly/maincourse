package com.getmaincourse.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.getmaincourse.app.R
import com.getmaincourse.app.data.model.User
import com.getmaincourse.app.ui.theme.MainCourseColors
import com.getmaincourse.app.ui.theme.MainCourseShapes

@Composable
fun EditNameDialog(
    user: User,
    accountState: AccountState,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by rememberSaveable(user.id) { mutableStateOf(user.name.orEmpty()) }
    var submittedName by rememberSaveable(user.id) { mutableStateOf<String?>(null) }
    val trimmed = draft.trim()
    val valid = trimmed.isNotEmpty() && trimmed.length <= 50 && trimmed != user.name
    val busy = accountState.operation != AccountOperation.IDLE
    LaunchedEffect(user.name, accountState.operation, accountState.error, submittedName) {
        if (submittedName != null && user.name == submittedName && !busy && accountState.error == null) onDismiss()
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.edit_name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text(stringResource(R.string.auth_name)) },
                    supportingText = {
                        Text(
                            if (draft.trim().length > 50) stringResource(R.string.name_too_long)
                            else stringResource(R.string.edit_name_help),
                        )
                    },
                    isError = draft.trim().length > 50,
                    enabled = !busy,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_name_input"),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    shape = MainCourseShapes.Card,
                )
                accountState.error?.let {
                    Text(
                        it,
                        color = MainCourseColors.Danger,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    submittedName = trimmed
                    onSave(trimmed)
                },
                enabled = valid && !busy,
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) }
        },
        shape = MainCourseShapes.Panel,
    )
}

@Composable
fun ManageAccountScreen(user: User, onDeleteAccount: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().testTag("manage_account_screen"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.signed_in_as), color = MainCourseColors.Muted)
                Text(user.email)
                user.name?.takeIf { it.isNotBlank() }?.let { Text(it, color = MainCourseColors.Body) }
                TextButton(onClick = onDeleteAccount) {
                    Text(stringResource(R.string.delete_account), color = MainCourseColors.Danger)
                }
                Text(stringResource(R.string.delete_account_summary), color = MainCourseColors.Body)
            }
        }
    }
}

@Composable
fun DeleteAccountScreen(
    accountState: AccountState,
    onDeleteAccount: () -> Unit,
    onClearError: () -> Unit,
) {
    var confirmation by rememberSaveable { mutableStateOf("") }
    val deleting = accountState.operation == AccountOperation.DELETING
    LazyColumn(
        Modifier.fillMaxSize().testTag("delete_account_screen"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.delete_warning), style = MaterialTheme.typography.titleLarge, color = MainCourseColors.Danger)
                Text(stringResource(R.string.delete_account_details))
                Text(stringResource(R.string.delete_account_transfer), color = MainCourseColors.Body)
                Text(stringResource(R.string.delete_confirmation_prompt))
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it },
                    enabled = !deleting,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("delete_confirmation"),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    shape = MainCourseShapes.Card,
                )
                Button(
                    onClick = onDeleteAccount,
                    enabled = confirmation == DELETE_PHRASE && !deleting,
                    modifier = Modifier.fillMaxWidth().testTag("delete_account_button"),
                    shape = MainCourseShapes.Control,
                    colors = ButtonDefaults.buttonColors(containerColor = MainCourseColors.Danger),
                ) {
                    if (deleting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.delete_my_account), Modifier.padding(start = if (deleting) 8.dp else 0.dp))
                }
                accountState.error?.let {
                    AccountError(it, false, onRetry = {}, onDismiss = onClearError)
                }
            }
        }
    }
}

private const val DELETE_PHRASE = "DELETE"
