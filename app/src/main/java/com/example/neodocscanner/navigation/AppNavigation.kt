package com.example.neodocscanner.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.neodocscanner.feature.auth.presentation.AuthViewModel
import com.example.neodocscanner.feature.auth.presentation.LoginScreen
import com.example.neodocscanner.feature.auth.presentation.SplashScreen
import com.example.neodocscanner.feature.hub.presentation.ApplicationHubScreen
import com.example.neodocscanner.feature.hub.presentation.ApplicationLinkScreen
import com.example.neodocscanner.feature.profile.presentation.ProfileScreen
import com.example.neodocscanner.feature.settings.presentation.FieldExtractorDebugScreen
import com.example.neodocscanner.feature.settings.presentation.SettingsScreen
import com.example.neodocscanner.feature.vault.presentation.DocuVaultScreen
import com.example.neodocscanner.feature.vault.presentation.pdf.PdfViewerScreen
import com.example.neodocscanner.feature.vault.presentation.viewer.DocumentViewerScreen

/**
 * Root navigation graph for the entire app.
 *
 * iOS equivalent: ContentView.swift — the single root view that conditionally
 * shows SplashScreenView, LoginView, or ApplicationHubView based on isLoggedIn.
 *
 * Navigation strategy:
 * 1. Start on Splash. It reads DataStore via AuthViewModel and routes to
 *    Login or Hub WITHOUT adding itself to the back-stack.
 * 2. Successful login navigates to Hub and clears the Login route so Back
 *    from Hub exits the app rather than returning to Login.
 * 3. Logout pops to Login and clears Hub from the back-stack.
 *
 * [authViewModel] is hoisted to this level so both SplashScreen and
 * LoginScreen share the same ViewModel instance (single DataStore subscription).
 */
@Composable
fun AppNavigation(initialQrPayload: String? = null) {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = hiltViewModel()

    NavHost(
        navController    = navController,
        startDestination = Screen.Splash.route
    ) {

        // ── Splash ────────────────────────────────────────────────────────────
        composable(route = Screen.Splash.route) {
            SplashScreen(
                viewModel         = authViewModel,
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToHub   = {
                    navController.navigate(Screen.Hub.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Login ─────────────────────────────────────────────────────────────
        composable(route = Screen.Login.route) {
            LoginScreen(
                viewModel      = authViewModel,
                onLoginSuccess = {
                    navController.navigate(Screen.Hub.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Hub ───────────────────────────────────────────────────────────────
        composable(route = Screen.Hub.route) {
            LaunchedEffect(initialQrPayload) {
                if (!initialQrPayload.isNullOrBlank()) {
                    navController.navigate("${Screen.ApplicationLink.route}?payload=${android.net.Uri.encode(initialQrPayload)}")
                }
            }
            ApplicationHubScreen(
                authViewModel      = authViewModel,
                onNavigateToVault  = { instanceId ->
                    navController.navigate(Screen.Vault.createRoute(instanceId))
                },
                onNavigateToProfile  = { navController.navigate(Screen.Profile.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToApplicationLink = { navController.navigate(Screen.ApplicationLink.route) },
                onLogout           = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Hub.route) { inclusive = true }
                    }
                }
            )
        }

        // ── Profile ───────────────────────────────────────────────────────────
        composable(route = Screen.Profile.route) {
            ProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                onLogout       = {
                    // Clear the entire back-stack so Back from Login exits the app,
                    // not re-enters Profile or Hub.
                    navController.navigate(Screen.Login.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        // ── Settings ──────────────────────────────────────────────────────────
        composable(route = Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onOpenFieldExtractorDebug = {
                    navController.navigate(Screen.FieldExtractorDebug.route)
                }
            )
        }

        composable(route = Screen.FieldExtractorDebug.route) {
            FieldExtractorDebugScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(route = Screen.ApplicationLink.route) {
            ApplicationLinkScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(
            route = "${Screen.ApplicationLink.route}?payload={payload}",
            arguments = listOf(
                navArgument("payload") { type = NavType.StringType; nullable = true }
            )
        ) { backStackEntry ->
            val payload = backStackEntry.arguments?.getString("payload")
            ApplicationLinkScreen(
                onNavigateBack = { navController.popBackStack() },
                initialPayload = payload
            )
        }

        // ── Vault ─────────────────────────────────────────────────────────────
        composable(
            route     = Screen.Vault.route,
            arguments = listOf(
                navArgument(Screen.Vault.ARG_INSTANCE_ID) { type = NavType.StringType }
            )
        ) {
            DocuVaultScreen(
                onNavigateBack  = { navController.popBackStack() },
                onOpenDocument  = { documentId ->
                    navController.navigate(Screen.DocumentViewer.createRoute(documentId))
                },
                onOpenPdfViewer = { documentId ->
                    navController.navigate(Screen.PdfViewer.createRoute(documentId))
                }
            )
        }

        // ── Document Viewer ───────────────────────────────────────────────────
        composable(
            route     = Screen.DocumentViewer.route,
            arguments = listOf(
                navArgument(Screen.DocumentViewer.ARG_DOCUMENT_ID) { type = NavType.StringType }
            )
        ) {
            DocumentViewerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ── PDF Viewer ────────────────────────────────────────────────────────
        composable(
            route     = Screen.PdfViewer.route,
            arguments = listOf(
                navArgument(Screen.PdfViewer.ARG_DOCUMENT_ID) { type = NavType.StringType }
            )
        ) {
            PdfViewerScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
