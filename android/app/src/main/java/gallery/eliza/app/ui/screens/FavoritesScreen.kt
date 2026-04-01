package gallery.eliza.app.ui.screens

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import gallery.eliza.app.data.Api
import gallery.eliza.app.data.DataCache
import gallery.eliza.app.data.FavoriteItem
import gallery.eliza.app.data.ProductImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    token: String,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenProduct: (productId: Int) -> Unit,
) {
    var favorites by remember { mutableStateOf<List<FavoriteItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingDeleteId by remember { mutableStateOf<Int?>(null) }
    var fullscreenItem by remember { mutableStateOf<FavoriteItem?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        loading = true
        error = null
        try {
            favorites = Api.service.getFavorites("Token $token")
        } catch (e: Exception) {
            error = "Не удалось загрузить избранное"
        }
        loading = false
    }

    // Диалог подтверждения удаления
    pendingDeleteId?.let { imageId ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            text = { Text("Удалить из избранного?") },
            confirmButton = {
                TextButton(onClick = {
                    val idToDelete = imageId
                    pendingDeleteId = null
                    scope.launch {
                        try {
                            Api.service.deleteFavorite("Token $token", idToDelete)
                            favorites = favorites.filter { it.image_id != idToDelete }
                            DataCache.favoriteImageIds.remove(idToDelete)
                        } catch (_: Exception) { }
                    }
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("Отмена") }
            },
        )
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Избранное") },
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
                    error != null -> Text(
                        error!!,
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.error
                    )
                    favorites.isEmpty() -> Text(
                        "Избранное пусто",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(favorites, key = { it.image_id }) { item ->
                            // confirmValueChange возвращает false → свайп сбрасывается автоматически,
                            // мы только показываем диалог подтверждения
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        pendingDeleteId = item.image_id
                                    }
                                    false
                                }
                            )
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.errorContainer)
                                            .padding(end = 20.dp),
                                        contentAlignment = Alignment.CenterEnd,
                                    ) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = "Удалить",
                                            tint = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                    }
                                },
                            ) {
                                FavoriteRow(
                                    item = item,
                                    onImageClick = { fullscreenItem = item },
                                    onProductClick = { onOpenProduct(item.product_id) },
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }

        // Полноэкранный просмотр картинки
        AnimatedVisibility(
            visible = fullscreenItem != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            fullscreenItem?.let { item ->
                val image = ProductImage(
                    id = item.image_id,
                    image_url = item.image_url,
                    image_url_100 = item.image_url_100,
                    image_url_200 = null,
                    image_url_300 = null,
                    order = 0,
                )
                val favoriteImageIds = remember(favorites) { favorites.map { it.image_id }.toSet() }
                FullscreenImageViewer(
                    images = listOf(image),
                    onDismiss = { fullscreenItem = null },
                    favoriteImageIds = favoriteImageIds,
                    onFavoriteToggle = { imageId ->
                        scope.launch {
                            try {
                                Api.service.deleteFavorite("Token $token", imageId)
                                favorites = favorites.filter { it.image_id != imageId }
                                DataCache.favoriteImageIds.remove(imageId)
                                fullscreenItem = null
                            } catch (_: Exception) { }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun FavoriteRow(item: FavoriteItem, onImageClick: () -> Unit, onProductClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = item.image_url_100 ?: item.image_url,
            contentDescription = item.product_name,
            contentScale = ContentScale.Crop,
            placeholder = ColorPainter(Color(0xFFE0E0E0)),
            error = ColorPainter(Color(0xFFE0E0E0)),
            modifier = Modifier
                .padding(start = 16.dp)
                .size(72.dp)
                .clickable(onClick = onImageClick)
        )
        Text(
            text = item.product_name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(onClick = onProductClick)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}
