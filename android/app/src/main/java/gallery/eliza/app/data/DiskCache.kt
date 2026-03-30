package gallery.eliza.app.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.lang.reflect.Type

/**
 * Persistent JSON cache for catalog screens.
 * Call [init] once in Application/MainActivity before setContent.
 * Subsequent [save*] calls write to disk synchronously (files are tiny).
 */
object DiskCache {
    private val gson = Gson()
    private var dir: File? = null

    fun init(context: Context) {
        dir = context.applicationContext.cacheDir
        preload()
    }

    private fun preload() {
        val d = dir ?: return
        DataCache.categories = loadList(File(d, "categories.json"),
            object : TypeToken<List<Category>>() {}.type)

        DataCache.products.clear()
        d.listFiles { f -> f.name.startsWith("products_") && f.extension == "json" }
            ?.forEach { f ->
                val id = f.nameWithoutExtension.removePrefix("products_").toIntOrNull() ?: return@forEach
                DataCache.products[id] = loadList(f, object : TypeToken<List<Product>>() {}.type) ?: return@forEach
            }

        DataCache.productDetail.clear()
        d.listFiles { f -> f.name.startsWith("detail_") && f.extension == "json" }
            ?.forEach { f ->
                val id = f.nameWithoutExtension.removePrefix("detail_").toIntOrNull() ?: return@forEach
                DataCache.productDetail[id] = loadOne(f, ProductDetail::class.java) ?: return@forEach
            }

        DataCache.comments.clear()
        d.listFiles { f -> f.name.startsWith("comments_") && f.extension == "json" }
            ?.forEach { f ->
                val id = f.nameWithoutExtension.removePrefix("comments_").toIntOrNull() ?: return@forEach
                DataCache.comments[id] = loadList(f, object : TypeToken<List<Comment>>() {}.type) ?: return@forEach
            }
    }

    fun saveCategories(list: List<Category>) = write("categories.json", list)
    fun saveProducts(categoryId: Int, list: List<Product>) = write("products_$categoryId.json", list)
    fun saveProductDetail(productId: Int, detail: ProductDetail) = write("detail_$productId.json", detail)
    fun saveComments(productId: Int, list: List<Comment>) = write("comments_$productId.json", list)

    /** Removes all cached files and clears the in-memory cache. */
    fun clearAll() {
        dir?.listFiles()?.forEach { it.delete() }
        DataCache.categories = null
        DataCache.products.clear()
        DataCache.productDetail.clear()
        DataCache.comments.clear()
    }

    private fun write(filename: String, data: Any) {
        try { File(dir ?: return, filename).writeText(gson.toJson(data)) } catch (_: Exception) {}
    }

    private fun <T> loadList(file: File, type: Type): List<T>? = try {
        gson.fromJson(file.readText(), type)
    } catch (_: Exception) { null }

    private fun <T> loadOne(file: File, clazz: Class<T>): T? = try {
        gson.fromJson(file.readText(), clazz)
    } catch (_: Exception) { null }
}
