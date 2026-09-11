package com.getmaincourse.app.features.recipes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.getmaincourse.app.R
import com.getmaincourse.app.data.model.Cookbook
import com.getmaincourse.app.ui.theme.MainCourseShapes

@Composable
internal fun CookbookTitleMenu(
    cookbooks: List<Cookbook>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = cookbooks.firstOrNull { it.id == selectedId }
    val canChange = cookbooks.size > 1
    val interactionModifier = if (canChange) {
        Modifier.clickable(
            role = Role.Button,
            onClick = { expanded = true },
        )
    } else {
        Modifier
    }

    Box {
        Row(
            modifier = Modifier
                .clip(MainCourseShapes.Control)
                .then(interactionModifier)
                .testTag("cookbook_picker")
                .heightIn(min = 48.dp)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selected?.name ?: stringResource(R.string.recipes),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (canChange) {
                Icon(
                    painter = painterResource(R.drawable.ic_expand_more),
                    contentDescription = stringResource(R.string.cookbook_change),
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            cookbooks.forEach { cookbook ->
                val isSelected = cookbook.id == selectedId
                DropdownMenuItem(
                    text = { Text(cookbook.name) },
                    onClick = {
                        expanded = false
                        onSelect(cookbook.id)
                    },
                    enabled = !isSelected,
                    trailingIcon = {
                        if (isSelected) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = stringResource(R.string.cookbook_selected),
                            )
                        }
                    },
                )
            }
        }
    }
}
