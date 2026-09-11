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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleEventEffect
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
import com.getmaincourse.app.data.ShoppingListRepository
import com.getmaincourse.app.data.network.MainCourseService
import com.getmaincourse.app.data.session.SessionProvider
import com.getmaincourse.app.data.session.SessionStore
import com.getmaincourse.app.features.auth.AuthScreen
import com.getmaincourse.app.features.recipes.CookbookTitleMenu
import com.getmaincourse.app.features.recipes.IngredientReviewScreen
import com.getmaincourse.app.features.recipes.RecipeDetailScreen
import com.getmaincourse.app.features.recipes.RecipeDetailViewModel
import com.getmaincourse.app.features.recipes.RecipeEditScreen
import com.getmaincourse.app.features.recipes.RecipeEditViewModel
import com.getmaincourse.app.features.recipes.RecipeImportScreen
import com.getmaincourse.app.features.recipes.RecipeImportViewModel
import com.getmaincourse.app.features.recipes.RecipesScreen
import com.getmaincourse.app.features.recipes.RecipesViewModel
import com.getmaincourse.app.features.recipes.SharedRecipeInput
import com.getmaincourse.app.features.search.SearchScreen
import com.getmaincourse.app.features.search.SearchViewModel
import com.getmaincourse.app.features.session.SessionUiState
import com.getmaincourse.app.features.session.SessionViewModel
import com.getmaincourse.app.features.shopping.ShoppingListScreen
import com.getmaincourse.app.features.shopping.ShoppingListViewModel
import com.getmaincourse.app.features.settings.SettingsScreen
import com.getmaincourse.app.features.settings.SettingsViewModel
import com.getmaincourse.app.ui.theme.MainCourseColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object RecipesRoute : NavKey

@Serializable
data class RecipeDetailRoute(val recipeId: Long, val cookbookId: Long) : NavKey

@Serializable
data class RecipeEditRoute(val recipeId: Long, val cookbookId: Long) : NavKey

@Serializable
data class IngredientReviewRoute(val recipeId: Long, val cookbookId: Long, val portions: Int) : NavKey

@Serializable
data object RecipeImportRoute : NavKey

@Serializable
data object ShoppingRoute : NavKey

@Serializable
data object SearchRoute : NavKey

@Serializable
data object SettingsRoute : NavKey

internal data class BrowsingViewModelFactories(
    val recipes: (Long) -> ViewModelProvider.Factory,
    val import: (Long) -> ViewModelProvider.Factory,
    val detail: (userId: Long, cookbookId: Long, recipeId: Long) -> ViewModelProvider.Factory,
    val edit: (userId: Long, cookbookId: Long, recipeId: Long) -> ViewModelProvider.Factory,
    val shopping: (Long) -> ViewModelProvider.Factory,
    val search: (Long) -> ViewModelProvider.Factory,
    val settings: () -> ViewModelProvider.Factory,
)

