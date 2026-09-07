package com.getmaincourse.app

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.getmaincourse.app.features.session.MainCourseViewModel
import com.getmaincourse.app.ui.theme.MainCourseTheme
import kotlinx.coroutines.CancellationException
import okhttp3.HttpUrl.Companion.toHttpUrl

class MainActivity : ComponentActivity() {
    private val appContainer: AppContainer
        get() = (application as MainCourseApplication).container

    private val viewModel by viewModels<MainCourseViewModel> { appContainer.viewModelFactory }
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
                val state by viewModel.state.collectAsStateWithLifecycle()
                val userId = state.user?.id
                var imageLoader by remember(userId) { mutableStateOf<coil3.ImageLoader?>(null) }
                LaunchedEffect(userId) {
                    imageLoader = null
                    if (userId != null) {
                        imageLoader = try {
                            appContainer.images.prepare(userId)
                        } catch (failure: CancellationException) {
                            throw failure
                        } catch (_: Throwable) {
                            null
                        }
                    }
                }
                MainCourseApp(
                    state = state,
                    actions = MainCourseActions(
                        restore = { viewModel.restore() },
                        signIn = { viewModel.signIn(it) },
                        signUp = { viewModel.signUp(it) },
                        switchCookbook = { viewModel.switchCookbook(it) },
                        refresh = { viewModel.refresh() },
                        openRecipe = { viewModel.openRecipe(it) },
                        closeRecipe = { viewModel.closeRecipe() },
                        logout = { viewModel.logout() },
                        reset = { viewModel.reset() },
                    ),
                    imageLoader = imageLoader,
                    resolveImage = appContainer.images::resolve,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (contentInstalled) viewModel.checkExpiry()
    }
}

internal fun shouldRequestLocalNetworkAccess(
    isDebugBuild: Boolean,
    sdkInt: Int,
    permissionGranted: Boolean,
    apiHost: String,
): Boolean = isDebugBuild && sdkInt >= 37 && !permissionGranted && apiHost in LOCAL_API_HOSTS

private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
private val LOCAL_API_HOSTS = setOf("10.0.2.2", "localhost", "127.0.0.1")
