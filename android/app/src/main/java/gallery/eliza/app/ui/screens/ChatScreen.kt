package gallery.eliza.app.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gallery.eliza.app.data.*
import gallery.eliza.app.ui.theme.BrownDark
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val BubbleUser = Color(0xFFF5EDE4)
private val BubbleStaff = Color(0xFFEAE0D5)

/**
 * Универсальный экран чата.
 * Для обычного пользователя: staffUserId = null, token — токен пользователя.
 * Для staff: staffUserId = id пользователя, token — токен сотрудника.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    token: String,
    staffUserId: Int?,        // null = режим пользователя, Int = staff смотрит чат юзера
    chatTitle: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { ChatDatabase.get(context).messageDao() }

    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var inputText by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var hasHistory by remember { mutableStateOf(false) }   // есть ли что грузить выше
    var loadingHistory by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Начальная загрузка: читаем локальную БД, затем подгружаем новые с сервера
    LaunchedEffect(Unit) {
        val local = dao.getAll().map { it.toModel() }
        messages = local

        val minId = dao.minId()
        hasHistory = (minId != null)   // если база не пуста — возможно, есть история выше

        // Подгружаем новые сообщения (после последнего известного)
        val afterId = dao.maxId()
        try {
            val fresh = if (staffUserId == null) {
                Api.service.getChatMessages(token = "Token $token", after = afterId)
            } else {
                Api.service.getStaffChatMessages(token = "Token $token", userId = staffUserId, after = afterId)
            }
            if (fresh.isNotEmpty()) {
                dao.insertAll(fresh.map { it.toEntity() })
                messages = dao.getAll().map { it.toModel() }
                markRead(context, token, staffUserId, fresh.last().id, dao)
            }
        } catch (_: Exception) { }

        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.size - 1)
        }
    }

    // Polling новых сообщений каждые 5 секунд
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            try {
                val afterId = dao.maxId()
                val fresh = if (staffUserId == null) {
                    Api.service.getChatMessages(token = "Token $token", after = afterId)
                } else {
                    Api.service.getStaffChatMessages(token = "Token $token", userId = staffUserId, after = afterId)
                }
                if (fresh.isNotEmpty()) {
                    dao.insertAll(fresh.map { it.toEntity() })
                    messages = dao.getAll().map { it.toModel() }
                    markRead(context, token, staffUserId, fresh.last().id, dao)
                    listState.animateScrollToItem(messages.size - 1)
                }
            } catch (_: Exception) { }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(chatTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Сообщение...") },
                    shape = RoundedCornerShape(24.dp),
                    maxLines = 4,
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val text = inputText.trim()
                        if (text.isEmpty() || sending) return@IconButton
                        sending = true
                        scope.launch {
                            try {
                                val msg = if (staffUserId == null) {
                                    Api.service.sendChatMessage("Token $token", mapOf("text" to text))
                                } else {
                                    Api.service.sendStaffChatMessage("Token $token", staffUserId, mapOf("text" to text))
                                }
                                dao.insertAll(listOf(msg.toEntity()))
                                messages = dao.getAll().map { it.toModel() }
                                inputText = ""
                                listState.animateScrollToItem(messages.size - 1)
                            } catch (_: Exception) { }
                            sending = false
                        }
                    },
                    enabled = inputText.isNotBlank() && !sending,
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить", tint = BrownDark)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Кнопка "Загрузить историю" вверху
            if (hasHistory) {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (loadingHistory) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        } else {
                            TextButton(onClick = {
                                scope.launch {
                                    loadingHistory = true
                                    try {
                                        val minId = dao.minId() ?: return@launch
                                        val older = if (staffUserId == null) {
                                            Api.service.getChatMessages(token = "Token $token", before = minId, limit = 50)
                                        } else {
                                            Api.service.getStaffChatMessages(token = "Token $token", userId = staffUserId, before = minId, limit = 50)
                                        }
                                        if (older.isEmpty()) {
                                            hasHistory = false
                                        } else {
                                            dao.insertAll(older.map { it.toEntity() })
                                            messages = dao.getAll().map { it.toModel() }
                                            if (older.size < 50) hasHistory = false
                                        }
                                    } catch (_: Exception) { }
                                    loadingHistory = false
                                }
                            }) {
                                Text("Загрузить историю")
                            }
                        }
                    }
                }
            }

            items(messages, key = { it.id }) { msg ->
                MessageBubble(msg, isOwnMessage = if (staffUserId == null) !msg.is_staff else msg.is_staff)
            }

            item { Spacer(Modifier.height(4.dp)) }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage, isOwnMessage: Boolean) {
    val alignment = if (isOwnMessage) Alignment.End else Alignment.Start
    val bubbleColor = if (isOwnMessage) BubbleUser else BubbleStaff
    val shape = if (isOwnMessage) {
        RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
    } else {
        RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(bubbleColor, shape)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(msg.text, color = BrownDark, fontSize = 15.sp)
        }
    }
}

private suspend fun markRead(
    context: Context,
    token: String,
    staffUserId: Int?,
    upToId: Int,
    dao: ChatMessageDao,
) {
    try {
        if (staffUserId == null) {
            Api.service.markChatRead("Token $token", mapOf("up_to_id" to upToId))
        } else {
            Api.service.markStaffChatRead("Token $token", staffUserId, mapOf("up_to_id" to upToId))
        }
        dao.markReadUpTo(upToId)
    } catch (_: Exception) { }
}
