package com.getmaincourse.app.features.cookbooks

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.getmaincourse.app.R
import com.getmaincourse.app.data.model.Cookbook

/**
 * Name to show. The stored English default is replaced by the app's own
 * translation; any other name is user content and shown as typed.
 */
@Composable
fun Cookbook.displayName(): String =
    if (defaultName) stringResource(R.string.cookbook_default_name) else name
