package com.eink.reader.data.repository

import com.eink.reader.data.api.MangaDexApiService
import com.eink.reader.data.model.ChapterItem
import com.eink.reader.data.model.MangaItem

data class HomeFeedCache(
    val followedList: List<MangaItem>,
    val popularNewList: List<MangaItem>,
    val latestUploadsList: List<MangaItem>,
    val recentlyAddedList: List<MangaItem>,
    val popularList: List<MangaItem>,
    val randomList: List<MangaItem>,
    val ratingsKey: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class LibrarySyncCache(
    val mangaList: List<MangaItem>,
    val statusesMap: Map<String, String>,
    val timestamp: Long = System.currentTimeMillis()
)

class MangaRepository(
    val settingsManager: SettingsManager,
    val apiService: MangaDexApiService = MangaDexApiService(settingsManager),
    val tagCacheManager: MangaTagCacheManager = MangaTagCacheManager.getInstance(settingsManager.context),
    val readingHistoryManager: ReadingHistoryManager = ReadingHistoryManager.getInstance(settingsManager.context)
) {
    var homeFeedCache: HomeFeedCache? = null
    var librarySyncCache: LibrarySyncCache? = null

    fun getRatingsKey(): String {
        return settingsManager.getContentRatings().sorted().joinToString(",")
    }

    fun invalidateHomeFeedCache() {
        homeFeedCache = null
    }

    suspend fun getPopularManga(limit: Int = 20, offset: Int = 0): Result<List<MangaItem>> {
        return runCatching {
            apiService.getPopularManga(limit, offset).data
        }
    }

    suspend fun getPopularNewTitles(limit: Int = 20, offset: Int = 0): Result<List<MangaItem>> {
        return runCatching {
            apiService.getPopularNewTitles(limit, offset).data
        }
    }

    suspend fun getLatestUploads(limit: Int = 20, offset: Int = 0): Result<List<MangaItem>> {
        return runCatching {
            apiService.getLatestUploads(limit, offset).data
        }
    }

    suspend fun getRecentlyAdded(limit: Int = 20, offset: Int = 0): Result<List<MangaItem>> {
        return runCatching {
            apiService.getRecentlyAdded(limit, offset).data
        }
    }

    suspend fun getRandomManga(): Result<MangaItem> {
        return runCatching {
            apiService.getRandomManga()
        }
    }

    suspend fun verifyUserMe(token: String): Result<String> {
        return runCatching {
            apiService.verifyUserMe(token)
        }
    }

    suspend fun markChapterRead(chapterId: String): Boolean {
        return if (settingsManager.autoSyncReadStatus) {
            apiService.markChapterRead(chapterId)
        } else false
    }

    suspend fun searchManga(query: String): Result<List<MangaItem>> {
        return runCatching {
            apiService.searchManga(query).data
        }
    }

    suspend fun getMangaDetails(mangaId: String): Result<MangaItem> {
        return runCatching {
            val item = apiService.getMangaDetails(mangaId)
            tagCacheManager.saveMangaTag(item)
            item
        }
    }

    suspend fun getChapters(mangaId: String, languages: List<String>): Result<List<ChapterItem>> {
        return runCatching {
            val response = apiService.getChapterFeed(mangaId, languages)
            response.data.sortedWith(
                compareBy(
                    { it.attributes.chapter?.toDoubleOrNull() ?: Double.MAX_VALUE },
                    { it.attributes.publishAt ?: "" }
                )
            )
        }
    }

    suspend fun getChapterPageUrls(chapterId: String, dataSaver: Boolean = false): Result<List<String>> {
        return runCatching {
            val atHome = apiService.getChapterPages(chapterId)
            val atHomeBaseUrl = atHome.baseUrl
            val hash = atHome.chapter.hash
            val pages = if (dataSaver) atHome.chapter.dataSaver else atHome.chapter.data
            val qualityFolder = if (dataSaver) "data-saver" else "data"

            val proxyBase = settingsManager.apiBaseUrl.trimEnd('/')
            val isCustomProxy = !proxyBase.contains("api.mangadex.org")

            pages.map { fileName ->
                if (isCustomProxy) {
                    "$proxyBase/$qualityFolder/$hash/$fileName"
                } else {
                    "$atHomeBaseUrl/$qualityFolder/$hash/$fileName"
                }
            }
        }
    }

    suspend fun exchangeOAuthCode(code: String, verifier: String): Result<com.eink.reader.data.api.TokenResponse> {
        return com.eink.reader.data.api.OAuthHelper.exchangeCodeForToken(
            client = apiService.client,
            code = code,
            verifier = verifier,
            proxyBaseUrl = settingsManager.apiBaseUrl
        )
    }

    suspend fun loginWithPersonalClient(
        clientId: String,
        clientSecret: String,
        username: String,
        pass: String
    ): Result<com.eink.reader.data.api.TokenResponse> {
        return com.eink.reader.data.api.OAuthHelper.loginWithPersonalClient(
            client = apiService.client,
            clientId = clientId,
            clientSecret = clientSecret,
            username = username,
            password = pass,
            proxyBaseUrl = settingsManager.apiBaseUrl
        )
    }

    suspend fun refreshAuthToken(): Result<com.eink.reader.data.api.TokenResponse> {
        val refreshTok = settingsManager.refreshToken
            ?: return Result.failure(Exception("Không có Refresh Token. Vui lòng đăng nhập lại."))
        val res = com.eink.reader.data.api.OAuthHelper.refreshToken(
            client = apiService.client,
            refreshToken = refreshTok,
            clientId = settingsManager.personalClientId,
            clientSecret = settingsManager.personalClientSecret,
            proxyBaseUrl = settingsManager.apiBaseUrl
        )
        res.onSuccess { tokenResp ->
            settingsManager.sessionToken = tokenResp.accessToken
            settingsManager.refreshToken = tokenResp.refreshToken
        }
        return res
    }

    suspend fun getUserFollowedManga(limit: Int = 100, offset: Int = 0): Result<List<MangaItem>> {
        return runCatching {
            if (!settingsManager.isLoggedIn) {
                throw java.io.IOException("Chưa đăng nhập MangaDex")
            }

            var authFailed = false

            // 1. Lấy danh sách manga từ GET /user/follows/manga
            val followsList = try {
                val res = apiService.getUserFollowedManga(limit = limit, offset = offset)
                android.util.Log.d("MangaRepository", "Follows returned ${res.data.size} items")
                res.data
            } catch (e: Exception) {
                if (e.message?.contains("401") == true || e.message?.contains("UNAUTHORIZED") == true) {
                    authFailed = true
                }
                android.util.Log.e("MangaRepository", "Failed getUserFollowedManga: ${e.message}", e)
                emptyList()
            }

            // 2. Lấy danh sách manga từ Reading Status (GET /manga/status)
            val statuses = try {
                val st = apiService.getAllReadingStatuses()
                android.util.Log.d("MangaRepository", "Reading statuses returned ${st.size} items")
                st
            } catch (e: Exception) {
                if (e.message?.contains("401") == true || e.message?.contains("UNAUTHORIZED") == true) {
                    authFailed = true
                }
                android.util.Log.e("MangaRepository", "Failed getAllReadingStatuses: ${e.message}", e)
                emptyMap()
            }

            // 3. Lấy danh sách manga từ Custom List cá nhân (GET /user/list)
            val userCustomListMangaIds = try {
                val customLists = apiService.getUserCustomLists(limit = 100)
                val ids = customLists.data.flatMap { list ->
                    list.relationships.filter { it.type == "manga" }.map { it.id }
                }.toSet()
                android.util.Log.d("MangaRepository", "User custom lists returned ${ids.size} manga IDs")
                ids
            } catch (e: Exception) {
                if (e.message?.contains("401") == true || e.message?.contains("UNAUTHORIZED") == true) {
                    authFailed = true
                }
                android.util.Log.e("MangaRepository", "Failed getUserCustomLists: ${e.message}", e)
                emptySet()
            }

            // 4. Lấy danh sách manga từ Followed Custom List (GET /user/follows/list)
            val followedCustomListMangaIds = try {
                val followedLists = apiService.getUserFollowedCustomLists(limit = 100)
                val ids = followedLists.data.flatMap { list ->
                    list.relationships.filter { it.type == "manga" }.map { it.id }
                }.toSet()
                android.util.Log.d("MangaRepository", "Followed custom lists returned ${ids.size} manga IDs")
                ids
            } catch (e: Exception) {
                if (e.message?.contains("401") == true || e.message?.contains("UNAUTHORIZED") == true) {
                    authFailed = true
                }
                android.util.Log.e("MangaRepository", "Failed getUserFollowedCustomLists: ${e.message}", e)
                emptySet()
            }

            if (authFailed && followsList.isEmpty() && statuses.isEmpty() && userCustomListMangaIds.isEmpty()) {
                throw java.io.IOException("Phiên đăng nhập MangaDex đã hết hạn (HTTP 401). Vui lòng đăng nhập lại.")
            }

            // Tìm những manga có trong Reading Status hoặc Custom Lists mà chưa nằm trong danh sách follows trực tiếp
            val existingIds = followsList.map { it.id }.toSet()
            val missingIds = (statuses.keys + userCustomListMangaIds + followedCustomListMangaIds)
                .filterNot { existingIds.contains(it) }
                .distinct()

            val extraMangaList = if (missingIds.isNotEmpty()) {
                try {
                    apiService.getMangaListByIds(missingIds.take(100)).data
                } catch (e: Exception) {
                    android.util.Log.e("MangaRepository", "Failed getMangaListByIds: ${e.message}", e)
                    emptyList()
                }
            } else {
                emptyList()
            }

            // Gộp cả các nguồn lại và loại trừ trùng lặp
            val merged = (followsList + extraMangaList).distinctBy { it.id }
            android.util.Log.d("MangaRepository", "Total merged follows/status/custom_lists: ${merged.size} items")

            // Lưu cache tags và lọc theo cài đặt contentRating (tự động ẩn pornographic nếu không bật)
            val filtered = tagCacheManager.filterMangaByRatings(
                list = merged,
                allowedRatings = settingsManager.getContentRatings(),
                allowPornographic = settingsManager.contentRatingPornographic
            )
            android.util.Log.d("MangaRepository", "After filtering mature/pornographic: ${filtered.size} items")
            filtered
        }
    }


    suspend fun isMangaFollowed(mangaId: String): Result<Boolean> = runCatching {
        apiService.isMangaFollowed(mangaId)
    }

    suspend fun followManga(mangaId: String): Result<Boolean> = runCatching {
        apiService.followManga(mangaId)
    }

    suspend fun unfollowManga(mangaId: String): Result<Boolean> = runCatching {
        apiService.unfollowManga(mangaId)
    }

    suspend fun getAllReadingStatuses(): Result<Map<String, String>> = runCatching {
        apiService.getAllReadingStatuses()
    }

    suspend fun getMangaReadingStatus(mangaId: String): Result<String?> = runCatching {
        apiService.getMangaReadingStatus(mangaId)
    }

    suspend fun updateMangaReadingStatus(mangaId: String, status: String?): Result<Boolean> = runCatching {
        apiService.updateMangaReadingStatus(mangaId, status)
    }
}