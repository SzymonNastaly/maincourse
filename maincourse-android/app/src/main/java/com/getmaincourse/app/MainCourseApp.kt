package com.getmaincourse.app

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import coil3.ImageLoader
import com.getmaincourse.app.data.CookbookRepository
import com.getmaincourse.app.data.RecipeRepository
import com.getmaincourse.app.features.auth.AuthScreen
import com.getmaincourse.app.features.preview.PreviewScreen
import com.getmaincourse.app.features.recipes.RecipeDetailScreen
import com.getmaincourse.app.features.recipes.RecipeDetailViewModel
import com.getmaincourse.app.features.recipes.RecipesScreen
import com.getmaincourse.app.features.recipes.RecipesViewModel
import com.getmaincourse.app.features.session.SessionUiState
import com.getmaincourse.app.features.session.SessionViewModel
import com.getmaincourse.app.ui.theme.MainCourseColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

@Serializable
data object RecipesRoute : NavKey

@Serializable
data class RecipeDetailRoute(val recipeId: Long, val cookbookId: Long) : NavKey

@Serializable
data object ShoppingRoute : NavKey

@Serializable
data object SearchRoute : NavKey

@Serializable
data object SettingsRoute : NavKey

internal data class BrowsingViewModelFactories(
    val recipes: (Long) -> ViewModelProvider.Factory,
    val detail: (userId: Long, cookbookId: Long, recipeId: Long) -> ViewModelProvider.Factory,
)

@Composable
fun MainCourseApp(
    sessionViewModel: SessionViewModel,
    cookbookRepository: CookbookRepository,
    recipeRepository: RecipeRepository,
    resolveImage: (String?) -> String?,
) {
    val sessionState by sessionViewModel.state.collectAsStateWithLifecycle()
    MainCourseAppContent(
        state = sessionState,
        onSignIn = sessionViewModel::signIn,
        onSignUp = sessionViewModel::signUp,
        onRetryRestore = sessionViewModel::restore,
        onRetryCleanup = sessionViewModel::retryCleanup,
        factories = BrowsingViewModelFactories(
            recipes = { userId ->
                simpleViewModelFactory {
                    RecipesViewModel(userId, cookbookRepository, recipeRepository)
                }
            },
            detail = { userId, cookbookId, recipeId ->
                simpleViewModelFactory {
                    RecipeDetailViewModel(userId, cookbookId, recipeId, recipeRepository)
                }
            },
        ),
        imageLoader = sessionViewModel.imageLoader,
        resolveImage = resolveImage,
    )
}

