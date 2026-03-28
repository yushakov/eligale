package gallery.eliza.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gallery.eliza.app.data.Api
import gallery.eliza.app.data.ChatListItem
import gallery.eliza.app.ui.theme.BrownDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    token: String,
    onChatClick: (userId: Int, userEmail: String) -> Unit,
    onBack: () -> Unit,
) {
    var chats by remember { mutableStateOf<List<ChatListItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            chats = Api.service.getStaffChatList("Token $token")
        } catch (_: Exception) { }
        loading = false
    }

    LaunchedEffect(Unit) { load() }

    // Обновляем список каждые 15 сек (чтобы обновлялись счётчики и порядок)
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            try { chats = Api.service.getStaffChatList("Token $token") } catch (_: Exception) { }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Чаты") },
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
                chats.isEmpty() -> Text("Нет чатов", Modifier.align(Alignment.Center))
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(chats, key = { it.id }) { chat ->
                        ChatListRow(
                            chat = chat,
                            onClick = { onChatClick(chat.user_id, chat.user_email) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatListRow(chat: ChatListItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = chat.user_display_name?.takeIf { it.isNotBlank() } ?: chat.user_email,
                fontWeight = FontWeight.SemiBold,
                color = BrownDark,
                fontSize = 15.sp,
            )
            if (chat.last_message != null) {
                Text(
                    text = chat.last_message.text,
                    color = BrownDark.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (chat.unread_count > 0) {
            Spacer(Modifier.width(8.dp))
            UnreadBadge(chat.unread_count)
        }
    }
}

@Composable
fun UnreadBadge(count: Int) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .then(
                if (count > 9) Modifier.wrapContentWidth() else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = BrownDark,
        ) {
            Text(
                text = if (count > 99) "99+" else count.toString(),
                color = Color.White,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
    }
}
