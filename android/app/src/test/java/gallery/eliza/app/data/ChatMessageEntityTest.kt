package gallery.eliza.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Тесты конвертации ChatMessage ↔ ChatMessageEntity.
 * Гарантируют, что при сохранении в Room и чтении из Room
 * ни одно поле не теряется и chat_user_id проставляется корректно.
 */
class ChatMessageEntityTest {

    @Test
    fun toModel_mapsAllFieldsCorrectly() {
        val entity = ChatMessageEntity(
            id = 42,
            chat_user_id = 7,
            sender_email = "user@example.com",
            is_staff = true,
            text = "Привет!",
            is_read = false,
            created_at = "2024-06-01T12:00:00",
        )
        val model = entity.toModel()
        assertEquals(42, model.id)
        assertEquals("user@example.com", model.sender_email)
        assertEquals(true, model.is_staff)
        assertEquals("Привет!", model.text)
        assertEquals(false, model.is_read)
        assertEquals("2024-06-01T12:00:00", model.created_at)
    }

    @Test
    fun toEntity_mapsAllFieldsCorrectly() {
        val model = ChatMessage(
            id = 99,
            sender_email = "staff@example.com",
            is_staff = false,
            text = "Ответ",
            is_read = true,
            created_at = "2024-07-15T09:30:00",
        )
        val entity = model.toEntity(chatUserId = 5)
        assertEquals(99, entity.id)
        assertEquals(5, entity.chat_user_id)
        assertEquals("staff@example.com", entity.sender_email)
        assertEquals(false, entity.is_staff)
        assertEquals("Ответ", entity.text)
        assertEquals(true, entity.is_read)
        assertEquals("2024-07-15T09:30:00", entity.created_at)
    }

    @Test
    fun toEntity_chatUserIdZero_forOwnChat() {
        val model = ChatMessage(
            id = 1, sender_email = "x@x.com", is_staff = false,
            text = "msg", is_read = false, created_at = "2024-01-01",
        )
        assertEquals(0, model.toEntity(chatUserId = 0).chat_user_id)
    }

    @Test
    fun toEntity_chatUserIdSet_forStaffViewingUser() {
        val model = ChatMessage(
            id = 1, sender_email = "x@x.com", is_staff = true,
            text = "msg", is_read = false, created_at = "2024-01-01",
        )
        assertEquals(42, model.toEntity(chatUserId = 42).chat_user_id)
    }

    @Test
    fun roundTrip_preservesAllFields() {
        val original = ChatMessage(
            id = 7,
            sender_email = "round@trip.com",
            is_staff = true,
            text = "Туда и обратно",
            is_read = true,
            created_at = "2025-03-15T18:45:00",
        )
        val restored = original.toEntity(chatUserId = 3).toModel()
        assertEquals(original.id, restored.id)
        assertEquals(original.sender_email, restored.sender_email)
        assertEquals(original.is_staff, restored.is_staff)
        assertEquals(original.text, restored.text)
        assertEquals(original.is_read, restored.is_read)
        assertEquals(original.created_at, restored.created_at)
    }
}
