package com.yenaly.han1meviewer.logic

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.yenaly.han1meviewer.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object TranslationMigrationHelper {
    private const val OLD_ENABLE_TRANSLATION = "enable_translation"
    private const val OLD_TRANSLATION_API_KEY = "translation_api_key"
    private const val OLD_TARGET_LANGUAGE = "translation_target_language"
    private const val OLD_TRANSLATE_CONTENT_TYPES = "translate_content_types"

    suspend fun migrateIfNeeded(context: Context) = withContext(Dispatchers.IO) {
        try {
            val sharedPrefs = context.getSharedPreferences("translation_prefs", Context.MODE_PRIVATE)
            migrateFromSharedPrefs(sharedPrefs)
            migrateWebTranslationSettings()
            TranslationManager.getInstance(context)
            Log.d("TranslationMigrationHelper", "Migration completed")
        } catch (e: Exception) {
            Log.e("TranslationMigrationHelper", "Migration failed: ${e.message}", e)
        }
    }

    private fun migrateFromSharedPrefs(sharedPrefs: SharedPreferences) {
        if (!sharedPrefs.contains(OLD_ENABLE_TRANSLATION)) return

        Log.d("TranslationMigrationHelper", "Found old translation settings")

        val oldEnabled = sharedPrefs.getBoolean(OLD_ENABLE_TRANSLATION, false)
        Preferences.isTranslationEnabled = oldEnabled

        val oldApiKey = sharedPrefs.getString(OLD_TRANSLATION_API_KEY, "")
        if (!oldApiKey.isNullOrBlank()) {
            Preferences.translationApiKeys = setOf(oldApiKey)
        }

        val oldTargetLang = sharedPrefs.getString(OLD_TARGET_LANGUAGE, "EN")
        if (!oldTargetLang.isNullOrBlank()) {
            Preferences.translationTargetLang = oldTargetLang
        }

        val oldContentTypes = sharedPrefs.getStringSet(OLD_TRANSLATE_CONTENT_TYPES, null)
        oldContentTypes?.let { types ->
            Preferences.translateTitles = types.contains("titles") || types.isEmpty()
            Preferences.translateDescriptions = types.contains("descriptions") || types.isEmpty()
            Preferences.translateComments = types.contains("comments") || types.isEmpty()
            Preferences.translateTags = types.contains("tags") || types.isEmpty()
        }

        if (oldContentTypes == null) {
            Preferences.translateTitles = true
            Preferences.translateDescriptions = true
            Preferences.translateComments = true
            Preferences.translateTags = true
        }

        sharedPrefs.edit().clear().apply()
        Log.d("TranslationMigrationHelper", "Old preferences cleared")
    }

    private fun migrateWebTranslationSettings() {
        Log.d("TranslationMigrationHelper", "Web translation settings migration attempted")
    }

    suspend fun resetToDefaults(context: Context) {
        Preferences.isTranslationEnabled = false
        Preferences.translationApiKeys = emptySet()
        Preferences.translationMonthlyLimit = 500000
        Preferences.translationTargetLang = "EN"
        Preferences.translationBatchSize = 30000
        Preferences.translateTitles = true
        Preferences.translateDescriptions = true
        Preferences.translateComments = true
        Preferences.translateTags = true
        TranslationManager.getInstance(context).clearCache()
    }

    fun exportSettings(): Map<String, Any> {
        return mapOf(
            "version" to 1,
            "isTranslationEnabled" to Preferences.isTranslationEnabled,
            "translationApiKeys" to Preferences.translationApiKeys.toList(),
            "translationMonthlyLimit" to Preferences.translationMonthlyLimit,
            "translationTargetLang" to Preferences.translationTargetLang,
            "translationBatchSize" to Preferences.translationBatchSize,
            "translateTitles" to Preferences.translateTitles,
            "translateDescriptions" to Preferences.translateDescriptions,
            "translateComments" to Preferences.translateComments,
            "translateTags" to Preferences.translateTags
        )
    }

    fun importSettings(settings: Map<String, Any>) {
        (settings["isTranslationEnabled"] as? Boolean)?.let {
            Preferences.isTranslationEnabled = it
        }

        (settings["translationApiKeys"] as? List<*>)?.let { keys ->
            Preferences.translationApiKeys = keys.filterIsInstance<String>().toSet()
        }

        (settings["translationMonthlyLimit"] as? Int)?.let {
            Preferences.translationMonthlyLimit = it
        }

        (settings["translationTargetLang"] as? String)?.let {
            Preferences.translationTargetLang = it
        }

        (settings["translationBatchSize"] as? Int)?.let {
            Preferences.translationBatchSize = it
        }

        (settings["translateTitles"] as? Boolean)?.let {
            Preferences.translateTitles = it
        }

        (settings["translateDescriptions"] as? Boolean)?.let {
            Preferences.translateDescriptions = it
        }

        (settings["translateComments"] as? Boolean)?.let {
            Preferences.translateComments = it
        }

        (settings["translateTags"] as? Boolean)?.let {
            Preferences.translateTags = it
        }
    }
}