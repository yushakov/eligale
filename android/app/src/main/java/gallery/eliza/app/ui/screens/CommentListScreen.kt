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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Комментарии") },
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
                        CommentRow(
                            comment = comment,
                            onOpenChat = { onOpenChat(comment.user_id, comment.user_email) },
                            onOpenProduct = {
                                // Помечаем как прочитанный и переходим к товару
                                scope.launch {
                                    try { Api.service.markStaffCommentRead("Token $token", comment.id) } catch (_: Exception) { }
                                    // Обновляем локально чтобы убрать жирность сразу
                                    comments = comments.map {
                                        if (it.id == comment.id) it.copy(is_read_by_staff = true) else it
                                    }
                                }
                                onOpenProduct(comment.product_id, comment.id)
                            },
                        )
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
