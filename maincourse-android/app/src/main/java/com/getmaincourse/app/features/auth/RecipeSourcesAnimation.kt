package com.getmaincourse.app.features.auth

import android.animation.ValueAnimator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getmaincourse.app.R
import com.getmaincourse.app.ui.theme.MainCourseColors
import com.getmaincourse.app.ui.theme.MainCourseMono
import com.getmaincourse.app.ui.theme.MainCourseShapes
import kotlinx.coroutines.delay

/** The same four recognizable sources as iOS, drawn with Compose instead of a static image. */
@Composable
internal fun RecipeSourcesAnimation() {
    val density = LocalDensity.current
    if (density.fontScale >= 1.5f) return
    val sample = rememberDemoRecipe()
    var merged by rememberSaveable { mutableStateOf(!ValueAnimator.areAnimatorsEnabled()) }
    var visible by remember { mutableIntStateOf(if (merged) 4 else 0) }
    LaunchedEffect(Unit) {
        if (!merged) {
            delay(300)
            repeat(4) { visible = it + 1; delay(400) }
            delay(1_200)
            merged = true
        }
    }
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, 1f),
        LocalTextStyle provides LocalTextStyle.current.copy(lineHeight = TextUnit.Unspecified, letterSpacing = TextUnit.Unspecified),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(356.dp).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
            val fit = (maxWidth.value / 340f).coerceAtMost(1f)
            repeat(4) { index ->
                val shown = visible > index
                val spread by animateFloatAsState(if (merged) 0f else if (shown) 1f else 1.3f, tween(600), label = "source-position")
                val opacity by animateFloatAsState(if (shown && !merged) 1f else 0f, tween(500), label = "source-opacity")
                val scale by animateFloatAsState(if (merged) .5f else 1f, tween(600), label = "source-scale")
                Surface(
                    modifier = Modifier.size(136.dp, 164.dp).graphicsLayer {
                        translationX = (if (index % 2 == 0) -84.dp.toPx() else 86.dp.toPx()) * spread * fit
                        translationY = (if (index < 2) -80.dp.toPx() else 84.dp.toPx()) * spread * fit
                        rotationZ = listOf(-8f, 6f, 5f, -6f)[index] * spread
                        scaleX = scale * fit
                        scaleY = scale * fit
                        alpha = opacity
                    },
                    shape = MainCourseShapes.Card,
                    color = MainCourseColors.Surface,
                    border = BorderStroke(if (index == 3) 2.dp else 1.dp, if (index == 3) MainCourseColors.Ink else MainCourseColors.Hairline),
                    shadowElevation = 2.dp,
                ) {
                    when (index) {
                        0 -> MiniSocialPost(sample)
                        1 -> MiniWebsite()
                        2 -> MiniCookbookPage()
                        else -> MiniScreenshot()
                    }
                }
            }
            val resultOpacity by animateFloatAsState(if (merged) 1f else 0f, tween(650), label = "saved-card-opacity")
            val resultScale by animateFloatAsState(if (merged) 1f else .85f, tween(650), label = "saved-card-scale")
            MiniSavedRecipe(sample, Modifier.width(224.dp).graphicsLayer {
                alpha = resultOpacity; scaleX = resultScale; scaleY = resultScale
            })
        }
    }
}

@Composable
private fun MiniSocialPost(sample: DemoRecipe) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.demo_creator), null, Modifier.size(16.dp).clip(CircleShape))
            Text(sample.creator, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        DemoPhoto(sample, Modifier.height(76.dp), rounded = false)
        Row(Modifier.padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(R.drawable.ic_demo_heart, R.drawable.ic_demo_chat, R.drawable.ic_demo_send).forEach { Icon(painterResource(it), null, Modifier.size(10.dp)) }
        }
        Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PlaceholderLine(110.dp)
            PlaceholderLine(84.dp)
        }
    }
}

