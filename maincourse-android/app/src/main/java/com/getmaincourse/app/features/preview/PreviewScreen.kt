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
import com.getmaincourse.app.R
import com.getmaincourse.app.ui.theme.MainCourseColors
import com.getmaincourse.app.ui.theme.MainCourseShapes

@Composable
fun PreviewScreen(
    title: String,
    body: String,
    testTag: String,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth().testTag(testTag),
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
                    Text(title, style = MaterialTheme.typography.headlineLarge)
                    Text(
                        body,
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
                    }
                }
            }
        }
    }
}
