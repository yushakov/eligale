package gallery.eliza.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import gallery.eliza.app.data.Api
import gallery.eliza.app.data.TokenStorage
import gallery.eliza.app.ui.screens.CategoryScreen
import gallery.eliza.app.ui.screens.ChatListScreen
import gallery.eliza.app.ui.screens.ChatScreen
import gallery.eliza.app.ui.screens.ProductDetailScreen
import gallery.eliza.app.ui.screens.ProductListScreen
import gallery.eliza.app.ui.theme.ElizaGalleryTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val isReady = mutableStateOf(false)
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !isReady.value }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ElizaGalleryTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                var token by remember { mutableStateOf(TokenStorage.get(context)) }
                var isStaff by remember { mutableStateOf(false) }

                val onTokenChange: (String?) -> Unit = { newToken ->
                    if (newToken != null) {
                        TokenStorage.save(context, newToken)
                        // Проверяем is_staff после входа
                        scope.launch {
                            try {
                                val profile = Api.service.getProfile("Token $newToken")
                                isStaff = profile.is_staff
                            } catch (_: Exception) { }
                        }
                    } else {
                        TokenStorage.clear(context)
                        isStaff = false
                    }
                    token = newToken
                }

                // Загружаем is_staff при старте если токен есть
                val savedToken = token
                if (savedToken != null) {
                    scope.launch {
                        try {
                            val profile = Api.service.getProfile("Token $savedToken")
                            isStaff = profile.is_staff
                        } catch (_: Exception) { }
                    }
                }

                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "categories") {
                    composable("categories") {
                        CategoryScreen(
                            onCategoryClick = { id, name ->
                                navController.navigate("products/$id?name=${name}")
                            },
                            onReady = { isReady.value = true },
                            token = token,
                            onTokenChange = onTokenChange,
                            onChatClick = { navController.navigate("chat") },
                            onChatsClick = { navController.navigate("chats") },
                            isStaff = isStaff,
                        )
                    }
                    composable(
                        "products/{categoryId}?name={name}",
                        arguments = listOf(
                            navArgument("categoryId") { type = NavType.IntType },
                            navArgument("name") { defaultValue = "" }
                        )
                    ) { backStack ->
                        ProductListScreen(
                            categoryId = backStack.arguments!!.getInt("categoryId"),
                            categoryName = backStack.arguments!!.getString("name") ?: "",
                            onProductClick = { id -> navController.navigate("product/$id") },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        "product/{productId}",
                        arguments = listOf(navArgument("productId") { type = NavType.IntType })
                    ) { backStack ->
                        ProductDetailScreen(
                            productId = backStack.arguments!!.getInt("productId"),
                            onBack = { navController.popBackStack() },
                            token = token,
                            onTokenChange = onTokenChange,
                            isStaff = isStaff,
                            onOpenChat = { userId, userEmail ->
                                navController.navigate("chat_staff/$userId?email=$userEmail")
                            },
                        )
                    }
                    // Чат пользователя
                    composable("chat") {
                        val t = token ?: return@composable
                        ChatScreen(
                            token = t,
                            staffUserId = null,
                            chatTitle = "Чат с Елизаветой",
                            onBack = { navController.popBackStack() },
                        )
                    }
                    // Список чатов (staff)
                    composable("chats") {
                        val t = token ?: return@composable
                        ChatListScreen(
                            token = t,
                            onChatClick = { userId, userEmail ->
                                navController.navigate("chat_staff/$userId?email=$userEmail")
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    // Чат staff с конкретным пользователем
                    composable(
                        "chat_staff/{userId}?email={email}",
                        arguments = listOf(
                            navArgument("userId") { type = NavType.IntType },
                            navArgument("email") { defaultValue = "" },
                        )
                    ) { backStack ->
                        val t = token ?: return@composable
                        val userId = backStack.arguments!!.getInt("userId")
                        val email = backStack.arguments!!.getString("email") ?: ""
                        ChatScreen(
                            token = t,
                            staffUserId = userId,
                            chatTitle = email,
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
