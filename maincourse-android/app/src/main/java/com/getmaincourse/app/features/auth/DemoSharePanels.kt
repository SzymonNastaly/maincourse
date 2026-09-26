package com.getmaincourse.app.features.auth

import android.animation.ValueAnimator
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.getmaincourse.app.R
import com.getmaincourse.app.ui.theme.MainCourseColors
import kotlinx.coroutines.delay

@Composable
internal fun DemoSocialSharePanel(onClose: () -> Unit, onShareTo: () -> Unit) {
    val hinted = demoActionHint(true)
    val title = stringResource(R.string.demo_share_title)
    Column(Modifier.fillMaxWidth().semantics { paneTitle = title }, verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.weight(1f), shape = CircleShape, color = MainCourseColors.Sunken) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(painterResource(R.drawable.ic_search), null, tint = MainCourseColors.Body)
                    Text(stringResource(R.string.search), color = MainCourseColors.Body)
                }
            }
            Icon(painterResource(R.drawable.ic_demo_people), null, Modifier.size(28.dp), tint = MainCourseColors.Body)
            FilledTonalIconButton(onClick = onClose) {
                Icon(painterResource(R.drawable.ic_demo_close), stringResource(R.string.demo_close))
            }
        }
        val contacts = listOf("AM" to "Alex", "JL" to "Jamie", "SK" to "Sam", "TC" to "Taylor", "MP" to "Morgan", "RL" to "Robin")
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            contacts.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { (initials, name) ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(shape = CircleShape, color = MainCourseColors.Sunken) {
                                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                                    Text(initials, style = MaterialTheme.typography.titleMedium)
                                }
                            }
                            Text(name, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = MainCourseColors.Hairline)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DemoShareAction(R.string.demo_add_story, R.drawable.ic_demo_add, Modifier.weight(1f))
            DemoShareAction(R.string.demo_message, R.drawable.ic_demo_chat, Modifier.weight(1f))
            DemoShareAction(R.string.demo_share_to, R.drawable.ic_demo_share, Modifier.weight(1f), hinted, "demo_share_to", onShareTo)
            DemoShareAction(R.string.demo_copy_link, R.drawable.ic_demo_link, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DemoShareAction(
    label: Int,
    icon: Int,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    tag: String = "",
    onClick: (() -> Unit)? = null,
) {
    val scale = demoHintScale(highlighted)
    Column(
        modifier.then(if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick).testTag(tag))
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(
            modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
            shape = CircleShape, color = if (highlighted) MainCourseColors.Accent else MainCourseColors.Sunken,
        ) {
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), null, Modifier.size(26.dp), tint = if (highlighted) MainCourseColors.Surface else MainCourseColors.Body)
            }
        }
        Text(stringResource(label), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
            fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
            color = if (highlighted) MainCourseColors.Accent else MainCourseColors.Body)
    }
}

@Composable
internal fun DemoDestinationsPanel(sample: DemoRecipe, onBack: () -> Unit, onMainCourse: () -> Unit) {
    val scroll = rememberScrollState()
    val mainCourseVisible by remember { derivedStateOf { scroll.value >= scroll.maxValue && scroll.maxValue > 0 } }
    var touched by remember { mutableStateOf(false) }
    var demonstrated by remember { mutableStateOf(false) }
    val hinted = demoActionHint(true)
    val scale = demoHintScale(hinted)
    val accessibility = LocalContext.current.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    LaunchedEffect(hinted, touched) {
        if (hinted && !touched && !demonstrated && ValueAnimator.areAnimatorsEnabled() && !accessibility.isTouchExplorationEnabled) {
            demonstrated = true
            scroll.animateScrollTo((scroll.maxValue / 2).coerceAtLeast(0), tween(450))
            scroll.animateScrollTo(0, tween(350))
            delay(250)
            scroll.animateScrollTo(scroll.maxValue, tween(650))
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            DemoPhoto(sample, Modifier.size(52.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.demo_post_from, sample.creator), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.demo_recipe_link), style = MaterialTheme.typography.bodySmall, color = MainCourseColors.Body)
            }
            FilledTonalIconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back)) }
        }
        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MainCourseColors.Hairline)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val itemWidth = ((maxWidth - 88.dp) / 3.5f).coerceAtLeast(72.dp)
            Row(
                Modifier.horizontalScroll(scroll).pointerInput(Unit) {
                    awaitEachGesture { awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial); touched = true }
                }.padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Top,
            ) {
                listOf(R.string.demo_messages to R.drawable.ic_demo_chat, R.string.demo_notes to R.drawable.ic_demo_notes,
                    R.string.demo_lists to R.drawable.ic_shopping).forEach { (label, icon) ->
                    Column(Modifier.width(itemWidth), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(shape = RoundedCornerShape(14.dp), color = MainCourseColors.Sunken) {
                            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                                Icon(painterResource(icon), null, Modifier.size(30.dp), tint = MainCourseColors.Muted)
                            }
                        }
                        Text(stringResource(label), style = MaterialTheme.typography.bodySmall, color = MainCourseColors.Muted, textAlign = TextAlign.Center)
                    }
                }
                Column(
                    Modifier.width(itemWidth).clickable(role = Role.Button, onClick = onMainCourse).testTag("demo_maincourse"),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale },
                        shape = RoundedCornerShape(14.dp), color = MainCourseColors.Sunken,
                        border = if (hinted) BorderStroke(1.dp, MainCourseColors.AccentLine) else null) {
                        Image(painterResource(R.drawable.brand_mark), null, Modifier.size(64.dp).padding(4.dp))
                    }
                    Text(stringResource(R.string.onboarding_brand_name), style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold, color = MainCourseColors.Accent, textAlign = TextAlign.Center)
                }
            }
        }
        Text(stringResource(if (mainCourseVisible) R.string.demo_tap_maincourse else R.string.demo_swipe),
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).semantics { liveRegion = LiveRegionMode.Polite },
            color = MainCourseColors.Accent, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MainCourseColors.Hairline)
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DemoShareAction(R.string.copy, R.drawable.ic_demo_copy, Modifier.weight(1f))
            DemoShareAction(R.string.save, R.drawable.ic_demo_bookmark, Modifier.weight(1f))
            DemoShareAction(R.string.demo_more, R.drawable.ic_demo_more, Modifier.weight(1f))
        }
    }
}
