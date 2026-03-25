package gallery.eliza.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import gallery.eliza.app.data.ProductDetail

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ProductDetailScreen(productId: Int, onBack: () -> Unit) {
    var product by remember { mutableStateOf<ProductDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(productId) {
        try {
            product = Api.service.getProduct(productId)
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(product?.name ?: "") },
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
                product != null -> {
                    val images = product!!.images.ifEmpty {
                        product!!.cover_url?.let {
                            listOf(gallery.eliza.app.data.ProductImage(0, it, 0))
                        } ?: emptyList()
                    }
                    if (images.isEmpty()) {
                        Text("Нет изображений", Modifier.align(Alignment.Center))
                    } else {
                        val pagerState = rememberPagerState(pageCount = { images.size })
                        Column(Modifier.fillMaxSize()) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxWidth().weight(1f)
                            ) { page ->
                                AsyncImage(
                                    model = images[page].image_url,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            if (images.size > 1) {
                                Text(
                                    text = "${pagerState.currentPage + 1} / ${images.size}",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
