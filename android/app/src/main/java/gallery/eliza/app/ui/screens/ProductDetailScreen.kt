package gallery.eliza.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.app.DownloadManager
import android.net.Uri
import android.os.Environment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import gallery.eliza.app.data.Api
import gallery.eliza.app.data.Comment
import gallery.eliza.app.data.ProductDetail
import gallery.eliza.app.ui.theme.BrownDark
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ProductDetailScreen(
    productId: Int,
    onBack: () -> Unit,
    token: String?,
    onTokenChange: (String?) -> Unit,
    isStaff: Boolean = false,
    onOpenChat: (userId: Int, userEmail: String) -> Unit = { _, _ -> },
    scrollToCommentId: Int? = null,
    initialImagePage: Int = 0,
    onGoToChat: (() -> Unit)? = null,
    onGoToChats: ((pendingText: String) -> Unit)? = null,
    categoryName: String = "",
) {
    var product by remember { mutableStateOf<ProductDetail?>(null) }
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAuth by remember { mutableStateOf(false) }
    var commentText by remember { mutableStateOf("") }
    var sendingComment by remember { mutableStateOf(false) }
    var fullscreenUrl by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(productId, retryKey) {
        loading = true
        error = null
        try {
            product = Api.service.getProduct(productId)
            comments = Api.service.getComments(productId)
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    // Прокрутка к нужному комментарию после загрузки
    LaunchedEffect(comments, scrollToCommentId) {
        if (scrollToCommentId == null || comments.isEmpty() || product == null) return@LaunchedEffect
        val commentIndex = comments.indexOfFirst { it.id == scrollToCommentId }
        if (commentIndex < 0) return@LaunchedEffect
        val hasImages = product!!.images.isNotEmpty() || product!!.cover_url != null
        val hasDescription = product!!.description.isNotBlank()
        // Индексы: title(1) + gallery(1) + description(0/1) + header(1) + commentIndex
        val targetIndex = 1 + (if (hasImages) 1 else 0) + (if (hasDescription) 1 else 0) + 1 + commentIndex
        listState.animateScrollToItem(targetIndex)
    }

    if (showAuth) {
        AuthDialog(
            onTokenReceived = { newToken ->
                onTokenChange(newToken)
                showAuth = false
            },
            onDismiss = { showAuth = false }
        )
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (categoryName.isNotBlank()) categoryName else (product?.name ?: "")) },
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
                    product != null -> {
                        val images = product!!.images.ifEmpty {
                            product!!.cover_url?.let {
                                listOf(gallery.eliza.app.data.ProductImage(0, it, 0))
                            } ?: emptyList()
                        }

                        // Страница из диалога: null = диалог закрыт
                        var chatDialogPage by remember { mutableStateOf<Int?>(null) }
                        var staffChatDialogPage by remember { mutableStateOf<Int?>(null) }

                        // Диалог для обычного пользователя
                        chatDialogPage?.let { page ->
                            AlertDialog(
                                onDismissRequest = { chatDialogPage = null },
                                text = { Text("Вас интересует данный продукт?") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        chatDialogPage = null
                                        val productName = product!!.name
                                        scope.launch {
                                            try {
                                                val text = "Интересует товар «$productName» (фото ${page + 1})\n[product:$productId:$page]"
                                                Api.service.sendChatMessage("Token ${token!!}", mapOf("text" to text))
                                            } catch (_: Exception) { }
                                            onGoToChat?.invoke()
                                        }
                                    }) { Text("Да") }
                                },
                                dismissButton = {
                                    TextButton(onClick = {
                                        chatDialogPage = null
                                        onGoToChat?.invoke()
                                    }) { Text("Нет, просто в чат") }
                                },
                            )
                        }

                        // Диалог для staff
                        staffChatDialogPage?.let { page ->
                            AlertDialog(
                                onDismissRequest = { staffChatDialogPage = null },
                                text = { Text("В чат с этим товаром?") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        staffChatDialogPage = null
                                        onGoToChats?.invoke("[product:$productId:$page]")
                                    }) { Text("Да") }
                                },
                                dismissButton = {
                                    TextButton(onClick = {
                                        staffChatDialogPage = null
                                        onGoToChats?.invoke("")
                                    }) { Text("Нет, просто в чат") }
                                },
                            )
                        }

                        PullToRefreshBox(
                            isRefreshing = isRefreshing,
                            onRefresh = {
                                scope.launch {
                                    isRefreshing = true
                                    try {
                                        val newProduct = Api.service.getProduct(productId)
                                        val newComments = Api.service.getComments(productId)
                                        product = newProduct
                                        val existingCommentIds = comments.map { it.id }.toSet()
                                        val toAdd = newComments.filter { it.id !in existingCommentIds }
                                        if (toAdd.isNotEmpty()) comments = comments + toAdd
                                    } catch (_: Exception) { }
                                    isRefreshing = false
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        ) {
                        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                            // Название товара
                            item {
                                Text(
                                    text = product!!.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                                )
                            }

                            // Галерея
                            item {
                                if (images.isNotEmpty()) {
                                    ProductGallery(
                                        images = images,
                                        initialPage = initialImagePage,
                                        onFullscreen = { url -> fullscreenUrl = url },
                                        onChatButtonClick = when {
                                            onGoToChat != null && !isStaff -> { page -> chatDialogPage = page }
                                            onGoToChats != null && isStaff -> { page -> staffChatDialogPage = page }
                                            else -> null
                                        },
                                    )
                                }
                            }

                            // Описание
                            if (product!!.description.isNotBlank()) {
                                item {
                                    Text(
                                        text = product!!.description,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }

                            // Комментарии
                            item {
                                Text(
                                    text = "Комментарии",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
                                )
                            }

                            if (comments.isEmpty()) {
                                item {
                                    Text(
                                        "Пока нет комментариев",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            } else {
                                items(comments) { comment ->
                                    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                                        if (isStaff) {
                                            Text(
                                                comment.author,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = BrownDark,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                                textDecoration = TextDecoration.Underline,
                                                modifier = Modifier.clickable {
                                                    onOpenChat(comment.user_id, comment.user_email)
                                                }
                                            )
                                        } else {
                                            Text(
                                                comment.author,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = Color.Black,
                                                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                                            )
                                        }
                                        Text(
                                            comment.text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = BrownDark
                                        )
                                        Spacer(Modifier.height(20.dp))
                                    }
                                }
                            }

                            // Поле ввода комментария
                            item {
                                Row(
                                    Modifier.padding(12.dp).fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = commentText,
                                        onValueChange = { commentText = it },
                                        placeholder = { Text("Написать комментарий...") },
                                        modifier = Modifier.weight(1f),
                                        maxLines = 3,
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            if (token == null) {
                                                showAuth = true
                                            } else {
                                                sendingComment = true
                                            }
                                        },
                                        enabled = commentText.isNotBlank() && !sendingComment
                                    ) {
                                        Text("Отправить")
                                    }
                                }
                            }
                        }
                        } // PullToRefreshBox

                        // Отправка комментария
                        if (sendingComment && token != null) {
                            LaunchedEffect(sendingComment) {
                                try {
                                    val newComment = Api.service.postComment(
                                        productId,
                                        "Token $token",
                                        mapOf("text" to commentText)
                                    )
                                    comments = comments + newComment
                                    commentText = ""
                                } catch (e: Exception) {
                                    if (e.message?.contains("401") == true) {
                                        onTokenChange(null)
                                        showAuth = true
                                    }
                                } finally {
                                    sendingComment = false
                                }
                            }
                        }
                    }
                }
            }
        }

        // Полноэкранный просмотр с зумом
        AnimatedVisibility(
            visible = fullscreenUrl != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            fullscreenUrl?.let { url ->
                FullscreenImageViewer(url = url, onDismiss = { fullscreenUrl = null })
            }
        }
    }
}

