package com.getmaincourse.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import java.text.NumberFormat
import java.util.Locale

/** Resource configuration follows the app language, which can differ from the device. */
@Composable
fun appLocale(): Locale = LocalResources.current.configuration.locales[0]

@Composable
fun displayNumber(value: Int): String = NumberFormat.getIntegerInstance(appLocale()).format(value)
