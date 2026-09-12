package com.eink.reader.data.api

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

data class TokenResponse(
    val accessToken: String,
    val expiresIn: Long = 0L,
    val refreshExpiresIn: Long = 0L,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val scope: String? = null
)

object OAuthHelper {
    const val BASE_AUTH_URL = "https://auth.mangadex.org"
    const val CLIENT_ID = "neko"
    const val REDIRECT_URI = "neko://mangadex-auth"

    fun getAuthBaseUrl(proxyBaseUrl: String? = null): String {
        if (!proxyBaseUrl.isNullOrBlank() && !proxyBaseUrl.contains("api.mangadex.org")) {
            return proxyBaseUrl.trimEnd('/')
        }
        return BASE_AUTH_URL
    }

    /**
     * Sinh chuỗi ngẫu nhiên an toàn code_verifier (64 ký tự)
     */
    fun generateCodeVerifier(): String {
        val bytes = ByteArray(48)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    /**
     * Sinh code_challenge từ code_verifier bằng thuật toán SHA-256
     */
    fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    /**
     * Tạo URL mở giao diện đăng nhập Keycloak của MangaDex
     */
    fun buildAuthUrl(verifier: String, proxyBaseUrl: String? = null): String {
        val challenge = generateCodeChallenge(verifier)
        val base = getAuthBaseUrl(proxyBaseUrl)
        val authEndpoint = "$base/realms/mangadex/protocol/openid-connect/auth"
        return "$authEndpoint?client_id=$CLIENT_ID" +
                "&response_type=code" +
                "&redirect_uri=${android.net.Uri.encode(REDIRECT_URI)}" +
                "&code_challenge=$challenge" +
                "&code_challenge_method=S256"
    }

    /**
     * Gửi request trao đổi mã Authorization Code để nhận Access Token & Refresh Token
     */
    suspend fun exchangeCodeForToken(
        client: OkHttpClient,
        code: String,
        verifier: String,
        proxyBaseUrl: String? = null
    ): Result<TokenResponse> = withContext(Dispatchers.IO) {
        try {
            val base = getAuthBaseUrl(proxyBaseUrl)
            val tokenEndpoint = "$base/realms/mangadex/protocol/openid-connect/token"
            val formBody = FormBody.Builder()
                .add("client_id", CLIENT_ID)
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("code_verifier", verifier)
                .add("redirect_uri", REDIRECT_URI)
                .build()

            val request = Request.Builder()
                .url(tokenEndpoint)
                .post(formBody)
                .header("User-Agent", "InkDex-Reader/1.0 (Android; E-Ink)")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(body)
                val tokenResponse = TokenResponse(
                    accessToken = json.getString("access_token"),
                    expiresIn = json.optLong("expires_in", 0L),
                    refreshExpiresIn = json.optLong("refresh_expires_in", 0L),
                    refreshToken = json.getString("refresh_token"),
                    tokenType = json.optString("token_type", "Bearer"),
                    scope = if (json.has("scope") && !json.isNull("scope")) json.getString("scope") else null
                )
                Result.success(tokenResponse)
            } else {
                Result.failure(Exception("Lỗi máy chủ xác thực (${response.code}): $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getTokenExpirationTimestamp(token: String?): Long? {
        if (token.isNullOrBlank()) return null
        return try {
            val parts = token.split(".")
            if (parts.size >= 2) {
                val payloadBytes = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
                val json = JSONObject(String(payloadBytes, Charsets.UTF_8))
                if (json.has("exp")) json.getLong("exp") else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun isTokenExpired(token: String?): Boolean {
        val exp = getTokenExpirationTimestamp(token) ?: return false
        val now = System.currentTimeMillis() / 1000L
        return now >= (exp - 30) // Xem như hết hạn nếu còn ít hơn 30 giây
    }

    fun extractClientIdFromToken(token: String?): String? {
        if (token.isNullOrBlank()) return null
        return try {
            val parts = token.split(".")
            if (parts.size >= 2) {
                val payloadBytes = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
                val json = JSONObject(String(payloadBytes, Charsets.UTF_8))
                json.optString("azp").takeIf { it.isNotBlank() }
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Làm mới token khi hết hạn
     */
    suspend fun refreshToken(
        client: OkHttpClient,
        refreshToken: String,
        clientId: String? = null,
        clientSecret: String? = null,
        proxyBaseUrl: String? = null
    ): Result<TokenResponse> = withContext(Dispatchers.IO) {
        try {
            val base = getAuthBaseUrl(proxyBaseUrl)
            val tokenEndpoint = "$base/realms/mangadex/protocol/openid-connect/token"
            val effectiveClientId = if (!clientId.isNullOrBlank()) {
                clientId.trim()
            } else {
                extractClientIdFromToken(refreshToken) ?: CLIENT_ID
            }
            if (effectiveClientId.startsWith("personal-client-") && clientSecret.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Personal Client yêu cầu Client Secret để làm mới token. Vui lòng đăng nhập lại."))
            }
            val formBuilder = FormBody.Builder()

                .add("client_id", effectiveClientId)
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
            if (!clientSecret.isNullOrBlank()) {
                formBuilder.add("client_secret", clientSecret.trim())
            } else if (effectiveClientId == CLIENT_ID) {
                // Chỉ thêm redirect_uri đối với client công khai mặc định của web (neko)
                formBuilder.add("redirect_uri", REDIRECT_URI)
            }
            val formBody = formBuilder.build()

            val request = Request.Builder()
                .url(tokenEndpoint)
                .post(formBody)
                .header("User-Agent", "InkDex-Reader/1.0 (Android; E-Ink)")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(body)
                val tokenResponse = TokenResponse(
                    accessToken = json.getString("access_token"),
                    expiresIn = json.optLong("expires_in", 0L),
                    refreshExpiresIn = json.optLong("refresh_expires_in", 0L),
                    refreshToken = json.getString("refresh_token"),
                    tokenType = json.optString("token_type", "Bearer"),
                    scope = if (json.has("scope") && !json.isNull("scope")) json.getString("scope") else null
                )
                Result.success(tokenResponse)
            } else {
                Result.failure(Exception("Lỗi làm mới token (${response.code}): $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Đăng nhập trực tiếp bằng Personal API Client (Username, Password, Client ID, Client Secret)
     * Đây là API chính thức của MangaDex dành cho cá nhân, không cần qua WebView hay Captcha!
     */
    suspend fun loginWithPersonalClient(
        client: OkHttpClient,
        clientId: String,
        clientSecret: String,
        username: String,
        password: String,
        proxyBaseUrl: String? = null
    ): Result<TokenResponse> = withContext(Dispatchers.IO) {
        try {
            val base = getAuthBaseUrl(proxyBaseUrl)
            val tokenEndpoint = "$base/realms/mangadex/protocol/openid-connect/token"
            val formBody = FormBody.Builder()
                .add("grant_type", "password")
                .add("client_id", clientId.trim())
                .add("client_secret", clientSecret.trim())
                .add("username", username.trim())
                .add("password", password)
                .build()

            val request = Request.Builder()
                .url(tokenEndpoint)
                .post(formBody)
                .header("User-Agent", "InkDex-Reader/1.0 (Android; E-Ink)")
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful) {
                val json = JSONObject(body)
                val tokenResponse = TokenResponse(
                    accessToken = json.getString("access_token"),
                    expiresIn = json.optLong("expires_in", 0L),
                    refreshExpiresIn = json.optLong("refresh_expires_in", 0L),
                    refreshToken = json.getString("refresh_token"),
                    tokenType = json.optString("token_type", "Bearer"),
                    scope = if (json.has("scope") && !json.isNull("scope")) json.getString("scope") else null
                )
                Result.success(tokenResponse)
            } else {
                val errorMsg = try {
                    val errJson = JSONObject(body)
                    errJson.optString("error_description", errJson.optString("error", body))
                } catch (_: Exception) {
                    body
                }
                Result.failure(Exception("Lỗi đăng nhập ($errorMsg)"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
