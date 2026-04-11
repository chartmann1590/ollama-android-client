package com.charles.ollama.client.ui.navigation

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.CircularProgressIndicator
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.charles.ollama.client.ads.InterstitialAdManager
import com.charles.ollama.client.ui.chat.ChatScreen
import com.charles.ollama.client.ui.chat.ChatThreadsScreen
import com.charles.ollama.client.ui.models.ModelsScreen
import com.charles.ollama.client.ui.servers.ServerListScreen
import com.charles.ollama.client.ui.servers.ServersViewModel
import com.charles.ollama.client.ui.settings.SettingsScreen
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay

@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    
    // Get InterstitialAdManager from Hilt
    val interstitialAdManager = remember {
        if (activity != null) {
            val hiltEntryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                InterstitialAdManagerEntryPoint::class.java
            )
            hiltEntryPoint.interstitialAdManager()
        } else {
            null
        }
    }
    
    val serversViewModel: ServersViewModel = hiltViewModel()
    val defaultServer by serversViewModel.defaultServer.collectAsState()
    
    // Compute the start destination based on defaultServer
    // IMPORTANT: Only compute this when we're ready to create NavHost
    // Don't use remember(defaultServer) because it recomputes, but NavHost's startDestination is only used at creation
    // We'll compute it directly when creating NavHost, using the current value of defaultServer at that moment
    
    // Track if we've waited for defaultServer to load
    // Don't show NavHost until defaultServer has been determined (either it's non-null or we've waited long enough)
    var hasWaitedForDefaultServer by remember { mutableStateOf(false) }
    var hasSeenDefaultServerChange by remember { mutableStateOf(false) }
    
    // Track when defaultServer changes from initial state
    LaunchedEffect(defaultServer) {
        hasSeenDefaultServerChange = true
        
        // If defaultServer is now non-null, we can proceed immediately
        if (defaultServer != null && !hasWaitedForDefaultServer) {
            delay(50) // Small delay to ensure state is stable
            hasWaitedForDefaultServer = true
        }
    }
    
    // Wait for defaultServer to be determined (either it changes, or we wait long enough)
    LaunchedEffect(Unit) {
        delay(400) // Give database time to load (increased from 300ms)
        // Only mark as ready if we haven't already (i.e., defaultServer is still null after waiting)
        if (!hasWaitedForDefaultServer) {
            hasWaitedForDefaultServer = true
        }
    }
    
    // Track previous route to detect navigation
    var previousRoute by remember { mutableStateOf<String?>(null) }
    
    // Track if we've done the initial navigation based on default server
    var hasInitializedNavigation by remember { mutableStateOf(false) }
    
    // Show ad on navigation (randomly)
    LaunchedEffect(navController.currentBackStackEntry?.destination?.route) {
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        
        if (currentRoute != null && currentRoute != previousRoute && previousRoute != null && activity != null && interstitialAdManager != null) {
            // Navigation occurred, try to show ad
            interstitialAdManager.showAdIfAvailable(activity)
        }
        previousRoute = currentRoute
    }
    
    // Navigate to the correct screen based on whether a default server exists
    // CRITICAL: Only run this AFTER NavHost is created (hasWaitedForDefaultServer is true)
    // This prevents crashes from accessing navController.graph before it's set
    LaunchedEffect(defaultServer, hasWaitedForDefaultServer) {
        // Only navigate if NavHost has been created (graph is set) and we haven't initialized yet
        if (!hasInitializedNavigation && hasWaitedForDefaultServer) {
            val currentRoute = navController.currentBackStackEntry?.destination?.route
            
            if (defaultServer != null) {
                // If default server exists but we started on Servers (because defaultServer was null initially),
                // navigate to ChatThreads
                
                // Only navigate if we're still on Servers (start destination was Servers because defaultServer was null)
                if (currentRoute == Screen.Servers.route || currentRoute == null) {
                    // Now it's safe to access navController.graph because NavHost has been created
                    navController.navigate(Screen.ChatThreads.route) {
                        // Clear the back stack and set ChatThreads as the new root
                        popUpTo(navController.graph.startDestinationId) {
                            inclusive = true
                        }
                    }
                }
                // Mark as initialized after handling defaultServer=true case
                hasInitializedNavigation = true
            } else {
                // If defaultServer is null, mark as initialized
                hasInitializedNavigation = true
            }
        }
    }
    
    // Only show NavHost after we've waited for defaultServer to load
    // Compute startDestination at this moment using the current defaultServer value
    if (hasWaitedForDefaultServer) {
        // Compute startDestination based on current defaultServer value (not remembered)
        val startDestination = if (defaultServer != null) {
            Screen.ChatThreads.route
        } else {
            Screen.Servers.route
        }
        
        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
        composable(Screen.ChatThreads.route) {
            ChatThreadsScreen(
                onNavigateToChat = { threadId ->
                    navController.navigate(Screen.Chat.createRoute(threadId))
                },
                onNavigateToServers = {
                    navController.navigate(Screen.Servers.route)
                },
                onNavigateToModels = {
                    navController.navigate(Screen.Models.route)
                }
            )
        }
        
        composable(
            route = Screen.Chat.route,
            arguments = listOf(navArgument("threadId") { type = NavType.LongType })
        ) { backStackEntry ->
            val threadId = backStackEntry.arguments?.getLong("threadId") ?: 0L
            ChatScreen(
                threadId = threadId,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.Models.route) {
            ModelsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.Servers.route) {
            ServerListScreen(
                onNavigateToModels = {
                    navController.navigate(Screen.Models.route)
                },
                onNavigateBack = {
                    if (defaultServer != null) {
                        val popped = navController.popBackStack(Screen.ChatThreads.route, inclusive = false)
                        if (!popped) {
                            navController.navigate(Screen.ChatThreads.route) {
                                launchSingleTop = true
                            }
                        }
                    } else {
                        navController.popBackStack()
                    }
                }
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
    } else {
        // Show loading indicator while waiting for defaultServer to load
        // This prevents the flash of Servers screen
        Box(
            modifier = androidx.compose.ui.Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

