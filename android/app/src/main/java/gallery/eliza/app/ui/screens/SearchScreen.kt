package gallery.eliza.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import gallery.eliza.app.data.Api
import gallery.eliza.app.data.SearchResult
import gallery.eliza.app.ui.theme.BrownDark
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    token: String,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onOpenResult: (SearchResult) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun doSearch() {
        val q = query.trim()
        if (q.isEmpty() || searching) return
        searching = true
        scope.launch {
            try {
                results = Api.service.search("Token $token", q)
            } catch (_: Exception) {
                results = emptyList()
            }
            searched = true
            searching = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Поиск") },
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
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Введите запрос...") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                )
                Button(
                    onClick = { doSearch() },
                    enabled = query.isNotBlank() && !searching,
                    colors = ButtonDefaults.buttonColors(containerColor = BrownDark),
                ) {
                    if (searching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("Искать")
                    }
                }
            }

            when {
                searched && results.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Ничего не найдено", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                results.isNotEmpty() -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(results, key = { "${it.type}_${it.id}" }) { result ->
                        SearchResultItem(
                            result = result,
                            query = query.trim(),
                            onClick = { onOpenResult(result) },
                        )
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(result: SearchResult, query: String, onClick: () -> Unit) {
    val typeLabel = if (result.type == "comment") "коммент" else "чат"
    val subtitle = if (result.type == "comment") {
        listOfNotNull(result.product_name, result.author).joinToString(" · ")
    } else {
        result.user_email ?: ""
    }
    val dateStr = result.created_at.take(16).replace('T', ' ')

    val annotatedSnippet = buildAnnotatedString {
        if (query.isEmpty()) {
            append(result.snippet)
        } else {
            var cursor = 0
            val lower = result.snippet.lowercase()
            val lowerQuery = query.lowercase()
            var idx = lower.indexOf(lowerQuery, cursor)
            while (idx != -1) {
                append(result.snippet.substring(cursor, idx))
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(result.snippet.substring(idx, idx + query.length))
                }
                cursor = idx + query.length
                idx = lower.indexOf(lowerQuery, cursor)
            }
            append(result.snippet.substring(cursor))
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    typeLabel,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BrownDark.copy(alpha = 0.55f),
                )
                Text(
                    dateStr,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (subtitle.isNotBlank()) {
                Text(subtitle, fontSize = 12.sp, color = BrownDark.copy(alpha = 0.75f))
            }
            Text(
                text = annotatedSnippet,
                fontSize = 14.sp,
                color = BrownDark,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}
