package gallery.eliza.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import gallery.eliza.app.data.Api
import gallery.eliza.app.data.Category

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(
    onCategoryClick: (Int, String) -> Unit,
    onReady: () -> Unit,
    token: String?,
    onTokenChange: (String?) -> Unit
) {
    var categories by remember { mutableStateOf<List<Category>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAuth by remember { mutableStateOf(false) }
    var showAccount by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            categories = Api.service.getCategories()
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
            onReady()
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
                error != null -> Text("Ошибка: $error", Modifier.align(Alignment.Center))
                else -> LazyVerticalGrid(
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

@Composable
private fun CategoryCard(category: Category, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f).clickable(onClick = onClick)
    ) {
        Box(Modifier.fillMaxSize()) {
            if (category.cover_url != null) {
                AsyncImage(
                    model = category.cover_url,
                    contentDescription = category.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            ) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}
