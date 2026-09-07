package com.getmaincourse.app.features.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.getmaincourse.app.BuildConfig
import com.getmaincourse.app.R
import com.getmaincourse.app.data.model.User
import com.getmaincourse.app.ui.theme.MainCourseColors
import com.getmaincourse.app.ui.theme.MainCourseMono
import com.getmaincourse.app.ui.theme.MainCourseShapes

@Composable
fun SettingsScreen(user: User, onOpenDesignSystem: () -> Unit, onLogout: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("screen_Settings"),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Surface(shape = MainCourseShapes.Panel, color = MainCourseColors.Surface, border = BorderStroke(1.dp, MainCourseColors.Hairline)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.account), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.signed_in_as), style = MaterialTheme.typography.labelMedium, color = MainCourseColors.Muted)
                    user.name?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
                    Text(user.email, color = MainCourseColors.Body)
                    Button(onClick = onLogout, shape = MainCourseShapes.Control) { Text(stringResource(R.string.sign_out)) }
                }
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
}
