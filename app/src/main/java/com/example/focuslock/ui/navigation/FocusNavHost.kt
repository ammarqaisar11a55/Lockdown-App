package com.example.focuslock.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
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
import com.example.focuslock.ui.components.LucideIcons
import com.example.focuslock.ui.theme.LockdownType
import com.example.focuslock.ui.theme.Tokens

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
    DASHBOARD(Routes.DASHBOARD, R.string.nav_dashboard, LucideIcons.Home),
    SCHEDULES(Routes.SCHEDULES, R.string.nav_schedules, LucideIcons.Calendar),
    HISTORY(Routes.HISTORY, R.string.nav_history, LucideIcons.List),
    SETTINGS(Routes.SETTINGS, R.string.nav_settings, LucideIcons.Sliders),
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
        containerColor = Tokens.bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                BottomBar(currentRoute) { navController.navigateTopLevel(it) }
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

@Composable
private fun BottomBar(currentRoute: String?, onSelect: (String) -> Unit) {
    val colors = Tokens
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .navigationBarsPadding(),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
        Row(Modifier.fillMaxWidth().selectableGroup()) {
            TopLevel.entries.forEach { item ->
                val selected = currentRoute == item.route
                val tint by animateColorAsState(if (selected) colors.accent else colors.faint, label = "navTint")
                val indicator by animateFloatAsState(if (selected) 1f else 0f, label = "navIndicator")
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = NAV_HEIGHT)
                        .selectable(selected = selected, role = Role.Tab) { onSelect(item.route) }
                        .drawBehind {
                            drawRect(colors.accentSolid.copy(alpha = indicator), size = size.copy(height = 2.dp.toPx()))
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
                ) {
                    Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(21.dp))
                    Text(
                        stringResource(item.label).uppercase(),
                        style = LockdownType.sectionLabel.copy(letterSpacing = 0.1.em),
                        color = tint,
                    )
                }
            }
        }
    }
}

private val NAV_HEIGHT = 62.dp

private fun NavHostController.navigateTopLevel(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
