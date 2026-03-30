package gallery.eliza.app.util

import kotlinx.coroutines.delay

suspend fun <T> withRetry(
    attempts: Int = 3,
    delayMs: Long = 1000L,
    block: suspend () -> T,
): T {
    var last: Exception? = null
    for (i in 0 until attempts) {
        if (i > 0) delay(delayMs)
        try { return block() } catch (e: Exception) { last = e }
    }
    throw last!!
}
