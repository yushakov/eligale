package gallery.eliza.app.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class RetryTest {

    @Test
    fun `success on first attempt makes exactly one call`() = runTest {
        var calls = 0
        val result = withRetry { calls++; "ok" }
        assertEquals("ok", result)
        assertEquals(1, calls)
    }

    @Test
    fun `retries after failure and succeeds on second attempt`() = runTest {
        var calls = 0
        val result = withRetry {
            calls++
            if (calls < 2) throw IOException("fail")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(2, calls)
    }

    @Test
    fun `retries after failure and succeeds on third attempt`() = runTest {
        var calls = 0
        val result = withRetry {
            calls++
            if (calls < 3) throw IOException("fail")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(3, calls)
    }

    @Test
    fun `throws after all attempts exhausted`() = runTest {
        var calls = 0
        try {
            withRetry(attempts = 3) { calls++; throw IOException("network error") }
            fail("expected exception")
        } catch (e: IOException) {
            assertEquals("network error", e.message)
        }
        assertEquals(3, calls)
    }

    @Test
    fun `throws last exception when all attempts fail`() = runTest {
        var calls = 0
        try {
            withRetry(attempts = 3) {
                calls++
                throw IOException("error $calls")
            }
            fail("expected exception")
        } catch (e: IOException) {
            assertEquals("error 3", e.message)
        }
    }

    @Test
    fun `exception with null message is propagated as-is`() = runTest {
        try {
            withRetry(attempts = 1) { throw IOException() }
            fail("expected exception")
        } catch (e: IOException) {
            assertEquals(null, e.message)
        }
    }

    @Test
    fun `delay is applied between attempts`() = runTest {
        val delayMs = 500L
        var calls = 0
        withRetry(attempts = 3, delayMs = delayMs) {
            calls++
            if (calls < 3) throw IOException("fail")
        }
        // 2 delays between 3 attempts
        assertEquals(delayMs * 2, testScheduler.currentTime)
    }

    @Test
    fun `no delay before first attempt`() = runTest {
        withRetry(attempts = 1, delayMs = 1000L) { /* success immediately */ }
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun `custom attempts count is respected`() = runTest {
        var calls = 0
        try {
            withRetry(attempts = 5) { calls++; throw IOException() }
            fail("expected exception")
        } catch (_: IOException) { }
        assertEquals(5, calls)
    }
}
