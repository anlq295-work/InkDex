package com.eink.reader.data.api

import android.util.Log
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp Interceptor tự động fallback ảnh bìa MangaDex:
 * 1. Nếu ảnh thumbnail .512.jpg bị 404/lỗi -> Thử .256.jpg
 * 2. Nếu .256.jpg vẫn bị 404/lỗi -> Thử ảnh gốc (raw filename)
 * 3. Nếu đi qua proxy bị 404/lỗi -> Thử trực tiếp uploads.mangadex.org
 */
class CoverFallbackInterceptor(
    private val getProxyBaseUrl: () -> String? = { null }
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val originalUrl = originalRequest.url.toString()

        var response = try {
            chain.proceed(originalRequest)
        } catch (e: Exception) {
            Log.w("CoverFallback", "Initial request failed for $originalUrl: ${e.message}")
            null
        }

        // Nếu thành công (HTTP 200..299) hoặc không phải link bìa truyện (/covers/) thì trả về luôn
        if (response != null && response.isSuccessful) {
            return response
        }

        if (!originalUrl.contains("/covers/")) {
            return response ?: throw java.io.IOException("Network request failed: $originalUrl")
        }

        // Bóc tách mangaId và fileName từ URL
        val pathSegments = originalRequest.url.pathSegments
        val coverIdx = pathSegments.indexOf("covers")
        if (coverIdx == -1 || coverIdx + 2 >= pathSegments.size) {
            return response ?: throw java.io.IOException("Invalid cover URL structure: $originalUrl")
        }

        val mangaId = pathSegments[coverIdx + 1]
        val fileWithThumb = pathSegments[coverIdx + 2]

        val rawFileName = fileWithThumb
            .removeSuffix(".512.jpg")
            .removeSuffix(".256.jpg")

        val proxyBase = getProxyBaseUrl()?.trim()?.trimEnd('/')

        val candidateUrls = mutableListOf<String>()

        // Tạo danh sách fallback theo thứ tự ưu tiên:
        // Nếu có proxy, ưu tiên thử qua proxy trước để tránh lỗi Connection Reset (SNI blocking của nhà mạng VN)
        if (!proxyBase.isNullOrBlank() && !proxyBase.contains("api.mangadex.org")) {
            candidateUrls.add("$proxyBase/covers/$mangaId/$rawFileName.512.jpg")
            candidateUrls.add("$proxyBase/covers/$mangaId/$rawFileName.256.jpg")
            candidateUrls.add("$proxyBase/covers/$mangaId/$rawFileName")
            if (rawFileName.contains(".")) {
                val baseWithoutExt = rawFileName.substringBeforeLast(".")
                candidateUrls.add("$proxyBase/covers/$mangaId/$baseWithoutExt.512.jpg")
                candidateUrls.add("$proxyBase/covers/$mangaId/$baseWithoutExt.256.jpg")
            }
        }

        // Fallback trực tiếp uploads.mangadex.org
        candidateUrls.add("https://uploads.mangadex.org/covers/$mangaId/$rawFileName.512.jpg")
        candidateUrls.add("https://uploads.mangadex.org/covers/$mangaId/$rawFileName.256.jpg")
        candidateUrls.add("https://uploads.mangadex.org/covers/$mangaId/$rawFileName")
        if (rawFileName.contains(".")) {
            val baseWithoutExt = rawFileName.substringBeforeLast(".")
            candidateUrls.add("https://uploads.mangadex.org/covers/$mangaId/$baseWithoutExt.512.jpg")
            candidateUrls.add("https://uploads.mangadex.org/covers/$mangaId/$baseWithoutExt.256.jpg")
        }

        // Lọc bỏ URL gốc đã thử và trùng lặp
        val distinctCandidates = candidateUrls.distinct().filter { it != originalUrl }

        for (candidateUrl in distinctCandidates) {
            response?.close()
            try {
                val fallbackRequest = originalRequest.newBuilder()
                    .url(candidateUrl)
                    .header("User-Agent", "InkDex-Reader/1.0 (Android; Color E-Ink Manga Reader)")
                    .header("Referer", "https://mangadex.org/")
                    .build()

                val nextResponse = chain.proceed(fallbackRequest)
                if (nextResponse.isSuccessful) {
                    Log.d("CoverFallback", "Phục hồi thành công ảnh bìa: $originalUrl -> $candidateUrl (HTTP ${nextResponse.code})")
                    return nextResponse
                }
                response = nextResponse
            } catch (e: Exception) {
                Log.w("CoverFallback", "Thử fallback $candidateUrl thất bại: ${e.message}")
            }
        }

        return response ?: throw java.io.IOException("Không thể tải ảnh bìa sau khi thử tất cả fallback: $originalUrl")
    }
}