@Composable
fun MainCourseApp(
    sessionViewModel: SessionViewModel,
    cookbookRepository: CookbookRepository,
    recipeRepository: RecipeRepository,
    shoppingListRepository: ShoppingListRepository,
    service: MainCourseService,
    sessionStore: SessionStore,
    sessionProvider: SessionProvider,
    resolveImage: (String?) -> String?,
    sharedRecipeInput: StateFlow<SharedRecipeInput?>,
    onSharedRecipeInputConsumed: () -> Unit,
) {
    val sessionState by sessionViewModel.state.collectAsStateWithLifecycle()
    val pendingShare by sharedRecipeInput.collectAsStateWithLifecycle()
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
            import = { userId ->
                simpleViewModelFactory {
                    RecipeImportViewModel(userId, cookbookRepository, recipeRepository)
                }
            },
            detail = { userId, cookbookId, recipeId ->
                simpleViewModelFactory {
                    RecipeDetailViewModel(
                        userId,
                        cookbookId,
                        recipeId,
                        recipeRepository,
                        shoppingListRepository,
                        cookbookRepository,
                    )
                }
            },
            edit = { userId, cookbookId, recipeId ->
                simpleViewModelFactory {
                    RecipeEditViewModel(userId, cookbookId, recipeId, recipeRepository)
                }
            },
            shopping = { userId ->
                simpleViewModelFactory {
                    ShoppingListViewModel(userId, cookbookRepository, shoppingListRepository)
                }
            },
            search = { userId ->
                simpleViewModelFactory {
                    SearchViewModel(userId, cookbookRepository, recipeRepository)
                }
            },
            settings = {
                simpleViewModelFactory {
                    SettingsViewModel(
                        service = service,
                        sessionStore = sessionStore,
                        sessionProvider = sessionProvider,
                        deleteAccount = sessionViewModel::deleteAccount,
                        signOut = sessionViewModel::signOut,
                    )
                }
            },
        ),
        imageLoader = sessionViewModel.imageLoader,
        resolveImage = resolveImage,
        sharedRecipeInput = pendingShare,
        onSharedRecipeInputConsumed = onSharedRecipeInputConsumed,
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
    sharedRecipeInput: SharedRecipeInput? = null,
    onSharedRecipeInputConsumed: () -> Unit = {},
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
                        sharedRecipeInput = sharedRecipeInput,
                        onSharedRecipeInputConsumed = onSharedRecipeInputConsumed,
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
    sharedRecipeInput: SharedRecipeInput?,
    onSharedRecipeInputConsumed: () -> Unit,
) {
    val backStack = rememberNavBackStack(RecipesRoute)
    val latestImageLoader by rememberUpdatedState(imageLoader)
    val latestResolveImage by rememberUpdatedState(resolveImage)
    val snackbarHostState = remember { SnackbarHostState() }
    val shellScope = rememberCoroutineScope()
    val importStartedMessage = stringResource(R.string.recipe_import_accepted)
    val recipeSavedMessage = stringResource(R.string.recipe_edit_saved)
    val recipesViewModel: RecipesViewModel = viewModel(
        key = "recipes-$userId",
        factory = factories.recipes(userId),
    )
    val recipesState by recipesViewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        recipesViewModel.reconcileAfterResume()
    }
    val current = backStack.last()
    LaunchedEffect(sharedRecipeInput) {
        if (sharedRecipeInput != null && backStack.lastOrNull() != RecipeImportRoute) {
            backStack.add(RecipeImportRoute)
        }
    }
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (current !is RecipeEditRoute) TopAppBar(
                title = {
                    when (current) {
                        RecipesRoute, ShoppingRoute, SearchRoute -> CookbookTitleMenu(
                            cookbooks = recipesState.cookbooks,
                            selectedId = recipesState.selectedCookbookId,
                            onSelect = recipesViewModel::selectCookbook,
                        )
                        else -> Text(
                            when (current) {
                                is RecipeDetailRoute -> stringResource(R.string.recipes)
                                RecipeImportRoute -> stringResource(R.string.recipe_import)
                                is IngredientReviewRoute -> stringResource(R.string.recipe_ingredients)
                                ShoppingRoute -> stringResource(R.string.shopping_list)
                                SearchRoute -> stringResource(R.string.search)
                                SettingsRoute -> stringResource(R.string.settings)
                                else -> stringResource(R.string.app_name)
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MainCourseColors.Canvas),
                navigationIcon = {
                    if (current is RecipeDetailRoute || current is IngredientReviewRoute || current == RecipeImportRoute) {
                        IconButton(
                            onClick = { backStack.removeLastOrNull() },
                            modifier = Modifier.testTag("navigate_back"),
                        ) {
                            Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    if (current == RecipesRoute) {
                        TextButton(
                            onClick = { backStack.add(RecipeImportRoute) },
                            enabled = recipesState.selectedCookbookId != null,
                            modifier = Modifier.testTag("open_recipe_import"),
                        ) {
                            Text(stringResource(R.string.recipe_import_short))
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (current !is RecipeEditRoute) NavigationBar(
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
                    RecipesScreen(
                        state = recipesState,
                        imageLoader = latestImageLoader,
                        resolveImage = latestResolveImage,
                        onRefresh = { recipesViewModel.refresh() },
                        onOpenRecipe = { recipeId ->
                            recipesState.selectedCookbookId?.let { cookbookId ->
                                backStack.add(RecipeDetailRoute(recipeId, cookbookId))
                            }
                        },
                    )
                }
                entry<RecipeImportRoute> {
                    val importViewModel: RecipeImportViewModel = viewModel(
                        key = "recipe-import-$userId",
                        factory = factories.import(userId),
                    )
                    val importState by importViewModel.state.collectAsStateWithLifecycle()
                    LaunchedEffect(sharedRecipeInput) {
                        sharedRecipeInput?.let { input ->
                            importViewModel.acceptSharedInput(input.value)
                            onSharedRecipeInputConsumed()
                        }
                    }
                    LaunchedEffect(importState.importedRecipeId) {
                        if (importState.importedRecipeId != null) {
                            importState.selectedCookbookId?.let(recipesViewModel::importAccepted)
                            importViewModel.acknowledgeImport()
                            backStack.removeLastOrNull()
                            shellScope.launch { snackbarHostState.showSnackbar(importStartedMessage) }
                        }
                    }
                    RecipeImportScreen(
                        state = importState,
                        onSelectCookbook = importViewModel::selectCookbook,
                        onModeChange = importViewModel::setMode,
                        onUrlChange = importViewModel::updateUrl,
                        onTextChange = importViewModel::updateText,
                        onSubmit = { importViewModel.submit() },
                    )
                }
                entry<RecipeDetailRoute> { route ->
                    val detailViewModel: RecipeDetailViewModel = viewModel(
                        key = "recipe-$userId-${route.cookbookId}-${route.recipeId}",
                        factory = factories.detail(userId, route.cookbookId, route.recipeId),
                    )
                    val detailState by detailViewModel.state.collectAsStateWithLifecycle()
                    val actionState by detailViewModel.action.collectAsStateWithLifecycle()
                    RecipeDetailScreen(
                        state = detailState,
                        imageLoader = latestImageLoader,
                        resolveImage = latestResolveImage,
                        onRefresh = { detailViewModel.refresh() },
                        cookbookId = route.cookbookId,
                        actionState = actionState,
                        onMove = { detailViewModel.moveTo(it) },
                        onDelete = { detailViewModel.delete() },
                        onEdit = {
                            backStack.add(RecipeEditRoute(route.recipeId, route.cookbookId))
                        },
                        onAddIngredients = { portions ->
                            backStack.add(IngredientReviewRoute(route.recipeId, route.cookbookId, portions))
                        },
                        onActionSucceeded = { backStack.removeLastOrNull() },
                    )
                }
                entry<RecipeEditRoute> { route ->
                    val editViewModel: RecipeEditViewModel = viewModel(
                        key = "recipe-edit-$userId-${route.cookbookId}-${route.recipeId}",
                        factory = factories.edit(userId, route.cookbookId, route.recipeId),
                    )
                    val editState by editViewModel.state.collectAsStateWithLifecycle()
                    LaunchedEffect(editState.saved) {
                        if (editState.saved) {
                            backStack.removeLastOrNull()
                            shellScope.launch { snackbarHostState.showSnackbar(recipeSavedMessage) }
                        }
                    }
                    RecipeEditScreen(
                        state = editState,
                        imageLoader = latestImageLoader,
                        resolveImage = latestResolveImage,
                        onBack = { backStack.removeLastOrNull() },
                        onSave = { editViewModel.save() },
                        onNameChange = editViewModel::updateName,
                        onPrepTimeChange = editViewModel::updatePrepTime,
                        onCookTimeChange = editViewModel::updateCookTime,
                        onServingsChange = editViewModel::updateServings,
                        onIngredientChange = editViewModel::updateIngredient,
                        onAddIngredient = editViewModel::addIngredient,
                        onRemoveIngredient = editViewModel::removeIngredient,
                        onMoveIngredient = editViewModel::moveIngredient,
                        onInstructionChange = editViewModel::updateInstruction,
                        onAddInstruction = editViewModel::addInstruction,
                        onRemoveInstruction = editViewModel::removeInstruction,
                        onMoveInstruction = editViewModel::moveInstruction,
                        onNotesChange = editViewModel::updateNotes,
                        onSourceUrlChange = editViewModel::updateSourceUrl,
                        onImageSelected = editViewModel::selectImage,
                        onImageError = editViewModel::reportImageError,
                        onClearError = editViewModel::clearError,
                    )
                }
                entry<IngredientReviewRoute> { route ->
                    val detailViewModel: RecipeDetailViewModel = viewModel(
                        key = "review-$userId-${route.cookbookId}-${route.recipeId}",
                        factory = factories.detail(userId, route.cookbookId, route.recipeId),
                    )
                    val detailState by detailViewModel.state.collectAsStateWithLifecycle()
                    val actionState by detailViewModel.action.collectAsStateWithLifecycle()
                    val recipe = detailState.recipe
                    if (recipe == null) {
                        LoadingScreen()
                    } else {
                        IngredientReviewScreen(
                            recipe = recipe,
                            portions = route.portions,
                            actionState = actionState,
                            onBack = { backStack.removeLastOrNull() },
                            onSubmit = { detailViewModel.addIngredients(it) },
                        )
                    }
                }
                entry<ShoppingRoute> {
                    val shoppingViewModel: ShoppingListViewModel = viewModel(
                        key = "shopping-$userId",
                        factory = factories.shopping(userId),
                    )
                    val shoppingState by shoppingViewModel.state.collectAsStateWithLifecycle()
                    ShoppingListScreen(
                        state = shoppingState,
                        onRefresh = shoppingViewModel::refresh,
                        onDraftChange = shoppingViewModel::updateDraft,
                        onAdd = shoppingViewModel::addItem,
                        onToggle = shoppingViewModel::toggleItem,
                        onDelete = shoppingViewModel::deleteItem,
                        onClear = shoppingViewModel::clearItems,
                        onClearError = shoppingViewModel::clearError,
                    )
                }
                entry<SearchRoute> {
                    val searchViewModel: SearchViewModel = viewModel(
                        key = "search-$userId",
                        factory = factories.search(userId),
                    )
                    val searchState by searchViewModel.state.collectAsStateWithLifecycle()
                    SearchScreen(
                        state = searchState,
                        imageLoader = latestImageLoader,
                        resolveImage = latestResolveImage,
                        onQueryChange = searchViewModel::updateQuery,
                        onRetry = searchViewModel::retry,
                        onOpenRecipe = { recipeId ->
                            searchState.selectedCookbookId?.let { cookbookId ->
                                backStack.add(RecipeDetailRoute(recipeId, cookbookId))
                            }
                        },
                    )
                }
                entry<SettingsRoute> {
                    val settingsViewModel: SettingsViewModel = viewModel(
                        key = "settings-$userId",
                        factory = factories.settings(),
                    )
                    val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
                    SettingsScreen(
                        state = settingsState,
                        onSave = { name, reminders -> settingsViewModel.saveProfile(name, reminders) },
                        onDeleteAccount = { settingsViewModel.deleteAccount() },
                        onSignOut = { settingsViewModel.signOut() },
                        onClearError = settingsViewModel::clearError,
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
