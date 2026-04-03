package com.marcelo.protocol.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.marcelo.protocol.ProtocolApp
import com.marcelo.protocol.ui.plan.PlanScreen
import com.marcelo.protocol.ui.plan.PlanViewModel
import com.marcelo.protocol.ui.today.TodayScreen
import com.marcelo.protocol.ui.today.TodayViewModel
import com.marcelo.protocol.ui.week.WeekScreen
import com.marcelo.protocol.ui.week.WeekViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable data object TodayRoute
@Serializable data object PlanRoute
@Serializable data object WeekRoute

data class TopLevelRoute(
    val label: String,
    val route: Any,
    val icon: ImageVector,
)

val TOP_LEVEL_ROUTES = listOf(
    TopLevelRoute("Today", TodayRoute, Icons.Default.CheckCircle),
    TopLevelRoute("Plan", PlanRoute, Icons.Default.CalendarMonth),
    TopLevelRoute("Week", WeekRoute, Icons.Default.DateRange),
)

private val SELECTED_TAB_KEY = intPreferencesKey("selected_tab")

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val todayVm: TodayViewModel = viewModel()
    val planVm: PlanViewModel = viewModel()
    val weekVm: WeekViewModel = viewModel()

    val app = LocalContext.current.applicationContext as ProtocolApp
    val scope = rememberCoroutineScope()
    val savedIndex by app.dataStore.data
        .map { it[SELECTED_TAB_KEY] ?: 0 }
        .collectAsState(initial = 0)

    // Restore tab on launch.
    LaunchedEffect(savedIndex) {
        if (savedIndex != 0) {
            navController.navigate(TOP_LEVEL_ROUTES[savedIndex].route) {
                popUpTo(navController.graph.startDestinationId) { saveState = true }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDest = navBackStackEntry?.destination

            NavigationBar {
                TOP_LEVEL_ROUTES.forEachIndexed { index, route ->
                    NavigationBarItem(
                        selected = currentDest?.hasRoute(route.route::class) == true,
                        onClick = {
                            scope.launch {
                                app.dataStore.edit { it[SELECTED_TAB_KEY] = index }
                            }
                            navController.navigate(route.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(route.icon, contentDescription = route.label) },
                        label = { Text(route.label) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TodayRoute,
            modifier = Modifier.padding(padding),
        ) {
            composable<TodayRoute> { TodayScreen(todayVm) }
            composable<PlanRoute> { PlanScreen(planVm) }
            composable<WeekRoute> { WeekScreen(weekVm) }
        }
    }
}
