package com.pitchforge.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pitchforge.app.ui.challenge.ChallengeScreen
import com.pitchforge.app.ui.checkup.CheckupScreen
import com.pitchforge.app.ui.dashboard.DashboardScreen
import com.pitchforge.app.ui.lesson.LessonScreen
import com.pitchforge.app.ui.onboarding.OnboardingScreen
import com.pitchforge.app.ui.probe.GeneralizationScreen
import com.pitchforge.app.ui.probe.RetentionScreen
import com.pitchforge.app.ui.settings.SettingsScreen
import com.pitchforge.app.ui.stats.StatsScreen
import kotlinx.coroutines.launch

object Routes {
    const val ONBOARDING = "onboarding"
    const val LESSON = "lesson"
    const val DASHBOARD = "dashboard"
    const val SETTINGS = "settings"
    const val STATS = "stats"
    const val CHALLENGE = "challenge"
    const val CHECKUP = "checkup"
    const val GENERALIZATION = "generalization"
    const val RETENTION = "retention"
}

@Composable
fun PitchForgeNavHost(rootViewModel: RootViewModel = hiltViewModel()) {
    val onboarded by rootViewModel.onboarded.collectAsState()

    when (onboarded) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else -> {
            val navController = rememberNavController()
            val scope = rememberCoroutineScope()
            val start = if (onboarded == true) Routes.DASHBOARD else Routes.ONBOARDING

            fun navigateMainTab(route: String) {
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }

            fun finishOnboarding() {
                scope.launch {
                    rootViewModel.refreshOnboardedNow()
                }
            }

            NavHost(navController = navController, startDestination = start) {
                composable(Routes.ONBOARDING) {
                    OnboardingScreen(onComplete = ::finishOnboarding)
                }
                composable(Routes.LESSON) {
                    LessonScreen(
                        onNavigateToDashboard = {
                            navController.navigate(Routes.DASHBOARD) {
                                popUpTo(Routes.DASHBOARD) { inclusive = true }
                            }
                        }
                    )
                }
                composable(Routes.DASHBOARD) {
                    DashboardScreen(
                        onStartLesson = { navController.navigate(Routes.LESSON) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onOpenStats = { navigateMainTab(Routes.STATS) },
                        onOpenChallenge = { navigateMainTab(Routes.CHALLENGE) },
                        onOpenCheckup = { navController.navigate(Routes.CHECKUP) },
                        onOpenGeneralization = { navController.navigate(Routes.GENERALIZATION) },
                        onOpenRetention = { navController.navigate(Routes.RETENTION) }
                    )
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.STATS) {
                    StatsScreen(
                        onOpenHome = { navigateMainTab(Routes.DASHBOARD) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                        onOpenChallenges = { navigateMainTab(Routes.CHALLENGE) }
                    )
                }
                composable(Routes.CHALLENGE) {
                    ChallengeScreen(
                        onOpenHome = { navigateMainTab(Routes.DASHBOARD) },
                        onOpenStats = { navigateMainTab(Routes.STATS) },
                        onOpenSettings = { navController.navigate(Routes.SETTINGS) }
                    )
                }
                composable(Routes.CHECKUP) {
                    CheckupScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.GENERALIZATION) {
                    GeneralizationScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.RETENTION) {
                    RetentionScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}
