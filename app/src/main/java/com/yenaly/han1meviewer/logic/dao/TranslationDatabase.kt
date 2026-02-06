package com.yenaly.han1meviewer.logic.dao

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yenaly.han1meviewer.logic.entity.TranslationCache

@Database(entities = [TranslationCache::class], version = 2)
abstract class TranslationDatabase : RoomDatabase() {
    abstract fun cacheDao(): TranslationCacheDao

    companion object {
        @Volatile
        private var INSTANCE: TranslationDatabase? = null

        fun getInstance(context: Context): TranslationDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TranslationDatabase::class.java,
                    "translation.db"
                )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE translation_cache ADD COLUMN translationEngine TEXT NOT NULL DEFAULT 'DEEPL'"
                )
            }
        }
    }
}

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
