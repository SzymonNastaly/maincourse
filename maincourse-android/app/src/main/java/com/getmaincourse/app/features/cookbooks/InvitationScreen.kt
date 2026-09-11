package com.getmaincourse.app.features.cookbooks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.getmaincourse.app.R
import com.getmaincourse.app.ui.theme.MainCourseColors
import com.getmaincourse.app.ui.theme.MainCourseShapes

@Composable
fun InvitationScreen(
    state: InvitationUiState,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).testTag("screen_Invitation"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.weight(1f))
        when {
            state.loading -> {
                CircularProgressIndicator()
                Text(stringResource(R.string.invitation_loading), color = MainCourseColors.Body)
            }
            state.accepting -> {
                CircularProgressIndicator()
                Text(stringResource(R.string.invitation_joining), color = MainCourseColors.Body)
            }
            state.acceptance != null -> {
                Text(stringResource(R.string.invitation_joined), style = MaterialTheme.typography.headlineMedium)
                Text(
                    stringResource(R.string.invitation_joined_name, state.acceptance.cookbookName),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MainCourseColors.Body,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = onDone,
                    modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().testTag("invitation_done"),
                    shape = MainCourseShapes.Control,
                ) { Text(stringResource(R.string.done)) }
            }
            state.error != null -> {
                Text(stringResource(R.string.invitation_error_title), style = MaterialTheme.typography.headlineMedium)
                Text(
                    state.error,
                    modifier = Modifier.testTag("invitation_error"),
                    color = MainCourseColors.Body,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = onDone,
                    modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                    shape = MainCourseShapes.Control,
                ) { Text(stringResource(R.string.dismiss)) }
            }
            state.preview != null -> {
                Text(stringResource(R.string.invitation_title), style = MaterialTheme.typography.headlineMedium)
                Surface(
                    modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                    shape = MainCourseShapes.Panel,
                    color = MainCourseColors.Surface,
                    border = BorderStroke(1.dp, MainCourseColors.Hairline),
                ) {
                    Column(
                        Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(state.preview.cookbookName, style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.invitation_from, state.preview.inviterEmail),
                            color = MainCourseColors.Body,
                        )
                    }
                }
                if (state.hasSharedCookbook) {
                    Surface(
                        modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth(),
                        shape = MainCourseShapes.Card,
                        color = MainCourseColors.AmberTint,
                    ) {
                        Text(
                            stringResource(R.string.invitation_existing_shared),
                            modifier = Modifier.padding(14.dp),
                            color = MainCourseColors.Body,
                        )
                    }
                }
                Button(
                    onClick = onAccept,
                    modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().testTag("invitation_accept"),
                    enabled = !state.hasSharedCookbook,
                    shape = MainCourseShapes.Control,
                ) { Text(stringResource(R.string.invitation_join)) }
                OutlinedButton(
                    onClick = onDecline,
                    modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth().testTag("invitation_decline"),
                    shape = MainCourseShapes.Control,
                ) { Text(stringResource(R.string.invitation_decline)) }
            }
        }
        Spacer(Modifier.weight(1f))
    }
}
