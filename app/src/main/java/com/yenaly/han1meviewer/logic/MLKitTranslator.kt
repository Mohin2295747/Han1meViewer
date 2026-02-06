package com.yenaly.han1meviewer.logic

import android.content.Context
import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class MLKitTranslator private constructor(context: Context) {
    private var translator: Translator? = null
    private val isInitialized = AtomicBoolean(false)
    private val initializationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val translationCache = ConcurrentHashMap<String, String>()

    companion object {
        @Volatile
        private var INSTANCE: MLKitTranslator? = null

        fun getInstance(context: Context): MLKitTranslator {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MLKitTranslator(context.applicationContext).also {
                    it.initialize()
                    INSTANCE = it
                }
            }
        }
    }

    fun initialize() {
        if (isInitialized.get()) return

        initializationScope.launch {
            try {
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(TranslateLanguage.CHINESE)
                    .setTargetLanguage(TranslateLanguage.ENGLISH)
                    .build()

                translator = Translation.getClient(options)

                val conditions = DownloadConditions.Builder()
                    .requireWifi()
                    .build()

                translator?.downloadModelIfNeeded(conditions)
                    ?.addOnSuccessListener {
                        isInitialized.set(true)
                        Log.d("MLKitTranslator", "Model downloaded successfully")
                    }
                    ?.addOnFailureListener { exception ->
                        Log.e("MLKitTranslator", "Failed to download model: ${exception.message}")
                    }

            } catch (e: Exception) {
                Log.e("MLKitTranslator", "Initialization failed: ${e.message}")
            }
        }
    }

    suspend fun translate(text: String): String {
        if (!isInitialized.get() || text.isBlank()) {
            return text
        }

        translationCache[text]?.let { return it }

        return try {
            val result = withContext(Dispatchers.IO) {
                translator?.translate(text)?.await()
            } ?: text

            translationCache[text] = result
            result
        } catch (e: Exception) {
            Log.e("MLKitTranslator", "Translation failed: ${e.message}")
            text
        }
    }

    suspend fun translateBatch(texts: List<String>): List<String> {
        if (!isInitialized.get() || texts.isEmpty()) {
            return texts
        }

        return try {
            withContext(Dispatchers.IO) {
                texts.map { text ->
                    async { translate(text) }
                }.awaitAll()
            }
        } catch (e: Exception) {
            Log.e("MLKitTranslator", "Batch translation failed: ${e.message}")
            texts
        }
    }

    fun clearCache() {
        translationCache.clear()
    }

    fun isReady(): Boolean = isInitialized.get()

    fun getModelSize(): Long {
        return 40 * 1024 * 1024 // 40MB estimated
    }

    suspend fun deleteModel() {
        translator?.let {
            try {
                it.deleteDownloadedModel().await()
                translator = null
                isInitialized.set(false)
                clearCache()
            } catch (e: Exception) {
                Log.e("MLKitTranslator", "Failed to delete model: ${e.message}")
            }
        }
    }

    suspend fun checkModelStatus(): ModelStatus {
        return try {
            if (translator == null) return ModelStatus.NOT_INITIALIZED
            
            val isDownloaded = translator?.isModelDownloaded() ?: false
            
            if (isDownloaded) ModelStatus.DOWNLOADED else ModelStatus.NOT_DOWNLOADED
        } catch (e: Exception) {
            ModelStatus.ERROR
        }
    }

    enum class ModelStatus {
        NOT_INITIALIZED,
        NOT_DOWNLOADED,
        DOWNLOADING,
        DOWNLOADED,
        ERROR
    }
}