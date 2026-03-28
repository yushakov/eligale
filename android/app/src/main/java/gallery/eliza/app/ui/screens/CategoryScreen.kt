package gallery.eliza.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import gallery.eliza.app.data.Api
import gallery.eliza.app.data.Category
import gallery.eliza.app.ui.theme.BrownDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    onCategoryClick: (Int, String) -> Unit,
    onReady: () -> Unit,
    token: String?,
    onTokenChange: (String?) -> Unit,
    onChatClick: () -> Unit,
    onChatsClick: () -> Unit,
    onCommentsClick: () -> Unit,
    isStaff: Boolean,
) {
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAuth by remember { mutableStateOf(false) }
    var showAccount by remember { mutableStateOf(false) }
    var retryKey by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    var unreadCount by remember { mutableStateOf(0) }
    var unreadCommentCount by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(retryKey) {
        loading = true
        error = null
        try {
            categories = Api.service.getCategories()
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
            onReady()
        }
    }

    // Polling счётчика непрочитанных чатов каждые 15 сек
    // isStaff в ключах — чтобы сразу вызывать правильный эндпоинт без ожидания 15 сек
    LaunchedEffect(token, isStaff) {
        if (token == null) { unreadCount = 0; return@LaunchedEffect }
        while (true) {
            try {
                unreadCount = if (isStaff) {
                    Api.service.getStaffUnread("Token $token").unread
                } else {
                    Api.service.getChatUnread("Token $token").unread
                }
            } catch (_: Exception) { }
            delay(15_000)
        }
    }

    // Polling непрочитанных комментариев (только для staff) — считаем из того же списка,
    // что показывает CommentListScreen, чтобы бейдж точно совпадал с числом жирных строк
    LaunchedEffect(token, isStaff) {
        if (token == null || !isStaff) { unreadCommentCount = 0; return@LaunchedEffect }
        while (true) {
            try {
                val list = Api.service.getStaffComments("Token $token")
                unreadCommentCount = list.count { !it.is_read_by_staff }
            } catch (_: Exception) { }
            delay(15_000)
        }
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

    if (showAccount && token != null) {
        AccountDialog(
            token = token,
            onSignOut = {
                scope.launch {
                    try { Api.service.logout("Token $token") } catch (_: Exception) { }
                }
                onTokenChange(null)
                showAccount = false
            },
            onDeleted = {
                onTokenChange(null)
                showAccount = false
            },
            onDismiss = { showAccount = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Eliza Gallery") },
                actions = {
                    // Кнопка "Комменты" с красной точкой (только для staff)
                    if (token != null && isStaff) {
                        Box {
                            TextButton(onClick = onCommentsClick) {
                                Text("Комменты")
                            }
                            if (unreadCommentCount > 0) {
                                RedDot(Modifier.align(Alignment.TopEnd).offset(x = (-4).dp, y = 8.dp))
                            }
                        }
                    }
                    // Кнопка чата с красной точкой
                    if (token != null) {
                        Box {
                            TextButton(onClick = {
                                if (isStaff) onChatsClick() else onChatClick()
                            }) {
                                Text(if (isStaff) "Чаты" else "Чат")
                            }
                            if (unreadCount > 0) {
                                RedDot(Modifier.align(Alignment.TopEnd).offset(x = (-4).dp, y = 8.dp))
                            }
                        }
                    } else {
                        TextButton(onClick = { showAuth = true }) {
                            Text("Чат")
                        }
                    }
                    TextButton(onClick = {
                        if (token == null) showAuth = true else showAccount = true
                    }) {
                        Text(if (token == null) "Войти" else "Аккаунт")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                loading -> {}
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
                else -> PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        scope.launch {
                            isRefreshing = true
                            try {
                                val newList = Api.service.getCategories()
                                val existingIds = categories.map { it.id }.toSet()
                                val toAdd = newList.filter { it.id !in existingIds }
                                if (toAdd.isNotEmpty()) categories = categories + toAdd
                            } catch (_: Exception) { }
                            isRefreshing = false
                        }
                    }
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categories) { category ->
                            CategoryCard(category, onClick = { onCategoryClick(category.id, category.name) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(category: Category, onClick: () -> Unit) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val circleSize = (screenWidth - 8.dp * 2 - 8.dp) / 2
    val labelTopPadding = circleSize * 0.75f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
            Box(
                modifier = Modifier
                    .size(circleSize)
                    .align(Alignment.TopCenter)
            ) {
                if (category.cover_url != null) {
                    AsyncImage(
                        model = category.cover_url,
                        contentDescription = category.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.radialGradient(
                                    colorStops = arrayOf(
                                        0.70f to Color.Transparent,
                                        1.0f to Color.White
                                    )
                                )
                            )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = labelTopPadding)
                    .heightIn(min = circleSize * 0.25f)
                    .background(Color.White.copy(alpha = 0.7f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = category.name,
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                        color = BrownDark
                    )
                    Text(
                        text = "товаров: ${category.product_count}",
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        color = BrownDark.copy(alpha = 0.6f)
                    )
                }
            }
    }
}
