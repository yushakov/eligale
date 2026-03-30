package gallery.eliza.app.util

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class CachePolicyTest {

    // ──────────────────────────────────────────────────────────────────
    // shouldShowSpinner
    // ──────────────────────────────────────────────────────────────────

    @Test
    fun `no cached data — spinner is shown`() {
        assertTrue(shouldShowSpinner(hasCachedData = false))
    }

    @Test
    fun `cached data present — spinner suppressed`() {
        assertFalse(shouldShowSpinner(hasCachedData = true))
    }

    // ──────────────────────────────────────────────────────────────────
    // errorMessageForDisplay
    // ──────────────────────────────────────────────────────────────────

    @Test
    fun `no cached data — error message is shown`() {
        val msg = errorMessageForDisplay(hasCachedData = false, exception = IOException("network fail"))
        assertEquals("network fail", msg)
    }

    @Test
    fun `cached data present — error is suppressed`() {
        val msg = errorMessageForDisplay(hasCachedData = true, exception = IOException("network fail"))
        assertNull(msg)
    }

    @Test
    fun `no cached data, null exception message — fallback text shown`() {
        val msg = errorMessageForDisplay(hasCachedData = false, exception = IOException())
        assertEquals("Ошибка загрузки", msg)
    }

    @Test
    fun `cached data present, null exception message — error still suppressed`() {
        val msg = errorMessageForDisplay(hasCachedData = true, exception = IOException())
        assertNull(msg)
    }
}
