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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.getmaincourse.app.features.session.SessionUiState
import com.getmaincourse.app.features.session.SessionViewModel
import com.getmaincourse.app.features.recipes.SharedRecipeInput
import com.getmaincourse.app.features.recipes.sharedRecipeInput
import com.getmaincourse.app.features.cookbooks.invitationToken
import com.getmaincourse.app.ui.theme.MainCourseTheme
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrl

class MainActivity : ComponentActivity() {
    private val appContainer: AppContainer
        get() = (application as MainCourseApplication).container

    private val viewModel by viewModels<SessionViewModel> { appContainer.sessionViewModelFactory }
    private var contentInstalled = false
    private val pendingSharedRecipe = MutableStateFlow<SharedRecipeInput?>(null)
    private val pendingInvitationToken = MutableStateFlow<String?>(null)
    private var sharedRecipeConsumed = false
    private var invitationConsumed = false
    private val localNetworkPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        installAppContent()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        sharedRecipeConsumed = savedInstanceState?.getBoolean(SHARED_RECIPE_CONSUMED_KEY) == true
        invitationConsumed = savedInstanceState?.getBoolean(INVITATION_CONSUMED_KEY) == true
        if (!sharedRecipeConsumed) pendingSharedRecipe.value = intent.sharedRecipeInput()
        if (!invitationConsumed) pendingInvitationToken.value = invitationToken(intent.dataString)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.sharedRecipeInput()?.let { input ->
            sharedRecipeConsumed = false
            pendingSharedRecipe.value = input
        }
        invitationToken(intent.dataString)?.let { token ->
            invitationConsumed = false
            pendingInvitationToken.value = token
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(SHARED_RECIPE_CONSUMED_KEY, sharedRecipeConsumed)
        outState.putBoolean(INVITATION_CONSUMED_KEY, invitationConsumed)
        super.onSaveInstanceState(outState)
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
                    shoppingListRepository = appContainer.shoppingListRepository,
                    service = appContainer.service,
                    sessionStore = appContainer.sessionStore,
                    sessionProvider = appContainer.sessionProvider,
                    resolveImage = appContainer.images::resolve,
                    sharedRecipeInput = pendingSharedRecipe,
                    onSharedRecipeInputConsumed = {
                        sharedRecipeConsumed = true
                        pendingSharedRecipe.value = null
                    },
                    invitationToken = pendingInvitationToken,
                    onInvitationConsumed = {
                        invitationConsumed = true
                        pendingInvitationToken.value = null
                    },
                )
            }
        }
    }
}

internal fun shouldRequestLocalNetworkAccess(
    isDebugBuild: Boolean,
    sdkInt: Int,
    permissionGranted: Boolean,
    apiHost: String,
): Boolean = isDebugBuild && sdkInt >= 37 && !permissionGranted && apiHost in LOCAL_API_HOSTS

internal const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
private const val SHARED_RECIPE_CONSUMED_KEY = "shared_recipe_consumed"
private const val INVITATION_CONSUMED_KEY = "invitation_consumed"
private val LOCAL_API_HOSTS = setOf("10.0.2.2", "localhost", "127.0.0.1")
