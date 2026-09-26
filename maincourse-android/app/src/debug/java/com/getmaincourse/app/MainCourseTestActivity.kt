package com.getmaincourse.app

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object MainCourseTestContent {
    var content by mutableStateOf<@Composable () -> Unit>({})
}

class MainCourseTestActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        // Existing UI fixtures assert English copy regardless of the emulator language.
        // Explicit per-app locales use the real platform configuration for language-switch tests.
        val hasAppLocale = Build.VERSION.SDK_INT >= 33 &&
            !newBase.getSystemService(LocaleManager::class.java).applicationLocales.isEmpty
        val context = if (hasAppLocale) newBase else newBase.createConfigurationContext(
            Configuration(newBase.resources.configuration).apply {
                setLocales(LocaleList.forLanguageTags("en"))
            },
        )
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        setContent { MainCourseTestContent.content() }
    }
}
