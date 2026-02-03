package com.yenaly.han1meviewer.logic.model

import kotlinx.serialization.Serializable

@Serializable
data class PageVersion(
    val url: String,
    val originalHtml: String = "",
    val translatedHtml: String = "",
    val parsedData: ParsedMetadata = ParsedMetadata(),
    val checksum: String = "",
    val translationStatus: TranslationStatus = TranslationStatus.PENDING,
    val lastChecked: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis()
) {
    @Serializable
    enum class TranslationStatus {
        PENDING,        // Not yet translated
        TRANSLATED,     // Successfully translated
        FAILED,         // Translation failed
        STALE,          // Page changed, needs re-translation
        UNCHANGED       // Page unchanged, translation valid
    }
}

@Serializable
data class ParsedMetadata(
    val title: String = "",
    val views: String = "",
    val uploadTime: String = "",
    val tags: List<String> = emptyList(),
    val description: String = "",
    val videoCode: String = "",
    val artist: String = "",
    val checksum: String = "" // Checksum of metadata only
)