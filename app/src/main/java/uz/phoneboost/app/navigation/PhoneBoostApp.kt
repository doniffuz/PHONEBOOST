package uz.phoneboost.app.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import uz.phoneboost.app.R
import uz.phoneboost.app.core.designsystem.PhoneBoostBottomBar
import uz.phoneboost.app.core.designsystem.PhoneBoostBackground
import uz.phoneboost.app.core.designsystem.PbNavItem
import androidx.compose.ui.graphics.Color
import uz.phoneboost.app.domain.AppTarget
import uz.phoneboost.app.features.admin.AdminScreen
import uz.phoneboost.app.features.smart.SmartOptimizeScreen
import uz.phoneboost.app.features.update.UpdateScreen
import uz.phoneboost.app.features.apps.AppsScreen
import uz.phoneboost.app.features.battery.BatteryScreen
import uz.phoneboost.app.features.device.DeviceScreen
import uz.phoneboost.app.features.diagnostics.DiagnosticsScreen
import uz.phoneboost.app.features.game.GameScreen
import uz.phoneboost.app.features.home.FeatureKey
import uz.phoneboost.app.features.home.HomeScreen
import uz.phoneboost.app.features.home.HomeViewModel
import uz.phoneboost.app.features.privacy.PrivacyScreen
import uz.phoneboost.app.features.automation.AutomationScreen
import uz.phoneboost.app.features.history.HistoryScreen
import uz.phoneboost.app.features.report.ReportScreen
import uz.phoneboost.app.features.settings.SettingsScreen
import uz.phoneboost.app.features.settings.SettingsViewModel
import uz.phoneboost.app.features.speed.SpeedScreen
import uz.phoneboost.app.features.storage.StorageScreen
import uz.phoneboost.app.features.support.SupportScreen

private object Route {
    const val BATTERY = "battery"
    const val SPEED = "speed"
    const val GAME = "game"
    const val STORAGE = "storage"
    const val DIAGNOSTICS = "diagnostics"
    const val DEVICE = "device"
    const val PRIVACY = "privacy"
    const val APPS = "apps"
    const val HISTORY = "history"
    const val AUTOMATION = "automation"
    const val SMART = "smart"
    const val UPDATE = "update"
    const val ADMIN = "admin"

    /** Bosh sahifadan ochiladigan ichki sahifalar: pastki navigatsiyada “Bosh sahifa” tanlangan ko‘rinadi. */
    val homeChildren = setOf(SMART, BATTERY, SPEED, GAME, STORAGE, DIAGNOSTICS, DEVICE, PRIVACY, APPS)
}

enum class Tab(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    HOME("home", R.string.nav_home, Icons.Filled.Home),
    REPORT("report", R.string.nav_report, Icons.Filled.Info),
    SUPPORT("support", R.string.nav_support, Icons.Filled.Favorite),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings)
}

private fun routeFor(key: FeatureKey): String = when (key) {
    FeatureKey.SMART -> Route.SMART
    FeatureKey.SPEED -> Route.SPEED
    FeatureKey.GAME -> Route.GAME
    FeatureKey.BATTERY -> Route.BATTERY
    FeatureKey.STORAGE -> Route.STORAGE
    FeatureKey.DIAGNOSTICS -> Route.DIAGNOSTICS
    FeatureKey.PHONE -> Route.DEVICE
    FeatureKey.PRIVACY -> Route.PRIVACY
    FeatureKey.APPS -> Route.APPS
}

private fun NavHostController.openTarget(target: AppTarget) {
    when (target) {
        AppTarget.STORAGE -> navigate(Route.STORAGE)
        AppTarget.BATTERY -> navigate(Route.BATTERY)
        AppTarget.NONE -> Unit
    }
}

@Composable
fun PhoneBoostApp(settingsViewModel: SettingsViewModel, homeViewModel: HomeViewModel) {
    val navController = rememberNavController()

    PhoneBoostBackground {
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val destination = backStackEntry?.destination
            val items = Tab.entries.map { tab ->
                val selected = destination?.hierarchy?.any { it.route == tab.route } == true ||
                    (tab == Tab.HOME && destination?.route in Route.homeChildren) ||
                    (tab == Tab.REPORT && destination?.route == Route.HISTORY) ||
                    (tab == Tab.SETTINGS && (destination?.route == Route.AUTOMATION || destination?.route == Route.UPDATE || destination?.route == Route.ADMIN))
                PbNavItem(tab.route, stringResource(tab.label), tab.icon, selected)
            }
            PhoneBoostBottomBar(items) { route ->
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    ) { innerPadding ->
        val back: () -> Unit = { navController.popBackStack() }
        NavHost(
            navController = navController,
            startDestination = Tab.HOME.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Tab.HOME.route) {
                HomeScreen(
                    viewModel = homeViewModel,
                    onFeatureClick = { key -> navController.navigate(routeFor(key)) },
                    onOpenTarget = { target -> navController.openTarget(target) }
                )
            }
            composable(Route.SMART) { SmartOptimizeScreen(onBack = back, onOpenTarget = { navController.openTarget(it) }) }
            composable(Route.UPDATE) { UpdateScreen(onBack = back) }
            composable(Route.ADMIN) { AdminScreen(onBack = back) }
            composable(Route.BATTERY) { BatteryScreen(homeViewModel, onBack = back) }
            composable(Route.SPEED) { SpeedScreen(onBack = back) }
            composable(Route.GAME) {
                GameScreen(
                    homeViewModel,
                    onBack = back,
                    onOpenApps = { navController.navigate(Route.APPS) }
                )
            }
            composable(Route.STORAGE) { StorageScreen(homeViewModel, onBack = back) }
            composable(Route.DIAGNOSTICS) { DiagnosticsScreen(homeViewModel, onBack = back) }
            composable(Route.DEVICE) { DeviceScreen(homeViewModel, onBack = back) }
            composable(Route.PRIVACY) { PrivacyScreen(onBack = back) }
            composable(Route.APPS) { AppsScreen(onBack = back) }
            composable(Tab.REPORT.route) {
                ReportScreen(
                    homeViewModel,
                    onOpenTarget = { navController.openTarget(it) },
                    onOpenHistory = { navController.navigate(Route.HISTORY) }
                )
            }
            composable(Route.HISTORY) { HistoryScreen(onBack = back) }
            composable(Route.AUTOMATION) {
                AutomationScreen(onBack = back, onOpenHistory = { navController.navigate(Route.HISTORY) })
            }
            composable(Tab.SUPPORT.route) { SupportScreen() }
            composable(Tab.SETTINGS.route) {
                SettingsScreen(
                    settingsViewModel,
                    onOpenAutomation = { navController.navigate(Route.AUTOMATION) },
                    onOpenUpdate = { navController.navigate(Route.UPDATE) },
                    onOpenAdmin = { navController.navigate(Route.ADMIN) }
                )
            }
        }
    }
    }
}
