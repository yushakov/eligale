package gallery.eliza.app.data

data class Category(
    val id: Int,
    val name: String,
    val cover_url: String?,
    val cover_url_600: String? = null,
    val product_count: Int = 0
)

data class Product(
    val id: Int,
    val name: String,
    val cover_url: String?,
    val cover_url_300: String? = null,
    val created_at: String,
    val image_count: Int = 0
)

data class ProductImage(
    val id: Int,
    val image_url: String?,        // оригинал — для fullscreen
    val image_url_100: String?,    // 100×100 — плитка товаров (4 колонки)
    val image_url_200: String?,    // 200×200 — галерея товара (3 колонки)
    val image_url_300: String?,    // 300×300 — обложки категорий
    val order: Int
)

data class ProductDetail(
    val id: Int,
    val name: String,
    val description: String,
    val cover_url: String?,
    val created_at: String,
    val images: List<ProductImage>
)

data class Comment(
    val id: Int,
    val user_id: Int,
    val user_email: String,
    val author: String,
    val text: String,
    val created_at: String
)

data class UserProfile(val email: String, val display_name: String, val is_staff: Boolean = false)

data class ChatMessage(
    val id: Int,
    val sender_email: String,
    val is_staff: Boolean,
    val text: String,
    val is_read: Boolean,
    val created_at: String,
)

data class ChatInfo(val chat_id: Int, val last_message_id: Int?)

data class UnreadCount(val unread: Int)

data class ChatListItem(
    val id: Int,
    val user_id: Int,
    val user_email: String,
    val user_display_name: String?,
    val unread_count: Int,
    val last_message: ChatLastMessage?,
    val last_message_at: String?,
)

data class ChatLastMessage(val text: String, val created_at: String)

data class StaffComment(
    val id: Int,
    val user_id: Int,
    val user_email: String,
    val user_display_name: String?,
    val product_id: Int,
    val product_name: String,
    val text: String,
    val is_read_by_staff: Boolean,
    val created_at: String,
)

data class MyComment(
    val id: Int,
    val product_id: Int,
    val product_name: String,
    val text: String,
    val created_at: String,
)

data class SearchResult(
    val type: String,           // "comment" or "message"
    val id: Int,
    val snippet: String,
    val created_at: String,
    val product_id: Int? = null,
    val product_name: String? = null,
    val author: String? = null,
    val chat_user_id: Int? = null,
    val user_email: String? = null,
)

data class RequestCodeBody(val email: String)
data class VerifyCodeBody(val email: String, val code: String)
data class TokenResponse(val token: String, val has_name: Boolean)
data class SetNameBody(val name: String)
data class SetNameResponse(val display_name: String)
data class PresignResponse(val upload_url: String, val public_url: String)

data class FavoriteItem(
    val product_id: Int,
    val product_name: String,
    val cover_url: String?,
    val cover_url_100: String?,
    val created_at: String,
)
