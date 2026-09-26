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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.getmaincourse.app.features.auth.PreAuthScreen
import com.getmaincourse.app.features.auth.PreAuthStep
import com.getmaincourse.app.features.auth.PreAuthUiState
import com.getmaincourse.app.features.auth.PreAuthViewModel
import com.getmaincourse.app.features.auth.ImportDemoScreen
import com.getmaincourse.app.features.auth.SampleSaveViewModel
import com.getmaincourse.app.features.auth.SampleSaveUiState
import com.getmaincourse.app.features.auth.SampleSaveBanner
import com.getmaincourse.app.features.cookbooks.CookbookManagementScreen
import com.getmaincourse.app.features.cookbooks.CookbookManagementViewModel
import com.getmaincourse.app.features.cookbooks.InvitationScreen
import com.getmaincourse.app.features.cookbooks.InvitationViewModel
import com.getmaincourse.app.features.recipes.CookbookTitleMenu
import com.getmaincourse.app.features.recipes.IngredientReviewScreen
import com.getmaincourse.app.features.recipes.RecipeDetailScreen
import com.getmaincourse.app.features.recipes.RecipeDetailViewModel
import com.getmaincourse.app.features.recipes.RecipeActionUiState
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
import com.getmaincourse.app.notifications.NotificationDestination
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
data object ImportDemoRoute : NavKey

@Serializable
data object ShoppingRoute : NavKey

@Serializable
data object SearchRoute : NavKey

@Serializable
data object SettingsRoute : NavKey

@Serializable
data object CookbookSettingsRoute : NavKey

@Serializable
data class InvitationRoute(val token: String) : NavKey

internal data class BrowsingViewModelFactories(
    val recipes: (Long) -> ViewModelProvider.Factory,
    val import: (Long) -> ViewModelProvider.Factory,
    val detail: (userId: Long, cookbookId: Long, recipeId: Long) -> ViewModelProvider.Factory,
    val edit: (userId: Long, cookbookId: Long, recipeId: Long) -> ViewModelProvider.Factory,
    val shopping: (Long) -> ViewModelProvider.Factory,
    val search: (Long) -> ViewModelProvider.Factory,
    val settings: () -> ViewModelProvider.Factory,
    val cookbooks: (Long) -> ViewModelProvider.Factory,
    val invitation: (userId: Long, token: String) -> ViewModelProvider.Factory,
)

