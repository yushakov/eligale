package gallery.eliza.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
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
    onHome: () -> Unit = {},
) {
    var product by remember { mutableStateOf<ProductDetail?>(null) }
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAuth by remember { mutableStateOf(false) }
    var commentText by remember { mutableStateOf("") }
    var sendingComment by remember { mutableStateOf(false) }
    var fullscreenState by remember { mutableStateOf<Pair<List<gallery.eliza.app.data.ProductImage>, Int>?>(null) }
    var initialFullscreenOpened by remember { mutableStateOf(false) }
    // Диалоги "В чат" — подняты на верхний уровень, чтобы быть доступными из FullscreenImageViewer
    var chatDialogPage by remember { mutableStateOf<Int?>(null) }
    var staffChatDialogPage by remember { mutableStateOf<Int?>(null) }
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

    // Автооткрытие fullscreen при переходе из чата по [product:id:page]
    LaunchedEffect(product) {
        if (!initialFullscreenOpened && initialImagePage > 0 && product != null) {
            val imgs = product!!.images.ifEmpty {
                product!!.cover_url?.let {
                    listOf(gallery.eliza.app.data.ProductImage(0, it, null, null, null, 0))
                } ?: emptyList()
            }
            if (imgs.isNotEmpty()) {
                fullscreenState = imgs to initialImagePage.coerceIn(0, imgs.size - 1)
                initialFullscreenOpened = true
            }
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
                    product != null -> {
                        val images = product!!.images.ifEmpty {
                            product!!.cover_url?.let {
                                listOf(gallery.eliza.app.data.ProductImage(0, it, null, null, null, 0))
                            } ?: emptyList()
                        }

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
                                        onPhotoTap = { page -> fullscreenState = images to page },
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
                                    val clipboard = LocalClipboardManager.current
                                    var showCommentMenu by remember { mutableStateOf(false) }
                                    Box {
                                        Column(
                                            Modifier
                                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                                .pointerInput(Unit) {
                                                    detectTapGestures(onLongPress = { showCommentMenu = true })
                                                }
                                        ) {
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
                                        DropdownMenu(
                                            expanded = showCommentMenu,
                                            onDismissRequest = { showCommentMenu = false },
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Скопировать") },
                                                onClick = {
                                                    clipboard.setText(AnnotatedString("${comment.author}\n${comment.text}"))
                                                    showCommentMenu = false
                                                }
                                            )
                                        }
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
            visible = fullscreenState != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            fullscreenState?.let { (imgs, page) ->
                FullscreenImageViewer(
                    images = imgs,
                    initialPage = page,
                    onDismiss = { fullscreenState = null },
                    onChatButtonClick = when {
                        onGoToChat != null && !isStaff -> { p -> chatDialogPage = p }
                        onGoToChats != null && isStaff -> { p -> staffChatDialogPage = p }
                        else -> null
                    },
                )
            }
        }
    }
}

/**
 * Галерея изображений товара в виде сетки 3×N.
 * Тап по фотографии открывает полноэкранный просмотр.
 */
@Composable
fun ProductGallery(
    images: List<gallery.eliza.app.data.ProductImage>,
    onPhotoTap: (index: Int) -> Unit,
) {
    val columns = 3
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val rows = (images.size + columns - 1) / columns
        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                for (col in 0 until columns) {
                    val index = row * columns + col
                    if (index < images.size) {
                        AsyncImage(
                            model = images[index].image_url_200 ?: images[index].image_url,
                            contentDescription = "Фото ${index + 1}",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clickable { onPhotoTap(index) },
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun FullscreenImageViewer(
    images: List<gallery.eliza.app.data.ProductImage>,
    initialPage: Int = 0,
    onDismiss: () -> Unit,
    onChatButtonClick: ((page: Int) -> Unit)? = null,
) {
    // Cyclic pager: virtual page count is huge, real index = virtualPage % images.size.
    // Start offset keeps the user near the center so they can swipe both ways indefinitely.
    val startPage = images.size * 10_000 + initialPage.coerceIn(0, (images.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(
        initialPage = startPage,
        pageCount = { if (images.size > 1) Int.MAX_VALUE else 1 },
    )
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    // Зум и смещение — общие для текущей страницы; сбрасываются при смене страницы
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(pagerState.currentPage) {
        scale = 1f
        offset = Offset.Zero
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        // Свайп между страницами отключается когда зум > 1, чтобы не конфликтовать с панорамированием
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = scale <= 1f,
        ) { virtualPage ->
            val page = virtualPage % images.size
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { onDismiss() })
                    },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = images[page].image_url,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                        )
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                // awaitEachGesture already starts after a down event
                                do {
                                    val event = awaitPointerEvent()
                                    val fingerCount = event.changes.count { it.pressed }
                                    // Consume only on two-finger zoom OR single-finger pan while zoomed.
                                    // Single finger at scale==1 is left unconsumed so the pager swipe works.
                                    if (fingerCount >= 2 || (fingerCount == 1 && scale > 1f)) {
                                        val zoom = event.calculateZoom()
                                        val pan = event.calculatePan()
                                        scale = (scale * zoom).coerceIn(1f, 8f)
                                        offset = if (scale > 1f) offset + pan else Offset.Zero
                                        event.changes.forEach { it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })
                            }
                        },
                )
            }
        }

        val currentRealPage = pagerState.currentPage % images.size
        val currentUrl = images[currentRealPage].image_url

        // Кнопка "В чат" — привязана к текущей фотографии
        if (onChatButtonClick != null) {
            Button(
                onClick = { onChatButtonClick(currentRealPage) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = 12.dp, top = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrownDark.copy(alpha = 0.85f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text("В чат", color = Color.White, fontSize = 13.sp)
            }
        }

        // Кнопки скачать / скопировать
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = {
                    if (currentUrl != null) {
                        val fileName = currentUrl.substringAfterLast("/").substringBefore("?")
                            .ifBlank { "image.jpg" }
                        val request = DownloadManager.Request(Uri.parse(currentUrl))
                            .setTitle(fileName)
                            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        val dm = context.getSystemService(DownloadManager::class.java)
                        dm.enqueue(request)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = BrownDark.copy(alpha = 0.7f))
            ) {
                Text("Скачать")
            }
            Button(
                onClick = { clipboard.setText(AnnotatedString(currentUrl ?: "")) },
                colors = ButtonDefaults.buttonColors(containerColor = BrownDark.copy(alpha = 0.7f))
            ) {
                Text("Копировать ссылку")
            }
        }
    }
}
