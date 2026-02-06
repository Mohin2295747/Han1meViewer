package com.yenaly.han1meviewer.logic

import com.yenaly.han1meviewer.logic.entity.TranslationCache

data class TranslationStats(
    val totalChars: Long,
    val totalItems: Int,
    val byType: Map<TranslationCache.ContentType, Int>,
    val byApiKey: Map<String, Long>,
    val apiKeyUsages: List<ApiKeyUsage>
) {
    data class ApiKeyUsage(
        val key: String,
        val charsUsed: Int,
        val monthlyLimit: Int,
        val remaining: Int,
        val isActive: Boolean
    )
}

// Extension for display names
fun TranslationCache.ContentType.displayName(): String {
    return when (this) {
        TranslationCache.ContentType.TITLE -> "Title"
        TranslationCache.ContentType.DESCRIPTION -> "Description"
        TranslationCache.ContentType.COMMENT -> "Comment"
        TranslationCache.ContentType.TAG -> "Tag"
        TranslationCache.ContentType.ARTIST_NAME -> "Artist Name"
        TranslationCache.ContentType.OTHER -> "Other"
    }
}