@Composable
private fun MiniWebsite() {
    Box {
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(Modifier.fillMaxWidth().background(MainCourseColors.Sunken).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(3) { Box(Modifier.size(5.dp).background(MainCourseColors.Hairline, CircleShape)) }
                Text("best-recipes.blog", fontSize = 7.sp, color = MainCourseColors.Muted)
            }
            Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                PlaceholderLine(96.dp, 7.dp, MainCourseColors.Body.copy(alpha = .5f))
                PlaceholderLine(118.dp)
                PlaceholderLine(104.dp)
                Box(Modifier.fillMaxWidth().height(34.dp).background(MainCourseColors.Sunken, RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.demo_ad), fontSize = 8.sp, color = MainCourseColors.Muted)
                }
                PlaceholderLine(112.dp)
                PlaceholderLine(90.dp)
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(MainCourseColors.Ink).padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.demo_cookies), fontSize = 8.sp, color = MainCourseColors.Surface)
        }
    }
}

@Composable
private fun MiniCookbookPage() {
    Box(Modifier.fillMaxSize().background(MainCourseColors.Body), contentAlignment = Alignment.Center) {
        Column(Modifier.size(112.dp, 144.dp).graphicsLayer { rotationZ = -4f }.background(MainCourseColors.Surface).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PlaceholderLine(70.dp, 7.dp, MainCourseColors.Ink.copy(alpha = .6f))
            PlaceholderLine(46.dp, color = MainCourseColors.Muted)
            repeat(4) { index ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text((index + 1).toString(), fontFamily = MainCourseMono, fontSize = 11.sp, color = MainCourseColors.Body)
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        PlaceholderLine(74.dp)
                        PlaceholderLine(58.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniScreenshot() {
    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("9:41", fontFamily = MainCourseMono, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Box(Modifier.size(10.dp, 5.dp).background(MainCourseColors.Ink, RoundedCornerShape(1.dp)))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PlaceholderLine(80.dp, 7.dp, MainCourseColors.Body.copy(alpha = .5f))
            PlaceholderLine(100.dp)
            PlaceholderLine(88.dp)
            PlaceholderLine(96.dp)
        }
        Surface(Modifier.align(Alignment.End), color = MainCourseColors.Accent, shape = CircleShape) {
            Text(stringResource(R.string.demo_make_this), Modifier.padding(horizontal = 7.dp, vertical = 4.dp), fontSize = 8.sp, color = MainCourseColors.Surface)
        }
        PlaceholderLine(72.dp)
    }
}

@Composable
private fun MiniSavedRecipe(sample: DemoRecipe, modifier: Modifier) {
    Surface(modifier, shape = MainCourseShapes.Panel, border = BorderStroke(1.dp, MainCourseColors.Hairline), shadowElevation = 2.dp) {
        Column {
            Box {
                DemoPhoto(sample, Modifier.height(112.dp), rounded = false)
                Surface(Modifier.padding(8.dp), color = MainCourseColors.Accent, shape = CircleShape) {
                    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(painterResource(R.drawable.ic_check), null, Modifier.size(12.dp), tint = MainCourseColors.Surface)
                        Text(stringResource(R.string.demo_saved_badge), fontSize = 10.sp, color = MainCourseColors.Surface)
                    }
                }
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(sample.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.recipe_time, sample.prepTime + sample.cookTime), fontSize = 10.sp, fontFamily = MainCourseMono, color = MainCourseColors.Body)
                    Icon(painterResource(R.drawable.ic_demo_people), null, Modifier.size(12.dp), tint = MainCourseColors.Body)
                    Text(sample.servings.toString(), fontSize = 10.sp, fontFamily = MainCourseMono, color = MainCourseColors.Body)
                }
                HorizontalDivider(color = MainCourseColors.Hairline)
                sample.ingredients.take(3).forEach { ingredient ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = CircleShape, border = BorderStroke(1.dp, MainCourseColors.Muted)) { Spacer(Modifier.size(8.dp)) }
                        Text(ingredient.raw, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MainCourseColors.Body)
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceholderLine(width: Dp, height: Dp = 4.dp, color: Color = MainCourseColors.Line) {
    Box(Modifier.size(width, height).background(color, CircleShape))
}
