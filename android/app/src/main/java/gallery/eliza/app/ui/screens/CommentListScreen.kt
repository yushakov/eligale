package gallery.eliza.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gallery.eliza.app.data.Api
import gallery.eliza.app.data.StaffComment
import gallery.eliza.app.ui.theme.BrownDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentListScreen(
    token: String,
    onOpenChat: (userId: Int, userEmail: String) -> Unit,
    onOpenProduct: (productId: Int, commentId: Int) -> Unit,
    onBack: () -> Unit,
) {
    var comments by remember { mutableStateOf<List<StaffComment>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var deleteConfirmId by remember { mutableStateOf<Int?>(null) }
    val unreadCount = comments.count { !it.is_read_by_staff }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            comments = Api.service.getStaffComments("Token $token")
        } catch (_: Exception) { }
        loading = false
    }

    LaunchedEffect(Unit) { load() }

    // Обновляем список каждые 15 сек
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            try { comments = Api.service.getStaffComments("Token $token") } catch (_: Exception) { }
        }
    }

    // Диалог подтверждения удаления
    deleteConfirmId?.let { idToDelete ->
        AlertDialog(
            onDismissRequest = { deleteConfirmId = null },
            text = { Text("Удалить комментарий?") },
            confirmButton = {
                TextButton(onClick = {
                    deleteConfirmId = null
                    scope.launch {
                        try { Api.service.deleteStaffComment("Token $token", idToDelete) } catch (_: Exception) { }
                        comments = comments.filter { it.id != idToDelete }
                    }
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmId = null }) { Text("Отмена") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Комментарии")
                        if (unreadCount > 0) {
                            Text(
                                "Непрочитанных: $unreadCount",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
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
                comments.isEmpty() -> Text("Нет комментариев", Modifier.align(Alignment.Center))
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(comments, key = { it.id }) { comment ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { value ->
                                when (value) {
                                    SwipeToDismissBoxValue.StartToEnd -> {
                                        // Свайп вправо — пометить прочитанным
                                        if (!comment.is_read_by_staff) {
                                            scope.launch {
                                                try { Api.service.markStaffCommentRead("Token $token", comment.id) } catch (_: Exception) { }
                                                comments = comments.map {
                                                    if (it.id == comment.id) it.copy(is_read_by_staff = true) else it
                                                }
                                            }
                                        }
                                        false // не убирать из списка
                                    }
                                    SwipeToDismissBoxValue.EndToStart -> {
                                        // Свайп влево — показать диалог удаления
                                        deleteConfirmId = comment.id
                                        false // snap back, удаление через диалог
                                    }
                                    else -> false
                                }
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                val color = when (dismissState.dismissDirection) {
                                    SwipeToDismissBoxValue.StartToEnd -> Color(0xFF4CAF50) // зелёный — прочитать
                                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error // красный — удалить
                                    else -> Color.Transparent
                                }
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(color)
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd)
                                        Alignment.CenterStart else Alignment.CenterEnd
                                ) {
                                    Text(
                                        text = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) "✓ Прочитано" else "Удалить",
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        ) {
                            CommentRow(
                                comment = comment,
                                onOpenChat = { onOpenChat(comment.user_id, comment.user_email) },
                                onOpenProduct = {
                                    scope.launch {
                                        try { Api.service.markStaffCommentRead("Token $token", comment.id) } catch (_: Exception) { }
                                        comments = comments.map {
                                            if (it.id == comment.id) it.copy(is_read_by_staff = true) else it
                                        }
                                    }
                                    onOpenProduct(comment.product_id, comment.id)
                                },
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: StaffComment,
    onOpenChat: () -> Unit,
    onOpenProduct: () -> Unit,
) {
    val isUnread = !comment.is_read_by_staff
    val authorName = comment.user_display_name?.takeIf { it.isNotBlank() } ?: comment.user_email

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onOpenProduct)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Имя пользователя — ссылка в чат
                Text(
                    text = authorName,
                    fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal,
                    color = BrownDark,
                    fontSize = 13.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable(onClick = onOpenChat)
                )
                Text(
                    text = "  →  ${comment.product_name}",
                    color = BrownDark.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = comment.text,
                fontWeight = if (isUnread) FontWeight.SemiBold else FontWeight.Normal,
                color = BrownDark.copy(alpha = if (isUnread) 0.9f else 0.65f),
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
