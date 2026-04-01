package gallery.eliza.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import gallery.eliza.app.data.Api
import gallery.eliza.app.data.CommentReport
import gallery.eliza.app.ui.theme.BrownDark
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    token: String,
    onBack: () -> Unit,
    onHome: () -> Unit,
) {
    var reports by remember { mutableStateOf<List<CommentReport>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }
    var selectedReport by remember { mutableStateOf<CommentReport?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun loadReports() {
        try {
            reports = Api.service.getStaffReports("Token $token")
        } catch (_: Exception) { }
    }

    LaunchedEffect(Unit) {
        loadReports()
        loading = false
    }

    selectedReport?.let { report ->
        ReportActionDialog(
            report = report,
            onDismiss = { selectedReport = null },
            onDismissReport = {
                scope.launch {
                    try {
                        Api.service.dismissReport("Token $token", report.id)
                        reports = reports.filter { it.id != report.id }
                    } catch (_: Exception) { }
                    selectedReport = null
                }
            },
            onDeleteComment = {
                scope.launch {
                    try {
                        Api.service.deleteCommentViaReport("Token $token", report.id)
                        reports = reports.filter { it.id != report.id }
                    } catch (_: Exception) { }
                    selectedReport = null
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Жалобы") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Home, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = onHome) {
                        Icon(Icons.Default.Home, contentDescription = "На главную")
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    loadReports()
                    isRefreshing = false
                }
            },
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            if (loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else if (reports.isEmpty()) {
                Text(
                    "Жалоб нет",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(reports) { report ->
                        ReportRow(report = report, onClick = { selectedReport = report })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportRow(report: CommentReport, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = report.reporter_email,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "«${report.text}»",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (!report.is_read) FontWeight.Bold else FontWeight.Normal,
            color = BrownDark
        )
        Text(
            text = "Комментарий: ${report.comment_author}: ${report.comment_text}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2
        )
    }
}

@Composable
private fun ReportActionDialog(
    report: CommentReport,
    onDismiss: () -> Unit,
    onDismissReport: () -> Unit,
    onDeleteComment: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Жалоба") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Комментарий:", style = MaterialTheme.typography.labelMedium)
                Text(
                    "${report.comment_author}: ${report.comment_text}",
                    style = MaterialTheme.typography.bodyMedium
                )
                HorizontalDivider()
                Text("Жалоба от ${report.reporter_email}:", style = MaterialTheme.typography.labelMedium)
                Text(report.text, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(
                    onClick = onDismissReport,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Отклонить жалобу")
                }
                Button(
                    onClick = onDeleteComment,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Удалить комментарий")
                }
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Ещё подумать")
                }
            }
        },
        dismissButton = {}
    )
}
