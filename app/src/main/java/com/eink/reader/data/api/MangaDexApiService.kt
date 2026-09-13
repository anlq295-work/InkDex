package com.eink.reader.data.api

import com.eink.reader.data.model.AtHomeServerResponse
import com.eink.reader.data.model.ChapterListResponse
import com.eink.reader.data.model.MangaItem
import com.eink.reader.data.model.MangaListResponse
import com.eink.reader.data.model.MangaReadingStatusResponse
import com.eink.reader.data.model.SingleMangaReadingStatusResponse
import com.eink.reader.data.repository.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class MangaDexApiService(
    private val settingsManager: SettingsManager? = null
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .dns(DoHDns {
            if (settingsManager?.useDoH == true) settingsManager.dohProvider else "system"
        })
        .addInterceptor { chain ->
            val builder = chain.request().newBuilder()
                .header("User-Agent", "InkDex-Reader/1.0 (Android; Color E-Ink Manga Reader)")
                .header("Accept", "application/json")
            
            val token = settingsManager?.sessionToken
            if (!token.isNullOrBlank() && chain.request().header("Authorization") == null) {
                builder.header("Authorization", "Bearer $token")
            }
            chain.proceed(builder.build())
        }
        .authenticator { _, response ->
            if (responseCount(response) >= 3) return@authenticator null
            val refreshTok = settingsManager?.refreshToken ?: return@authenticator null
            synchronized(this) {
                val currentToken = settingsManager.sessionToken
                val reqAuth = response.request.header("Authorization")
                if (reqAuth != null && reqAuth != "Bearer $currentToken" && !currentToken.isNullOrBlank()) {
                    return@authenticator response.request.newBuilder()
                        .header("Authorization", "Bearer $currentToken")
                        .build()
                }
                try {
                    val rawClient = OkHttpClient.Builder()
                        .dns(DoHDns { if (settingsManager.useDoH) settingsManager.dohProvider else "system" })
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(15, TimeUnit.SECONDS)
                        .build()
                    val refreshRes = kotlinx.coroutines.runBlocking {
                        OAuthHelper.refreshToken(
                            client = rawClient,
                            refreshToken = refreshTok,
                            clientId = settingsManager.personalClientId,
                            clientSecret = settingsManager.personalClientSecret,
                            proxyBaseUrl = settingsManager.apiBaseUrl
                        )
                    }
                    if (refreshRes.isSuccess) {
                        val tokenResp = refreshRes.getOrThrow()
                        settingsManager.sessionToken = tokenResp.accessToken
                        settingsManager.refreshToken = tokenResp.refreshToken
                        android.util.Log.d("MangaDexApi", "Token refreshed successfully via Authenticator!")
                        return@authenticator response.request.newBuilder()
                            .header("Authorization", "Bearer ${tokenResp.accessToken}")
                            .build()
                    } else {
                        android.util.Log.e("MangaDexApi", "Token refresh failed: ${refreshRes.exceptionOrNull()?.message}")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MangaDexApi", "Authenticator exception: ${e.message}", e)
                }
                null
            }
        }
        .build()

    private val baseUrl: String
        get() = settingsManager?.apiBaseUrl ?: "https://api.mangadex.org"

    private fun HttpUrl.Builder.applyContentRatings(): HttpUrl.Builder {
        val ratings = settingsManager?.getContentRatings() ?: listOf("safe", "suggestive")
        ratings.forEach { rating ->
            addQueryParameter("contentRating[]", rating)
        }
        return this
    }

    // 1. Phổ biến (Popular)
    suspend fun getPopularManga(limit: Int = 20, offset: Int = 0): MangaListResponse = withContext(Dispatchers.IO) {
        val url = "$baseUrl/manga".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("includes[]", "cover_art")
            .addQueryParameter("includes[]", "author")
            .addQueryParameter("order[followedCount]", "desc")
            .applyContentRatings()
            .build()

        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("Lỗi kết nối API: HTTP ${response.code}")
        val body = response.body?.string() ?: throw IOException("Phản hồi rỗng")
        json.decodeFromString<MangaListResponse>(body)
    }

    // 2. Truyện mới nổi bật (Popular New Titles)
    suspend fun getPopularNewTitles(limit: Int = 20, offset: Int = 0): MangaListResponse = withContext(Dispatchers.IO) {
        val url = "$baseUrl/manga".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("includes[]", "cover_art")
            .addQueryParameter("includes[]", "author")
            .addQueryParameter("hasAvailableChapters", "true")
            .addQueryParameter("order[rating]", "desc")
            .applyContentRatings()
            .build()

        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("Lỗi tải truyện mới nổi bật: HTTP ${response.code}")
        val body = response.body?.string() ?: throw IOException("Phản hồi rỗng")
        json.decodeFromString<MangaListResponse>(body)
    }

    // 3. Tải lên gần nhất (Latest Uploads)
    suspend fun getLatestUploads(limit: Int = 20, offset: Int = 0): MangaListResponse = withContext(Dispatchers.IO) {
        val url = "$baseUrl/manga".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("includes[]", "cover_art")
            .addQueryParameter("includes[]", "author")
            .addQueryParameter("order[latestUploadedChapter]", "desc")
            .applyContentRatings()
            .build()

        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("Lỗi tải chương mới nhất: HTTP ${response.code}")
        val body = response.body?.string() ?: throw IOException("Phản hồi rỗng")
        json.decodeFromString<MangaListResponse>(body)
    }

    // 4. Mới thêm gần đây (Recently Added)
    suspend fun getRecentlyAdded(limit: Int = 20, offset: Int = 0): MangaListResponse = withContext(Dispatchers.IO) {
        val url = "$baseUrl/manga".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("includes[]", "cover_art")
            .addQueryParameter("includes[]", "author")
            .addQueryParameter("order[createdAt]", "desc")
            .applyContentRatings()
            .build()

        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("Lỗi tải truyện mới thêm: HTTP ${response.code}")
        val body = response.body?.string() ?: throw IOException("Phản hồi rỗng")
        json.decodeFromString<MangaListResponse>(body)
    }

    // 5. Truyện ngẫu nhiên (Random Manga)
    suspend fun getRandomManga(): MangaItem = withContext(Dispatchers.IO) {
        val url = "$baseUrl/manga/random".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("includes[]", "cover_art")
            .addQueryParameter("includes[]", "author")
            .applyContentRatings()
            .build()

        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("Lỗi lấy truyện ngẫu nhiên: HTTP ${response.code}")
        val body = response.body?.string() ?: throw IOException("Phản hồi rỗng")

        val root = json.parseToJsonElement(body).jsonObject
        val dataObj = root["data"] ?: throw IOException("Không tìm thấy dữ liệu truyện")
        json.decodeFromJsonElement(MangaItem.serializer(), dataObj)
    }

    suspend fun searchManga(query: String, limit: Int = 24, offset: Int = 0): MangaListResponse = withContext(Dispatchers.IO) {
        val urlBuilder = "$baseUrl/manga".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("includes[]", "cover_art")
            .addQueryParameter("includes[]", "author")
            .addQueryParameter("order[relevance]", "desc")
            .applyContentRatings()

        if (query.isNotBlank()) {
            urlBuilder.addQueryParameter("title", query.trim())
        }

        val request = Request.Builder().url(urlBuilder.build()).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("Lỗi tìm kiếm: HTTP ${response.code}")
        val body = response.body?.string() ?: throw IOException("Phản hồi rỗng")
        json.decodeFromString<MangaListResponse>(body)
    }

    suspend fun getMangaDetails(mangaId: String): MangaItem = withContext(Dispatchers.IO) {
        val url = "$baseUrl/manga/$mangaId".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("includes[]", "cover_art")
            .addQueryParameter("includes[]", "author")
            .build()

        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("Lỗi tải chi tiết: HTTP ${response.code}")
        val body = response.body?.string() ?: throw IOException("Phản hồi rỗng")
        
        val root = json.parseToJsonElement(body).jsonObject
        val dataObj = root["data"] ?: throw IOException("Không tìm thấy dữ liệu truyện")
        json.decodeFromJsonElement(MangaItem.serializer(), dataObj)
    }

    suspend fun getChapterFeed(
        mangaId: String,
        languages: List<String> = listOf("vi", "en"),
        limit: Int = 500,
        offset: Int = 0
    ): ChapterListResponse = withContext(Dispatchers.IO) {
        val urlBuilder = "$baseUrl/manga/$mangaId/feed".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("order[chapter]", "asc")
            .addQueryParameter("includes[]", "scanlation_group")
            .applyContentRatings()

        if (!languages.contains("all") && languages.isNotEmpty()) {
            languages.forEach { lang ->
                urlBuilder.addQueryParameter("translatedLanguage[]", lang)
            }
        }

        val request = Request.Builder().url(urlBuilder.build()).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("Lỗi tải danh sách chương: HTTP ${response.code}")
        val body = response.body?.string() ?: throw IOException("Phản hồi rỗng")
        json.decodeFromString<ChapterListResponse>(body)
    }

    suspend fun getChapterPages(chapterId: String): AtHomeServerResponse = withContext(Dispatchers.IO) {
        val url = "$baseUrl/at-home/server/$chapterId".toHttpUrlOrNull()!!
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("Lỗi tải trang truyện: HTTP ${response.code}")
        val body = response.body?.string() ?: throw IOException("Phản hồi rỗng")
        json.decodeFromString<AtHomeServerResponse>(body)
    }

    // Xác thực tài khoản MangaDex (GET /user/me)
    suspend fun verifyUserMe(token: String): String = withContext(Dispatchers.IO) {
        val url = "$baseUrl/user/me".toHttpUrlOrNull()!!
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("Token không hợp lệ hoặc hết hạn (HTTP ${response.code})")
        val body = response.body?.string() ?: throw IOException("Phản hồi rỗng")
        val root = json.parseToJsonElement(body).jsonObject
        val dataObj = root["data"]?.jsonObject ?: throw IOException("Không có dữ liệu user")
        val attributes = dataObj["attributes"]?.jsonObject
        attributes?.get("username")?.toString()?.replace("\"", "") ?: "MangaDex User"
    }

    // Đánh dấu đã đọc trên MangaDex (POST /chapter/{id}/read)
    suspend fun markChapterRead(chapterId: String): Boolean = withContext(Dispatchers.IO) {
        val token = settingsManager?.sessionToken ?: return@withContext false
        val url = "$baseUrl/chapter/$chapterId/read".toHttpUrlOrNull()!!
        val request = Request.Builder()
            .url(url)
            .post(okhttp3.RequestBody.create(null, ByteArray(0)))
            .header("Authorization", "Bearer $token")
            .build()
        try {
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (_: Exception) {
            false
        }
    }

    // Lấy danh sách truyện đang theo dõi của người dùng (GET /user/follows/manga)
    // LƯU Ý: Endpoint này KHÔNG hỗ trợ contentRating[] (MangaDex sẽ trả về lỗi HTTP 400 nếu truyền vào)
    suspend fun getUserFollowedManga(limit: Int = 100, offset: Int = 0): MangaListResponse = withContext(Dispatchers.IO) {
        val token = settingsManager?.sessionToken ?: throw IOException("Chưa đăng nhập MangaDex")
        val urlBuilder = "$baseUrl/user/follows/manga".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("limit", limit.coerceAtMost(100).toString())
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("includes[]", "cover_art")
            .addQueryParameter("includes[]", "author")

        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("Authorization", "Bearer $token")
            .build()

        val response = client.newCall(request).execute()
        if (response.code == 401) throw IOException("MANGADEX_401_UNAUTHORIZED")
        if (!response.isSuccessful) throw IOException("Lỗi tải danh sách theo dõi: HTTP ${response.code}")
        val body = response.body?.string() ?: throw IOException("Phản hồi rỗng")
        json.decodeFromString<MangaListResponse>(body)
    }

    // Lấy danh sách Custom List (MDList) do người dùng tự tạo (GET /user/list)
    suspend fun getUserCustomLists(limit: Int = 100, offset: Int = 0): com.eink.reader.data.model.CustomListResponse = withContext(Dispatchers.IO) {
        val token = settingsManager?.sessionToken ?: throw IOException("Chưa đăng nhập MangaDex")
        val url = "$baseUrl/user/list".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("limit", limit.coerceAtMost(100).toString())
            .addQueryParameter("offset", offset.toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()

        val response = client.newCall(request).execute()
        if (response.code == 401) throw IOException("MANGADEX_401_UNAUTHORIZED")
        if (!response.isSuccessful) throw IOException("Lỗi tải Custom Lists: HTTP ${response.code}")
        val body = response.body?.string() ?: throw IOException("Phản hồi rỗng")
        json.decodeFromString<com.eink.reader.data.model.CustomListResponse>(body)
    }

    // Lấy danh sách Custom List mà người dùng đang theo dõi (GET /user/follows/list)
    suspend fun getUserFollowedCustomLists(limit: Int = 100, offset: Int = 0): com.eink.reader.data.model.CustomListResponse = withContext(Dispatchers.IO) {
        val token = settingsManager?.sessionToken ?: throw IOException("Chưa đăng nhập MangaDex")
        val url = "$baseUrl/user/follows/list".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("limit", limit.coerceAtMost(100).toString())
            .addQueryParameter("offset", offset.toString())
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()

        val response = client.newCall(request).execute()
        if (response.code == 401) throw IOException("MANGADEX_401_UNAUTHORIZED")
        if (!response.isSuccessful) throw IOException("Lỗi tải Followed Lists: HTTP ${response.code}")
        val body = response.body?.string() ?: throw IOException("Phản hồi rỗng")
        json.decodeFromString<com.eink.reader.data.model.CustomListResponse>(body)
    }

    // Tải thông tin nhiều manga theo danh sách ID (GET /manga?ids[]=...)
    suspend fun getMangaListByIds(ids: List<String>): MangaListResponse = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext MangaListResponse(result = "ok")
        val uniqueIds = ids.distinct().take(100)
        val urlBuilder = "$baseUrl/manga".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("limit", uniqueIds.size.toString())
            .addQueryParameter("includes[]", "cover_art")
            .addQueryParameter("includes[]", "author")
            .addQueryParameter("contentRating[]", "safe")
            .addQueryParameter("contentRating[]", "suggestive")
            .addQueryParameter("contentRating[]", "erotica")
            .addQueryParameter("contentRating[]", "pornographic")

        uniqueIds.forEach { id ->
            urlBuilder.addQueryParameter("ids[]", id)
        }

        val request = Request.Builder().url(urlBuilder.build()).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw IOException("Lỗi tải danh sách manga theo ID: HTTP ${response.code}")
        val body = response.body?.string() ?: throw IOException("Phản hồi rỗng")
        json.decodeFromString<MangaListResponse>(body)
    }

    // Kiểm tra truyện có đang được follow không (GET /user/follows/manga/{id})
    suspend fun isMangaFollowed(mangaId: String): Boolean = withContext(Dispatchers.IO) {
        val token = settingsManager?.sessionToken ?: return@withContext false
        val url = "$baseUrl/user/follows/manga/$mangaId".toHttpUrlOrNull()!!
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()
        try {
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (_: Exception) {
            false
        }
    }

    // Theo dõi truyện (POST /manga/{id}/follow)
    suspend fun followManga(mangaId: String): Boolean = withContext(Dispatchers.IO) {
        val token = settingsManager?.sessionToken ?: return@withContext false
        val url = "$baseUrl/manga/$mangaId/follow".toHttpUrlOrNull()!!
        val request = Request.Builder()
            .url(url)
            .post(okhttp3.RequestBody.create(null, ByteArray(0)))
            .header("Authorization", "Bearer $token")
            .build()
        try {
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (_: Exception) {
            false
        }
    }

    // Hủy theo dõi truyện (DELETE /manga/{id}/follow)
    suspend fun unfollowManga(mangaId: String): Boolean = withContext(Dispatchers.IO) {
        val token = settingsManager?.sessionToken ?: return@withContext false
        val url = "$baseUrl/manga/$mangaId/follow".toHttpUrlOrNull()!!
        val request = Request.Builder()
            .url(url)
            .delete()
            .header("Authorization", "Bearer $token")
            .build()
        try {
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (_: Exception) {
            false
        }
    }

    // Lấy toàn bộ trạng thái đọc MDList của người dùng (GET /manga/status)
    suspend fun getAllReadingStatuses(): Map<String, String> = withContext(Dispatchers.IO) {
        val token = settingsManager?.sessionToken ?: return@withContext emptyMap()
        val url = "$baseUrl/manga/status".toHttpUrlOrNull()!!
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()
        val response = client.newCall(request).execute()
        if (response.code == 401) throw IOException("MANGADEX_401_UNAUTHORIZED")
        if (!response.isSuccessful) return@withContext emptyMap()
        val body = response.body?.string() ?: return@withContext emptyMap()
        val parsed = json.decodeFromString<MangaReadingStatusResponse>(body)
        parsed.statuses
    }


    // Lấy trạng thái đọc của 1 manga cụ thể (GET /manga/{id}/status)
    suspend fun getMangaReadingStatus(mangaId: String): String? = withContext(Dispatchers.IO) {
        val token = settingsManager?.sessionToken ?: return@withContext null
        val url = "$baseUrl/manga/$mangaId/status".toHttpUrlOrNull()!!
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()
        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null
            val body = response.body?.string() ?: return@withContext null
            val parsed = json.decodeFromString<SingleMangaReadingStatusResponse>(body)
            parsed.status
        } catch (_: Exception) {
            null
        }
    }

    // Cập nhật trạng thái đọc của 1 manga (POST /manga/{id}/status)
    suspend fun updateMangaReadingStatus(mangaId: String, status: String?): Boolean = withContext(Dispatchers.IO) {
        val token = settingsManager?.sessionToken ?: return@withContext false
        val url = "$baseUrl/manga/$mangaId/status".toHttpUrlOrNull()!!
        val jsonBody = if (status != null) {
            """{"status": "$status"}"""
        } else {
            """{"status": null}"""
        }
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val requestBody = jsonBody.toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("Authorization", "Bearer $token")
            .build()
        try {
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (_: Exception) {
            false
        }
    }
}