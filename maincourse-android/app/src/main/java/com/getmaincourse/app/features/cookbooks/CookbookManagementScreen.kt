package com.getmaincourse.app.features.cookbooks

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.getmaincourse.app.R
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.ui.theme.MainCourseColors
import com.getmaincourse.app.ui.theme.MainCourseShapes

@Composable
fun CookbookManagementScreen(
    userId: Long,
    state: CookbookManagementUiState,
    onCreate: (String, Boolean) -> Unit,
    onGenerateInvitation: () -> Unit,
    onLeave: () -> Unit,
    onDelete: () -> Unit,
    onDismissInvitation: () -> Unit,
    onClearError: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var moveRecipes by rememberSaveable { mutableStateOf(false) }
    var confirmation by rememberSaveable { mutableStateOf<CookbookConfirmation?>(null) }
    val shared = state.sharedCookbook
    val busy = state.loading || state.working
    val ownsShared = shared?.members?.any { it.id == userId && it.role == "owner" } == true

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("screen_Cookbooks"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        state.personalCookbook?.let { personal ->
            item { CookbookSummaryCard(personal, stringResource(R.string.cookbook_personal)) }
        }
        if (state.loading && state.cookbooks.isEmpty()) {
            item { CircularProgressIndicator(Modifier.testTag("cookbook_loading")) }
        } else if (shared == null) {
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
                        Text(stringResource(R.string.cookbook_create_shared), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.cookbook_create_help),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MainCourseColors.Body,
                        )
                        OutlinedTextField(
                            value = name,
                            onValueChange = {
                                name = it
                                onClearError()
                            },
                            modifier = Modifier.fillMaxWidth().testTag("cookbook_name"),
                            enabled = !busy,
                            label = { Text(stringResource(R.string.cookbook_name)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            shape = MainCourseShapes.Control,
                        )
                        Row(
                            Modifier.fillMaxWidth()
                                .testTag("cookbook_move_recipes")
                                .toggleable(
                                    value = moveRecipes,
                                    enabled = !busy,
                                    role = Role.Checkbox,
                                    onValueChange = { moveRecipes = it },
                                )
                                .semantics(mergeDescendants = true) {}
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = moveRecipes, onCheckedChange = null, enabled = !busy)
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.cookbook_move_personal), style = MaterialTheme.typography.titleSmall)
                                Text(
                                    stringResource(R.string.cookbook_move_personal_help),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MainCourseColors.Body,
                                )
                            }
                        }
                        Button(
                            onClick = { onCreate(name, moveRecipes) },
                            modifier = Modifier.fillMaxWidth().testTag("cookbook_create"),
                            enabled = name.isNotBlank() && !busy,
                            shape = MainCourseShapes.Control,
                        ) {
                            if (state.working) CircularProgressIndicator(Modifier.padding(end = 8.dp), strokeWidth = 2.dp)
                            Text(stringResource(R.string.cookbook_create))
                        }
                    }
                }
            }
        } else {
            item {
                SharedCookbookCard(
                    cookbook = shared,
                    ownsShared = ownsShared,
                    working = state.working,
                    onGenerateInvitation = onGenerateInvitation,
                    onConfirmLeave = { confirmation = CookbookConfirmation.Leave },
                    onConfirmDelete = { confirmation = CookbookConfirmation.Delete },
                )
            }
        }
        state.error?.let { error ->
            item {
                Text(
                    error,
                    color = MainCourseColors.Danger,
                    modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().testTag("cookbook_error").semantics {
                        liveRegion = LiveRegionMode.Polite
                    },
                )
            }
        }
    }

    state.invitation?.let { invitation ->
        InvitationLinkDialog(invitation.inviteUrl, onDismissInvitation)
    }
    confirmation?.let { pending ->
        val deleting = pending == CookbookConfirmation.Delete
        AlertDialog(
            onDismissRequest = { if (!state.working) confirmation = null },
            title = {
                Text(stringResource(if (deleting) R.string.cookbook_delete_title else R.string.cookbook_leave_title))
            },
            text = {
                Text(stringResource(if (deleting) R.string.cookbook_delete_body else R.string.cookbook_leave_body))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmation = null
                        if (deleting) onDelete() else onLeave()
                    },
                    enabled = !state.working,
                    modifier = Modifier.testTag(if (deleting) "cookbook_confirm_delete" else "cookbook_confirm_leave"),
                ) {
                    Text(
                        stringResource(if (deleting) R.string.cookbook_delete else R.string.cookbook_leave),
                        color = MainCourseColors.Danger,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmation = null }, enabled = !state.working) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun CookbookSummaryCard(cookbook: Cookbook, label: String) {
    Surface(
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
        shape = MainCourseShapes.Panel,
        color = MainCourseColors.Surface,
        border = BorderStroke(1.dp, MainCourseColors.Hairline),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MainCourseColors.Muted)
            Text(cookbook.name, style = MaterialTheme.typography.titleMedium)
            Text(
                pluralStringResource(R.plurals.cookbook_recipes, cookbook.recipeCount, cookbook.recipeCount),
                style = MaterialTheme.typography.bodySmall,
                color = MainCourseColors.Body,
            )
        }
    }
}

