package com.getmaincourse.app.features.recipes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.getmaincourse.app.R
import com.getmaincourse.app.ui.localized
import com.getmaincourse.app.ui.theme.MainCourseColors
import com.getmaincourse.app.ui.theme.MainCourseShapes

@Composable
internal fun RecipeShareSheet(
    state: RecipeShareUiState,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    ShareSheetFrame {
        Text(
            stringResource(R.string.recipe_import_share_target),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        state.destinationName?.let { destination ->
            Text(
                stringResource(R.string.recipe_share_destination, destination),
                style = MaterialTheme.typography.bodyMedium,
                color = MainCourseColors.Body,
            )
        }
        Spacer(Modifier.height(6.dp))

        when (val status = state.status) {
            RecipeShareStatus.Preparing -> ShareProgress(stringResource(R.string.recipe_share_preparing))
            is RecipeShareStatus.ReadingPage -> ShareProgress(stringResource(R.string.recipe_share_reading_page))
            RecipeShareStatus.Sending -> ShareProgress(stringResource(R.string.recipe_share_sending))
            RecipeShareStatus.Success -> ShareSuccess()
            is RecipeShareStatus.Failed -> ShareFailure(status.message.localized(), onRetry)
        }

        if (state.status !is RecipeShareStatus.Success) {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End).testTag("share_cancel"),
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

@Composable
internal fun RecipeShareAuthenticationSheet(
    message: String? = null,
    onOpenApp: () -> Unit,
    onRetry: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    ShareSheetFrame {
        Text(
            if (message == null) {
                stringResource(R.string.recipe_share_sign_in)
            } else {
                stringResource(R.string.recipe_share_startup_failed)
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            message ?: stringResource(R.string.recipe_share_sign_in_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MainCourseColors.Body,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag("share_cancel")) {
                Text(stringResource(R.string.cancel))
            }
            if (onRetry != null) {
                TextButton(onClick = onRetry, modifier = Modifier.testTag("share_retry_startup")) {
                    Text(stringResource(R.string.retry))
                }
            }
            Button(onClick = onOpenApp, modifier = Modifier.testTag("share_open_app")) {
                Text(stringResource(R.string.recipe_share_open_app))
            }
        }
    }
}

@Composable
private fun ShareSheetFrame(
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
                .testTag("recipe_share_sheet"),
            shape = MainCourseShapes.Panel,
            color = MainCourseColors.Surface,
            shadowElevation = 10.dp,
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun ShareProgress(message: String) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("share_progress"),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ShareSuccess() {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("share_success"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = MainCourseShapes.Control, color = MainCourseColors.AccentTint) {
                Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp).size(20.dp),
                    tint = MainCourseColors.Accent,
                )
            }
            Text(
                stringResource(R.string.recipe_share_success),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            stringResource(R.string.recipe_share_success_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MainCourseColors.Body,
        )
    }
}

@Composable
private fun ShareFailure(message: String, onRetry: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth().testTag("share_error"),
            shape = MainCourseShapes.Panel,
            color = MainCourseColors.DangerTint,
            border = BorderStroke(1.dp, MainCourseColors.DangerLine),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.recipe_failed),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MainCourseColors.Danger,
                )
                Text(message, color = MainCourseColors.Danger)
            }
        }
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().testTag("share_retry")) {
            Text(stringResource(R.string.retry))
        }
    }
}
