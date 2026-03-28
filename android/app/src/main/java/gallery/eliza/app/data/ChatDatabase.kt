package gallery.eliza.app.data

import android.content.Context
import androidx.room.*

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: Int,
    val sender_email: String,
    val is_staff: Boolean,
    val text: String,
    val is_read: Boolean,
    val created_at: String,
)

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages ORDER BY id ASC")
    suspend fun getAll(): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE id < :beforeId ORDER BY id DESC LIMIT :limit")
    suspend fun getBefore(beforeId: Int, limit: Int = 50): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<ChatMessageEntity>)

    @Query("SELECT MIN(id) FROM chat_messages")
    suspend fun minId(): Int?

    @Query("SELECT MAX(id) FROM chat_messages")
    suspend fun maxId(): Int?

    @Query("SELECT COUNT(*) FROM chat_messages")
    suspend fun count(): Int

    @Query("UPDATE chat_messages SET is_read = 1 WHERE id <= :upToId AND is_staff = 1")
    suspend fun markReadUpTo(upToId: Int)
}

@Database(entities = [ChatMessageEntity::class], version = 1, exportSchema = false)
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
                ).build().also { instance = it }
            }
    }
}

fun ChatMessage.toEntity() = ChatMessageEntity(
    id = id,
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
