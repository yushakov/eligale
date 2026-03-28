package gallery.eliza.app.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.input.pointer.pointerInput
import coil.compose.AsyncImage
import gallery.eliza.app.data.*
import gallery.eliza.app.ui.theme.BrownDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

private val BubbleUser = Color(0xFFF5EDE4)
private val BubbleStaff = Color(0xFFEAE0D5)

/**
 * Универсальный экран чата.
 * Для обычного пользователя: staffUserId = null, token — токен пользователя.
 * Для staff: staffUserId = id пользователя, token — токен сотрудника.
 */
private val productLinkRegex = Regex("""\[product:(\d+):(\d+)\]""")
private val imageLinkRegex = Regex("""\[image:(https?://[^\]]+)\]""")
private val urlRegex = Regex("""https?://\S+""")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    token: String,
    staffUserId: Int?,        // null = режим пользователя, Int = staff смотрит чат юзера
    chatTitle: String,
    onBack: () -> Unit,
    onOpenProduct: ((productId: Int, imageIndex: Int) -> Unit)? = null,
    initialText: String = "",
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { ChatDatabase.get(context).messageDao() }
    val chatUserId = staffUserId ?: 0

    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var inputText by remember { mutableStateOf(initialText) }
    var sending by remember { mutableStateOf(false) }
    var fullscreenImageUrl by remember { mutableStateOf<String?>(null) }
    var hasHistory by remember { mutableStateOf(false) }   // есть ли что грузить выше
    var atBeginning by remember { mutableStateOf(false) }  // дошли до начала переписки
    var loadingHistory by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        sending = true
        scope.launch {
            try {
                val imageText = uploadImageAndGetMessage(context, uri, token)
                if (imageText != null) {
                    val msg = if (staffUserId == null) {
                        Api.service.sendChatMessage("Token $token", mapOf("text" to imageText))
                    } else {
                        Api.service.sendStaffChatMessage("Token $token", staffUserId, mapOf("text" to imageText))
                    }
                    dao.insertAll(listOf(msg.toEntity(chatUserId)))
                    messages = dao.getAll(chatUserId).map { it.toModel() }
                    listState.animateScrollToItem(messages.size - 1)
                }
            } catch (_: Exception) { }
            sending = false
        }
    }

    // Перезапускаем загрузку при каждом RESUME (в т.ч. после пробуждения телефона)
    var resumeKey by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeKey++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Начальная загрузка: читаем локальную БД, затем подгружаем новые с сервера.
    // Перезапускается при каждом RESUME, чтобы экран оживал после сна телефона.
    LaunchedEffect(resumeKey) {
        val isFirstLoad = resumeKey == 0

        val local = dao.getAll(chatUserId).map { it.toModel() }
        messages = local
        atBeginning = false

        // Подгружаем новые сообщения (после последнего известного)
        val afterId = dao.maxId(chatUserId)
        try {
            val fresh = if (staffUserId == null) {
                Api.service.getChatMessages(token = "Token $token", after = afterId)
            } else {
                Api.service.getStaffChatMessages(token = "Token $token", userId = staffUserId, after = afterId)
            }
            if (fresh.isNotEmpty()) {
                dao.insertAll(fresh.map { it.toEntity(chatUserId) })
                messages = dao.getAll(chatUserId).map { it.toModel() }
                markRead(context, token, staffUserId, fresh.last().id, dao, chatUserId)
                listState.animateScrollToItem(messages.size - 1)
            } else if (isFirstLoad && messages.isNotEmpty()) {
                listState.scrollToItem(messages.size - 1)
            }
            // Кнопка "Загрузить историю" нужна только если:
            // - при первом открытии (afterId=null) сервер вернул полную страницу — значит есть более ранние
            // - при повторных открытиях (afterId!=null) оставляем hasHistory как есть (пользователь мог
            //   грузить историю раньше и теперь в БД есть сообщения, но не все)
            if (afterId == null) {
                hasHistory = fresh.size >= 50
            } else if (!hasHistory) {
                // Если мы никогда не видели кнопку, но в БД есть сообщения — возможно, история была
                // загружена в предыдущих сессиях, но не до конца
                hasHistory = dao.minId(chatUserId) != null
            }
        } catch (_: Exception) {
            if (isFirstLoad && messages.isNotEmpty()) {
                listState.scrollToItem(messages.size - 1)
            }
            // При ошибке сети показываем кнопку если есть что пробовать грузить
            if (afterId == null && messages.isNotEmpty()) hasHistory = true
        }
    }

    // Polling новых сообщений каждые 5 секунд
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            try {
                val afterId = dao.maxId(chatUserId)
                val fresh = if (staffUserId == null) {
                    Api.service.getChatMessages(token = "Token $token", after = afterId)
                } else {
                    Api.service.getStaffChatMessages(token = "Token $token", userId = staffUserId, after = afterId)
                }
                if (fresh.isNotEmpty()) {
                    dao.insertAll(fresh.map { it.toEntity(chatUserId) })
                    messages = dao.getAll(chatUserId).map { it.toModel() }
                    markRead(context, token, staffUserId, fresh.last().id, dao, chatUserId)
                    listState.animateScrollToItem(messages.size - 1)
                }
            } catch (_: Exception) { }
        }
    }

    Box(Modifier.fillMaxSize()) {
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
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { imagePicker.launch("image/*") },
                    enabled = !sending,
                ) {
                    Text("🖼", fontSize = 20.sp)
                }
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
                                dao.insertAll(listOf(msg.toEntity(chatUserId)))
                                messages = dao.getAll(chatUserId).map { it.toModel() }
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
            // Начало переписки или кнопка "Загрузить историю"
            if (atBeginning) {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            "Начало переписки",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            } else if (hasHistory) {
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (loadingHistory) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        } else {
                            TextButton(onClick = {
                                scope.launch {
                                    loadingHistory = true
                                    try {
                                        val minId = dao.minId(chatUserId) ?: return@launch
                                        val older = if (staffUserId == null) {
                                            Api.service.getChatMessages(token = "Token $token", before = minId, limit = 50)
                                        } else {
                                            Api.service.getStaffChatMessages(token = "Token $token", userId = staffUserId, before = minId, limit = 50)
                                        }
                                        if (older.isEmpty()) {
                                            hasHistory = false
                                            atBeginning = true
                                        } else {
                                            dao.insertAll(older.map { it.toEntity(chatUserId) })
                                            messages = dao.getAll(chatUserId).map { it.toModel() }
                                            if (older.size < 50) {
                                                hasHistory = false
                                                atBeginning = true
                                            }
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
                MessageBubble(
                    msg = msg,
                    isOwnMessage = if (staffUserId == null) !msg.is_staff else msg.is_staff,
                    onOpenProduct = onOpenProduct,
                    onImageDoubleTap = { url -> fullscreenImageUrl = url },
                )
            }

            item { Spacer(Modifier.height(4.dp)) }
        }
    }

    fullscreenImageUrl?.let { url ->
        FullscreenImageViewer(url = url, onDismiss = { fullscreenImageUrl = null })
    }
    } // Box
}

@Composable
private fun MessageBubble(
    msg: ChatMessage,
    isOwnMessage: Boolean,
    onOpenProduct: ((productId: Int, imageIndex: Int) -> Unit)? = null,
    onImageDoubleTap: ((url: String) -> Unit)? = null,
) {
    val alignment = if (isOwnMessage) Alignment.End else Alignment.Start
    val bubbleColor = if (isOwnMessage) BubbleUser else BubbleStaff
    val shape = if (isOwnMessage) {
        RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
    } else {
        RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)
    }

    val productMatch = productLinkRegex.find(msg.text)
    val imageMatch = imageLinkRegex.find(msg.text)
    var displayText = msg.text
    if (productMatch != null) displayText = displayText.replace(productMatch.value, "").trimEnd()
    if (imageMatch != null) displayText = displayText.replace(imageMatch.value, "").trimEnd()

    var showMenu by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment,
    ) {
        Box {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .background(bubbleColor, shape)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onLongPress = { showMenu = true })
                    }
            ) {
                Column {
                    if (imageMatch != null) {
                        val imageUrl = imageMatch.groupValues[1]
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onLongPress = { showMenu = true },
                                        onDoubleTap = { onImageDoubleTap?.invoke(imageUrl) },
                                    )
                                }
                        )
                        if (displayText.isNotBlank()) Spacer(Modifier.height(4.dp))
                    }
                    if (displayText.isNotBlank()) {
                        val uriHandler = LocalUriHandler.current
                        val annotated = buildAnnotatedString {
                            var cursor = 0
                            for (match in urlRegex.findAll(displayText)) {
                                val url = match.value.trimEnd('.', ',', ')', ']', '!', ';', ':')
                                append(displayText.substring(cursor, match.range.first))
                                pushStringAnnotation("URL", url)
                                withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                                    append(url)
                                }
                                pop()
                                cursor = match.range.first + url.length
                                if (match.range.last + 1 > cursor) {
                                    append(displayText.substring(cursor, match.range.last + 1))
                                }
                                cursor = match.range.last + 1
                            }
                            if (cursor < displayText.length) append(displayText.substring(cursor))
                        }
                        ClickableText(
                            text = annotated,
                            style = TextStyle(color = BrownDark, fontSize = 15.sp),
                            onClick = { offset ->
                                annotated.getStringAnnotations("URL", offset, offset)
                                    .firstOrNull()?.let { uriHandler.openUri(it.item) }
                            }
                        )
                    }
                    if (productMatch != null && onOpenProduct != null) {
                        val productId = productMatch.groupValues[1].toInt()
                        val imageIndex = productMatch.groupValues[2].toInt()
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "→ Открыть товар",
                            color = BrownDark,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clickable { onOpenProduct(productId, imageIndex) }
                                .padding(vertical = 2.dp),
                            style = androidx.compose.ui.text.TextStyle(
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                            )
                        )
                    }
                }
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Скопировать") },
                    onClick = {
                        clipboard.setText(AnnotatedString(msg.text))
                        showMenu = false
                    }
                )
            }
        }
    }
}

