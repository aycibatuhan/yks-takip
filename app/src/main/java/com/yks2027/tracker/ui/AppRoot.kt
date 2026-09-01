package com.yks2027.tracker.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.yks2027.tracker.core.ai.AiClient
import com.yks2027.tracker.feature.aikoc.AiKocScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.yks2027.tracker.feature.dashboard.DashboardScreen
import com.yks2027.tracker.feature.exams.ExamEntryScreen
import com.yks2027.tracker.feature.exams.ExamsScreen
import com.yks2027.tracker.feature.planner.PastWeekDetailScreen
import com.yks2027.tracker.feature.planner.PastWeeksScreen
import com.yks2027.tracker.feature.planner.PlannerScreen
import com.yks2027.tracker.feature.settings.SettingsScreen
import com.yks2027.tracker.feature.timer.TimerScreen
import kotlin.reflect.KClass
import kotlinx.serialization.Serializable

@Serializable data object DashboardRoute
@Serializable data object ExamsRoute
@Serializable data class ExamEntryRoute(val examId: Long = -1L, val prefill: Boolean = false)
@Serializable data object TopicsRoute
@Serializable data object PlannerRoute
@Serializable data object PastWeeksRoute
@Serializable data class PastWeekDetailRoute(val weekStart: Long)
@Serializable data object TimerRoute
@Serializable data object NotesRoute
@Serializable data object ImportHubRoute
@Serializable data object AiKocRoute
@Serializable data object SettingsRoute

@HiltViewModel
class AppRootViewModel @Inject constructor(aiClient: AiClient) : ViewModel() {
    /** PRD §8 — AI Koç appears as a destination only once a key/provider is configured. */
    val aiConfigured = aiClient.configured
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}

private enum class TopDest(
    val label: String,
    val icon: ImageVector,
    val route: Any,
    val routeClass: KClass<*>,
) {
    ANA_SAYFA("Ana Sayfa", Icons.Outlined.Home, DashboardRoute, DashboardRoute::class),
    DENEMELER("Denemeler", Icons.AutoMirrored.Outlined.List, ExamsRoute, ExamsRoute::class),
    KONULAR("Konular", Icons.Outlined.School, TopicsRoute, TopicsRoute::class),
    PLANLAYICI("Planlayıcı", Icons.Outlined.DateRange, PlannerRoute, PlannerRoute::class),
    SAYAC("Sayaç", Icons.Outlined.Timer, TimerRoute, TimerRoute::class),
    NOTLAR("Notlar", Icons.AutoMirrored.Outlined.Notes, NotesRoute, NotesRoute::class),
    AYARLAR("Ayarlar", Icons.Outlined.Settings, SettingsRoute, SettingsRoute::class),
}

/**
 * PRD §10.1 — NavigationSuiteScaffold renders a rail at Expanded width and a bottom
 * bar at Compact automatically. AI Koç joins as a conditional destination in M3.
 */
@Composable
fun AppRoot(rootViewModel: AppRootViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentDestination = backStack?.destination
    val aiConfigured by rootViewModel.aiConfigured.collectAsStateWithLifecycle()

    data class NavItem(val label: String, val icon: ImageVector, val route: Any, val routeClass: KClass<*>)

    val navItems = buildList {
        TopDest.entries.forEach { add(NavItem(it.label, it.icon, it.route, it.routeClass)) }
        if (aiConfigured) {
            // Before Ayarlar, wherever the static list ends up.
            add(size - 1, NavItem("AI Koç", Icons.Outlined.AutoAwesome, AiKocRoute, AiKocRoute::class))
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            navItems.forEach { dest ->
                val selected = currentDestination?.hierarchy?.any { it.hasRoute(dest.routeClass) } == true
                item(
                    selected = selected,
                    onClick = {
                        navController.navigate(dest.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    icon = { Icon(dest.icon, contentDescription = dest.label) },
                    label = { Text(dest.label) },
                )
            }
        },
    ) {
        NavHost(navController = navController, startDestination = DashboardRoute) {
            composable<DashboardRoute> {
                DashboardScreen(
                    onAddExam = { navController.navigate(ExamEntryRoute()) },
                    onStartTimer = { navController.navigate(TimerRoute) },
                )
            }
            composable<ExamsRoute> {
                ExamsScreen(
                    onAdd = { navController.navigate(ExamEntryRoute()) },
                    onEdit = { id -> navController.navigate(ExamEntryRoute(examId = id)) },
                    onOpenImportHub = { navController.navigate(ImportHubRoute) },
                )
            }
            composable<ExamEntryRoute> {
                ExamEntryScreen(onDone = { navController.popBackStack() })
            }
            composable<ImportHubRoute> {
                com.yks2027.tracker.feature.importexport.ImportHubScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPrefilledEntry = { navController.navigate(ExamEntryRoute(prefill = true)) },
                )
            }
            composable<NotesRoute> { com.yks2027.tracker.feature.notes.NotesScreen() }
            composable<TopicsRoute> { com.yks2027.tracker.feature.topics.TopicsScreen() }
            composable<PlannerRoute> {
                PlannerScreen(onOpenHistory = { navController.navigate(PastWeeksRoute) })
            }
            composable<PastWeeksRoute> {
                PastWeeksScreen(
                    onOpenWeek = { weekStart -> navController.navigate(PastWeekDetailRoute(weekStart)) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable<PastWeekDetailRoute> {
                PastWeekDetailScreen(onBack = { navController.popBackStack() })
            }
            composable<TimerRoute> { TimerScreen() }
            composable<AiKocRoute> { AiKocScreen() }
            composable<SettingsRoute> {
                SettingsScreen(onOpenImportHub = { navController.navigate(ImportHubRoute) })
            }
        }
    }
}
