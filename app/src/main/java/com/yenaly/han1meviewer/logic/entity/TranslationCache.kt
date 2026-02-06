package com.yenaly.han1meviewer.logic.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * @project Han1meViewer
 * @author Yenaly Liew
 * @time 2022/07/02 002 13:13
 */
@Entity(tableName = "translation_cache")
data class TranslationCache(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val originalText: String,
    val translatedText: String,
    val sourceLang: String = "ZH",
    val targetLang: String = "EN",
    val contentType: ContentType,
    val videoCode: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val apiKeyUsed: String = "",
    val charsConsumed: Int = 0,
    val translationEngine: TranslationEngine = TranslationEngine.DEEPL // NEW FIELD
) {
    enum class ContentType {
        TITLE, DESCRIPTION, COMMENT, TAG, ARTIST_NAME, OTHER
    }

    // NEW ENUM
    enum class TranslationEngine {
        DEEPL,
        MLKIT
    }
}