@Composable
internal fun MainCourseAppContent(
    state: SessionUiState,
    onSignIn: (String, String) -> Unit = { _, _ -> },
    onSignUp: (String?, String, String, String) -> Unit = { _, _, _, _ -> },
    onRetryRestore: () -> Unit = {},
    onRetryCleanup: () -> Unit = {},
    factories: BrowsingViewModelFactories? = null,
    imageLoader: StateFlow<ImageLoader?> = EmptyImageLoader,
    resolveImage: (String?) -> String? = { it },
) {
    val currentImageLoader by imageLoader.collectAsStateWithLifecycle()
    when (state) {
        SessionUiState.Restoring -> LoadingScreen()
        is SessionUiState.SignedOut -> AuthScreen(
            busy = state.busy,
            error = state.authError,
            onSignIn = onSignIn,
            onSignUp = onSignUp,
        )
        is SessionUiState.RestoreError -> RecoveryScreen(state.message, onRetryRestore)
        is SessionUiState.CleanupError -> RecoveryScreen(state.message, onRetryCleanup)
        is SessionUiState.SignedIn -> {
            val availableFactories = checkNotNull(factories) {
                "Browsing factories are required when signed in"
            }
            key(state.session.user.id) {
                val shellOwner = rememberViewModelStoreOwner()
                CompositionLocalProvider(LocalViewModelStoreOwner provides shellOwner) {
                    ProtectedShell(
                        userId = state.session.user.id,
                        factories = availableFactories,
                        imageLoader = currentImageLoader,
                        resolveImage = resolveImage,
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ProtectedShell(
    userId: Long,
    factories: BrowsingViewModelFactories,
    imageLoader: ImageLoader?,
    resolveImage: (String?) -> String?,
) {
    val backStack = rememberNavBackStack(RecipesRoute)
    val current = backStack.last()
    val selected = backStack.filter { it.isTopLevel() }.lastOrNull() ?: RecipesRoute
    val destinations = listOf(
        NavigationDestination(RecipesRoute, R.string.recipes, R.drawable.ic_recipes),
        NavigationDestination(ShoppingRoute, R.string.shopping, R.drawable.ic_shopping),
        NavigationDestination(SearchRoute, R.string.search, R.drawable.ic_search),
        NavigationDestination(SettingsRoute, R.string.settings, R.drawable.ic_settings),
    )
    val selectDestination: (NavKey) -> Unit = { destination ->
        backStack.clear()
        backStack.add(destination)
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MainCourseColors.Canvas,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (current) {
                            RecipesRoute, is RecipeDetailRoute -> stringResource(R.string.recipes)
                            ShoppingRoute -> stringResource(R.string.shopping)
                            SearchRoute -> stringResource(R.string.search)
                            SettingsRoute -> stringResource(R.string.settings)
                            else -> stringResource(R.string.app_name)
                        },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MainCourseColors.Canvas),
                navigationIcon = {
                    if (current is RecipeDetailRoute) {
                        IconButton(
                            onClick = { backStack.removeLastOrNull() },
                            modifier = Modifier.testTag("navigate_back"),
                        ) {
                            Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back))
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.testTag("navigation_bar"),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
            ) {
                destinations.forEach { destination ->
                    NavigationBarItem(
                        modifier = Modifier.testTag("nav_${destination.route.routeName()}"),
                        selected = selected == destination.route,
                        onClick = { selectDestination(destination.route) },
                        icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                        label = { Text(stringResource(destination.title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        },
    ) { padding ->
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            onBack = { backStack.removeLastOrNull() },
            entryProvider = entryProvider {
                entry<RecipesRoute> {
                    val recipesViewModel: RecipesViewModel = viewModel(
                        key = "recipes-$userId",
                        factory = factories.recipes(userId),
                    )
                    val recipesState by recipesViewModel.state.collectAsStateWithLifecycle()
                    RecipesScreen(
                        state = recipesState,
                        imageLoader = imageLoader,
                        resolveImage = resolveImage,
                        onSelectCookbook = { recipesViewModel.selectCookbook(it) },
                        onRefresh = { recipesViewModel.refresh() },
                        onOpenRecipe = { recipeId ->
                            recipesState.selectedCookbookId?.let { cookbookId ->
                                backStack.add(RecipeDetailRoute(recipeId, cookbookId))
                            }
                        },
                    )
                }
                entry<RecipeDetailRoute> { route ->
                    val detailViewModel: RecipeDetailViewModel = viewModel(
                        key = "recipe-$userId-${route.cookbookId}-${route.recipeId}",
                        factory = factories.detail(userId, route.cookbookId, route.recipeId),
                    )
                    val detailState by detailViewModel.state.collectAsStateWithLifecycle()
                    RecipeDetailScreen(
                        state = detailState,
                        imageLoader = imageLoader,
                        resolveImage = resolveImage,
                        onRefresh = { detailViewModel.refresh() },
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
                entry<ShoppingRoute> {
                    PreviewScreen(
                        title = stringResource(R.string.shopping_preview_title),
                        body = stringResource(R.string.shopping_preview_body),
                        testTag = "screen_Shopping",
                    )
                }
                entry<SearchRoute> {
                    PreviewScreen(
                        title = stringResource(R.string.search),
                        body = stringResource(R.string.search_prompt),
                        testTag = "screen_Search",
                    )
                }
                entry<SettingsRoute> {
                    PreviewScreen(
                        title = stringResource(R.string.settings),
                        body = stringResource(R.string.settings_preview_body),
                        testTag = "screen_Settings",
                    )
                }
            },
        )
    }
}

@Composable
private fun LoadingScreen() {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp).testTag("session_loading"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Text(stringResource(R.string.startup_loading), Modifier.padding(top = 16.dp), color = MainCourseColors.Body)
    }
}

@Composable
private fun RecoveryScreen(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp).testTag("session_recovery"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, style = MaterialTheme.typography.titleLarge)
        Button(onClick = onRetry, modifier = Modifier.padding(top = 20.dp)) {
            Text(stringResource(R.string.retry))
        }
    }
}

private data class NavigationDestination(
    val route: NavKey,
    @param:StringRes val title: Int,
    @param:DrawableRes val icon: Int,
)

private fun NavKey.isTopLevel(): Boolean =
    this == RecipesRoute || this == ShoppingRoute || this == SearchRoute || this == SettingsRoute

private fun NavKey.routeName(): String = when (this) {
    RecipesRoute -> "Recipes"
    ShoppingRoute -> "Shopping"
    SearchRoute -> "Search"
    SettingsRoute -> "Settings"
    else -> error("Not a top-level route")
}

private val EmptyImageLoader = MutableStateFlow<ImageLoader?>(null)
