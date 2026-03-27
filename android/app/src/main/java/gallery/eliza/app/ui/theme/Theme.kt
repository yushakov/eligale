package gallery.eliza.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val ElizaColorScheme = lightColorScheme(
    primary = BrownDark,
    onPrimary = White,
    background = White,
    onBackground = BrownDark,
    surface = White,
    onSurface = BrownDark,
    onSurfaceVariant = BrownDark,
    surfaceVariant = BrownLight,
    secondaryContainer = BrownLight,
    onSecondaryContainer = BrownDark,
)

@Composable
fun ElizaGalleryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ElizaColorScheme,
        typography = Typography,
        content = content
    )
}
