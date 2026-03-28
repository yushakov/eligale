package gallery.eliza.app.data

import android.content.Context
import androidx.room.*

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: Int,
    val chat_user_id: Int,   // 0 = own chat (user mode), >0 = staff viewing that userId's chat
    val sender_email: String,
    val is_staff: Boolean,
    val text: String,
    val is_read: Boolean,
    val created_at: String,
)

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE chat_user_id = :chatUserId ORDER BY id ASC")
    suspend fun getAll(chatUserId: Int): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<ChatMessageEntity>)

    @Query("SELECT MIN(id) FROM chat_messages WHERE chat_user_id = :chatUserId")
    suspend fun minId(chatUserId: Int): Int?

    @Query("SELECT MAX(id) FROM chat_messages WHERE chat_user_id = :chatUserId")
    suspend fun maxId(chatUserId: Int): Int?

    @Query("UPDATE chat_messages SET is_read = 1 WHERE chat_user_id = :chatUserId AND id <= :upToId AND is_staff = 1")
    suspend fun markReadUpTo(chatUserId: Int, upToId: Int)
}

@Database(entities = [ChatMessageEntity::class], version = 2, exportSchema = false)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun messageDao(): ChatMessageDao

    companion object {
        @Volatile private var instance: ChatDatabase? = null

        fun get(context: Context): ChatDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ChatDatabase::class.java,
                    "chat_db"
                )
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}

fun ChatMessage.toEntity(chatUserId: Int) = ChatMessageEntity(
    id = id,
    chat_user_id = chatUserId,
    sender_email = sender_email,
    is_staff = is_staff,
    text = text,
    is_read = is_read,
    created_at = created_at,
)

fun ChatMessageEntity.toModel() = ChatMessage(
    id = id,
    sender_email = sender_email,
    is_staff = is_staff,
    text = text,
    is_read = is_read,
    created_at = created_at,
)