private val uploadClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()

/**
 * Сжимает изображение, загружает в Яндекс через presign и возвращает текст сообщения [image:url].
 * Возвращает null при ошибке.
 */
private suspend fun uploadImageAndGetMessage(context: Context, uri: Uri, token: String): String? =
    withContext(Dispatchers.IO) {
        try {
            // Сжимаем до JPEG 85%
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri))
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            val bytes = out.toByteArray()

            // Presign
            val presign = Api.service.chatMediaPresign("Token $token", mapOf("filename" to "image.jpg"))

            // PUT напрямую в Яндекс
            val body = bytes.toRequestBody("image/jpeg".toMediaType())
            val request = Request.Builder()
                .url(presign.upload_url)
                .put(body)
                .header("Content-Type", "image/jpeg")
                .build()
            val response = uploadClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            "[image:${presign.public_url}]"
        } catch (_: Exception) {
            null
        }
    }

private suspend fun markRead(
    context: Context,
    token: String,
    staffUserId: Int?,
    upToId: Int,
    dao: ChatMessageDao,
    chatUserId: Int,
) {
    try {
        if (staffUserId == null) {
            Api.service.markChatRead("Token $token", mapOf("up_to_id" to upToId))
        } else {
            Api.service.markStaffChatRead("Token $token", staffUserId, mapOf("up_to_id" to upToId))
        }
        dao.markReadUpTo(chatUserId, upToId)
    } catch (_: Exception) { }
}
