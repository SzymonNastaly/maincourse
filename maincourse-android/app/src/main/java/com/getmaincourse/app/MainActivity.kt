package com.getmaincourse.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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

class MainActivity : ComponentActivity() {
    private val appContainer: AppContainer
        get() = (application as MainCourseApplication).container

    private val viewModel by viewModels<MainCourseViewModel> { appContainer.viewModelFactory }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
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
        viewModel.checkExpiry()
    }
}
