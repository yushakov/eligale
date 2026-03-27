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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import gallery.eliza.app.data.Api
import gallery.eliza.app.data.Category
import gallery.eliza.app.ui.theme.BrownDark

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
    var retryKey by remember { mutableStateOf(0) }

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
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        val circleSize = maxWidth
        val labelTopPadding = circleSize * 0.75f

        Box(modifier = Modifier.fillMaxWidth()) {
            // Круглая обложка
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
                    // Мягкие края: радиальный градиент поверх
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.radialGradient(
                                    colorStops = arrayOf(
                                        0.65f to Color.Transparent,
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

            // Название: полупрозрачный прямоугольник, начинается в нижней четверти круга
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = labelTopPadding)
                    .background(Color.White.copy(alpha = 0.7f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                    color = BrownDark
                )
            }
        }
    }
}
