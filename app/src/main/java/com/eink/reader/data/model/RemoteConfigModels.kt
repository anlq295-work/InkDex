package com.eink.reader.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteConfig(
    @SerialName("config_version")
    val configVersion: Int = 1,
    @SerialName("updated_at")
    val updatedAt: String = "",
    @SerialName("system_notice")
    val systemNotice: SystemNoticeConfig = SystemNoticeConfig(),
    @SerialName("network")
    val network: NetworkConfig = NetworkConfig(),
    @SerialName("chapter_rules")
    val chapterRules: ChapterRulesConfig = ChapterRulesConfig(),
    @SerialName("eink_optimization")
    val einkOptimization: EInkOptimizationConfig = EInkOptimizationConfig()
)

@Serializable
data class SystemNoticeConfig(
    val enabled: Boolean = false,
    val title: String = "",
    val message: String = "",
    val type: String = "info"
)

@Serializable
data class NetworkConfig(
    @SerialName("default_worker_url")
    val defaultWorkerUrl: String = "https://mangadex-proxy.an-lq295-work.workers.dev",
    @SerialName("backup_proxy_urls")
    val backupProxyUrls: List<String> = emptyList(),
    @SerialName("doh_providers")
    val dohProviders: Map<String, String> = emptyMap()
)

@Serializable
data class ChapterRulesConfig(
    @SerialName("extra_prefixes")
    val extraPrefixes: List<String> = emptyList(),
    @SerialName("custom_regex")
    val customRegex: String? = null
)

@Serializable
data class EInkOptimizationConfig(
    @SerialName("kaleido3_saturation")
    val kaleido3Saturation: Float = 1.75f,
    @SerialName("kaleido3_contrast")
    val kaleido3Contrast: Float = 1.15f,
    @SerialName("kaleido3_brightness_boost")
    val kaleido3BrightnessBoost: Float = 20.0f
)
