package gallery.eliza.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import gallery.eliza.app.data.Api
import gallery.eliza.app.data.Comment
import gallery.eliza.app.data.ProductDetail
import gallery.eliza.app.data.TokenStorage

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ProductDetailScreen(productId: Int, onBack: () -> Unit) {
    val context = LocalContext.current
    var product by remember { mutableStateOf<ProductDetail?>(null) }
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAuth by remember { mutableStateOf(false) }
    var commentText by remember { mutableStateOf("") }
    var sendingComment by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf(TokenStorage.get(context)) }

    LaunchedEffect(productId) {
        try {
            product = Api.service.getProduct(productId)
            comments = Api.service.getComments(productId)
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    if (showAuth) {
        AuthDialog(
            onTokenReceived = { newToken ->
                TokenStorage.save(context, newToken)
                token = newToken
                showAuth = false
            },
            onDismiss = { showAuth = false }
        )
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

                    LazyColumn(Modifier.fillMaxSize()) {
                        // Галерея
                        item {
                            if (images.isNotEmpty()) {
                                val pagerState = rememberPagerState(pageCount = { images.size })
                                Box {
                                    HorizontalPager(
                                        state = pagerState,
                                        modifier = Modifier.fillMaxWidth().height(320.dp)
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
                                            modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
                                        )
                                    }
                                }
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
                                    Text(comment.author, style = MaterialTheme.typography.labelMedium)
                                    Text(comment.text, style = MaterialTheme.typography.bodyMedium)
                                    HorizontalDivider(Modifier.padding(top = 6.dp))
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
                                    maxLines = 3
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
                                    TokenStorage.clear(context)
                                    token = null
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
}
