package com.eink.reader

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.eink.reader.data.api.CoverFallbackInterceptor
import com.eink.reader.data.api.DoHDns
import com.eink.reader.data.repository.SettingsManager
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class EInkApplication : Application(), ImageLoaderFactory {

    lateinit var settingsManager: SettingsManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settingsManager = SettingsManager(this)
    }

    override fun newImageLoader(): ImageLoader {
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .dns(DoHDns {
                if (settingsManager.useDoH) settingsManager.dohProvider else "system"
            })
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "InkDex-Reader/1.0 (Android; Color E-Ink Manga Reader)")
                    .header("Referer", "https://mangadex.org/")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(CoverFallbackInterceptor(getProxyBaseUrl = { settingsManager.apiBaseUrl }))
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .crossfade(false)
            .respectCacheHeaders(false)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05)
                    .build()
            }
            .build()
    }

    companion object {
        lateinit var instance: EInkApplication
            private set
    }
}
