package gallery.eliza.app.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

interface ApiService {
    @GET("api/categories/")
    suspend fun getCategories(): List<Category>

    @GET("api/categories/{id}/products/")
    suspend fun getProducts(@Path("id") categoryId: Int): List<Product>

    @GET("api/products/{id}/")
    suspend fun getProduct(@Path("id") productId: Int): ProductDetail

    @GET("api/products/{id}/comments/")
    suspend fun getComments(@Path("id") productId: Int): List<Comment>

    @POST("api/products/{id}/comments/")
    suspend fun postComment(
        @Path("id") productId: Int,
        @Header("Authorization") token: String,
        @Body body: Map<String, String>
    ): Comment

    @POST("api/auth/request-code/")
    suspend fun requestCode(@Body body: RequestCodeBody)

    @POST("api/auth/verify-code/")
    suspend fun verifyCode(@Body body: VerifyCodeBody): TokenResponse

    @POST("api/auth/set-name/")
    suspend fun setName(
        @Header("Authorization") token: String,
        @Body body: SetNameBody
    ): SetNameResponse
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
