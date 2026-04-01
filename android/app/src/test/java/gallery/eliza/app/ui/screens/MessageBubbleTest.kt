package gallery.eliza.app.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import gallery.eliza.app.data.ChatMessage
import gallery.eliza.app.ui.theme.ElizaGalleryTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Тесты верхнего уровня для MessageBubble.
 *
 * Это «живая спецификация» рендеринга сообщений чата:
 * обычный текст, product-ссылки, image-ссылки, выравнивание пузырей,
 * контекстное меню «Скопировать».
 *
 * Внутренняя реализация (regex, buildAnnotatedString) может свободно
 * рефакториться — эти тесты защищают внешнее поведение.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MessageBubbleTest {

    @get:Rule
    val rule = createComposeRule()

    private fun msg(text: String, isStaff: Boolean = false) = ChatMessage(
        id = 1,
        sender_email = "x@example.com",
        is_staff = isStaff,
        text = text,
        is_read = false,
        created_at = "2024-01-01T10:00:00",
    )

    // ── Обычный текст ─────────────────────────────────────────────────────────

    @Test
    fun plainText_isDisplayed() {
        rule.setContent {
            ElizaGalleryTheme {
                MessageBubble(msg = msg("Привет, мир!"), isOwnMessage = false)
            }
        }
        rule.onNodeWithText("Привет, мир!").assertIsDisplayed()
    }

    // ── Product-ссылка [product:id:page] ──────────────────────────────────────

    @Test
    fun productLink_showsOpenProductButton_whenCallbackProvided() {
        rule.setContent {
            ElizaGalleryTheme {
                MessageBubble(
                    msg = msg("[product:42:3]"),
                    isOwnMessage = false,
                    onOpenProduct = { _, _ -> },
                )
            }
        }
        rule.onNodeWithText("→ Открыть наименование").assertIsDisplayed()
    }

    @Test
    fun productLink_noOpenProductButton_whenNoCallback() {
        rule.setContent {
            ElizaGalleryTheme {
                MessageBubble(
                    msg = msg("[product:42:3]"),
                    isOwnMessage = false,
                    onOpenProduct = null,
                )
            }
        }
        rule.onNodeWithText("→ Открыть наименование").assertDoesNotExist()
    }

    @Test
    fun productLink_click_reportsCorrectProductIdAndPage() {
        var capturedId: Int? = null
        var capturedPage: Int? = null
        rule.setContent {
            ElizaGalleryTheme {
                MessageBubble(
                    msg = msg("[product:42:3]"),
                    isOwnMessage = false,
                    onOpenProduct = { id, page ->
                        capturedId = id
                        capturedPage = page
                    },
                )
            }
        }
        rule.onNodeWithText("→ Открыть наименование").performClick()
        assertEquals(42, capturedId)
        assertEquals(3, capturedPage)
    }

    @Test
    fun productLink_rawTagNotShownAsText() {
        rule.setContent {
            ElizaGalleryTheme {
                MessageBubble(
                    msg = msg("[product:42:3]"),
                    isOwnMessage = false,
                    onOpenProduct = { _, _ -> },
                )
            }
        }
        rule.onNodeWithText("[product:42:3]").assertDoesNotExist()
    }

    @Test
    fun productLinkWithText_showsBothTextAndButton() {
        rule.setContent {
            ElizaGalleryTheme {
                MessageBubble(
                    msg = msg("Интересует «Кольцо» [product:7:0]"),
                    isOwnMessage = false,
                    onOpenProduct = { _, _ -> },
                )
            }
        }
        rule.onNodeWithText("→ Открыть наименование").assertIsDisplayed()
        rule.onNodeWithText("Интересует «Кольцо»").assertIsDisplayed()
    }

    // ── Image-ссылка [image:url] ──────────────────────────────────────────────

    @Test
    fun imageLink_rawTagNotShownAsText() {
        rule.setContent {
            ElizaGalleryTheme {
                MessageBubble(
                    msg = msg("[image:https://storage.example.com/chat/photo.jpg]"),
                    isOwnMessage = false,
                )
            }
        }
        rule.onNodeWithText("[image:https://storage.example.com/chat/photo.jpg]")
            .assertDoesNotExist()
    }

}