/**
 * Галерея изображений товара. Отдельный composable, чтобы pagerState жил на правильном уровне
 * и его можно было тестировать в изоляции.
 *
 * @param images список фотографий
 * @param initialPage начальная страница (например, когда открываем конкретное фото из чата)
 * @param onFullscreen двойной тап → полноэкранный просмотр
 * @param onChatButtonClick если передан — показывает кнопку "В чат"; вызывается с текущей страницей
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ProductGallery(
    images: List<gallery.eliza.app.data.ProductImage>,
    initialPage: Int = 0,
    onFullscreen: (url: String) -> Unit,
    onChatButtonClick: ((page: Int) -> Unit)? = null,
) {
    val safeInitialPage = initialPage.coerceIn(0, (images.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(
        initialPage = safeInitialPage,
        pageCount = { images.size },
    )

    Box(modifier = Modifier.fillMaxWidth().height(320.dp)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            AsyncImage(
                model = images[page].image_url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                images[page].image_url?.let { onFullscreen(it) }
                            }
                        )
                    }
            )
        }

        if (images.size > 1) {
            Text(
                text = "${pagerState.currentPage + 1} / ${images.size}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(8.dp)
            )
        }

        if (onChatButtonClick != null) {
            val currentPage = pagerState.currentPage
            Button(
                onClick = { onChatButtonClick(currentPage) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrownDark),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text("В чат", color = Color.White, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun FullscreenImageViewer(url: String, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 8f)
        offset = if (scale > 1f) offset + panChange else Offset.Zero
    }

    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
                .transformable(state = transformState)
        )

        Button(
            onClick = {
                val fileName = url.substringAfterLast("/").substringBefore("?")
                    .ifBlank { "image.jpg" }
                val request = DownloadManager.Request(Uri.parse(url))
                    .setTitle(fileName)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                val dm = context.getSystemService(DownloadManager::class.java)
                dm.enqueue(request)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BrownDark.copy(alpha = 0.7f)
            )
        ) {
            Text("Скачать")
        }
    }
}
