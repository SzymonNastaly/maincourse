package com.getmaincourse.app.features.recipes

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun Modifier.recipeScreenAwake(): Modifier {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var keepAwake by remember(context, lifecycle) { mutableStateOf(false) }

    DisposableEffect(context, lifecycle) {
        val power = context.getSystemService(PowerManager::class.java)
        fun update() {
            keepAwake = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && !power.isPowerSaveMode
        }

        val observer = LifecycleEventObserver { _, _ -> update() }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = update()
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        lifecycle.addObserver(observer)
        update()
        onDispose {
            lifecycle.removeObserver(observer)
            context.unregisterReceiver(receiver)
        }
    }

    // Compose counts owners, so an outgoing recipe cannot clear an incoming one's request.
    return if (keepAwake) keepScreenOn() else this
}
