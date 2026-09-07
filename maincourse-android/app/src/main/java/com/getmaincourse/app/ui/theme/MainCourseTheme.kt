package com.getmaincourse.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.getmaincourse.app.R

// Mirrors app/assets/tailwind/application.css; Android chrome stays Material.
object MainCourseColors {
    val Canvas = Color(0xFFEEF0F2)
    val Surface = Color(0xFFFFFFFF)
    val Rail = Color(0xFFF8F9FA)
    val Sunken = Color(0xFFF5F7F8)
    val Ink = Color(0xFF14171C)
    val Body = Color(0xFF5B6570)
    val Muted = Color(0xFF9AA3AE)
    val Line = Color(0xFFE3E6EA)
    val Hairline = Color(0xFFDCE0E6)
    val Accent = Color(0xFF16624B)
    val AccentDark = Color(0xFF0F4736)
    val AccentTint = Color(0xFFF1F7F4)
    val AccentLine = Color(0xFFD6E7DF)
    val Lime = Color(0xFFCDEB7A)
    val Amber = Color(0xFFB07D12)
    val AmberTint = Color(0xFFFBF3E0)
    val Danger = Color(0xFFB42318)
    val DangerTint = Color(0xFFFDF3F2)
    val DangerLine = Color(0xFFEFD5D3)
}

object MainCourseShapes {
    val Control = RoundedCornerShape(8.dp)
    val Card = RoundedCornerShape(10.dp)
    val Panel = RoundedCornerShape(12.dp)
}

val MainCourseMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
)

internal val MainCourseColorScheme = lightColorScheme(
    primary = MainCourseColors.Accent,
    onPrimary = MainCourseColors.Surface,
    primaryContainer = MainCourseColors.AccentTint,
    onPrimaryContainer = MainCourseColors.AccentDark,
    inversePrimary = MainCourseColors.AccentTint,
    secondary = MainCourseColors.Accent,
    onSecondary = MainCourseColors.Surface,
    secondaryContainer = MainCourseColors.AccentTint,
    onSecondaryContainer = MainCourseColors.AccentDark,
    tertiary = MainCourseColors.Accent,
    onTertiary = MainCourseColors.Surface,
    tertiaryContainer = MainCourseColors.AccentTint,
    onTertiaryContainer = MainCourseColors.AccentDark,
    background = MainCourseColors.Canvas,
    onBackground = MainCourseColors.Ink,
    surface = MainCourseColors.Surface,
    onSurface = MainCourseColors.Ink,
    surfaceVariant = MainCourseColors.Sunken,
    onSurfaceVariant = MainCourseColors.Body,
    surfaceTint = MainCourseColors.Accent,
    inverseSurface = MainCourseColors.Ink,
    inverseOnSurface = MainCourseColors.Surface,
    surfaceBright = MainCourseColors.Surface,
    surfaceDim = MainCourseColors.Canvas,
    surfaceContainerLowest = MainCourseColors.Surface,
    surfaceContainerLow = MainCourseColors.Rail,
    surfaceContainer = MainCourseColors.Sunken,
    surfaceContainerHigh = MainCourseColors.Canvas,
    surfaceContainerHighest = MainCourseColors.Line,
    outline = MainCourseColors.Body,
    outlineVariant = MainCourseColors.Hairline,
    error = MainCourseColors.Danger,
    onError = MainCourseColors.Surface,
    errorContainer = MainCourseColors.DangerTint,
    onErrorContainer = MainCourseColors.Danger,
    scrim = MainCourseColors.Ink,
)

@Composable
fun MainCourseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MainCourseColorScheme,
        typography = Typography(),
        shapes = Shapes(
            extraSmall = MainCourseShapes.Control,
            small = MainCourseShapes.Control,
            medium = MainCourseShapes.Card,
            large = MainCourseShapes.Panel,
        ),
        content = content,
    )
}
