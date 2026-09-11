package com.getmaincourse.app.features.auth

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.getmaincourse.app.R
import com.getmaincourse.app.ui.theme.MainCourseColors
import com.getmaincourse.app.ui.theme.MainCourseShapes

@Composable
fun PreAuthScreen(
    state: PreAuthUiState,
    busy: Boolean,
    error: String?,
    onStart: () -> Unit,
    onSelectHousehold: (HouseholdSize) -> Unit,
    onToggleSaveToday: (SaveTodayOption) -> Unit,
    onToggleDiet: (DietOption) -> Unit,
    onAdvance: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onSignIn: (email: String, password: String) -> Unit,
    onSignUp: (name: String?, email: String, password: String, confirmation: String) -> Unit,
) {
    BackHandler(enabled = state.onboarding && state.step != PreAuthStep.WELCOME) { onBack() }
    when (state.step) {
        PreAuthStep.WELCOME -> WelcomeScreen(onStart)
        PreAuthStep.AUTH -> if (state.onboarding) {
            AuthScreen(
                busy = busy,
                error = error,
                initialMode = AuthMode.SIGN_UP,
                onSignIn = onSignIn,
                onSignUp = onSignUp,
            )
        } else {
            AuthScreen(
                busy = busy,
                error = error,
                onSignIn = onSignIn,
                onSignUp = onSignUp,
            )
        }
        else -> OnboardingQuestionScreen(
            state = state,
            onSelectHousehold = onSelectHousehold,
            onToggleSaveToday = onToggleSaveToday,
            onToggleDiet = onToggleDiet,
            onAdvance = onAdvance,
            onBack = onBack,
            onSkip = onSkip,
        )
    }
}

@Composable
private fun WelcomeScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Image(
            painter = painterResource(R.drawable.brand_mark),
            contentDescription = null,
            modifier = Modifier.size(96.dp).testTag("onboarding_logo"),
        )
        Spacer(Modifier.size(24.dp))
        Text(
            text = buildAnnotatedString {
                append(stringResource(R.string.onboarding_tagline_start))
                append(" ")
                withStyle(SpanStyle(color = MainCourseColors.Accent)) {
                    append(stringResource(R.string.onboarding_tagline_emphasis))
                }
                append(" ")
                append(stringResource(R.string.onboarding_tagline_end))
            },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.onboarding_welcome_body),
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MainCourseColors.Body,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).testTag("onboarding_start"),
            shape = MainCourseShapes.Control,
        ) {
            Text(stringResource(R.string.onboarding_get_started))
        }
    }
}

@Composable
private fun OnboardingQuestionScreen(
    state: PreAuthUiState,
    onSelectHousehold: (HouseholdSize) -> Unit,
    onToggleSaveToday: (SaveTodayOption) -> Unit,
    onToggleDiet: (DietOption) -> Unit,
    onAdvance: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        OnboardingHeader(state.step, onBack, onSkip)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
        ) {
            val copy = questionCopy(state.step)
            Text(stringResource(copy.title), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(copy.subtitle),
                modifier = Modifier.padding(top = 10.dp, bottom = 28.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MainCourseColors.Body,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (state.step) {
                    PreAuthStep.HOUSEHOLD -> HouseholdSize.entries.forEach { size ->
                        AnswerChip(
                            label = stringResource(size.label),
                            selected = state.household == size,
                            tag = "onboarding_household_${size.name.lowercase()}",
                            onClick = { onSelectHousehold(size) },
                        )
                    }
                    PreAuthStep.SAVE_TODAY -> SaveTodayOption.entries.forEach { option ->
                        AnswerChip(
                            label = stringResource(option.label),
                            selected = option in state.saveToday,
                            tag = "onboarding_save_${option.name.lowercase()}",
                            onClick = { onToggleSaveToday(option) },
                        )
                    }
                    PreAuthStep.DIET -> DietOption.entries.forEach { option ->
                        AnswerChip(
                            label = stringResource(option.label),
                            selected = option in state.diet,
                            tag = "onboarding_diet_${option.name.lowercase()}",
                            onClick = { onToggleDiet(option) },
                        )
                    }
                    else -> Unit
                }
            }
        }
        Button(
            onClick = onAdvance,
            enabled = state.canAdvance,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)
                .testTag("onboarding_continue"),
            shape = MainCourseShapes.Control,
        ) {
            Text(
                stringResource(
                    if (state.step == PreAuthStep.DIET) {
                        R.string.onboarding_continue_to_signup
                    } else {
                        R.string.onboarding_continue
                    },
                ),
            )
        }
    }
}

