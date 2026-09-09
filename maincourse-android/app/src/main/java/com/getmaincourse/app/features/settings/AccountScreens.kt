package com.getmaincourse.app.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.getmaincourse.app.R
import com.getmaincourse.app.ui.theme.MainCourseColors
import com.getmaincourse.app.ui.theme.MainCourseShapes

@Composable
fun DeleteAccountDialog(
    deleting: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmation by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        title = { Text(stringResource(R.string.delete_warning), color = MainCourseColors.Danger) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.delete_account_details))
                Text(stringResource(R.string.delete_account_transfer), color = MainCourseColors.Body)
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = { confirmation = it },
                    label = { Text(stringResource(R.string.delete_confirmation_prompt)) },
                    enabled = !deleting,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("delete_confirmation"),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    shape = MainCourseShapes.Control,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = confirmation == DELETE_PHRASE && !deleting,
                modifier = Modifier.testTag("delete_account_button"),
                colors = ButtonDefaults.buttonColors(containerColor = MainCourseColors.Danger),
            ) {
                if (deleting) CircularProgressIndicator(strokeWidth = 2.dp)
                Text(stringResource(R.string.delete_my_account))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !deleting) {
                Text(stringResource(R.string.cancel))
            }
        },
        shape = MainCourseShapes.Panel,
    )
}

private const val DELETE_PHRASE = "DELETE"
