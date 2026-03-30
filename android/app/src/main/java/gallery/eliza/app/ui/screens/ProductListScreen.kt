package gallery.eliza.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import gallery.eliza.app.data.Api
import gallery.eliza.app.data.Product
import gallery.eliza.app.util.withRetry
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(
    categoryId: Int,
    categoryName: String,
    onProductClick: (Int) -> Unit,
    onBack: () -> Unit,
    onHome: () -> Unit,
) {
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(categoryId, retryKey) {
        loading = true
        error = null
        try {
            products = withRetry { Api.service.getProducts(categoryId) }
        } catch (e: Exception) {
            error = e.message ?: "Ошибка загрузки"
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(categoryName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = onHome) {
                        Icon(Icons.Filled.Home, contentDescription = "На главную")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                error != null -> Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Не удалось загрузить данные")
                    Button(onClick = { retryKey++ }) {
                        Text("Переподключиться")
                    }
                }
                products.isEmpty() -> Text("Нет товаров", Modifier.align(Alignment.Center))
                else -> PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        scope.launch {
                            isRefreshing = true
                            try {
                                val newList = Api.service.getProducts(categoryId)
                                val existingIds = products.map { it.id }.toSet()
                                val toAdd = newList.filter { it.id !in existingIds }
                                if (toAdd.isNotEmpty()) products = products + toAdd
                            } catch (_: Exception) { }
                            isRefreshing = false
                        }
                    }
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items(products) { product ->
                            ProductTile(product, onClick = { onProductClick(product.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductTile(product: Product, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = product.cover_url_300 ?: product.cover_url,
            contentDescription = product.name,
            contentScale = ContentScale.Crop,
            placeholder = ColorPainter(Color(0xFFE0E0E0)),
            error = ColorPainter(Color(0xFFE0E0E0)),
            modifier = Modifier.fillMaxSize()
        )
        Text(
            text = "${product.image_count} фото",
            fontSize = 9.sp,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 3.dp, vertical = 1.dp)
        )
    }
}
