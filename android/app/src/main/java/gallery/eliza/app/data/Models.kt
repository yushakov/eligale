package gallery.eliza.app.data

data class Category(
    val id: Int,
    val name: String,
    val cover_url: String?,
    val product_count: Int = 0
)

data class Product(
    val id: Int,
    val name: String,
    val cover_url: String?,
    val created_at: String,
    val image_count: Int = 0
)

data class ProductImage(
    val id: Int,
    val image_url: String?,
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
    val author: String,
    val text: String,
    val created_at: String
)

data class UserProfile(val email: String, val display_name: String)

data class RequestCodeBody(val email: String)
data class VerifyCodeBody(val email: String, val code: String)
data class TokenResponse(val token: String, val has_name: Boolean)
data class SetNameBody(val name: String)
data class SetNameResponse(val display_name: String)
