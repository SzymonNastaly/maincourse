package com.getmaincourse.app

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import com.getmaincourse.app.features.designsystem.DesignSystemScreen
import com.getmaincourse.app.features.preview.PreviewScreen
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

private val topLevelDestinations = listOf(
    Destination.Recipes, Destination.Shopping, Destination.Search, Destination.Settings,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainCourseApp() {
    val backStack = rememberNavBackStack(Destination.Recipes)
    val current = backStack.last() as Destination
    val selected = backStack.last { it in topLevelDestinations }
    val selectDestination: (Destination) -> Unit = { destination ->
        // Keep Recipes as the start destination for system Back from another tab.
        backStack.clear()
        backStack.add(Destination.Recipes)
        if (destination != Destination.Recipes) backStack.add(destination)
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
                        title = { Text(stringResource(current.title)) },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MainCourseColors.Canvas),
                        navigationIcon = {
                            if (current == Destination.DesignSystem) {
                                IconButton(onClick = { backStack.removeLastOrNull() }) {
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
                    onBack = { backStack.removeLastOrNull() },
                    entryProvider = entryProvider {
                        entry<Destination> { destination ->
                            if (destination == Destination.DesignSystem) {
                                DesignSystemScreen()
                            } else {
                                PreviewScreen(
                                    destination = destination,
                                    onOpenDesignSystem = { backStack.add(Destination.DesignSystem) },
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}
