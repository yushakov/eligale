package gallery.eliza.app.data

/**
 * In-memory cache для трёх экранов каталога.
 * Позволяет показывать ранее загруженные данные мгновенно при навигации назад,
 * пока фоновое обновление с сервера ещё выполняется.
 * Живёт пока живёт процесс приложения.
 */
object DataCache {
    var categories: List<Category>? = null
    val products: MutableMap<Int, List<Product>> = mutableMapOf()
    val productDetail: MutableMap<Int, ProductDetail> = mutableMapOf()
    val comments: MutableMap<Int, List<Comment>> = mutableMapOf()
    val favoriteProductIds: MutableSet<Int> = mutableSetOf()
}
