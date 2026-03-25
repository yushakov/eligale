package gallery.eliza.app.data

data class Category(
    val id: Int,
    val name: String,
    val cover_url: String?
)

data class Product(
    val id: Int,
    val name: String,
    val cover_url: String?,
    val created_at: String
)

data class ProductImage(
    val id: Int,
    val image_url: String?,
    val order: Int
)

data class ProductDetail(
    val id: Int,
    val name: String,
    val cover_url: String?,
    val created_at: String,
    val images: List<ProductImage>
)