@Composable
fun MainCourseApp(
    sessionViewModel: SessionViewModel,
    preAuthViewModel: PreAuthViewModel,
    sampleSaveViewModel: SampleSaveViewModel,
    cookbookRepository: CookbookRepository,
    recipeRepository: RecipeRepository,
    shoppingListRepository: ShoppingListRepository,
    service: MainCourseService,
    sessionStore: SessionStore,
    sessionProvider: SessionProvider,
    resolveImage: (String?) -> String?,
    sharedRecipeInput: StateFlow<SharedRecipeInput?>,
    onSharedRecipeInputConsumed: () -> Unit,
    invitationToken: StateFlow<String?>,
    onInvitationConsumed: () -> Unit,
    notificationDestination: StateFlow<NotificationDestination?>,
    onNotificationConsumed: (Long?) -> Unit,
    notificationsEnabled: StateFlow<Boolean>,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    val sessionState by sessionViewModel.state.collectAsStateWithLifecycle()
    val preAuthState by preAuthViewModel.state.collectAsStateWithLifecycle()
    val sampleSaveState by sampleSaveViewModel.state.collectAsStateWithLifecycle()
    val pendingShare by sharedRecipeInput.collectAsStateWithLifecycle()
    val pendingInvitation by invitationToken.collectAsStateWithLifecycle()
    val pendingNotification by notificationDestination.collectAsStateWithLifecycle()
    val deviceNotificationsEnabled by notificationsEnabled.collectAsStateWithLifecycle()
    LaunchedEffect(sessionState) {
        if (sessionState is SessionUiState.SignedIn) preAuthViewModel.authenticated()
        if (sessionState is SessionUiState.SignedOut) sampleSaveViewModel.signedOut()
    }
    MainCourseAppContent(
        state = sessionState,
        preAuthState = preAuthState,
        onStartOnboarding = preAuthViewModel::start,
        onDemoCompleted = preAuthViewModel::finishDemo,
        onAdvanceOnboarding = preAuthViewModel::advance,
        onBackOnboarding = preAuthViewModel::goBack,
        onLogIn = { sampleSaveViewModel.continueWithoutRecipe(); preAuthViewModel.logIn() },
        onKeepSample = { sampleSaveViewModel.keep(); preAuthViewModel.signUp() },
        onContinueWithoutSample = { sampleSaveViewModel.continueWithoutRecipe(); preAuthViewModel.signUp() },
        sampleSaveState = sampleSaveState,
        onRetrySampleSave = sampleSaveViewModel::retry,
        onCancelSampleSave = sampleSaveViewModel::continueWithoutRecipe,
        onSampleOpened = sampleSaveViewModel::acknowledgeSaved,
        onDismissDemo = sampleSaveViewModel::dismissDemo,
        onAuthenticatedKeepSample = sampleSaveViewModel::keep,
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
            cookbooks = { userId ->
                simpleViewModelFactory { CookbookManagementViewModel(userId, cookbookRepository) }
            },
            invitation = { userId, token ->
                simpleViewModelFactory { InvitationViewModel(userId, token, cookbookRepository) }
            },
        ),
        imageLoader = sessionViewModel.imageLoader,
        resolveImage = resolveImage,
        sharedRecipeInput = pendingShare,
        onSharedRecipeInputConsumed = onSharedRecipeInputConsumed,
        invitationToken = pendingInvitation,
        onInvitationConsumed = onInvitationConsumed,
        notificationDestination = pendingNotification,
        onNotificationConsumed = onNotificationConsumed,
        notificationsEnabled = deviceNotificationsEnabled,
        onRequestNotificationPermission = onRequestNotificationPermission,
        onOpenNotificationSettings = onOpenNotificationSettings,
    )
}

