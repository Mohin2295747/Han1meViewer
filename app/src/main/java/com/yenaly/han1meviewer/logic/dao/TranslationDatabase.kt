@Database(entities = [TranslationCache::class], version = 2) // CHANGED FROM 1 TO 2
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
                .addMigrations(MIGRATION_1_2) // ADDED MIGRATION
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
            }
        }

        // ADD THIS MIGRATION
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE translation_cache ADD COLUMN translationEngine TEXT NOT NULL DEFAULT 'DEEPL'"
                )
            }
        }
    }
}