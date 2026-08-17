package com.medsreminder.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.medsreminder.ui.main.MainSideEffect
import com.medsreminder.ui.main.MainViewModel
import com.medsreminder.ui.screens.*

sealed class BottomNavTab(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Horarios : BottomNavTab("horarios", "Horarios", Icons.Filled.Alarm, Icons.Outlined.Alarm)
    data object Medicamentos : BottomNavTab("medicamentos", "Fármacos", Icons.Filled.Medication, Icons.Outlined.Medication)
    data object Perfiles : BottomNavTab("perfiles", "Perfiles", Icons.Filled.People, Icons.Outlined.People)
    data object Ajustes : BottomNavTab("ajustes", "Ajustes", Icons.Filled.Settings, Icons.Outlined.Settings)
}

val BottomNavTabs = listOf(
    BottomNavTab.Horarios,
    BottomNavTab.Medicamentos,
    BottomNavTab.Perfiles,
    BottomNavTab.Ajustes
)

@Composable
fun MedsAppScaffold(
    viewModel: MainViewModel,
    onRequestAlarmPermission: () -> Unit,
    navController: NavHostController = rememberNavController()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isTopLevelTab = BottomNavTabs.any { it.route == currentRoute }

    LaunchedEffect(viewModel.sideEffects) {
        viewModel.sideEffects.collect { effect ->
            when (effect) {
                is MainSideEffect.ShowSnackbar -> snackbarHostState.showSnackbar(effect.message)
                is MainSideEffect.RequestExactAlarmPermission -> onRequestAlarmPermission()
                is MainSideEffect.NavigateBack -> navController.navigateUp()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (isTopLevelTab) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 3.dp
                ) {
                    BottomNavTabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title
                                )
                            },
                            label = { Text(tab.title) }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = BottomNavTab.Horarios.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            composable(BottomNavTab.Horarios.route) {
                HorariosScreen(
                    state = state,
                    onIntent = viewModel::onIntent,
                    onNavigateToAddGroup = { groupId ->
                        val route = if (groupId != null) "add_edit_group?groupId=$groupId" else "add_edit_group"
                        navController.navigate(route)
                    },
                    onRequestAlarmPermission = onRequestAlarmPermission
                )
            }

            composable(BottomNavTab.Medicamentos.route) {
                MedicamentosScreen(
                    state = state,
                    onIntent = viewModel::onIntent
                )
            }

            composable(BottomNavTab.Perfiles.route) {
                PerfilesScreen(
                    state = state,
                    onIntent = viewModel::onIntent
                )
            }

            composable(BottomNavTab.Ajustes.route) {
                AjustesScreen(
                    state = state,
                    onIntent = viewModel::onIntent,
                    onRequestAlarmPermission = onRequestAlarmPermission
                )
            }

            composable(
                route = "add_edit_group?groupId={groupId}",
                arguments = listOf(
                    navArgument("groupId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val groupIdStr = backStackEntry.arguments?.getString("groupId")
                val groupId = groupIdStr?.toLongOrNull()
                AddEditGroupScreen(
                    groupId = groupId,
                    state = state,
                    onIntent = viewModel::onIntent,
                    onNavigateBack = { navController.navigateUp() }
                )
            }
        }
    }
}