@Composable
private fun SharedCookbookCard(
    cookbook: Cookbook,
    ownsShared: Boolean,
    working: Boolean,
    onGenerateInvitation: () -> Unit,
    onConfirmLeave: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().testTag("shared_cookbook"),
        shape = MainCourseShapes.Panel,
        color = MainCourseColors.Surface,
        border = BorderStroke(1.dp, MainCourseColors.Hairline),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.cookbook_shared), style = MaterialTheme.typography.labelMedium, color = MainCourseColors.Muted)
                Text(cookbook.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    pluralStringResource(R.plurals.cookbook_recipes, cookbook.recipeCount, cookbook.recipeCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MainCourseColors.Body,
                )
            }
            Text(stringResource(R.string.cookbook_members), style = MaterialTheme.typography.titleSmall)
            cookbook.members.forEach { member ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(member.email, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(if (member.role == "owner") R.string.cookbook_owner else R.string.cookbook_member),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (member.role == "owner") FontWeight.SemiBold else FontWeight.Normal,
                        color = if (member.role == "owner") MainCourseColors.Amber else MainCourseColors.Body,
                    )
                }
            }
            if (ownsShared) {
                Button(
                    onClick = onGenerateInvitation,
                    modifier = Modifier.fillMaxWidth().testTag("cookbook_invite"),
                    enabled = !working,
                    shape = MainCourseShapes.Control,
                ) {
                    Text(stringResource(R.string.cookbook_invite))
                }
                OutlinedButton(
                    onClick = onConfirmDelete,
                    modifier = Modifier.fillMaxWidth().testTag("cookbook_delete"),
                    enabled = !working,
                    shape = MainCourseShapes.Control,
                ) {
                    Text(stringResource(R.string.cookbook_delete), color = MainCourseColors.Danger)
                }
            } else {
                OutlinedButton(
                    onClick = onConfirmLeave,
                    modifier = Modifier.fillMaxWidth().testTag("cookbook_leave"),
                    enabled = !working,
                    shape = MainCourseShapes.Control,
                ) {
                    Text(stringResource(R.string.cookbook_leave), color = MainCourseColors.Danger)
                }
            }
        }
    }
}

@Composable
private fun InvitationLinkDialog(inviteUrl: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.invitation_created)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.invitation_share_help))
                Surface(shape = MainCourseShapes.Control, color = MainCourseColors.Sunken) {
                    Text(
                        inviteUrl,
                        modifier = Modifier.fillMaxWidth().padding(12.dp).testTag("invitation_url"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { shareInvitation(context, inviteUrl) }, modifier = Modifier.testTag("invitation_share")) {
                Text(stringResource(R.string.share))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { copyInvitation(context, inviteUrl) }, modifier = Modifier.testTag("invitation_copy")) {
                    Text(stringResource(R.string.copy))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) }
            }
        },
    )
}

private fun copyInvitation(context: Context, inviteUrl: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.invitation_link), inviteUrl))
}

private fun shareInvitation(context: Context, inviteUrl: String) {
    val intent = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_TEXT, inviteUrl)
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.invitation_share)))
}

private enum class CookbookConfirmation { Leave, Delete }
