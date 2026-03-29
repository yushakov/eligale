package gallery.eliza.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gallery.eliza.app.data.Api
import gallery.eliza.app.data.MyComment
import gallery.eliza.app.ui.theme.BrownDark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyCommentsScreen(
    token: String,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenProduct: (productId: Int, commentId: Int) -> Unit,
) {
    var comments by remember { mutableStateOf<List<MyComment>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            comments = Api.service.getMyComments("Token $token")
        } catch (_: Exception) {
            error = true
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мои комментарии") },
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
                error -> Text(
                    "Не удалось загрузить комментарии",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                comments.isEmpty() -> Text(
                    "У вас пока нет комментариев",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(comments, key = { it.id }) { comment ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenProduct(comment.product_id, comment.id) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    comment.product_name,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = BrownDark,
                                    fontSize = 12.sp,
                                )
                                Text(
                                    comment.created_at.take(10),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                comment.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = BrownDark,
                            )
                            HorizontalDivider(Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }
    }
}
