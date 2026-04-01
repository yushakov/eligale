package gallery.eliza.app.data

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface ApiService {
    @GET("api/comments/my/")
    suspend fun getMyComments(@Header("Authorization") token: String): List<MyComment>

    @GET("api/search/")
    suspend fun search(
        @Header("Authorization") token: String,
        @Query("q") query: String,
    ): List<SearchResult>

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

    @POST("api/auth/logout/")
    suspend fun logout(@Header("Authorization") token: String)

    @DELETE("api/auth/delete-account/")
    suspend fun deleteAccount(@Header("Authorization") token: String)

    @POST("api/auth/record-consent/")
    suspend fun recordConsent(@Header("Authorization") token: String)

    // ── Комментарии (пользователь) ────────────────────────────────────────────

    @POST("api/comments/{id}/report/")
    suspend fun reportComment(
        @Header("Authorization") token: String,
        @Path("id") commentId: Int,
        @Body body: Map<String, String>
    )

    @DELETE("api/comments/{id}/delete/")
    suspend fun deleteOwnComment(
        @Header("Authorization") token: String,
        @Path("id") commentId: Int
    )

    // ── Staff комментарии ─────────────────────────────────────────────────────

    @GET("api/staff/comments/")
    suspend fun getStaffComments(@Header("Authorization") token: String): List<StaffComment>

    @GET("api/staff/comments/unread/")
    suspend fun getStaffCommentsUnread(@Header("Authorization") token: String): UnreadCount

    @POST("api/staff/comments/{id}/mark-read/")
    suspend fun markStaffCommentRead(
        @Header("Authorization") token: String,
        @Path("id") commentId: Int,
    )

    @DELETE("api/staff/comments/{id}/delete/")
    suspend fun deleteStaffComment(
        @Header("Authorization") token: String,
        @Path("id") commentId: Int,
    )

    // ── Избранное ─────────────────────────────────────────────────────────────

    @GET("api/favorites/")
    suspend fun getFavorites(@Header("Authorization") token: String): List<FavoriteItem>

    @POST("api/favorites/")
    suspend fun addFavorite(
        @Header("Authorization") token: String,
        @Body body: Map<String, Int>,
    )

    @DELETE("api/favorites/{imageId}/")
    suspend fun deleteFavorite(
        @Header("Authorization") token: String,
        @Path("imageId") imageId: Int,
    )

    // ── Медиа в чате ──────────────────────────────────────────────────────────

    @POST("api/chat/media/presign/")
    suspend fun chatMediaPresign(
        @Header("Authorization") token: String,
        @Body body: Map<String, String>,
    ): PresignResponse

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

    // ── Staff жалобы ──────────────────────────────────────────────────────────

    @GET("api/staff/reports/")
    suspend fun getStaffReports(@Header("Authorization") token: String): List<CommentReport>

    @GET("api/staff/reports/unread/")
    suspend fun getStaffReportsUnread(@Header("Authorization") token: String): UnreadCount

    @POST("api/staff/reports/{id}/dismiss/")
    suspend fun dismissReport(@Header("Authorization") token: String, @Path("id") reportId: Int)

    @POST("api/staff/reports/{id}/delete-comment/")
    suspend fun deleteCommentViaReport(@Header("Authorization") token: String, @Path("id") reportId: Int)
}

object Api {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
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
