package com.getmaincourse.app.features.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.getmaincourse.app.R
import com.getmaincourse.app.data.model.SignInRequest
import com.getmaincourse.app.data.model.SignUpRequest
import com.getmaincourse.app.features.auth.AuthenticationMethod
import com.getmaincourse.app.features.auth.AuthScreen
import com.getmaincourse.app.ui.theme.MainCourseColors
import com.getmaincourse.app.ui.theme.MainCourseShapes

@Composable
fun OnboardingScreen(
    state: OnboardingState,
    authenticationMethod: AuthenticationMethod?,
    authError: String?,
    onStart: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onExistingAccount: () -> Unit,
    onAdvance: () -> Unit,
    onHouseholdChanged: (Int) -> Unit,
    onSavingChanged: (String) -> Unit,
    onDietChanged: (String) -> Unit,
    onRetryPersistence: () -> Unit,
    onContinueWithoutSaving: () -> Unit,
    onSignIn: (SignInRequest) -> Unit,
    onSignUp: (SignUpRequest) -> Unit,
    onGoogleSignIn: () -> Unit,
    onAppleSignIn: () -> Unit,
    onCancelAppleSignIn: () -> Unit,
    appleCanCancel: Boolean,
) {
    val isPreparingAuthentication = authenticationMethod != null
    if (state.step == OnboardingStep.WELCOME) {
        Welcome(state, onStart, onExistingAccount, onRetryPersistence, onContinueWithoutSaving)
        return
    }
    if (state.step == OnboardingStep.AUTH) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("onboarding_auth_container")) {
            OnboardingHeader(
                step = state.step,
                backEnabled = !isPreparingAuthentication,
                onBack = onBack,
                onSkip = onSkip,
            )
            PersistenceError(state, onRetryPersistence, onContinueWithoutSaving)
            AuthScreen(
                modifier = Modifier.weight(1f),
                authenticationMethod = authenticationMethod,
                error = authError,
                startsInSignUpMode = true,
                onSignIn = onSignIn,
                onSignUp = onSignUp,
                onGoogleSignIn = onGoogleSignIn,
                onAppleSignIn = onAppleSignIn,
                onCancelAppleSignIn = onCancelAppleSignIn,
                appleCanCancel = appleCanCancel,
            )
        }
        return
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
            OnboardingHeader(state.step, !isPreparingAuthentication, onBack, onSkip)
            PersistenceError(state, onRetryPersistence, onContinueWithoutSaving)
            Question(
                state = state,
                choicesEnabled = !isPreparingAuthentication,
                onHouseholdChanged = onHouseholdChanged,
                onSavingChanged = onSavingChanged,
                onDietChanged = onDietChanged,
            )
            Button(
                onClick = onAdvance,
                enabled = state.canAdvance() && !isPreparingAuthentication,
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp).testTag("onboarding_continue"),
                shape = MainCourseShapes.Control,
            ) {
                Text(
                    stringResource(
                        if (state.step == OnboardingStep.DIET) R.string.onboarding_continue_to_signup
                        else R.string.continue_label,
                    ),
                )
            }
        }
    }
}

@Composable
private fun Welcome(
    state: OnboardingState,
    onStart: () -> Unit,
    onExistingAccount: () -> Unit,
    onRetryPersistence: () -> Unit,
    onContinueWithoutSaving: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            Modifier.widthIn(max = 440.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Image(painterResource(R.drawable.brand_mark), null, Modifier.size(96.dp))
            Text(
                stringResource(R.string.onboarding_tagline),
                style = MaterialTheme.typography.headlineSmall,
                color = MainCourseColors.Ink,
            )
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth(), shape = MainCourseShapes.Control) {
                Text(stringResource(R.string.onboarding_get_started))
            }
            TextButton(onClick = onExistingAccount) {
                Text(stringResource(R.string.onboarding_existing_account))
            }
            PersistenceError(state, onRetryPersistence, onContinueWithoutSaving)
        }
    }
}

