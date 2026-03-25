package gallery.eliza.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import gallery.eliza.app.data.Api
import gallery.eliza.app.data.Product

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(
    categoryId: Int,
    categoryName: String,
    onProductClick: (Int) -> Unit,
    onBack: () -> Unit
) {
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(categoryId) {
        try {
            products = Api.service.getProducts(categoryId)
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(categoryName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                error != null -> Text("Ошибка: $error", Modifier.align(Alignment.Center))
                products.isEmpty() -> Text("Нет товаров", Modifier.align(Alignment.Center))
                else -> LazyColumn(
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(products) { product ->
                        ProductCard(product, onClick = { onProductClick(product.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductCard(product: Product, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(Modifier.fillMaxWidth().height(100.dp)) {
            if (product.cover_url != null) {
                AsyncImage(
                    model = product.cover_url,
                    contentDescription = product.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(100.dp).fillMaxHeight()
                )
            } else {
                Box(Modifier.width(100.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text("No image")
                }
            }
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(12.dp).align(Alignment.CenterVertically)
            )
        }
    }
}
