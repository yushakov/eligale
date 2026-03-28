package gallery.eliza.app.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import gallery.eliza.app.data.ProductImage
import gallery.eliza.app.ui.theme.ElizaGalleryTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Тесты для ProductGallery — проверяют, что pagerState корректно отражает initialPage
 * и передаёт текущую страницу в колбэки. Это гарантирует, что кнопка "В чат"
 * всегда отправляет правильный индекс фото.
 */
@RunWith(AndroidJUnit4::class)
class ProductGalleryTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // image_url = null: Coil ничего не загружает, но UI-структура сохраняется
    private fun fakeImages(count: Int): List<ProductImage> =
        List(count) { i -> ProductImage(id = i, image_url = null, order = i) }

    // ──────────────────────────────────────────────────────────────────
    // Счётчик страниц
    // ──────────────────────────────────────────────────────────────────

    @Test
    fun gallery_showsCorrectInitialPageCounter() {
        composeTestRule.setContent {
            ElizaGalleryTheme {
                ProductGallery(
                    images = fakeImages(5),
                    initialPage = 2,
                    onFullscreen = {},
                    onChatButtonClick = null,
                )
            }
        }
        // Должно показывать "3 / 5" (страницы с 1, не с 0)
        composeTestRule.onNodeWithText("3 / 5").assertIsDisplayed()
    }

    @Test
    fun gallery_noCounter_onSingleImage() {
        composeTestRule.setContent {
            ElizaGalleryTheme {
                ProductGallery(
                    images = fakeImages(1),
                    initialPage = 0,
                    onFullscreen = {},
                    onChatButtonClick = null,
                )
            }
        }
        // На одной картинке счётчик не нужен
        composeTestRule.onNodeWithText("1 / 1").assertDoesNotExist()
    }

    // ──────────────────────────────────────────────────────────────────
    // Кнопка "В чат"
    // ──────────────────────────────────────────────────────────────────

    @Test
    fun gallery_chatButtonVisible_whenCallbackProvided() {
        composeTestRule.setContent {
            ElizaGalleryTheme {
                ProductGallery(
                    images = fakeImages(3),
                    initialPage = 0,
                    onFullscreen = {},
                    onChatButtonClick = {},
                )
            }
        }
        composeTestRule.onNodeWithText("В чат").assertIsDisplayed()
    }

    @Test
    fun gallery_chatButtonAbsent_whenNoCallback() {
        composeTestRule.setContent {
            ElizaGalleryTheme {
                ProductGallery(
                    images = fakeImages(3),
                    initialPage = 0,
                    onFullscreen = {},
                    onChatButtonClick = null,
                )
            }
        }
        composeTestRule.onNodeWithText("В чат").assertDoesNotExist()
    }

    // ──────────────────────────────────────────────────────────────────
    // Ключевой тест: колбэк передаёт ТЕКУЩУЮ страницу
    // ──────────────────────────────────────────────────────────────────

    @Test
    fun gallery_chatButtonClick_reportsInitialPageAsCurrentPage() {
        // Этот тест защищает от регрессии: если pagerState создаётся не там где надо,
        // он может игнорировать initialPage и всегда возвращать 0.
        var capturedPage: Int? = null
        composeTestRule.setContent {
            ElizaGalleryTheme {
                ProductGallery(
                    images = fakeImages(5),
                    initialPage = 3,
                    onFullscreen = {},
                    onChatButtonClick = { page -> capturedPage = page },
                )
            }
        }
        composeTestRule.onNodeWithText("В чат").performClick()
        assertEquals("Кнопка должна передать текущую страницу (3), а не 0", 3, capturedPage)
    }
}
