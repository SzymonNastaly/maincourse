package com.getmaincourse.app.features.preview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.getmaincourse.app.BuildConfig
import com.getmaincourse.app.Destination
import com.getmaincourse.app.R
import com.getmaincourse.app.ui.theme.MainCourseColors
import com.getmaincourse.app.ui.theme.MainCourseMono
import com.getmaincourse.app.ui.theme.MainCourseShapes

@Composable
fun PreviewScreen(destination: Destination, onOpenDesignSystem: () -> Unit) {
    val (title, description) = when (destination) {
        Destination.Recipes -> R.string.recipes_preview_title to R.string.recipes_preview_body
        Destination.Shopping -> R.string.shopping_preview_title to R.string.shopping_preview_body
        Destination.Search -> R.string.search_preview_title to R.string.search_preview_body
        Destination.Settings -> R.string.settings_preview_title to R.string.settings_preview_body
        Destination.DesignSystem -> error("The gallery has its own destination")
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth().testTag("screen_${destination.name}"),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    Image(
                        painter = painterResource(R.drawable.brand_mark),
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                    )
                    Surface(color = MainCourseColors.Ink, shape = MainCourseShapes.Control) {
                        Text(
                            stringResource(R.string.preview_badge),
                            color = MainCourseColors.Surface,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                    Text(stringResource(title), style = MaterialTheme.typography.headlineLarge)
                    Text(
                        stringResource(description),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MainCourseColors.Body,
                    )
                }
            }
            item {
                Surface(
                    shape = MainCourseShapes.Panel,
                    color = MainCourseColors.Surface,
                    border = BorderStroke(1.dp, MainCourseColors.Hairline),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.native_foundation), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.foundation_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MainCourseColors.Body,
                        )
                        Button(onClick = onOpenDesignSystem, shape = MainCourseShapes.Control) {
                            Text(stringResource(R.string.explore_design))
                        }
                    }
                }
            }
            if (destination == Destination.Settings) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.preview_version), style = MaterialTheme.typography.labelMedium)
                        Text(BuildConfig.VERSION_NAME, fontFamily = MainCourseMono, color = MainCourseColors.Body)
                    }
                }
            }
        }
    }
}
