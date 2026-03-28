package gallery.eliza.app

import android.net.Uri
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
import gallery.eliza.app.ui.screens.CommentListScreen
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
                            onCommentsClick = { navController.navigate("comments") },
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
                        val catId = backStack.arguments!!.getInt("categoryId")
                        val catName = backStack.arguments!!.getString("name") ?: ""
                        ProductListScreen(
                            categoryId = catId,
                            categoryName = catName,
                            onProductClick = { id ->
                                navController.navigate("product/$id?categoryId=$catId&categoryName=${Uri.encode(catName)}")
                            },
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(
                        "product/{productId}?categoryId={categoryId}&categoryName={categoryName}&commentId={commentId}&imageIndex={imageIndex}",
                        arguments = listOf(
                            navArgument("productId") { type = NavType.IntType },
                            navArgument("categoryId") { type = NavType.IntType; defaultValue = -1 },
                            navArgument("categoryName") { defaultValue = "" },
                            navArgument("commentId") { type = NavType.IntType; defaultValue = -1 },
                            navArgument("imageIndex") { type = NavType.IntType; defaultValue = 0 },
                        )
                    ) { backStack ->
                        val categoryId = backStack.arguments!!.getInt("categoryId")
                        val categoryName = backStack.arguments!!.getString("categoryName") ?: ""
                        val commentId = backStack.arguments!!.getInt("commentId").takeIf { it != -1 }
                        val imageIndex = backStack.arguments!!.getInt("imageIndex")
                        ProductDetailScreen(
                            productId = backStack.arguments!!.getInt("productId"),
                            onBack = {
                                if (categoryId > 0) {
                                    navController.popBackStack()
                                } else {
                                    navController.popBackStack("categories", false)
                                }
                            },
                            token = token,
                            onTokenChange = onTokenChange,
                            isStaff = isStaff,
                            onOpenChat = { userId, userEmail ->
                                navController.navigate("chat_staff/$userId?email=$userEmail")
                            },
                            scrollToCommentId = commentId,
                            initialImagePage = imageIndex,
                            categoryName = categoryName,
                            onGoToChat = if (token != null && !isStaff) {
                                { navController.navigate("chat") }
                            } else null,
                            onGoToChats = if (isStaff) {
                                { pendingText ->
                                    navController.navigate("chats?pendingText=${Uri.encode(pendingText)}")
                                }
                            } else null,
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
                            onOpenProduct = { productId, imageIndex ->
                                navController.navigate("product/$productId?imageIndex=$imageIndex")
                            },
                        )
                    }
                    // Список комментариев (staff)
                    composable("comments") {
                        val t = token ?: return@composable
                        CommentListScreen(
                            token = t,
                            onOpenChat = { userId, userEmail ->
                                navController.navigate("chat_staff/$userId?email=$userEmail")
                            },
                            onOpenProduct = { productId, commentId ->
                                navController.navigate("product/$productId?commentId=$commentId")
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    // Список чатов (staff)
                    composable(
                        "chats?pendingText={pendingText}",
                        arguments = listOf(
                            navArgument("pendingText") { defaultValue = "" },
                        )
                    ) { backStack ->
                        val t = token ?: return@composable
                        val pendingText = backStack.arguments!!.getString("pendingText") ?: ""
                        ChatListScreen(
                            token = t,
                            onChatClick = { userId, userEmail ->
                                navController.navigate("chat_staff/$userId?email=$userEmail&initialText=${Uri.encode(pendingText)}")
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                    // Чат staff с конкретным пользователем
                    composable(
                        "chat_staff/{userId}?email={email}&initialText={initialText}",
                        arguments = listOf(
                            navArgument("userId") { type = NavType.IntType },
                            navArgument("email") { defaultValue = "" },
                            navArgument("initialText") { defaultValue = "" },
                        )
                    ) { backStack ->
                        val t = token ?: return@composable
                        val userId = backStack.arguments!!.getInt("userId")
                        val email = backStack.arguments!!.getString("email") ?: ""
                        val initialText = backStack.arguments!!.getString("initialText") ?: ""
                        ChatScreen(
                            token = t,
                            staffUserId = userId,
                            chatTitle = email,
                            onBack = { navController.popBackStack() },
                            onOpenProduct = { productId, imageIndex ->
                                navController.navigate("product/$productId?imageIndex=$imageIndex")
                            },
                            initialText = initialText,
                        )
                    }
                }
            }
        }
    }
}
