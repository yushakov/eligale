package gallery.eliza.app.data

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

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

    @GET("api/auth/profile/")
    suspend fun getProfile(@Header("Authorization") token: String): UserProfile

    @DELETE("api/auth/delete-account/")
    suspend fun deleteAccount(@Header("Authorization") token: String)

    // ── Чат (пользователь) ────────────────────────────────────────────────────

    @GET("api/chat/")
    suspend fun getChatInfo(@Header("Authorization") token: String): ChatInfo

    @GET("api/chat/messages/")
    suspend fun getChatMessages(
        @Header("Authorization") token: String,
        @Query("after") after: Int? = null,
        @Query("before") before: Int? = null,
        @Query("limit") limit: Int = 50,
    ): List<ChatMessage>

    @POST("api/chat/messages/send/")
    suspend fun sendChatMessage(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>,
    ): ChatMessage

    @POST("api/chat/mark-read/")
    suspend fun markChatRead(
        @Header("Authorization") token: String,
        @Body body: Map<String, Int>,
    )

    @GET("api/chat/unread/")
    suspend fun getChatUnread(@Header("Authorization") token: String): UnreadCount

    // ── Чат (staff) ───────────────────────────────────────────────────────────

    @GET("api/chats/")
    suspend fun getStaffChatList(@Header("Authorization") token: String): List<ChatListItem>

    @GET("api/chats/unread/")
    suspend fun getStaffUnread(@Header("Authorization") token: String): UnreadCount

    @GET("api/chats/{userId}/messages/")
    suspend fun getStaffChatMessages(
        @Header("Authorization") token: String,
        @Path("userId") userId: Int,
        @Query("after") after: Int? = null,
        @Query("before") before: Int? = null,
        @Query("limit") limit: Int = 50,
    ): List<ChatMessage>

    @POST("api/chats/{userId}/messages/send/")
    suspend fun sendStaffChatMessage(
        @Header("Authorization") token: String,
        @Path("userId") userId: Int,
        @Body body: Map<String, String>,
    ): ChatMessage

    @POST("api/chats/{userId}/mark-read/")
    suspend fun markStaffChatRead(
        @Header("Authorization") token: String,
        @Path("userId") userId: Int,
        @Body body: Map<String, Int>,
    )
}

object Api {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    val service: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://eliza.gallery/")
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
