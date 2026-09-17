package com.example.focuslock.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.focuslock.R
import com.example.focuslock.feature.applications.ApplicationsRoute
import com.example.focuslock.feature.dashboard.DashboardRoute
import com.example.focuslock.feature.developer.DeveloperEntry
import com.example.focuslock.feature.deviceowner.DeviceSetupRoute
import com.example.focuslock.feature.history.HistoryRoute
import com.example.focuslock.feature.onboarding.OnboardingRoute
import com.example.focuslock.feature.schedule.ScheduleEditorRoute
import com.example.focuslock.feature.schedule.ScheduleListRoute
import com.example.focuslock.feature.settings.SettingsRoute

object Routes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val SCHEDULES = "schedules"
    const val ARG_SCHEDULE_ID = "id"
    const val SCHEDULE_EDITOR = "schedule_editor?$ARG_SCHEDULE_ID={$ARG_SCHEDULE_ID}"
    const val APPLICATIONS = "applications"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val DEVICE_SETUP = "device_setup"
    const val DEVELOPER = "developer"

    fun scheduleEditor(id: String? = null): String =
        if (id == null) "schedule_editor" else "schedule_editor?$ARG_SCHEDULE_ID=$id"
}

private enum class TopLevel(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    DASHBOARD(Routes.DASHBOARD, R.string.nav_dashboard, Icons.Filled.Home),
    SCHEDULES(Routes.SCHEDULES, R.string.nav_schedules, Icons.Filled.DateRange),
    HISTORY(Routes.HISTORY, R.string.nav_history, Icons.AutoMirrored.Filled.List),
    SETTINGS(Routes.SETTINGS, R.string.nav_settings, Icons.Filled.Settings),
}

@Composable
fun FocusNavHost(
    startDestination: String,
    navController: NavHostController = rememberNavController(),
) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = TopLevel.entries.any { it.route == currentRoute }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevel.entries.forEach { item ->
                        NavigationBarItem(
                            selected = currentRoute == item.route,
                            onClick = { navController.navigateTopLevel(item.route) },
                            icon = { Icon(item.icon, contentDescription = null) },
                            label = { Text(stringResource(item.label)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingRoute(
                    onFinished = { openSetup ->
                        navController.navigate(Routes.DASHBOARD) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                        if (openSetup) navController.navigate(Routes.DEVICE_SETUP)
                    },
                )
            }
            composable(Routes.DASHBOARD) {
                DashboardRoute(
                    onCreateSchedule = { navController.navigate(Routes.scheduleEditor()) },
                    onOpenSchedules = { navController.navigateTopLevel(Routes.SCHEDULES) },
                    onOpenDeviceSetup = { navController.navigate(Routes.DEVICE_SETUP) },
                )
            }
            composable(Routes.SCHEDULES) {
                ScheduleListRoute(
                    onCreate = { navController.navigate(Routes.scheduleEditor()) },
                    onEdit = { id -> navController.navigate(Routes.scheduleEditor(id)) },
                )
            }
            composable(
                Routes.SCHEDULE_EDITOR,
                arguments = listOf(
                    navArgument(Routes.ARG_SCHEDULE_ID) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) {
                ScheduleEditorRoute(
                    onBack = navController::popBackStack,
                    onConfigureApps = { navController.navigate(Routes.APPLICATIONS) },
                )
            }
            composable(Routes.APPLICATIONS) { ApplicationsRoute(onBack = navController::popBackStack) }
            composable(Routes.HISTORY) { HistoryRoute() }
            composable(Routes.SETTINGS) {
                SettingsRoute(
                    onOpenApplications = { navController.navigate(Routes.APPLICATIONS) },
                    onOpenDeviceSetup = { navController.navigate(Routes.DEVICE_SETUP) },
                    onOpenDeveloper = if (DeveloperEntry.AVAILABLE) {
                        { navController.navigate(Routes.DEVELOPER) }
                    } else {
                        null
                    },
                )
            }
            composable(Routes.DEVICE_SETUP) { DeviceSetupRoute(onBack = navController::popBackStack) }
            if (DeveloperEntry.AVAILABLE) {
                composable(Routes.DEVELOPER) { DeveloperEntry.Screen(onBack = { navController.popBackStack() }) }
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
