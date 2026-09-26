package com.getmaincourse.app.features.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
    onDemoCompleted: () -> Unit,
    onAdvance: () -> Unit,
    onBack: () -> Unit,
    onLogIn: () -> Unit,
    onKeep: () -> Unit,
    onContinueWithoutRecipe: () -> Unit,
    onSignIn: (email: String, password: String) -> Unit,
    onSignUp: (name: String?, email: String, password: String, confirmation: String) -> Unit,
) {
    BackHandler(enabled = state.onboarding && state.step != PreAuthStep.WELCOME && !busy) { onBack() }
    when (state.step) {
        PreAuthStep.WELCOME -> WelcomeScreen(onStart, onLogIn)
        PreAuthStep.AUTH -> key(state.login) {
            AuthScreen(
                busy = busy,
                error = error,
                initialMode = if (state.onboarding && !state.login) AuthMode.SIGN_UP else AuthMode.SIGN_IN,
                onSignIn = onSignIn,
                onSignUp = onSignUp,
            )
        }
        PreAuthStep.DEMO -> Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            OnboardingHeader(onBack, onLogIn)
            ImportDemoScreen(
                startsWithRecipe = state.demoCompleted,
                onRecipeReady = onDemoCompleted,
                onContinue = onAdvance,
            )
        }
        PreAuthStep.FEATURES -> Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            OnboardingHeader(onBack)
            OnboardingFeaturesScreen(onKeep, onContinueWithoutRecipe)
        }
    }
}

@Composable
private fun WelcomeScreen(onStart: () -> Unit, onLogIn: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().wrapContentWidth().widthIn(max = 560.dp).padding(horizontal = 24.dp)) {
        BoxWithConstraints(Modifier.weight(1f)) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(vertical = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Image(painterResource(R.drawable.brand_mark), null, Modifier.size(32.dp).testTag("onboarding_logo"))
                    Text(stringResource(R.string.onboarding_brand_name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                RecipeSourcesAnimation()
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.onboarding_welcome_title), style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.onboarding_welcome_body), style = MaterialTheme.typography.bodyLarge, color = MainCourseColors.Body)
                }
            }
        }
        OnboardingAction(R.string.onboarding_get_started, "onboarding_start", onStart)
        TextButton(onClick = onLogIn, modifier = Modifier.fillMaxWidth().testTag("onboarding_login")) {
            Text(stringResource(R.string.onboarding_existing_account))
        }
    }
}

@Composable
internal fun OnboardingHeader(onBack: () -> Unit, onLogIn: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("onboarding_back")) {
            Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back))
        }
        Spacer(Modifier.weight(1f))
        if (onLogIn != null) TextButton(onClick = onLogIn) { Text(stringResource(R.string.onboarding_log_in)) }
    }
}

@Composable
internal fun OnboardingAction(label: Int, tag: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).heightIn(min = 52.dp).testTag(tag),
        shape = MainCourseShapes.Control,
    ) { Text(stringResource(label)) }
}