@Composable
private fun OnboardingHeader(
    step: OnboardingStep,
    backEnabled: Boolean,
    onBack: () -> Unit,
    onSkip: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("onboarding_header"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, enabled = backEnabled) {
            Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back))
        }
        Spacer(Modifier.weight(1f))
        Text(
            if (step == OnboardingStep.AUTH) stringResource(R.string.account)
            else stringResource(R.string.onboarding_progress, step.questionNumber()),
            style = MaterialTheme.typography.labelLarge,
            color = MainCourseColors.Body,
        )
        Spacer(Modifier.weight(1f))
        if (step in QUESTION_STEPS) {
            TextButton(onClick = onSkip) { Text(stringResource(R.string.skip)) }
        } else {
            Spacer(Modifier.size(48.dp))
        }
    }
}

@Composable
private fun Question(
    state: OnboardingState,
    choicesEnabled: Boolean,
    onHouseholdChanged: (Int) -> Unit,
    onSavingChanged: (String) -> Unit,
    onDietChanged: (String) -> Unit,
) {
    val title: Int
    val subtitle: Int
    val choices: List<Choice>
    val selected: (Choice) -> Boolean
    val onChoice: (Choice) -> Unit
    when (state.step) {
        OnboardingStep.HOUSEHOLD -> {
            title = R.string.onboarding_household_title
            subtitle = R.string.onboarding_household_subtitle
            choices = HOUSEHOLD_CHOICES
            selected = { state.householdSize == it.numericValue }
            onChoice = { onHouseholdChanged(requireNotNull(it.numericValue)) }
        }
        OnboardingStep.SAVING -> {
            title = R.string.onboarding_saving_title
            subtitle = R.string.onboarding_saving_subtitle
            choices = SAVING_CHOICES
            selected = { it.value in state.saveToday }
            onChoice = { onSavingChanged(it.value) }
        }
        else -> {
            title = R.string.onboarding_diet_title
            subtitle = R.string.onboarding_diet_subtitle
            choices = DIET_CHOICES
            selected = { it.value in state.diet }
            onChoice = { onDietChanged(it.value) }
        }
    }
    Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(subtitle), color = MainCourseColors.Body)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { choice ->
                FilterChip(
                    selected = selected(choice),
                    onClick = { onChoice(choice) },
                    enabled = choicesEnabled,
                    label = { Text(stringResource(choice.label)) },
                    shape = MainCourseShapes.Control,
                )
            }
        }
    }
}

@Composable
private fun PersistenceError(
    state: OnboardingState,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
) {
    if (state.persistenceError == null) return
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        color = MainCourseColors.AmberTint,
        shape = MainCourseShapes.Card,
    ) {
        Column(
            Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.onboarding_save_error), color = MainCourseColors.Ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.canRetryPersistence) {
                    OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
                TextButton(onClick = onContinue) {
                    Text(stringResource(R.string.onboarding_continue_without_saving))
                }
            }
        }
    }
}

private fun OnboardingState.canAdvance() = when (step) {
    OnboardingStep.HOUSEHOLD -> householdSize != null
    OnboardingStep.SAVING -> saveToday.isNotEmpty()
    OnboardingStep.DIET -> true
    else -> false
}

private fun OnboardingStep.questionNumber() = QUESTION_STEPS.indexOf(this) + 1

private data class Choice(val label: Int, val value: String, val numericValue: Int? = null)

private val QUESTION_STEPS = listOf(OnboardingStep.HOUSEHOLD, OnboardingStep.SAVING, OnboardingStep.DIET)
private val HOUSEHOLD_CHOICES = listOf(
    Choice(R.string.household_one, "1", 1), Choice(R.string.household_two, "2", 2),
    Choice(R.string.household_three_four, "3", 3), Choice(R.string.household_five_plus, "5", 5),
)
private val SAVING_CHOICES = listOf(
    Choice(R.string.saving_screenshots, "screenshots"),
    Choice(R.string.saving_browser_bookmarks, "browser_bookmarks"),
    Choice(R.string.saving_notes, "notes"),
    Choice(R.string.saving_recipe_apps, "recipe_apps"),
    Choice(R.string.saving_cookbooks, "cookbooks"),
    Choice(R.string.saving_dont_save, "dont_save"),
)
private val DIET_CHOICES = listOf(
    Choice(R.string.diet_vegetarian, "vegetarian"), Choice(R.string.diet_vegan, "vegan"),
    Choice(R.string.diet_gluten_free, "glutenFree"), Choice(R.string.diet_pescatarian, "pescatarian"),
    Choice(R.string.diet_halal, "halal"), Choice(R.string.diet_kosher, "kosher"),
    Choice(R.string.diet_lactose_free, "lactoseFree"),
)
