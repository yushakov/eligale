package gallery.eliza.app.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

interface ApiService {
    @GET("api/categories/")
    suspend fun getCategories(): List<Category>

    @GET("api/categories/{id}/products/")
    suspend fun getProducts(@Path("id") categoryId: Int): List<Product>

    @GET("api/products/{id}/")
    suspend fun getProduct(@Path("id") productId: Int): ProductDetail
}

object Api {
    val service: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://eliza.gallery/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
