package com.getmaincourse.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MainCourseThemeTest {
    @Test
    fun materialRolesPreserveTheBrandPalette() {
        assertEquals(MainCourseColors.Accent, MainCourseColorScheme.primary)
        assertEquals(MainCourseColors.Canvas, MainCourseColorScheme.background)
        assertEquals(MainCourseColors.Surface, MainCourseColorScheme.surface)
        assertEquals(MainCourseColors.Body, MainCourseColorScheme.onSurfaceVariant)
        assertEquals(MainCourseColors.Danger, MainCourseColorScheme.error)
    }

    @Test
    fun essentialTextMeetsNormalTextContrast() {
        listOf(
            MainCourseColors.Ink to MainCourseColors.Canvas,
            MainCourseColors.Body to MainCourseColors.Surface,
            MainCourseColors.Body to MainCourseColors.Canvas,
            MainCourseColors.Surface to MainCourseColors.Accent,
            MainCourseColors.AccentDark to MainCourseColors.AccentTint,
            MainCourseColors.Surface to MainCourseColors.Ink,
            MainCourseColors.Danger to MainCourseColors.DangerTint,
        ).forEach { (foreground, background) ->
            val ratio = contrast(foreground, background)
            assertTrue("$foreground on $background has contrast $ratio", ratio >= 4.5f)
        }
    }

    private fun contrast(first: Color, second: Color): Float {
        val a = first.luminance()
        val b = second.luminance()
        return (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    }
}
