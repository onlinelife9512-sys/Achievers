package com.oble.ideacapture

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.oble.ideacapture.ui.home.HomeScreen
import com.oble.ideacapture.ui.home.HomeViewModel
import com.oble.ideacapture.ui.ideas.IdeasScreen
import com.oble.ideacapture.ui.ideas.IdeasViewModel
import com.oble.ideacapture.ui.settings.ConversationsScreen
import com.oble.ideacapture.ui.settings.SettingsScreen
import com.oble.ideacapture.ui.settings.SettingsViewModel
import com.oble.ideacapture.ui.settings.TranscriptScreen
import com.oble.ideacapture.ui.theme.Oble
import com.oble.ideacapture.ui.theme.ObleTheme

class MainActivity : ComponentActivity() {
    private val pendingRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Oble.Black.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Oble.Black.toArgb()),
        )
        pendingRoute.value = intent?.getStringExtra(EXTRA_ROUTE)
        val container = (application as ObleApp).container
        setContent {
            ObleTheme { ObleRoot(container, pendingRoute.value) { pendingRoute.value = null } }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingRoute.value = intent.getStringExtra(EXTRA_ROUTE)
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Home", Icons.Outlined.Home),
    IDEAS("ideas", "Ideas", Icons.Outlined.Lightbulb),
    SETTINGS("settings", "Settings", Icons.Outlined.Tune),
}

private fun NavHostController.goTab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun ObleRoot(container: AppContainer, route: String?, onRouteConsumed: () -> Unit) {
    val nav = rememberNavController()
    LaunchedEffect(route) {
        if (route != null) {
            nav.goTab(route)
            onRouteConsumed()
        }
    }
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    Scaffold(
        containerColor = Oble.Black,
        bottomBar = {
            NavigationBar(containerColor = Oble.Black, tonalElevation = 0.dp) {
                Tab.entries.forEach { tab ->
                    val selected = current == tab.route ||
                        (tab == Tab.SETTINGS && (current?.startsWith("conversations") == true || current?.startsWith("transcript") == true))
                    NavigationBarItem(
                        selected = selected,
                        onClick = { nav.goTab(tab.route) },
                        icon = { Icon(tab.icon, tab.label) },
                        label = { Text(tab.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Oble.Green,
                            selectedTextColor = Oble.Green,
                            indicatorColor = Oble.GreenDeep,
                            unselectedIconColor = Oble.TextFaint,
                            unselectedTextColor = Oble.TextFaint,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Tab.HOME.route, modifier = Modifier.fillMaxSize().padding(padding)) {
            composable(Tab.HOME.route) {
                HomeScreen(
                    vm = viewModel { HomeViewModel(container) },
                    onViewIdeas = { nav.goTab(Tab.IDEAS.route) },
                    onOpenSettings = { nav.goTab(Tab.SETTINGS.route) },
                )
            }
            composable(Tab.IDEAS.route) { IdeasScreen(viewModel { IdeasViewModel(container) }) }
            composable(Tab.SETTINGS.route) {
                SettingsScreen(viewModel { SettingsViewModel(container) }, onOpenConversations = { nav.navigate("conversations") })
            }
            composable("conversations") {
                ConversationsScreen(
                    viewModel { SettingsViewModel(container) },
                    onBack = { nav.popBackStack() },
                    onOpen = { nav.navigate("transcript/$it") },
                )
            }
            composable("transcript/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                TranscriptScreen(
                    viewModel { SettingsViewModel(container) },
                    sessionId = entry.arguments?.getLong("id") ?: 0L,
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}
