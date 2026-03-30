package gallery.eliza.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiskCacheTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        DiskCache.init(context)
        DiskCache.clearAll()
    }

    // ──────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────

    /** Simulate app restart: clear in-memory cache, re-read from disk. */
    private fun restart() {
        DataCache.categories = null
        DataCache.products.clear()
        DataCache.productDetail.clear()
        DataCache.comments.clear()
        DiskCache.init(context)
    }

    // ──────────────────────────────────────────────────────────────────
    // Categories
    // ──────────────────────────────────────────────────────────────────

    @Test
    fun `categories null when nothing saved`() {
        DiskCache.init(context)
        assertNull(DataCache.categories)
    }

    @Test
    fun `categories restored after app restart`() {
        val cats = listOf(Category(1, "Посуда", "url", "url600", 5))
        DiskCache.saveCategories(cats)
        restart()
        assertEquals(cats, DataCache.categories)
    }

    // ──────────────────────────────────────────────────────────────────
    // Products
    // ──────────────────────────────────────────────────────────────────

    @Test
    fun `products restored by categoryId after restart`() {
        val products = listOf(Product(10, "Тарелка", "url", null, "2024-01-01", 3))
        DiskCache.saveProducts(42, products)
        restart()
        assertEquals(products, DataCache.products[42])
    }

    @Test
    fun `products isolated by categoryId`() {
        DiskCache.saveProducts(1, listOf(Product(1, "A", null, null, "2024", 0)))
        DiskCache.saveProducts(2, listOf(Product(2, "B", null, null, "2024", 0)))
        restart()
        assertEquals("A", DataCache.products[1]!![0].name)
        assertEquals("B", DataCache.products[2]!![0].name)
        assertNull(DataCache.products[3])
    }

    // ──────────────────────────────────────────────────────────────────
    // ProductDetail
    // ──────────────────────────────────────────────────────────────────

    @Test
    fun `productDetail restored after restart`() {
        val detail = ProductDetail(5, "Ваза", "desc", null, "2024-01-01", emptyList())
        DiskCache.saveProductDetail(5, detail)
        restart()
        assertEquals(detail, DataCache.productDetail[5])
    }

    // ──────────────────────────────────────────────────────────────────
    // Comments
    // ──────────────────────────────────────────────────────────────────

    @Test
    fun `comments restored after restart`() {
        val comments = listOf(Comment(1, 2, "a@b.com", "Автор", "Текст", "2024-01-01"))
        DiskCache.saveComments(5, comments)
        restart()
        assertEquals(comments, DataCache.comments[5])
    }

    // ──────────────────────────────────────────────────────────────────
    // clearAll
    // ──────────────────────────────────────────────────────────────────

    @Test
    fun `clearAll wipes disk and memory`() {
        DiskCache.saveCategories(listOf(Category(1, "Cat", null)))
        DiskCache.saveProducts(1, listOf(Product(1, "P", null, null, "2024", 0)))
        DiskCache.clearAll()
        restart()
        assertNull(DataCache.categories)
        assertTrue(DataCache.products.isEmpty())
    }
}
