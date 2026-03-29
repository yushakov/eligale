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

/**
 * Тесты для ProductGallery — сетка 3×N.
 * Проверяют, что все фотографии присутствуют в дереве семантики
 * и что тап на ячейку передаёт правильный индекс в колбэк.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProductGalleryTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // image_url = null: Coil ничего не загружает, но UI-структура сохраняется
    private fun fakeImages(count: Int): List<ProductImage> =
        List(count) { i -> ProductImage(id = i, image_url = null, image_url_100 = null, image_url_200 = null, image_url_300 = null, order = i) }

    // ──────────────────────────────────────────────────────────────────
    // Наличие элементов в сетке
    // ──────────────────────────────────────────────────────────────────

    @Test
    fun gallery_allPhotosPresent_byContentDescription() {
        composeTestRule.setContent {
            ElizaGalleryTheme {
                ProductGallery(images = fakeImages(5), onPhotoTap = {})
            }
        }
        for (i in 1..5) {
            composeTestRule.onNodeWithContentDescription("Фото $i").assertExists()
        }
    }

    @Test
    fun gallery_singleImage_rendersWithoutCrash() {
        composeTestRule.setContent {
            ElizaGalleryTheme {
                ProductGallery(images = fakeImages(1), onPhotoTap = {})
            }
        }
        composeTestRule.onNodeWithContentDescription("Фото 1").assertExists()
    }

    @Test
    fun gallery_emptyImages_rendersWithoutCrash() {
        composeTestRule.setContent {
            ElizaGalleryTheme {
                ProductGallery(images = emptyList(), onPhotoTap = {})
            }
        }
        // Просто проверяем отсутствие краша
    }

    // ──────────────────────────────────────────────────────────────────
    // Тап передаёт правильный индекс
    // ──────────────────────────────────────────────────────────────────

    @Test
    fun gallery_tapFirstPhoto_reportsIndex0() {
        var capturedIndex: Int? = null
        composeTestRule.setContent {
            ElizaGalleryTheme {
                ProductGallery(images = fakeImages(5), onPhotoTap = { capturedIndex = it })
            }
        }
        composeTestRule.onNodeWithContentDescription("Фото 1").performClick()
        assertEquals(0, capturedIndex)
    }

    @Test
    fun gallery_tapThirdPhoto_reportsIndex2() {
        var capturedIndex: Int? = null
        composeTestRule.setContent {
            ElizaGalleryTheme {
                ProductGallery(images = fakeImages(5), onPhotoTap = { capturedIndex = it })
            }
        }
        composeTestRule.onNodeWithContentDescription("Фото 3").performClick()
        assertEquals(2, capturedIndex)
    }

    @Test
    fun gallery_tapLastPhoto_reportsCorrectIndex() {
        var capturedIndex: Int? = null
        composeTestRule.setContent {
            ElizaGalleryTheme {
                ProductGallery(images = fakeImages(4), onPhotoTap = { capturedIndex = it })
            }
        }
        composeTestRule.onNodeWithContentDescription("Фото 4").performClick()
        assertEquals(3, capturedIndex)
    }
}
