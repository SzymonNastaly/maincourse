package com.getmaincourse.app.features.auth

import android.animation.ValueAnimator
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

/** Only foreground time counts toward a hint; revisiting a step starts its delay again. */
@Composable
internal fun demoActionHint(active: Boolean): Boolean {
    var hinted by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(active, lifecycle) {
        hinted = false
        if (active) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                delay(1_200)
                hinted = true
                awaitCancellation()
            } finally {
                hinted = false
            }
        }
    }
    return hinted
}

/** A slow size pulse, with no spring overshoot or changing border geometry. */
@Composable
internal fun demoHintScale(hinted: Boolean): Float {
    if (!hinted || !ValueAnimator.areAnimatorsEnabled()) return 1f
    val transition = rememberInfiniteTransition(label = "demo-hint")
    val scale by transition.animateFloat(
        initialValue = 1f, targetValue = 1.045f,
        animationSpec = infiniteRepeatable(tween(1_000), RepeatMode.Reverse), label = "gentle-size-pulse",
    )
    return scale
}
