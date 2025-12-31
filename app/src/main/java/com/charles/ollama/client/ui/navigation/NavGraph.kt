package com.charles.ollama.client.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.charles.ollama.client.ui.chat.ChatScreen
import com.charles.ollama.client.ui.chat.ChatThreadsScreen
import com.charles.ollama.client.ui.models.ModelsScreen
import com.charles.ollama.client.ui.servers.ServerListScreen
import com.charles.ollama.client.ui.servers.ServersViewModel
import com.charles.ollama.client.ui.settings.SettingsScreen

@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.ChatThreads.route
) {
    val serversViewModel: ServersViewModel = hiltViewModel()
    val defaultServer by serversViewModel.defaultServer.collectAsState()
    
    // Navigate to the correct screen based on whether a default server exists
    // If default server exists, start at ChatThreads; otherwise start at Servers
    LaunchedEffect(defaultServer) {
        val currentRoute = navController.currentBackStackEntry?.destination?.route
        // Only auto-navigate if we're at the root (back stack is empty or just one entry)
        val isAtRoot = navController.currentBackStack.value.size <= 1
        
        if (isAtRoot) {
            if (defaultServer != null) {
                // If default server exists, navigate to ChatThreads
                if (currentRoute != Screen.ChatThreads.route) {
                    navController.navigate(Screen.ChatThreads.route) {
                        // Clear the back stack and set ChatThreads as the new root
                        popUpTo(navController.graph.startDestinationId) {
                            inclusive = true
                        }
                    }
                }
            } else {
                // If no default server, navigate to Servers
                if (currentRoute != Screen.Servers.route) {
                    navController.navigate(Screen.Servers.route) {
                        // Clear the back stack and set Servers as the new root
                        popUpTo(navController.graph.startDestinationId) {
                            inclusive = true
                        }
                    }
                }
            }
        }
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
                onNavigateBack = {
                    navController.popBackStack()
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
}

