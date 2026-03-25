package gallery.eliza.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import gallery.eliza.app.ui.screens.CategoryScreen
import gallery.eliza.app.ui.screens.ProductDetailScreen
import gallery.eliza.app.ui.screens.ProductListScreen
import gallery.eliza.app.ui.theme.ElizaGalleryTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ElizaGalleryTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "categories") {
                    composable("categories") {
                        CategoryScreen(onCategoryClick = { id, name ->
                            navController.navigate("products/$id?name=${name}")
                        })
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
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
