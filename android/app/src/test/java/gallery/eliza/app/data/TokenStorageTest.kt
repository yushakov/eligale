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
class TokenStorageTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        TokenStorage.clear(context)
    }

    @Test
    fun get_returnsNull_whenNoTokenSaved() {
        assertNull(TokenStorage.get(context))
    }

    @Test
    fun save_thenGet_returnsExactToken() {
        TokenStorage.save(context, "abc123token")
        assertEquals("abc123token", TokenStorage.get(context))
    }

    @Test
    fun clear_afterSave_returnsNull() {
        TokenStorage.save(context, "abc123token")
        TokenStorage.clear(context)
        assertNull(TokenStorage.get(context))
    }

    @Test
    fun save_overwritesPreviousToken() {
        TokenStorage.save(context, "token_first")
        TokenStorage.save(context, "token_second")
        assertEquals("token_second", TokenStorage.get(context))
    }
}
