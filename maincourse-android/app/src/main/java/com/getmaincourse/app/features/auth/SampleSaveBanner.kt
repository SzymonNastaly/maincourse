package com.getmaincourse.app.features.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.getmaincourse.app.R
import com.getmaincourse.app.ui.theme.MainCourseColors

@Composable
internal fun SampleSaveBanner(state: SampleSaveUiState, onRetry: () -> Unit, onContinue: () -> Unit, onOpen: () -> Unit) {
    if (!state.saving && !state.failed && state.saved == null) return
    Surface(color = MainCourseColors.AccentTint) {
        Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())
            .padding(16.dp).testTag("sample_save_status").semantics { liveRegion = LiveRegionMode.Polite }) {
            if (state.saving || state.failed) {
                val sample = rememberDemoRecipe()
                DemoPhoto(sample, Modifier.height(90.dp))
                Text(sample.name, style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(when {
                state.saving -> R.string.demo_saving
                state.failed -> R.string.demo_save_failed
                else -> R.string.demo_saved
            }))
            if (state.saving) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.failed) TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                if (state.saved != null) TextButton(onClick = onOpen) { Text(stringResource(R.string.demo_open)) }
                else TextButton(onClick = onContinue) { Text(stringResource(R.string.onboarding_continue)) }
            }
        }
    }
}
