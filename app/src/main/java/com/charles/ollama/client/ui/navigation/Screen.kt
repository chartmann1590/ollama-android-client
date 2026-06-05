package com.charles.ollama.client.ui.navigation

sealed class Screen(val route: String) {
    object ChatThreads : Screen("chat_threads")
    object Chat : Screen("chat/{threadId}") {
        fun createRoute(threadId: Long) = "chat/$threadId"
    }
    object Models : Screen("models")
    object ModelDetail : Screen("model_detail/{modelName}") {
        fun createRoute(modelName: String): String {
            val encoded = java.net.URLEncoder.encode(modelName, "UTF-8")
            return "model_detail/$encoded"
        }
    }
    object Servers : Screen("servers")
    object Settings : Screen("settings")
    object Search : Screen("search")
    object Rewards : Screen("rewards")
    object About : Screen("about")
    object Paywall : Screen("paywall")
    object PromptLibrary : Screen("prompt_library/{threadId}") {
        fun createRoute(threadId: Long) = "prompt_library/$threadId"
    }
}