@Composable
internal fun MainCourseAppContent(
    state: SessionUiState,
    preAuthState: PreAuthUiState = PreAuthUiState(PreAuthStep.AUTH, onboarding = false),
    onStartOnboarding: () -> Unit = {},
    onDemoCompleted: () -> Unit = {},
    onAdvanceOnboarding: () -> Unit = {},
    onBackOnboarding: () -> Unit = {},
    onLogIn: () -> Unit = {},
    onKeepSample: () -> Unit = {},
    onContinueWithoutSample: () -> Unit = {},
    sampleSaveState: SampleSaveUiState = SampleSaveUiState(),
    onRetrySampleSave: () -> Unit = {},
    onCancelSampleSave: () -> Unit = {},
    onSampleOpened: () -> Unit = {},
    onDismissDemo: () -> Unit = {},
    onAuthenticatedKeepSample: () -> Unit = {},
    onSignIn: (String, String) -> Unit = { _, _ -> },
    onSignUp: (String?, String, String, String) -> Unit = { _, _, _, _ -> },
    onRetryRestore: () -> Unit = {},
    onRetryCleanup: () -> Unit = {},
    factories: BrowsingViewModelFactories? = null,
    imageLoader: StateFlow<ImageLoader?> = EmptyImageLoader,
    resolveImage: (String?) -> String? = { it },
    sharedRecipeInput: SharedRecipeInput? = null,
    onSharedRecipeInputConsumed: () -> Unit = {},
    invitationToken: String? = null,
    onInvitationConsumed: () -> Unit = {},
    notificationDestination: NotificationDestination? = null,
    onNotificationConsumed: (Long?) -> Unit = {},
    notificationsEnabled: Boolean = false,
    onRequestNotificationPermission: () -> Unit = {},
    onOpenNotificationSettings: () -> Unit = {},
) {
    val currentImageLoader by imageLoader.collectAsStateWithLifecycle()
    when (state) {
        SessionUiState.Restoring -> LoadingScreen()
        is SessionUiState.SignedOut -> PreAuthScreen(
            state = preAuthState,
            busy = state.busy,
            error = state.authError,
            onStart = onStartOnboarding,
            onDemoCompleted = onDemoCompleted,
            onAdvance = onAdvanceOnboarding,
            onBack = onBackOnboarding,
            onLogIn = onLogIn,
            onKeep = onKeepSample,
            onContinueWithoutRecipe = onContinueWithoutSample,
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
                        sampleSaveState = sampleSaveState.takeIf { it.userId == state.session.user.id } ?: SampleSaveUiState(),
                        onRetrySampleSave = onRetrySampleSave,
                        onCancelSampleSave = onCancelSampleSave,
                        onSampleOpened = onSampleOpened,
                        onDismissDemo = onDismissDemo,
                        onKeepSample = onAuthenticatedKeepSample,
                        userId = state.session.user.id,
                        factories = availableFactories,
                        imageLoader = currentImageLoader,
                        resolveImage = resolveImage,
                        sharedRecipeInput = sharedRecipeInput,
                        onSharedRecipeInputConsumed = onSharedRecipeInputConsumed,
                        invitationToken = invitationToken,
                        onInvitationConsumed = onInvitationConsumed,
                        notificationDestination = notificationDestination,
                        onNotificationConsumed = onNotificationConsumed,
                        notificationsEnabled = notificationsEnabled,
                        onRequestNotificationPermission = onRequestNotificationPermission,
                        onOpenNotificationSettings = onOpenNotificationSettings,
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ProtectedShell(
    sampleSaveState: SampleSaveUiState,
    onRetrySampleSave: () -> Unit,
    onCancelSampleSave: () -> Unit,
    onSampleOpened: () -> Unit,
    onDismissDemo: () -> Unit,
    onKeepSample: () -> Unit,
    userId: Long,
    factories: BrowsingViewModelFactories,
    imageLoader: ImageLoader?,
    resolveImage: (String?) -> String?,
    sharedRecipeInput: SharedRecipeInput?,
    onSharedRecipeInputConsumed: () -> Unit,
    invitationToken: String?,
    onInvitationConsumed: () -> Unit,
    notificationDestination: NotificationDestination?,
    onNotificationConsumed: (Long?) -> Unit,
    notificationsEnabled: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
) {
    val backStack = rememberNavBackStack(RecipesRoute)
    var priorityDestination by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(invitationToken, notificationDestination, sharedRecipeInput) {
        if (invitationToken != null || notificationDestination != null || sharedRecipeInput != null) priorityDestination = true
    }
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
    LaunchedEffect(invitationToken) {
        invitationToken?.let { token ->
            if (backStack.lastOrNull() != InvitationRoute(token)) {
                backStack.add(InvitationRoute(token))
            }
            onInvitationConsumed()
        }
    }
    LaunchedEffect(recipesState.initialLoading, recipesState.recipes) {
        if (!recipesState.initialLoading && recipesState.recipes.any { it.starterRecipeKey == null }) {
            onRequestNotificationPermission()
        }
    }
    LaunchedEffect(notificationDestination) {
        val destination = notificationDestination ?: return@LaunchedEffect

        suspend fun selectCookbook(cookbookId: Long?): Boolean {
            if (cookbookId == null || recipesViewModel.state.value.selectedCookbookId == cookbookId) return true
            if (recipesViewModel.state.value.cookbooks.none { it.id == cookbookId }) {
                recipesViewModel.refresh().join()
            }
            if (recipesViewModel.state.value.cookbooks.none { it.id == cookbookId }) return false
            recipesViewModel.selectCookbook(cookbookId).join()
            return recipesViewModel.state.value.selectedCookbookId == cookbookId
        }

        when (destination) {
            is NotificationDestination.Recipe -> {
                if (selectCookbook(destination.cookbookId)) {
                    backStack.clear()
                    backStack.add(RecipesRoute)
                    val cookbookId = destination.cookbookId ?: recipesViewModel.state.value.selectedCookbookId
                    cookbookId?.let { backStack.add(RecipeDetailRoute(destination.recipeId, it)) }
                }
            }
            is NotificationDestination.ShoppingList -> {
                if (selectCookbook(destination.cookbookId)) {
                    backStack.clear()
                    backStack.add(ShoppingRoute)
                }
            }
            NotificationDestination.Home -> {
                backStack.clear()
                backStack.add(RecipesRoute)
            }
        }
        onNotificationConsumed(destination.deliveryId)
    }
    val selected = backStack.filter { it.isTopLevel() }.lastOrNull() ?: RecipesRoute
    val openSample: () -> Unit = {
        sampleSaveState.saved?.let { saved ->
            backStack.add(RecipeDetailRoute(saved.recipeId, saved.cookbookId))
            onSampleOpened()
        }
    }
    LaunchedEffect(sampleSaveState.saved) {
        if (sampleSaveState.saved != null) {
            recipesViewModel.refresh().join()
            recipesViewModel.sampleSaved(sampleSaveState.saved.cookbookId).join()
            if (!priorityDestination && invitationToken == null && notificationDestination == null &&
                sharedRecipeInput == null && backStack.size == 1 && backStack.last() == RecipesRoute
            ) openSample()
        }
    }
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
                                CookbookSettingsRoute -> stringResource(R.string.cookbook_manage)
                                is InvitationRoute -> stringResource(R.string.invitation_title)
                                else -> stringResource(R.string.app_name)
                            },
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MainCourseColors.Canvas),
                navigationIcon = {
                    if (
                        current is RecipeDetailRoute || current is IngredientReviewRoute ||
                        current == RecipeImportRoute || current == ImportDemoRoute || current == CookbookSettingsRoute || current is InvitationRoute
                    ) {
                        IconButton(
                            onClick = { backStack.removeLastOrNull() },
                            modifier = Modifier.testTag("navigate_back"),
                        ) {
                            Icon(painterResource(R.drawable.ic_back), stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    when (val route = current) {
                        RecipesRoute -> {
                            TextButton(
                                onClick = { backStack.add(RecipeImportRoute) },
                                enabled = recipesState.selectedCookbookId != null,
                                modifier = Modifier.testTag("open_recipe_import"),
                            ) {
                                Text(stringResource(R.string.recipe_import_short))
                            }
                        }
                        is RecipeDetailRoute -> {
                            TextButton(
                                onClick = { backStack.add(RecipeEditRoute(route.recipeId, route.cookbookId)) },
                                modifier = Modifier.testTag("recipe_edit_open"),
                            ) {
                                Text(stringResource(R.string.recipe_edit))
                            }
                        }
                        else -> Unit
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
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
        SampleSaveBanner(sampleSaveState, onRetrySampleSave, onCancelSampleSave, openSample)
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.weight(1f),
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
                        showDemo = !sampleSaveState.demoDismissed,
                        onTryDemo = { backStack.add(ImportDemoRoute) },
                        onDismissDemo = onDismissDemo,
                        onImport = { backStack.add(RecipeImportRoute) },
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
                entry<ImportDemoRoute> {
                    ImportDemoScreen(
                        onRecipeReady = onDismissDemo,
                        continueLabel = R.string.demo_keep,
                        onContinue = { onKeepSample(); backStack.removeLastOrNull() },
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
                        imageLoader = latestImageLoader,
                        resolveImage = latestResolveImage,
                        onRefresh = { detailViewModel.refresh() },
                        onAddIngredients = { portions ->
                            backStack.add(IngredientReviewRoute(route.recipeId, route.cookbookId, portions))
                        },
                    )
                }
                entry<RecipeEditRoute> { route ->
                    val editViewModel: RecipeEditViewModel = viewModel(
                        key = "recipe-edit-$userId-${route.cookbookId}-${route.recipeId}",
                        factory = factories.edit(userId, route.cookbookId, route.recipeId),
                    )
                    val editState by editViewModel.state.collectAsStateWithLifecycle()
                    val detailViewModel: RecipeDetailViewModel = viewModel(
                        key = "recipe-edit-actions-$userId-${route.cookbookId}-${route.recipeId}",
                        factory = factories.detail(userId, route.cookbookId, route.recipeId),
                    )
                    val detailState by detailViewModel.state.collectAsStateWithLifecycle()
                    val actionState by detailViewModel.action.collectAsStateWithLifecycle()
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
                        cookbooks = detailState.cookbooks,
                        cookbookId = route.cookbookId,
                        actionState = actionState,
                        onMove = { detailViewModel.moveTo(it) },
                        onDelete = { detailViewModel.delete() },
                        onActionSucceeded = {
                            backStack.removeLastOrNull()
                            backStack.removeLastOrNull()
                        },
                    )
                }
                entry<IngredientReviewRoute> { route ->
                    val detailViewModel: RecipeDetailViewModel = viewModel(
                        key = "review-$userId-${route.cookbookId}-${route.recipeId}",
                        factory = factories.detail(userId, route.cookbookId, route.recipeId),
                    )
                    val detailState by detailViewModel.state.collectAsStateWithLifecycle()
                    val actionState by detailViewModel.action.collectAsStateWithLifecycle()
                    LaunchedEffect(actionState) {
                        if (actionState is RecipeActionUiState.Succeeded) {
                            onRequestNotificationPermission()
                        }
                    }
                    val recipe = detailState.recipe
                    if (recipe == null) {
                        LoadingScreen()
                    } else {
                        IngredientReviewScreen(
                            recipe = recipe,
                            portions = route.portions,
                            actionState = actionState,
                            shoppingListReviewReady = detailState.shoppingListReviewReady,
                            shoppingListNeedsReview = detailViewModel::shoppingListNeedsReview,
                            onBack = { backStack.removeLastOrNull() },
                            onSubmit = { items, clearExisting ->
                                detailViewModel.addIngredients(items, clearExisting)
                            },
                        )
                    }
                }
                entry<ShoppingRoute> {
                    val shoppingViewModel: ShoppingListViewModel = viewModel(
                        key = "shopping-$userId",
                        factory = factories.shopping(userId),
                    )
                    val shoppingState by shoppingViewModel.state.collectAsStateWithLifecycle()
                    LaunchedEffect(shoppingState.initialLoading, shoppingState.uncheckedItems.size) {
                        if (!shoppingState.initialLoading && shoppingState.uncheckedItems.size >= 3) {
                            onRequestNotificationPermission()
                        }
                    }
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
                        onManageCookbooks = { backStack.add(CookbookSettingsRoute) },
                        notificationsEnabled = notificationsEnabled,
                        onRequestNotificationPermission = onRequestNotificationPermission,
                        onOpenNotificationSettings = onOpenNotificationSettings,
                    )
                }
                entry<CookbookSettingsRoute> {
                    val cookbookViewModel: CookbookManagementViewModel = viewModel(
                        key = "cookbook-management-$userId",
                        factory = factories.cookbooks(userId),
                    )
                    val cookbookState by cookbookViewModel.state.collectAsStateWithLifecycle()
                    LaunchedEffect(cookbookState.invitation?.inviteUrl) {
                        if (cookbookState.invitation != null) onRequestNotificationPermission()
                    }
                    CookbookManagementScreen(
                        userId = userId,
                        state = cookbookState,
                        onCreate = cookbookViewModel::create,
                        onGenerateInvitation = cookbookViewModel::generateInvitation,
                        onLeave = cookbookViewModel::leave,
                        onDelete = cookbookViewModel::delete,
                        onDismissInvitation = cookbookViewModel::clearInvitation,
                        onClearError = cookbookViewModel::clearError,
                    )
                }
                entry<InvitationRoute> { route ->
                    val invitationViewModel: InvitationViewModel = viewModel(
                        key = "invitation-$userId-${route.token}",
                        factory = factories.invitation(userId, route.token),
                    )
                    val invitationState by invitationViewModel.state.collectAsStateWithLifecycle()
                    LaunchedEffect(invitationState.acceptance?.cookbookId) {
                        if (invitationState.acceptance != null) onRequestNotificationPermission()
                    }
                    LaunchedEffect(invitationState.declined) {
                        if (invitationState.declined) backStack.removeLastOrNull()
                    }
                    InvitationScreen(
                        state = invitationState,
                        onAccept = invitationViewModel::accept,
                        onDecline = invitationViewModel::decline,
                        onDone = { backStack.removeLastOrNull() },
                    )
                }
            },
        )
        }
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
