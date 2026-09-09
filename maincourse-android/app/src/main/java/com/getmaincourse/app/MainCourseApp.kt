package com.getmaincourse.app

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.getmaincourse.app.features.auth.AuthenticationMethod
import com.getmaincourse.app.features.auth.AuthScreen
import com.getmaincourse.app.features.designsystem.DesignSystemScreen
import com.getmaincourse.app.features.onboarding.OnboardingScreen
import com.getmaincourse.app.features.onboarding.OnboardingState
import com.getmaincourse.app.features.onboarding.OnboardingStep
import com.getmaincourse.app.features.preview.PreviewScreen
import com.getmaincourse.app.features.search.RecipeSearchScreen
import com.getmaincourse.app.features.search.RecipeSearchState
import com.getmaincourse.app.features.recipes.IngredientReviewScreen
import com.getmaincourse.app.features.recipes.RecipeActionState
import com.getmaincourse.app.features.recipes.RecipeActionOperation
import com.getmaincourse.app.features.recipes.RecipeEditDraft
import com.getmaincourse.app.features.recipes.RecipeEditScreen
import com.getmaincourse.app.features.recipes.RecipeImagePreparationState
import com.getmaincourse.app.features.recipes.RecipeEditorImageSelection
import com.getmaincourse.app.features.recipes.ShoppingItemInput
import com.getmaincourse.app.data.images.PreparedRecipeImage
import com.getmaincourse.app.data.cache.RecipeScope
import com.getmaincourse.app.features.recipes.RecipeDetailScreen
import com.getmaincourse.app.features.recipes.RecipesScreen
import com.getmaincourse.app.features.session.SessionPhase
import com.getmaincourse.app.features.session.SessionState
import com.getmaincourse.app.features.session.LoadStatus
import com.getmaincourse.app.features.session.DetailStatus
import com.getmaincourse.app.features.session.RecipeDetailState
import com.getmaincourse.app.features.settings.SettingsScreen
import com.getmaincourse.app.features.settings.AccountState
import com.getmaincourse.app.features.settings.DeleteAccountScreen
import com.getmaincourse.app.features.settings.ManageAccountScreen
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
data class RecipeDestination(val userId: Long, val cookbookId: Long, val recipeId: Long) : NavKey

@Serializable
data class RecipeEditDestination(val userId: Long, val cookbookId: Long, val recipeId: Long, val editorId: String) : NavKey

@Serializable
data class IngredientReviewDestination(val userId: Long, val cookbookId: Long, val recipeId: Long, val portions: Int) : NavKey

@Serializable
data class ManageAccountDestination(val userId: Long) : NavKey

@Serializable
data class DeleteAccountDestination(val userId: Long) : NavKey

data class MainCourseActions(
    val restore: () -> Unit = {},
    val signIn: (SignInRequest) -> Unit = {},
    val signUp: (SignUpRequest) -> Unit = {},
    val googleSignIn: () -> Unit = {},
    val appleSignIn: () -> Unit = {},
    val cancelAppleSignIn: () -> Unit = {},
    val startOnboarding: () -> Unit = {},
    val advanceOnboarding: () -> Unit = {},
    val backOnboarding: () -> Unit = {},
    val skipOnboarding: () -> Unit = {},
    val useExistingAccount: () -> Unit = {},
    val updateOnboardingHousehold: (Int) -> Unit = {},
    val updateOnboardingSaving: (String) -> Unit = {},
    val updateOnboardingDiet: (String) -> Unit = {},
    val retryOnboardingPersistence: () -> Unit = {},
    val continueOnboardingWithoutSaving: () -> Unit = {},
    val switchCookbook: (Long) -> Unit = {},
    val refresh: () -> Unit = {},
    val openRecipe: (Long) -> Unit = {},
    val closeRecipe: () -> Unit = {},
    val updateSearchQuery: (String) -> Unit = {},
    val saveRecipe: (RecipeEditDraft, PreparedRecipeImage?) -> Unit = { _, _ -> },
    val retryRecipePhoto: (PreparedRecipeImage?) -> Unit = { _ -> },
    val moveRecipe: (Long, Long) -> Unit = { _, _ -> },
    val deleteRecipe: (Long) -> Unit = {},
    val addReviewedIngredients: (Long, List<ShoppingItemInput>) -> Unit = { _, _ -> },
    val retryRecipeReconciliation: () -> Unit = {},
    val clearRecipeAction: () -> Unit = {},
    val prepareRecipeImage: (android.net.Uri, String) -> Unit = { _, _ -> },
    val releaseRecipeEditorImage: (RecipeEditorImageSelection) -> Unit = {},
    val updateName: (String) -> Unit = {},
    val updateLifecycleNotifications: (Boolean) -> Unit = {},
    val retryAccountPersistence: () -> Unit = {},
    val deleteAccount: () -> Unit = {},
    val clearAccountError: () -> Unit = {},
    val logout: () -> Unit = {},
    val reset: () -> Unit = {},
)

