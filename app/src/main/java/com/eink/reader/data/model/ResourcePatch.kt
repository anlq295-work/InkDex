package com.eink.reader.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ResourcePatch(
    @SerialName("patch_version")
    val patchVersion: Int = 0,
    @SerialName("updated_at")
    val updatedAt: String = "",
    val changelog: String = "",
    val strings: Map<String, Map<String, String>> = emptyMap(),
    @SerialName("chapter_rules")
    val chapterRules: ChapterRulesConfig? = null,
    val network: NetworkConfig? = null,
    @SerialName("system_notice")
    val systemNotice: SystemNoticeConfig? = null
)

data class PatchDownloadProgress(
    val percent: Int = 0,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val isDone: Boolean = false,
    val error: String? = null
)
