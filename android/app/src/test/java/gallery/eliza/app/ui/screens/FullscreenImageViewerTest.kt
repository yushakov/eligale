package gallery.eliza.app.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import gallery.eliza.app.data.ProductImage
import gallery.eliza.app.ui.theme.ElizaGalleryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FullscreenImageViewerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun fakeImages(count: Int): List<ProductImage> =
        List(count) { i -> ProductImage(id = i, image_url = null, image_url_100 = null, image_url_200 = null, image_url_300 = null, order = i) }

    // ──────────────────────────────────────────────────────────────────
    // Видимость кнопки "В чат"
    // ──────────────────────────────────────────────────────────────────

    @Test
    fun chatButton_visibleWhenCallbackNotNull() {
        composeTestRule.setContent {
            ElizaGalleryTheme {
                FullscreenImageViewer(
                    images = fakeImages(1),
                    onDismiss = {},
                    onChatButtonClick = {},
                )
            }
        }
        composeTestRule.onNodeWithText("В чат").assertExists()
    }

    @Test
    fun chatButton_hiddenWhenCallbackNull() {
        composeTestRule.setContent {
            ElizaGalleryTheme {
                FullscreenImageViewer(
                    images = fakeImages(1),
                    onDismiss = {},
                    onChatButtonClick = null,
                )
            }
        }
        composeTestRule.onNodeWithText("В чат").assertDoesNotExist()
    }

    // ──────────────────────────────────────────────────────────────────
    // Колбэк при нажатии
    // ──────────────────────────────────────────────────────────────────

    @Test
    fun chatButton_click_firesCallbackWithPage0() {
        var capturedPage: Int? = null
        composeTestRule.setContent {
            ElizaGalleryTheme {
                FullscreenImageViewer(
                    images = fakeImages(1),
                    initialPage = 0,
                    onDismiss = {},
                    onChatButtonClick = { page -> capturedPage = page },
                )
            }
        }
        composeTestRule.onNodeWithText("В чат").performClick()
        assertEquals(0, capturedPage)
    }

    // ──────────────────────────────────────────────────────────────────
    // Всегда присутствующие кнопки
    // ──────────────────────────────────────────────────────────────────

    @Test
    fun downloadAndLinkButtons_alwaysVisible_withoutChatCallback() {
        composeTestRule.setContent {
            ElizaGalleryTheme {
                FullscreenImageViewer(
                    images = fakeImages(1),
                    onDismiss = {},
                    onChatButtonClick = null,
                )
            }
        }
        composeTestRule.onNodeWithText("Скачать").assertExists()
        composeTestRule.onNodeWithText("Ссылка").assertExists()
    }

    @Test
    fun downloadAndLinkButtons_alwaysVisible_withChatCallback() {
        composeTestRule.setContent {
            ElizaGalleryTheme {
                FullscreenImageViewer(
                    images = fakeImages(1),
                    onDismiss = {},
                    onChatButtonClick = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Скачать").assertExists()
        composeTestRule.onNodeWithText("Ссылка").assertExists()
    }
}