private val topLevelDestinations = listOf(
    Destination.Recipes, Destination.Shopping, Destination.Search, Destination.Settings,
)

@Composable
fun MainCourseApp(
    state: SessionState,
    onboardingState: OnboardingState,
    accountState: AccountState,
    authenticationMethod: AuthenticationMethod?,
    actions: MainCourseActions,
    imageLoader: ImageLoader? = null,
    resolveImage: (String?) -> String? = { it },
    appleCanCancel: Boolean = false,
    searchState: RecipeSearchState = RecipeSearchState(),
    recipeActionState: RecipeActionState = RecipeActionState(),
    recipeImagePreparationState: RecipeImagePreparationState = RecipeImagePreparationState(),
    onKeepScreenOnChanged: (Boolean) -> Unit = {},
) {
    val isPreparingAuthentication = authenticationMethod != null
    val authenticatedUser = state.user
    if (authenticatedUser != null &&
        (state.phase == SessionPhase.LOADING_COOKBOOKS || state.phase == SessionPhase.READY)
    ) {
        ProtectedApp(
            state, accountState, actions, imageLoader, resolveImage, searchState,
            recipeActionState, recipeImagePreparationState, onKeepScreenOnChanged,
        )
        return
    }

    when (state.phase) {
        SessionPhase.RESTORING -> StartupScreen(R.string.startup_loading)
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
        SessionPhase.SIGNED_OUT, SessionPhase.LOADING_COOKBOOKS -> {
            if (onboardingState.isLoading) {
                StartupScreen(R.string.startup_loading)
            } else {
                var stableStep by remember { mutableStateOf(onboardingState.step) }
                SideEffect {
                    if (!isPreparingAuthentication) stableStep = onboardingState.step
                }
                val presentedState = onboardingState.copy(
                    step = if (isPreparingAuthentication) stableStep else onboardingState.step,
                )
                if (presentedState.step == OnboardingStep.COMPLETE) {
                    AuthScreen(
                        modifier = Modifier.safeDrawingPadding(),
                        authenticationMethod = authenticationMethod,
                        error = state.authError,
                        onSignIn = actions.signIn,
                        onSignUp = actions.signUp,
                        onGoogleSignIn = actions.googleSignIn,
                        onAppleSignIn = actions.appleSignIn,
                        onCancelAppleSignIn = actions.cancelAppleSignIn,
                        appleCanCancel = appleCanCancel,
                    )
                } else {
                    OnboardingScreen(
                        state = presentedState,
                        authenticationMethod = authenticationMethod,
                        authError = state.authError,
                        onStart = actions.startOnboarding,
                        onBack = actions.backOnboarding,
                        onSkip = actions.skipOnboarding,
                        onExistingAccount = actions.useExistingAccount,
                        onAdvance = actions.advanceOnboarding,
                        onHouseholdChanged = actions.updateOnboardingHousehold,
                        onSavingChanged = actions.updateOnboardingSaving,
                        onDietChanged = actions.updateOnboardingDiet,
                        onRetryPersistence = actions.retryOnboardingPersistence,
                        onContinueWithoutSaving = actions.continueOnboardingWithoutSaving,
                        onSignIn = actions.signIn,
                        onSignUp = actions.signUp,
                        onGoogleSignIn = actions.googleSignIn,
                        onAppleSignIn = actions.appleSignIn,
                        onCancelAppleSignIn = actions.cancelAppleSignIn,
                        appleCanCancel = appleCanCancel,
                    )
                }
            }
        }
        SessionPhase.READY -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProtectedApp(
    state: SessionState,
    accountState: AccountState,
    actions: MainCourseActions,
    imageLoader: ImageLoader?,
    resolveImage: (String?) -> String?,
    searchState: RecipeSearchState,
    recipeActionState: RecipeActionState,
    recipeImagePreparationState: RecipeImagePreparationState,
    onKeepScreenOnChanged: (Boolean) -> Unit,
) {
    val user = state.user ?: return StartupScreen(R.string.startup_loading)
    val backStack = rememberNavBackStack(Destination.Recipes)
    DisposableEffect(user.id, backStack) {
        onDispose {
            backStack.clear()
            backStack.add(Destination.Recipes)
        }
    }
    val current = backStack.last()
    val editorSaveBlocking = (current as? RecipeEditDestination)?.let { editor ->
        recipeActionState.isBusy && recipeActionState.scope == RecipeScope(editor.userId, editor.cookbookId) &&
            recipeActionState.recipeId == editor.recipeId &&
            recipeActionState.operation in setOf(RecipeActionOperation.SAVING, RecipeActionOperation.UPLOADING_PHOTO)
    } == true
    var pendingMoveRecipe by remember { mutableStateOf<RecipeRoute?>(null) }
    var pendingDeleteRecipe by remember { mutableStateOf<RecipeRoute?>(null) }
    var cookingRequested by remember { mutableStateOf(false) }
    var editorImageSelection by remember { mutableStateOf<RecipeEditorImageSelection?>(null) }
    val selected = backStack.filterIsInstance<Destination>().lastOrNull { it in topLevelDestinations }
        ?: Destination.Recipes
    val releaseCurrentEditorImage: () -> Unit = {
        val editor = current as? RecipeEditDestination
        val selection = editorImageSelection?.takeIf { it.editorId == editor?.editorId }
        if (selection != null) actions.releaseRecipeEditorImage(selection)
        editorImageSelection = null
    }
    val selectDestination: (Destination) -> Unit = { destination ->
        releaseCurrentEditorImage()
        cookingRequested = false
        actions.closeRecipe()
        backStack.clear()
        backStack.add(Destination.Recipes)
        if (destination != Destination.Recipes) backStack.add(destination)
    }

    LaunchedEffect(current, cookingRequested, state.user.id, state.activeCookbookId) {
        onKeepScreenOnChanged(current is RecipeDestination && cookingRequested)
    }
    DisposableEffect(state.user.id) { onDispose { onKeepScreenOnChanged(false) } }
    LaunchedEffect(state.user.id, state.activeCookbookId) {
        pendingMoveRecipe = pendingMoveRecipe?.takeIf { it.userId == state.user.id && it.cookbookId == state.activeCookbookId }
        pendingDeleteRecipe = pendingDeleteRecipe?.takeIf { it.userId == state.user.id && it.cookbookId == state.activeCookbookId }
    }
    LaunchedEffect(recipeActionState.outcome, recipeActionState.isBusy, current) {
        val editor = current as? RecipeEditDestination ?: return@LaunchedEffect
        if (!recipeActionState.isBusy && recipeActionState.outcome == com.getmaincourse.app.features.recipes.RecipeActionOutcome.SUCCEEDED &&
            recipeActionState.scope == RecipeScope(editor.userId, editor.cookbookId) && recipeActionState.recipeId == editor.recipeId
            && recipeActionState.requestKey == editor.editorId
        ) {
            releaseCurrentEditorImage()
            backStack.removeLastOrNull()
        }
    }

    LaunchedEffect(state.phase, state.user.id, state.activeCookbookId, state.recipeStatus, state.recipes, current) {
        val detailRoute = current.recipeRoute() ?: return@LaunchedEffect
        if (backStack.lastOrNull()?.recipeRoute() != detailRoute) return@LaunchedEffect
        if (detailRoute.userId != state.user.id) {
            backStack.removeLastOrNull()
            actions.closeRecipe()
            return@LaunchedEffect
        }
        val scopeUndecided = state.activeCookbookId == null && state.phase == SessionPhase.LOADING_COOKBOOKS
        val scopeFailedWithoutSelection = state.activeCookbookId == null &&
            (state.catalogStatus.isFailure() || state.recipeStatus.isFailure())
        val scopeChanged = !scopeUndecided && !scopeFailedWithoutSelection && detailRoute.cookbookId != state.activeCookbookId
        val unavailableHere = state.detail?.recipeId == detailRoute.recipeId &&
            state.detail.status == DetailStatus.UNAVAILABLE
        val authoritativelyMissing = state.recipesFetched &&
            state.recipeStatus != LoadStatus.LOADING &&
            state.recipes.none { it.id == detailRoute.recipeId }
        if (scopeChanged || (current is RecipeDestination && authoritativelyMissing && !unavailableHere)) {
            cookingRequested = false
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
                            enabled = !editorSaveBlocking,
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
                                    is RecipeEditDestination -> stringResource(R.string.recipe_edit)
                                    is IngredientReviewDestination -> stringResource(R.string.recipe_review_help)
                                    is ManageAccountDestination -> stringResource(R.string.manage_account)
                                    is DeleteAccountDestination -> stringResource(R.string.delete_account)
                                    else -> stringResource(R.string.app_name)
                                },
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MainCourseColors.Canvas),
                        navigationIcon = {
                            if (current is RecipeDestination || current is RecipeEditDestination ||
                                current is IngredientReviewDestination || current == Destination.DesignSystem ||
                                current is ManageAccountDestination || current is DeleteAccountDestination
                            ) {
                                IconButton(
                                    enabled = !editorSaveBlocking,
                                    onClick = {
                                        releaseCurrentEditorImage()
                                        cookingRequested = false
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
                                    enabled = !editorSaveBlocking,
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
                        if (!editorSaveBlocking) {
                            releaseCurrentEditorImage()
                            cookingRequested = false
                            if (backStack.lastOrNull() is RecipeDestination) actions.closeRecipe()
                            backStack.removeLastOrNull()
                        }
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
                                    catalogStatus = state.catalogStatus,
                                    imageLoader = imageLoader,
                                    resolveImage = resolveImage,
                                    onSwitchCookbook = actions.switchCookbook,
                                    onRefresh = actions.refresh,
                                    onLogout = actions.logout,
                                    onOpenRecipe = { recipeId ->
                                        val cookbookId = state.activeCookbookId
                                        if (cookbookId != null) {
                                            backStack.add(RecipeDestination(user.id, cookbookId, recipeId))
                                        }
                                    },
                                    onEditRecipe = { recipeId ->
                                        state.activeCookbookId?.let { cookbookId ->
                                            backStack.add(RecipeEditDestination(user.id, cookbookId, recipeId, java.util.UUID.randomUUID().toString()))
                                        }
                                    },
                                    onMoveRecipe = { state.activeCookbookId?.let { cookbookId -> pendingMoveRecipe = RecipeRoute(user.id, cookbookId, it) } },
                                    onDeleteRecipe = { state.activeCookbookId?.let { cookbookId -> pendingDeleteRecipe = RecipeRoute(user.id, cookbookId, it) } },
                                    actionsEnabled = !recipeActionState.isBusy,
                                    actionState = recipeActionState,
                                    onRetryReconciliation = actions.retryRecipeReconciliation,
                                    onClearAction = actions.clearRecipeAction,
                                )
                                Destination.Settings -> SettingsScreen(
                                    user = user,
                                    accountState = accountState,
                                    onUpdateName = actions.updateName,
                                    onUpdateLifecycleNotifications = actions.updateLifecycleNotifications,
                                    onRetryAccountPersistence = actions.retryAccountPersistence,
                                    onClearAccountError = actions.clearAccountError,
                                    onOpenManageAccount = {
                                        if (!accountState.canRetryPersistence) actions.clearAccountError()
                                        backStack.add(ManageAccountDestination(user.id))
                                    },
                                    onOpenDesignSystem = { backStack.add(Destination.DesignSystem) },
                                    onLogout = actions.logout,
                                )
                                Destination.DesignSystem -> DesignSystemScreen()
                                Destination.Shopping -> PreviewScreen { backStack.add(Destination.DesignSystem) }
                                Destination.Search -> {
                                    val cookbookId = state.activeCookbookId
                                    if (cookbookId == null) {
                                        when {
                                            state.catalogStatus == LoadStatus.LOADING -> RouteLoading("search_scope_loading")
                                            state.phase == SessionPhase.READY && state.catalogStatus == LoadStatus.FRESH && state.cookbooks.isEmpty() -> {
                                                EmptyRouteState(R.string.search_no_cookbooks, "search_no_cookbooks")
                                            }
                                            state.catalogStatus.isFailure() -> RecoveryPanel(
                                                actions.refresh,
                                                { selectDestination(Destination.Recipes) },
                                                "search_scope_error",
                                            )
                                            else -> RouteLoading("search_scope_loading")
                                        }
                                    } else {
                                        RecipeSearchScreen(
                                            scope = RecipeScope(user.id, cookbookId),
                                            state = searchState,
                                            imageLoader = imageLoader,
                                            resolveImage = resolveImage,
                                            onQueryChange = actions.updateSearchQuery,
                                            onOpenRecipe = { backStack.add(RecipeDestination(user.id, cookbookId, it)) },
                                            onEditRecipe = { recipeId -> backStack.add(RecipeEditDestination(user.id, cookbookId, recipeId, java.util.UUID.randomUUID().toString())) },
                                            onMoveRecipe = { pendingMoveRecipe = RecipeRoute(user.id, cookbookId, it) },
                                            onDeleteRecipe = { pendingDeleteRecipe = RecipeRoute(user.id, cookbookId, it) },
                                            actionsEnabled = !recipeActionState.isBusy,
                                            actionState = recipeActionState,
                                            onRefresh = actions.refresh,
                                            onRetryReconciliation = actions.retryRecipeReconciliation,
                                            onClearAction = actions.clearRecipeAction,
                                        )
                                    }
                                }
                            }
                        }
                        entry<RecipeDestination> { destination ->
                            val awaitingListRecovery = state.detail?.recipeId != destination.recipeId &&
                                !state.recipesFetched && (state.recipeStatus.isFailure() ||
                                (state.activeCookbookId == null && state.catalogStatus.isFailure()))
                            val displayedDetail = state.detail?.takeIf { it.recipeId == destination.recipeId }
                                ?: if (awaitingListRecovery) {
                                    RecipeDetailState(destination.recipeId, DetailStatus.ERROR)
                                } else {
                                    null
                                }
                            RecipeDetailScreen(
                                scope = RecipeScope(destination.userId, destination.cookbookId),
                                detailState = displayedDetail,
                                importFailed = state.recipes.firstOrNull {
                                    it.id == destination.recipeId
                                }?.importStatus == "failed",
                                imageLoader = imageLoader,
                                resolveImage = resolveImage,
                                onRetry = { retryRecipeRoute(state, destination.recipeId, actions) },
                                onBack = {
                                    cookingRequested = false
                                    backStack.removeLastOrNull()
                                    actions.closeRecipe()
                                },
                                actionState = recipeActionState,
                                onEdit = { backStack.add(RecipeEditDestination(destination.userId, destination.cookbookId, destination.recipeId, java.util.UUID.randomUUID().toString())) },
                                onMove = { pendingMoveRecipe = destination.recipeRoute() },
                                onDelete = { pendingDeleteRecipe = destination.recipeRoute() },
                                onReviewIngredients = { portions -> backStack.add(IngredientReviewDestination(destination.userId, destination.cookbookId, destination.recipeId, portions)) },
                                onCookingChanged = { cookingRequested = it },
                                onRetryPhoto = { actions.retryRecipePhoto(null) },
                                onRetryReconciliation = actions.retryRecipeReconciliation,
                                onDismissAction = actions.clearRecipeAction,
                            )
                        }
                        entry<RecipeEditDestination> { destination ->
                            val detail = state.detail?.takeIf { it.recipeId == destination.recipeId }?.recipe
                            if (detail == null) {
                                RecipeRouteState(
                                    state = state,
                                    destination = destination.recipeRoute(),
                                    onRetry = { retryRecipeRoute(state, destination.recipeId, actions) },
                                ) { backStack.removeLastOrNull() }
                            } else {
                                RecipeEditScreen(
                                    scope = RecipeScope(destination.userId, destination.cookbookId),
                                    recipe = detail,
                                    actionState = recipeActionState,
                                    imageState = recipeImagePreparationState,
                                    onSave = actions.saveRecipe,
                                    onPrepareImage = actions.prepareRecipeImage,
                                    onRetryPhoto = actions.retryRecipePhoto,
                                    onRetryReconciliation = actions.retryRecipeReconciliation,
                                    onCancel = { backStack.removeLastOrNull() },
                                    editorId = destination.editorId,
                                    onImageSelectionChanged = { selection ->
                                        if (selection.editorId == destination.editorId) editorImageSelection = selection
                                    },
                                    onLeaveEditor = actions.releaseRecipeEditorImage,
                                )
                            }
                        }
                        entry<IngredientReviewDestination> { destination ->
                            val detail = state.detail?.takeIf { it.recipeId == destination.recipeId }?.recipe
                            if (detail == null) {
                                RecipeRouteState(
                                    state = state,
                                    destination = destination.recipeRoute(),
                                    onRetry = { retryRecipeRoute(state, destination.recipeId, actions) },
                                ) { backStack.removeLastOrNull() }
                            } else {
                                IngredientReviewScreen(
                                    scope = RecipeScope(destination.userId, destination.cookbookId),
                                    recipe = detail,
                                    portions = destination.portions.coerceIn(1, 64),
                                    actionState = recipeActionState,
                                    onSubmit = { actions.addReviewedIngredients(detail.id, it) },
                                )
                            }
                        }
                        entry<ManageAccountDestination> { destination ->
                            if (destination.userId == user.id) {
                                ManageAccountScreen(
                                    user = user,
                                    accountState = accountState,
                                    onRetryPersistence = actions.retryAccountPersistence,
                                    onClearError = actions.clearAccountError,
                                    onDeleteAccount = {
                                        if (!accountState.canRetryPersistence) actions.clearAccountError()
                                        backStack.add(DeleteAccountDestination(user.id))
                                    },
                                )
                            }
                        }
                        entry<DeleteAccountDestination> { destination ->
                            if (destination.userId == user.id) {
                                DeleteAccountScreen(
                                    accountState = accountState,
                                    onDeleteAccount = actions.deleteAccount,
                                    onRetryPersistence = actions.retryAccountPersistence,
                                    onClearError = actions.clearAccountError,
                                )
                            }
                        }
                    },
                )
            }
        }
    }
    pendingMoveRecipe?.let { route ->
        val recipeName = state.recipes.firstOrNull { it.id == route.recipeId }?.name ?: return@let
        AlertDialog(
            onDismissRequest = { if (!recipeActionState.isBusy) pendingMoveRecipe = null },
            title = { Text(stringResource(R.string.recipe_move_title, recipeName)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.cookbooks.filter { it.id != state.activeCookbookId }.forEach { cookbook ->
                        OutlinedButton(
                            enabled = !recipeActionState.isBusy,
                            onClick = {
                                if (state.user.id == route.userId && state.activeCookbookId == route.cookbookId) actions.moveRecipe(route.recipeId, cookbook.id)
                                pendingMoveRecipe = null
                            },
                            modifier = Modifier.fillMaxWidth().testTag("move_to_${cookbook.id}"),
                        ) { Text(stringResource(R.string.recipe_move_target, cookbook.name)) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(enabled = !recipeActionState.isBusy, onClick = { pendingMoveRecipe = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    pendingDeleteRecipe?.let { route ->
        val recipeName = state.recipes.firstOrNull { it.id == route.recipeId }?.name ?: return@let
        AlertDialog(
            onDismissRequest = { if (!recipeActionState.isBusy) pendingDeleteRecipe = null },
            title = { Text(stringResource(R.string.recipe_delete_title, recipeName)) },
            text = { Text(stringResource(R.string.recipe_delete_body)) },
            confirmButton = {
                Button(
                    enabled = !recipeActionState.isBusy,
                    onClick = {
                        if (state.user.id == route.userId && state.activeCookbookId == route.cookbookId) actions.deleteRecipe(route.recipeId)
                        pendingDeleteRecipe = null
                    },
                    modifier = Modifier.testTag("confirm_delete_recipe"),
                ) { Text(stringResource(R.string.recipe_delete)) }
            },
            dismissButton = { TextButton(enabled = !recipeActionState.isBusy, onClick = { pendingDeleteRecipe = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private data class RecipeRoute(val userId: Long, val cookbookId: Long, val recipeId: Long)

private fun retryRecipeRoute(state: SessionState, recipeId: Long, actions: MainCourseActions) {
    val recipeKnown = state.recipes.any { it.id == recipeId }
    val listOrScopeUnresolved = !state.recipesFetched || state.activeCookbookId == null
    if (!recipeKnown && listOrScopeUnresolved) actions.refresh() else actions.openRecipe(recipeId)
}

private fun Any?.recipeRoute(): RecipeRoute? = when (this) {
    is RecipeDestination -> RecipeRoute(userId, cookbookId, recipeId)
    is RecipeEditDestination -> RecipeRoute(userId, cookbookId, recipeId)
    is IngredientReviewDestination -> RecipeRoute(userId, cookbookId, recipeId)
    else -> null
}

@Composable
private fun RecoveryPanel(onRetry: () -> Unit, onBack: () -> Unit, testTag: String? = null) {
    Column(
        Modifier.fillMaxSize().then(if (testTag == null) Modifier else Modifier.testTag(testTag)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.recipe_load_error), color = MainCourseColors.Body)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        }
    }
}

@Composable
private fun RouteLoading(testTag: String) {
    Box(Modifier.fillMaxSize().testTag(testTag)) {
        CircularProgressIndicator(Modifier.align(Alignment.Center))
    }
}

@Composable
private fun EmptyRouteState(@StringRes message: Int, testTag: String) {
    Column(
        Modifier.fillMaxSize().testTag(testTag).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { Text(stringResource(message), color = MainCourseColors.Body) }
}

@Composable
private fun RecipeRouteState(
    state: SessionState,
    destination: RecipeRoute?,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    val recipeId = destination?.recipeId ?: return
    val detailStatus = state.detail?.takeIf { it.recipeId == recipeId }?.status
    val status = when {
        detailStatus != null -> detailStatus
        state.recipesFetched && state.recipes.none { it.id == recipeId } -> DetailStatus.UNAVAILABLE
        state.recipeStatus.isFailure() || (state.activeCookbookId == null && state.catalogStatus.isFailure()) -> DetailStatus.ERROR
        else -> null
    }
    when (status) {
        null, DetailStatus.LOADING -> RouteLoading("recipe_route_loading")
        DetailStatus.UNAVAILABLE -> Column(
            Modifier.fillMaxSize().testTag("recipe_route_unavailable").padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.recipe_unavailable), color = MainCourseColors.Body)
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        }
        DetailStatus.NOT_READY -> EmptyRouteState(R.string.recipe_processing, "recipe_route_not_ready")
        DetailStatus.ERROR -> RecoveryPanel(onRetry, onBack, "recipe_route_error")
        DetailStatus.FRESH, DetailStatus.SAVED_OFFLINE -> RouteLoading("recipe_route_loading")
    }
}

@Composable
private fun StartupScreen(@StringRes message: Int) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
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
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
        Button(onClick = onRetry, modifier = Modifier.padding(top = 20.dp)) { Text(stringResource(retryLabel)) }
        OutlinedButton(onClick = onReset) { Text(stringResource(R.string.clear_local_data)) }
    }
}

private fun LoadStatus.isFailure(): Boolean = this == LoadStatus.DEGRADED || this == LoadStatus.ERROR
