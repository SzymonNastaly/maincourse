package com.getmaincourse.app.features.auth

import android.animation.ValueAnimator
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.getmaincourse.app.R
import com.getmaincourse.app.ui.appLocale
import com.getmaincourse.app.ui.displayNumber
import com.getmaincourse.app.features.recipes.IngredientFormatter
import com.getmaincourse.app.ui.theme.MainCourseColors
import com.getmaincourse.app.ui.theme.MainCourseMono
import com.getmaincourse.app.ui.theme.MainCourseShapes
import kotlinx.coroutines.delay

@Composable
internal fun OnboardingFeaturesScreen(onKeep: () -> Unit, onContinueWithoutRecipe: () -> Unit) {
    val sample = rememberDemoRecipe()
    var portions by remember { mutableIntStateOf(sample.servings) }
    LaunchedEffect(Unit) {
        if (ValueAnimator.areAnimatorsEnabled()) { delay(800); portions = sample.servings * 2 }
    }
    Column(Modifier.fillMaxSize().wrapContentWidth().widthIn(max = 560.dp).padding(horizontal = 20.dp)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.onboarding_features_title), style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp).semantics { heading() })
            FeatureCard(R.string.onboarding_portions_title, R.string.onboarding_portions_body) {
                Surface(shape = CircleShape, border = BorderStroke(1.dp, MainCourseColors.Hairline)) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("−", fontSize = 16.sp)
                        Text(displayNumber(portions), fontFamily = MainCourseMono, fontSize = 14.sp)
                        Text("+", fontSize = 16.sp)
                    }
                }
                sample.ingredients.firstOrNull { it.name == "orzo" }?.let {
                    Text(IngredientFormatter.formatIngredient(it.structured(), portions, sample.servings, appLocale()), fontSize = 11.sp, color = MainCourseColors.Body)
                }
            }
            FeatureCard(R.string.onboarding_shopping_title, R.string.onboarding_shopping_body) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    sample.ingredients.filter { it.name.length <= 12 }.take(3).forEachIndexed { index, ingredient ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = CircleShape, color = if (index == 0) MainCourseColors.Accent else MainCourseColors.Sunken,
                                border = if (index == 0) null else BorderStroke(1.dp, MainCourseColors.Muted)) {
                                Box(Modifier.size(10.dp), contentAlignment = Alignment.Center) {
                                    if (index == 0) Icon(painterResource(R.drawable.ic_check), null, Modifier.size(8.dp), tint = MainCourseColors.Surface)
                                }
                            }
                            Text(ingredient.name, fontSize = 11.sp, color = if (index == 0) MainCourseColors.Muted else MainCourseColors.Ink,
                                textDecoration = if (index == 0) TextDecoration.LineThrough else null)
                        }
                    }
                }
            }
            FeatureCard(R.string.onboarding_together_title, R.string.onboarding_together_body) {
                Row(horizontalArrangement = Arrangement.spacedBy((-7).dp)) {
                    listOf("SK", "AM", "+").forEach { initials ->
                        Surface(shape = CircleShape, color = if (initials == "+") MainCourseColors.Surface else MainCourseColors.AccentTint,
                            border = BorderStroke(1.dp, MainCourseColors.Hairline)) {
                            Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                                Text(initials, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MainCourseColors.Accent)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_recipes), null, Modifier.size(11.dp))
                    Text(stringResource(R.string.onboarding_family_cookbook), fontSize = 10.sp)
                }
            }
        }
        OnboardingAction(R.string.onboarding_keep, "onboarding_keep", onKeep)
        TextButton(onClick = onContinueWithoutRecipe, modifier = Modifier.fillMaxWidth().testTag("onboarding_without_recipe")) {
            Text(stringResource(R.string.onboarding_without_recipe))
        }
    }
}

@Composable
private fun FeatureCard(title: Int, body: Int, illustration: @Composable ColumnScope.() -> Unit) {
    val density = LocalDensity.current
    val fragment: @Composable () -> Unit = {
        CompositionLocalProvider(
            LocalDensity provides Density(density.density, 1f),
            LocalTextStyle provides LocalTextStyle.current.copy(lineHeight = TextUnit.Unspecified, letterSpacing = TextUnit.Unspecified),
        ) {
            Surface(color = MainCourseColors.Sunken, shape = MainCourseShapes.Card) {
                Column(Modifier.size(112.dp, 92.dp).padding(6.dp).clearAndSetSemantics {},
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically), content = illustration)
            }
        }
    }
    val copy: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MainCourseColors.Body)
        }
    }
    Surface(shape = MainCourseShapes.Panel, border = BorderStroke(1.dp, MainCourseColors.Hairline)) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(8.dp)) {
            if (density.fontScale > 1.3f || maxWidth < 300.dp) {
                Column(Modifier.padding(4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { fragment(); copy() }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    fragment()
                    Box(Modifier.weight(1f).padding(end = 6.dp)) { copy() }
                }
            }
        }
    }
}
