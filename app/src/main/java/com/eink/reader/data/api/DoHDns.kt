package com.eink.reader.data.api

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class DoHDns(
    private val enabledProvider: () -> String // "cloudflare", "google", or "system"
) : Dns {
    private val bootstrapClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .build()

    override fun lookup(hostname: String): List<InetAddress> {
        val provider = enabledProvider()
        if (provider == "system") {
            return Dns.SYSTEM.lookup(hostname)
        }

        // Nếu là địa chỉ IP sẵn có
        if (hostname.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
            return listOf(InetAddress.getByName(hostname))
        }

        return try {
            val url = if (provider == "google") {
                "https://8.8.8.8/resolve?name=$hostname&type=A"
            } else {
                "https://1.1.1.1/dns-query?name=$hostname&type=A"
            }
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/dns-json")
                .header("User-Agent", "InkDex-Reader/1.0")
                .build()

            val response = bootstrapClient.newCall(request).execute()
            val body = response.body?.string() ?: return Dns.SYSTEM.lookup(hostname)
            val json = JSONObject(body)
            val answer = json.optJSONArray("Answer")
            val addresses = mutableListOf<InetAddress>()
            if (answer != null) {
                for (i in 0 until answer.length()) {
                    val obj = answer.getJSONObject(i)
                    val data = obj.optString("data")
                    if (data.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))) {
                        addresses.add(InetAddress.getByName(data))
                    }
                }
            }
            if (addresses.isNotEmpty()) addresses else Dns.SYSTEM.lookup(hostname)
        } catch (e: Exception) {
            Dns.SYSTEM.lookup(hostname)
        }
    }
}