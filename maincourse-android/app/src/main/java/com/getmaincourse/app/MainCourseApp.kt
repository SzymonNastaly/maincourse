package com.getmaincourse.app

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import coil3.ImageLoader
import com.getmaincourse.app.data.model.SignInRequest
import com.getmaincourse.app.data.model.SignUpRequest
import com.getmaincourse.app.features.auth.AuthScreen
import com.getmaincourse.app.features.designsystem.DesignSystemScreen
import com.getmaincourse.app.features.preview.PreviewScreen
import com.getmaincourse.app.features.recipes.RecipeDetailScreen
import com.getmaincourse.app.features.recipes.RecipesScreen
import com.getmaincourse.app.features.session.SessionPhase
import com.getmaincourse.app.features.session.SessionState
import com.getmaincourse.app.features.session.LoadStatus
import com.getmaincourse.app.features.settings.SettingsScreen
import com.getmaincourse.app.ui.theme.MainCourseColors
import kotlinx.serialization.Serializable

@Serializable
enum class Destination(@get:StringRes val title: Int, @get:DrawableRes val icon: Int) : NavKey {
    Recipes(R.string.recipes, R.drawable.ic_recipes),
    Shopping(R.string.shopping, R.drawable.ic_shopping),
    Search(R.string.search, R.drawable.ic_search),
    Settings(R.string.settings, R.drawable.ic_settings),
    DesignSystem(R.string.design_system, R.drawable.ic_settings),
}

@Serializable
data class RecipeDestination(val cookbookId: Long, val recipeId: Long) : NavKey

data class MainCourseActions(
    val restore: () -> Unit = {},
    val signIn: (SignInRequest) -> Unit = {},
    val signUp: (SignUpRequest) -> Unit = {},
    val switchCookbook: (Long) -> Unit = {},
    val refresh: () -> Unit = {},
    val openRecipe: (Long) -> Unit = {},
    val closeRecipe: () -> Unit = {},
    val logout: () -> Unit = {},
    val reset: () -> Unit = {},
)

private val topLevelDestinations = listOf(
    Destination.Recipes, Destination.Shopping, Destination.Search, Destination.Settings,
)

@Composable
fun MainCourseApp(
    state: SessionState,
    actions: MainCourseActions,
    imageLoader: ImageLoader? = null,
    resolveImage: (String?) -> String? = { it },
) {
    when (state.phase) {
        SessionPhase.RESTORING -> StartupScreen(R.string.startup_loading)
        SessionPhase.SIGNED_OUT -> AuthScreen(false, state.authError, actions.signIn, actions.signUp)
        SessionPhase.LOADING_COOKBOOKS -> if (state.user == null) {
            AuthScreen(true, state.authError, actions.signIn, actions.signUp)
        } else if (state.activeCookbookId == null && state.catalogStatus == LoadStatus.ERROR) {
            CookbookRecoveryScreen(actions.refresh, actions.logout)
        } else {
            ProtectedApp(state, actions, imageLoader, resolveImage)
        }
        SessionPhase.READY -> ProtectedApp(state, actions, imageLoader, resolveImage)
        SessionPhase.SIGNING_OUT -> StartupScreen(R.string.auth_submitting)
        SessionPhase.RESTORE_FAILED -> RecoveryScreen(
            title = R.string.startup_failed,
            onRetry = actions.restore,
            onReset = actions.reset,
            retryLabel = R.string.retry,
        )
        SessionPhase.CLEANUP_FAILED -> RecoveryScreen(
            title = R.string.cleanup_failed,
            onRetry = actions.logout,
            onReset = actions.reset,
            retryLabel = R.string.retry_cleanup,
        )
    }
}