@Composable
private fun OnboardingHeader(step: PreAuthStep, onBack: () -> Unit, onSkip: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (step == PreAuthStep.HOUSEHOLD) {
                Spacer(Modifier.size(48.dp))
            } else {
                IconButton(onClick = onBack, modifier = Modifier.testTag("onboarding_back")) {
                    Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back))
                }
            }
            Box(Modifier.weight(1f))
            TextButton(onClick = onSkip, modifier = Modifier.testTag("onboarding_skip")) {
                Text(stringResource(R.string.onboarding_skip))
            }
        }
        LinearProgressIndicator(
            progress = { (step.questionIndex + 1) / 3f },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).testTag("onboarding_progress"),
            trackColor = MainCourseColors.Hairline,
        )
    }
}

@Composable
private fun AnswerChip(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        modifier = Modifier.testTag(tag),
        leadingIcon = if (selected) {
            { Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else {
            null
        },
    )
}

private data class QuestionCopy(@param:StringRes val title: Int, @param:StringRes val subtitle: Int)

private fun questionCopy(step: PreAuthStep): QuestionCopy = when (step) {
    PreAuthStep.HOUSEHOLD -> QuestionCopy(R.string.onboarding_household_title, R.string.onboarding_household_body)
    PreAuthStep.SAVE_TODAY -> QuestionCopy(R.string.onboarding_save_title, R.string.onboarding_save_body)
    PreAuthStep.DIET -> QuestionCopy(R.string.onboarding_diet_title, R.string.onboarding_diet_body)
    else -> error("No question copy for $step")
}

private val PreAuthStep.questionIndex: Int
    get() = when (this) {
        PreAuthStep.HOUSEHOLD -> 0
        PreAuthStep.SAVE_TODAY -> 1
        PreAuthStep.DIET -> 2
        else -> error("$this is not a question")
    }

private val HouseholdSize.label: Int
    get() = when (this) {
        HouseholdSize.ONE -> R.string.onboarding_household_one
        HouseholdSize.TWO -> R.string.onboarding_household_two
        HouseholdSize.THREE_OR_FOUR -> R.string.onboarding_household_three_four
        HouseholdSize.FIVE_OR_MORE -> R.string.onboarding_household_five_more
    }

private val SaveTodayOption.label: Int
    get() = when (this) {
        SaveTodayOption.SCREENSHOTS -> R.string.onboarding_save_screenshots
        SaveTodayOption.BROWSER_BOOKMARKS -> R.string.onboarding_save_bookmarks
        SaveTodayOption.NOTES -> R.string.onboarding_save_notes
        SaveTodayOption.RECIPE_APPS -> R.string.onboarding_save_apps
        SaveTodayOption.COOKBOOKS -> R.string.onboarding_save_cookbooks
        SaveTodayOption.DONT_SAVE -> R.string.onboarding_save_nowhere
    }

private val DietOption.label: Int
    get() = when (this) {
        DietOption.VEGETARIAN -> R.string.onboarding_diet_vegetarian
        DietOption.VEGAN -> R.string.onboarding_diet_vegan
        DietOption.GLUTEN_FREE -> R.string.onboarding_diet_gluten_free
        DietOption.PESCATARIAN -> R.string.onboarding_diet_pescatarian
        DietOption.HALAL -> R.string.onboarding_diet_halal
        DietOption.KOSHER -> R.string.onboarding_diet_kosher
        DietOption.LACTOSE_FREE -> R.string.onboarding_diet_lactose_free
    }
