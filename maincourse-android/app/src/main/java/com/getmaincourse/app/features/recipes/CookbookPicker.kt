package com.getmaincourse.app.features.recipes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.getmaincourse.app.R
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.ui.theme.MainCourseColors
import com.getmaincourse.app.ui.theme.MainCourseShapes

@Composable
internal fun CookbookPicker(
    cookbooks: List<Cookbook>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = cookbooks.firstOrNull { it.id == selectedId }

    Box {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MainCourseShapes.Control)
                .clickable(enabled = enabled && cookbooks.isNotEmpty()) { expanded = true }
                .testTag("cookbook_picker"),
            shape = MainCourseShapes.Control,
            border = BorderStroke(1.dp, MainCourseColors.Hairline),
            color = MainCourseColors.Surface,
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    stringResource(R.string.cookbook_picker),
                    style = MaterialTheme.typography.labelSmall,
                    color = MainCourseColors.Muted,
                )
                Text(
                    selected?.name ?: stringResource(R.string.cookbook_none_selected),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            cookbooks.forEach { cookbook ->
                DropdownMenuItem(
                    text = { Text(cookbook.name) },
                    onClick = {
                        expanded = false
                        if (cookbook.id != selectedId) onSelect(cookbook.id)
                    },
                )
            }
        }
    }
}