@Composable
private fun CookbookRecoveryScreen(onRetry: () -> Unit, onLogout: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.cookbooks_load_error), style = MaterialTheme.typography.titleLarge)
        Button(onClick = onRetry, modifier = Modifier.padding(top = 20.dp)) { Text(stringResource(R.string.retry)) }
        OutlinedButton(onClick = onLogout) { Text(stringResource(R.string.sign_out)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProtectedApp(
    state: SessionState,
    actions: MainCourseActions,
    imageLoader: ImageLoader?,
    resolveImage: (String?) -> String?,
) {
    val user = state.user ?: return StartupScreen(R.string.startup_loading)
    val backStack = rememberNavBackStack(Destination.Recipes)
    val current = backStack.last()
    val selected = backStack.filterIsInstance<Destination>().lastOrNull { it in topLevelDestinations }
        ?: Destination.Recipes
    val selectDestination: (Destination) -> Unit = { destination ->
        actions.closeRecipe()
        backStack.clear()
        backStack.add(Destination.Recipes)
        if (destination != Destination.Recipes) backStack.add(destination)
    }

    LaunchedEffect(state.phase, state.user.id, state.activeCookbookId, state.recipeStatus, state.recipes, current) {
        val detailRoute = current as? RecipeDestination ?: return@LaunchedEffect
        val scopeChanged = detailRoute.cookbookId != state.activeCookbookId
        val authoritativelyMissing = state.recipesFetched &&
            state.recipeStatus != com.getmaincourse.app.features.session.LoadStatus.LOADING &&
            state.recipes.none { it.id == detailRoute.recipeId }
        if (scopeChanged || authoritativelyMissing) {
            backStack.removeLastOrNull()
            actions.closeRecipe()
        } else if (state.detail?.recipeId != detailRoute.recipeId &&
            state.recipes.any { it.id == detailRoute.recipeId }
        ) {
            actions.openRecipe(detailRoute.recipeId)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp
        Row(Modifier.fillMaxSize()) {
            if (useRail) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight().background(MainCourseColors.Rail)
                        .verticalScroll(rememberScrollState()).testTag("navigation_rail"),
                    containerColor = MainCourseColors.Rail,
                ) {
                    topLevelDestinations.forEach { destination ->
                        NavigationRailItem(
                            modifier = Modifier.testTag("nav_${destination.name}"),
                            selected = selected == destination,
                            onClick = { selectDestination(destination) },
                            icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                            label = { Text(stringResource(destination.title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
            Scaffold(
                modifier = Modifier.weight(1f).imePadding(),
                containerColor = MainCourseColors.Canvas,
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                when (current) {
                                    is Destination -> stringResource(current.title)
                                    is RecipeDestination -> state.detail?.recipe?.name ?: stringResource(R.string.recipes)
                                    else -> stringResource(R.string.app_name)
                                },
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MainCourseColors.Canvas),
                        navigationIcon = {
                            if (current is RecipeDestination || current == Destination.DesignSystem) {
                                IconButton(
                                    onClick = {
                                        backStack.removeLastOrNull()
                                        if (current is RecipeDestination) actions.closeRecipe()
                                    },
                                ) {
                                    Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back))
                                }
                            }
                        },
                    )
                },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(
                            modifier = Modifier.testTag("navigation_bar"),
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp,
                        ) {
                            topLevelDestinations.forEach { destination ->
                                NavigationBarItem(
                                    modifier = Modifier.testTag("nav_${destination.name}"),
                                    selected = selected == destination,
                                    onClick = { selectDestination(destination) },
                                    icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                                    label = { Text(stringResource(destination.title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                NavDisplay(
                    backStack = backStack,
                    modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
                    onBack = {
                        if (backStack.lastOrNull() is RecipeDestination) actions.closeRecipe()
                        backStack.removeLastOrNull()
                    },
                    entryProvider = entryProvider {
                        entry<Destination> { destination ->
                            when (destination) {
                                Destination.Recipes -> RecipesScreen(
                                    cookbooks = state.cookbooks,
                                    activeCookbookId = state.activeCookbookId,
                                    recipes = state.recipes,
                                    recipesFetched = state.recipesFetched,
                                    status = state.recipeStatus,
                                    message = state.message,
                                    imageLoader = imageLoader,
                                    resolveImage = resolveImage,
                                    onSwitchCookbook = actions.switchCookbook,
                                    onRefresh = actions.refresh,
                                    onOpenRecipe = { recipeId ->
                                        val cookbookId = state.activeCookbookId
                                        if (cookbookId != null) {
                                            backStack.add(RecipeDestination(cookbookId, recipeId))
                                            actions.openRecipe(recipeId)
                                        }
                                    },
                                )
                                Destination.Settings -> SettingsScreen(user, { backStack.add(Destination.DesignSystem) }, actions.logout)
                                Destination.DesignSystem -> DesignSystemScreen()
                                Destination.Shopping, Destination.Search -> PreviewScreen(destination) {
                                    backStack.add(Destination.DesignSystem)
                                }
                            }
                        }
                        entry<RecipeDestination> { destination ->
                            RecipeDetailScreen(
                                detailState = state.detail?.takeIf { it.recipeId == destination.recipeId },
                                imageLoader = imageLoader,
                                resolveImage = resolveImage,
                                onRetry = { actions.openRecipe(destination.recipeId) },
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun StartupScreen(@StringRes message: Int) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(stringResource(message), Modifier.padding(top = 16.dp), color = MainCourseColors.Body)
    }
}

@Composable
private fun RecoveryScreen(
    @StringRes title: Int,
    onRetry: () -> Unit,
    onReset: () -> Unit,
    @StringRes retryLabel: Int,
) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
        Button(onClick = onRetry, modifier = Modifier.padding(top = 20.dp)) { Text(stringResource(retryLabel)) }
        OutlinedButton(onClick = onReset) { Text(stringResource(R.string.clear_local_data)) }
    }
}
