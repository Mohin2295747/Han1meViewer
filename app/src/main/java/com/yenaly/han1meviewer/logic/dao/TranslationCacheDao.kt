package com.yenaly.han1meviewer.logic.dao

import androidx.room.*
import com.yenaly.han1meviewer.logic.entity.TranslationCache
import kotlinx.coroutines.flow.Flow

@Dao
interface TranslationCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cache: TranslationCache)

    @Query("SELECT * FROM translation_cache WHERE originalText = :original AND targetLang = :targetLang AND contentType = :contentType")
    suspend fun get(original: String, targetLang: String, contentType: TranslationCache.ContentType): TranslationCache?

    @Query("SELECT * FROM translation_cache WHERE videoCode = :videoCode ORDER BY timestamp DESC")
    suspend fun getByVideoCode(videoCode: String): List<TranslationCache>

    @Query("SELECT * FROM translation_cache ORDER BY timestamp DESC")
    fun getAll(): Flow<List<TranslationCache>>

    @Query("DELETE FROM translation_cache WHERE id = :id")
    suspend fun delete(id: Int)

    @Query("DELETE FROM translation_cache WHERE contentType = :contentType")
    suspend fun deleteByType(contentType: TranslationCache.ContentType)

    @Query("DELETE FROM translation_cache")
    suspend fun deleteAll()

    @Query("SELECT SUM(charsConsumed) FROM translation_cache WHERE apiKeyUsed = :apiKey AND timestamp >= :startTime AND timestamp <= :endTime")
    suspend fun getCharsConsumed(apiKey: String, startTime: Long, endTime: Long): Long
}