package com.homedesign.android.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.homedesign.android.domain.settings.SettingsRepository
import com.homedesign.android.presentation.auth.AuthScreen
import com.homedesign.android.presentation.dashboard.DashboardScreen
import com.homedesign.android.presentation.editor.EditorScreen
import com.homedesign.android.presentation.landing.LandingScreen
import com.homedesign.android.presentation.onboarding.OnboardingScreen
import com.homedesign.android.presentation.setup.SetupScreen
import com.homedesign.android.presentation.sketch.SketchFlowScreen
import com.homedesign.android.presentation.splash.SplashScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PendingSketchResumeViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    suspend fun hasPending(): Boolean = settingsRepository.getPendingSketchJob() != null
}

@Composable
fun HomeNavHost(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val resumeVm: PendingSketchResumeViewModel = hiltViewModel()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    var resumedOnce by remember { mutableStateOf(false) }

    // Cold-start / dashboard resume: if a pending sketch job exists and we're not already
    // on /sketch or in the editor, navigate to sketch (poll-only resume).
    LaunchedEffect(route) {
        if (resumedOnce) return@LaunchedEffect
        if (route != Routes.Dashboard) return@LaunchedEffect
        if (resumeVm.hasPending()) {
            resumedOnce = true
            navController.navigate(Routes.Sketch)
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.Splash,
        modifier = modifier,
    ) {
        composable(Routes.Splash) {
            SplashScreen(
                onFinished = { hasOnboarded ->
                    val dest = if (hasOnboarded) Routes.Dashboard else Routes.Landing
                    navController.navigate(dest) {
                        popUpTo(Routes.Splash) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.Landing) {
            LandingScreen(
                onGetStarted = { navController.navigate(Routes.Onboarding) },
                onSignIn = { navController.navigate(Routes.Auth) },
            )
        }
        composable(Routes.Onboarding) {
            OnboardingScreen(
                onFinished = { navController.navigate(Routes.Setup) },
                onSkip = { navController.navigate(Routes.Setup) },
            )
        }
        composable(Routes.Setup) {
            SetupScreen(
                onBack = { navController.popBackStack() },
                onContinue = { navController.navigate(Routes.Auth) },
            )
        }
        composable(Routes.Auth) {
            AuthScreen(
                onBack = {
                    if (!navController.popBackStack()) {
                        navController.navigate(Routes.Landing) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    }
                },
                onDone = {
                    navController.navigate(Routes.Dashboard) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.Dashboard) {
            DashboardScreen(
                onOpenProject = { id -> navController.navigate(Routes.editor(id)) },
                onOpenSketch = { navController.navigate(Routes.Sketch) },
            )
        }
        composable(
            route = Routes.Editor,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            EditorScreen(
                projectId = id,
                onBack = { navController.popBackStack() },
                onOpenSketch = { navController.navigate(Routes.Sketch) },
                onOpenProject = { openedId ->
                    navController.navigate(Routes.editor(openedId)) {
                        popUpTo(Routes.Dashboard)
                    }
                },
            )
        }
        composable(Routes.Sketch) {
            SketchFlowScreen(
                onClose = { navController.popBackStack() },
                onOpened = { projectId ->
                    navController.navigate(Routes.editor(projectId)) {
                        popUpTo(Routes.Sketch) { inclusive = true }
                    }
                },
            )
        }
    }
}
