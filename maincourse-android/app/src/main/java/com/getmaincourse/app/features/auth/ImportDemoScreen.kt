package com.getmaincourse.app.features.auth

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import com.getmaincourse.app.R
import com.getmaincourse.app.features.recipes.IngredientFormatter
import com.getmaincourse.app.ui.theme.MainCourseColors
import com.getmaincourse.app.ui.theme.MainCourseMono
import com.getmaincourse.app.ui.theme.MainCourseShapes
import kotlinx.coroutines.delay

private enum class DemoStage { POST, SHARING, DESTINATIONS, PROCESSING, RECIPE }

@Composable
internal fun rememberDemoRecipe(): DemoRecipe {
    val assets = LocalContext.current.assets
    return remember(assets) { DemoRecipe.load(assets) }
}

@Composable
internal fun DemoPhoto(sample: DemoRecipe, modifier: Modifier = Modifier, rounded: Boolean = true) {
    AsyncImage(
        model = "file:///android_asset/${sample.imageName}",
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxWidth().then(if (rounded) Modifier.clip(MainCourseShapes.Card) else Modifier),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportDemoScreen(
    startsWithRecipe: Boolean = false,
    onRecipeReady: () -> Unit = {},
    continueLabel: Int = R.string.onboarding_continue,
    onContinue: () -> Unit,
) {
    val sample = rememberDemoRecipe()
    var stage by rememberSaveable { mutableStateOf(if (startsWithRecipe) DemoStage.RECIPE else DemoStage.POST) }
    val latestReady by rememberUpdatedState(onRecipeReady)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(stage, lifecycle) {
        if (stage == DemoStage.PROCESSING) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(1_500)
            stage = DemoStage.RECIPE
            latestReady()
        }
    }
    BackHandler(stage == DemoStage.PROCESSING) { stage = DemoStage.POST }
    Column(Modifier.fillMaxSize().wrapContentWidth().widthIn(max = 560.dp)) {
        Crossfade(targetState = stage, modifier = Modifier.weight(1f), label = "import-demo") { current ->
            when (current) {
                DemoStage.RECIPE -> DemoRecipePreview(sample)
                DemoStage.PROCESSING -> Column(
                    Modifier.fillMaxSize().testTag("demo_processing").semantics { liveRegion = LiveRegionMode.Polite },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                ) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.demo_processing))
                }
                else -> DemoPost(sample, current == DemoStage.POST) { stage = DemoStage.SHARING }
            }
        }
        if (stage == DemoStage.RECIPE) Box(Modifier.padding(horizontal = 20.dp)) {
            OnboardingAction(continueLabel, "demo_continue", onContinue)
        }
    }
    if (stage == DemoStage.SHARING || stage == DemoStage.DESTINATIONS) {
        ModalBottomSheet(
            onDismissRequest = { stage = DemoStage.POST },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = if (stage == DemoStage.SHARING) MainCourseColors.Surface else MainCourseColors.Canvas,
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {
                if (stage == DemoStage.SHARING) {
                    DemoSocialSharePanel(
                        onClose = { stage = DemoStage.POST },
                        onShareTo = { stage = DemoStage.DESTINATIONS },
                    )
                } else {
                    DemoDestinationsPanel(sample, onBack = { stage = DemoStage.SHARING }) { stage = DemoStage.PROCESSING }
                }
            }
        }
    }
}

@Composable
private fun DemoPost(sample: DemoRecipe, active: Boolean, onShare: () -> Unit) {
    val hinted = demoActionHint(active)
    val pulse = demoHintScale(hinted)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.demo_post_title), style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.demo_post_body), color = MainCourseColors.Body)
        }
        Surface(shape = MainCourseShapes.Panel, border = BorderStroke(1.dp, MainCourseColors.Hairline)) {
            Column {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Image(painterResource(R.drawable.demo_creator), null, Modifier.size(36.dp).clip(CircleShape))
                    Column {
                        Text(sample.creator, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.demo_inspiration), style = MaterialTheme.typography.bodySmall, color = MainCourseColors.Muted)
                    }
                }
                DemoPhoto(sample, Modifier.height(260.dp), rounded = false)
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_demo_heart), null, Modifier.size(24.dp))
                    Icon(painterResource(R.drawable.ic_demo_chat), null, Modifier.size(24.dp))
                    Box {
                        Button(
                            onClick = onShare,
                            modifier = Modifier.testTag("demo_share").graphicsLayer { scaleX = pulse; scaleY = pulse },
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (hinted) MainCourseColors.Accent else Color.Transparent,
                                contentColor = if (hinted) MainCourseColors.Surface else MainCourseColors.Ink,
                            ),
                        ) {
                            Icon(painterResource(R.drawable.ic_demo_send), null, Modifier.size(20.dp))
                            Text(stringResource(R.string.demo_share), Modifier.padding(start = 8.dp))
                        }
                        if (hinted) ShareHintBubble(Modifier.align(Alignment.TopCenter).offset(y = (-36).dp))
                    }
                    Spacer(Modifier.weight(1f))
                    Icon(painterResource(R.drawable.ic_demo_bookmark), null, Modifier.size(24.dp))
                }
                Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(sample.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(sample.caption, style = MaterialTheme.typography.bodyMedium, color = MainCourseColors.Body,
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Text(stringResource(R.string.demo_sources), Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodySmall,
            color = MainCourseColors.Muted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ShareHintBubble(modifier: Modifier) {
    Column(modifier.clearAndSetSemantics {}, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(shape = RoundedCornerShape(8.dp), color = MainCourseColors.Accent) {
            Text(stringResource(R.string.demo_tap_share), Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelMedium, color = MainCourseColors.Surface)
        }
        Canvas(Modifier.size(8.dp, 5.dp)) {
            drawPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2, size.height); close() }, MainCourseColors.Accent)
        }
    }
}

@Composable
internal fun DemoRecipePreview(sample: DemoRecipe) {
    var portions by rememberSaveable { mutableIntStateOf(sample.servings) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("demo_recipe"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DemoPhoto(sample, Modifier.height(200.dp))
        Text(sample.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.recipe_time, sample.prepTime + sample.cookTime), fontFamily = MainCourseMono)
        Text(stringResource(R.string.recipe_portions), style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val decrease = stringResource(R.string.demo_decrease_portions)
            val increase = stringResource(R.string.demo_increase_portions)
            OutlinedButton(onClick = { portions-- }, enabled = portions > 1, modifier = Modifier.semantics { contentDescription = decrease }) { Text("−") }
            Text(portions.toString(), fontFamily = MainCourseMono, modifier = Modifier.testTag("demo_portions"))
            OutlinedButton(onClick = { portions++ }, enabled = portions < 64, modifier = Modifier.testTag("demo_increment").semantics { contentDescription = increase }) { Text("+") }
        }
        Text(stringResource(R.string.recipe_ingredients), style = MaterialTheme.typography.titleLarge)
        sample.ingredients.forEach { Text(IngredientFormatter.formatIngredient(it.structured(), portions, sample.servings)) }
        Text(stringResource(R.string.recipe_steps), style = MaterialTheme.typography.titleLarge)
        sample.instructions.forEachIndexed { index, instruction -> Text("${index + 1}. $instruction") }
    }
}
