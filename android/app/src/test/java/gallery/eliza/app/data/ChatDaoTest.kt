package gallery.eliza.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Тесты ChatMessageDao с in-memory Room базой.
 *
 * Ключевые сценарии:
 * - Изоляция сообщений по chat_user_id (0 = свой чат, N = staff смотрит чат пользователя N)
 * - markReadUpTo помечает только сообщения от staff (is_staff = true) и только до upToId
 * - min/max ID корректно считаются в пределах одного chat_user_id
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChatDaoTest {

    private lateinit var db: ChatDatabase
    private lateinit var dao: ChatMessageDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ChatDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.messageDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun msg(
        id: Int,
        chatUserId: Int = 0,
        isStaff: Boolean = false,
        isRead: Boolean = false,
        text: String = "Сообщение $id",
    ) = ChatMessageEntity(
        id = id,
        chat_user_id = chatUserId,
        sender_email = "sender@example.com",
        is_staff = isStaff,
        text = text,
        is_read = isRead,
        created_at = "2024-01-01T10:00:00",
    )

    // ── getAll ────────────────────────────────────────────────────────────────

    @Test
    fun getAll_returnsEmpty_whenNothingInserted() = runBlocking {
        assertTrue(dao.getAll(0).isEmpty())
    }

    @Test
    fun getAll_returnsInsertedMessages() = runBlocking {
        dao.insertAll(listOf(msg(1), msg(2)))
        assertEquals(2, dao.getAll(0).size)
    }

    @Test
    fun getAll_orderedByIdAscending() = runBlocking {
        dao.insertAll(listOf(msg(3), msg(1), msg(2)))
        assertEquals(listOf(1, 2, 3), dao.getAll(0).map { it.id })
    }

    @Test
    fun getAll_isolatesByChatUserId() = runBlocking {
        dao.insertAll(listOf(msg(1, chatUserId = 0), msg(2, chatUserId = 5)))
        assertEquals(1, dao.getAll(0).size)
        assertEquals(1, dao.getAll(5).size)
        assertEquals(0, dao.getAll(99).size)
    }

    @Test
    fun getAll_doesNotReturnOtherUsersMessages() = runBlocking {
        dao.insertAll(listOf(msg(1, chatUserId = 0), msg(2, chatUserId = 7)))
        val ids = dao.getAll(0).map { it.id }
        assertFalse(2 in ids)
    }

    // ── insertAll OnConflict.REPLACE ──────────────────────────────────────────

    @Test
    fun insertAll_replacesExistingMessageOnSameId() = runBlocking {
        dao.insertAll(listOf(msg(1, text = "Старое")))
        dao.insertAll(listOf(msg(1, text = "Новое")))
        val result = dao.getAll(0)
        assertEquals(1, result.size)
        assertEquals("Новое", result[0].text)
    }

    // ── minId / maxId ─────────────────────────────────────────────────────────

    @Test
    fun minId_returnsNull_whenEmpty() = runBlocking {
        assertNull(dao.minId(0))
    }

    @Test
    fun minId_returnsSmallestId() = runBlocking {
        dao.insertAll(listOf(msg(5), msg(2), msg(8)))
        assertEquals(2, dao.minId(0))
    }

    @Test
    fun minId_isolatesByChatUserId() = runBlocking {
        dao.insertAll(listOf(msg(1, chatUserId = 0), msg(10, chatUserId = 3)))
        assertEquals(1, dao.minId(0))
        assertEquals(10, dao.minId(3))
    }

    @Test
    fun maxId_returnsNull_whenEmpty() = runBlocking {
        assertNull(dao.maxId(0))
    }

    @Test
    fun maxId_returnsLargestId() = runBlocking {
        dao.insertAll(listOf(msg(5), msg(2), msg(8)))
        assertEquals(8, dao.maxId(0))
    }

    @Test
    fun maxId_isolatesByChatUserId() = runBlocking {
        dao.insertAll(listOf(msg(100, chatUserId = 0), msg(5, chatUserId = 3)))
        assertEquals(100, dao.maxId(0))
        assertEquals(5, dao.maxId(3))
    }

    // ── markReadUpTo ──────────────────────────────────────────────────────────

    @Test
    fun markReadUpTo_marksStaffMessagesAsRead() = runBlocking {
        dao.insertAll(listOf(msg(1, isStaff = true), msg(2, isStaff = true)))
        dao.markReadUpTo(chatUserId = 0, upToId = 2)
        assertTrue(dao.getAll(0).all { it.is_read })
    }

    @Test
    fun markReadUpTo_doesNotMarkMessagesAfterUpToId() = runBlocking {
        dao.insertAll(listOf(msg(1, isStaff = true), msg(2, isStaff = true)))
        dao.markReadUpTo(chatUserId = 0, upToId = 1)
        val byId = dao.getAll(0).associateBy { it.id }
        assertTrue(byId[1]!!.is_read)
        assertFalse(byId[2]!!.is_read)
    }

    @Test
    fun markReadUpTo_doesNotMarkUserOwnMessages() = runBlocking {
        dao.insertAll(listOf(msg(1, isStaff = false)))
        dao.markReadUpTo(chatUserId = 0, upToId = 1)
        assertFalse(dao.getAll(0)[0].is_read)
    }

    @Test
    fun markReadUpTo_isolatesByChatUserId() = runBlocking {
        dao.insertAll(listOf(
            msg(1, chatUserId = 0, isStaff = true),
            msg(1, chatUserId = 5, isStaff = true),
        ))
        dao.markReadUpTo(chatUserId = 0, upToId = 1)
        // Чат пользователя 5 не должен быть затронут
        assertFalse(dao.getAll(5)[0].is_read)
    }

    @Test
    fun markReadUpTo_noEffect_whenNothingMatches() = runBlocking {
        dao.insertAll(listOf(msg(1, isStaff = true)))
        dao.markReadUpTo(chatUserId = 0, upToId = 0)  // upToId меньше любого id
        assertFalse(dao.getAll(0)[0].is_read)
    }
}
