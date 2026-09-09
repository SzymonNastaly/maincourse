package com.getmaincourse.app

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.getmaincourse.app.features.auth.AppleAuthenticationCallbackParser
import com.getmaincourse.app.features.session.SessionUiState
import com.getmaincourse.app.features.session.SessionViewModel
import com.getmaincourse.app.ui.theme.MainCourseTheme
import okhttp3.HttpUrl.Companion.toHttpUrl

class MainActivity : ComponentActivity() {
    private val appContainer: AppContainer
        get() = (application as MainCourseApplication).container

    private val viewModel by viewModels<SessionViewModel> { appContainer.sessionViewModelFactory }
    private var contentInstalled = false
    private val localNetworkPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        installAppContent()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        val needsLocalNetworkPermission = shouldRequestLocalNetworkAccess(
            isDebugBuild = BuildConfig.DEBUG,
            sdkInt = Build.VERSION.SDK_INT,
            permissionGranted = checkSelfPermission(LOCAL_NETWORK_PERMISSION) == PackageManager.PERMISSION_GRANTED,
            apiHost = BuildConfig.API_BASE_URL.toHttpUrl().host,
        )
        if (needsLocalNetworkPermission) {
            localNetworkPermission.launch(LOCAL_NETWORK_PERMISSION)
        } else {
            installAppContent()
        }
    }

    private fun installAppContent() {
        if (contentInstalled) return
        contentInstalled = true
        setContent {
            MainCourseTheme {
                LaunchedEffect(viewModel) {
                    if (viewModel.state.value == SessionUiState.Restoring) viewModel.restore()
                }
                MainCourseApp(
                    sessionViewModel = viewModel,
                    cookbookRepository = appContainer.cookbookRepository,
                    recipeRepository = appContainer.recipeRepository,
                    resolveImage = appContainer.images::resolve,
                )
            }
        }
    }
}

internal fun appleBrowserIntent(url: String): Intent =
    Intent(Intent.ACTION_VIEW, url.toUri())
        .addCategory(Intent.CATEGORY_BROWSABLE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

internal fun consumeAppleCallbackIntent(
    intent: Intent,
    isDebugBuild: Boolean,
    onCallback: (com.getmaincourse.app.features.auth.AppleAuthenticationCallback) -> Unit,
): Boolean {
    if (intent.action != Intent.ACTION_VIEW) return false
    val rawUri = intent.dataString ?: return false
    intent.data = null
    val callback = AppleAuthenticationCallbackParser.parse(rawUri, isDebugBuild) ?: return false
    onCallback(callback)
    return true
}

internal fun shouldRequestLocalNetworkAccess(
    isDebugBuild: Boolean,
    sdkInt: Int,
    permissionGranted: Boolean,
    apiHost: String,
): Boolean = isDebugBuild && sdkInt >= 37 && !permissionGranted && apiHost in LOCAL_API_HOSTS

private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
private val LOCAL_API_HOSTS = setOf("10.0.2.2", "localhost", "127.0.0.1")
