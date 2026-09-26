package com.getmaincourse.app
import com.getmaincourse.app.ui.localized

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.getmaincourse.app.features.recipes.RecipeShareAuthenticationSheet
import com.getmaincourse.app.features.recipes.RecipeShareContent
import com.getmaincourse.app.features.recipes.RecipeShareSheet
import com.getmaincourse.app.features.recipes.RecipeShareStatus
import com.getmaincourse.app.features.recipes.RecipeShareViewModel
import com.getmaincourse.app.features.recipes.RenderedRecipePage
import com.getmaincourse.app.features.recipes.SharedImageReader
import com.getmaincourse.app.features.recipes.recipeShareContent
import com.getmaincourse.app.features.session.SessionUiState
import com.getmaincourse.app.features.session.SessionViewModel
import com.getmaincourse.app.ui.theme.MainCourseTheme
import kotlinx.coroutines.delay
import okhttp3.HttpUrl.Companion.toHttpUrl

class RecipeShareActivity : ComponentActivity() {
    private val appContainer: AppContainer
        get() = (application as MainCourseApplication).container

    private val sessionViewModel by viewModels<SessionViewModel> { appContainer.sessionViewModelFactory }
    private val sharedImageReader by lazy { SharedImageReader(applicationContext) }
    private var contentInstalled = false
    private lateinit var sharedContent: RecipeShareContent
    private val localNetworkPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        installContent()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedContent = intent.recipeShareContent() ?: run {
            finish()
            return
        }
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
            installContent()
        }
    }

    private fun installContent() {
        if (contentInstalled || isFinishing) return
        contentInstalled = true
        setContent {
            MainCourseTheme {
                val sessionState by sessionViewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(sessionViewModel) {
                    if (sessionViewModel.state.value == SessionUiState.Restoring) sessionViewModel.restore()
                }

                when (val currentSession = sessionState) {
                    SessionUiState.Restoring -> RecipeShareSheet(
                        state = com.getmaincourse.app.features.recipes.RecipeShareUiState(),
                        onRetry = {},
                        onDismiss = ::finish,
                    )
                    is SessionUiState.SignedIn -> SignedInShareContent(currentSession)
                    is SessionUiState.SignedOut -> RecipeShareAuthenticationSheet(
                        onOpenApp = ::openMainApp,
                        onDismiss = ::finish,
                    )
                    is SessionUiState.RestoreError -> RecipeShareAuthenticationSheet(
                        message = currentSession.message.localized(),
                        onOpenApp = ::openMainApp,
                        onRetry = { sessionViewModel.restore() },
                        onDismiss = ::finish,
                    )
                    is SessionUiState.CleanupError -> RecipeShareAuthenticationSheet(
                        message = currentSession.message.localized(),
                        onOpenApp = ::openMainApp,
                        onRetry = { sessionViewModel.retryCleanup() },
                        onDismiss = ::finish,
                    )
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun SignedInShareContent(sessionState: SessionUiState.SignedIn) {
        val userId = sessionState.session.user.id
        val shareViewModel: RecipeShareViewModel = viewModel(
            key = "recipe-share-$userId-${sharedContent.hashCode()}",
            factory = simpleViewModelFactory {
                RecipeShareViewModel(
                    content = sharedContent,
                    observeCookbooks = { appContainer.cookbookRepository.observe(userId) },
                    refreshCookbooks = { appContainer.cookbookRepository.refresh(userId) },
                    importUrl = { cookbookId, url ->
                        appContainer.recipeRepository.importUrl(userId, cookbookId, url)
                    },
                    importContent = { cookbookId, content ->
                        appContainer.recipeRepository.importContent(userId, cookbookId, content)
                    },
                    importText = { cookbookId, text ->
                        appContainer.recipeRepository.importText(userId, cookbookId, text)
                    },
                    importImage = { cookbookId, uri, mimeType ->
                        val image = sharedImageReader.read(uri, mimeType)
                        appContainer.recipeRepository.importImage(userId, cookbookId, image.bytes, image.mimeType)
                    },
                )
            },
        )
        val shareState by shareViewModel.state.collectAsStateWithLifecycle()
        val reading = shareState.status as? RecipeShareStatus.ReadingPage

        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
            if (reading != null) {
                RenderedRecipePage(
                    request = reading,
                    onFinished = shareViewModel::pageExtractionFinished,
                    modifier = Modifier.fillMaxSize().alpha(0f),
                )
            }
            RecipeShareSheet(
                state = shareState,
                onRetry = shareViewModel::retry,
                onDismiss = ::finish,
            )
        }

        LaunchedEffect(shareState.status) {
            if (shareState.status is RecipeShareStatus.Success) {
                delay(SUCCESS_DISPLAY_MILLIS)
                finish()
            }
        }
    }

    private fun openMainApp() {
        val mainIntent = Intent(intent)
            .setClass(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(mainIntent)
        finish()
    }

    private companion object {
        const val SUCCESS_DISPLAY_MILLIS = 1_200L
    }
}
