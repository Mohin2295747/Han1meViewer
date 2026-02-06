package com.yenaly.han1meviewer.logic

import android.content.Context
import android.util.Log
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.dao.TranslationCacheDao
import com.yenaly.han1meviewer.logic.dao.TranslationDatabase
import com.yenaly.han1meviewer.logic.entity.TranslationCache
import com.yenaly.han1meviewer.logic.exception.TranslationException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

const val TEXT_SEPARATOR = "♧¥"
const val TAG_SEPARATOR = "{"

data class TranslationApiKey(
    val key: String,
    val monthlyLimit: Int = 500000,
    var charsUsedThisMonth: AtomicInteger = AtomicInteger(0),
    val isActive: Boolean = true,
    val lastReset: Long = System.currentTimeMillis()
) {
    fun resetIfNeeded(): Boolean {
        val now = System.currentTimeMillis()
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = lastReset
        val lastMonth = calendar.get(java.util.Calendar.MONTH)
        calendar.timeInMillis = now
        val currentMonth = calendar.get(java.util.Calendar.MONTH)

        if (currentMonth != lastMonth) {
            charsUsedThisMonth.set(0)
            return true
        }
        return false
    }

    fun hasQuota(chars: Int): Boolean {
        return charsUsedThisMonth.get() + chars <= monthlyLimit
    }

    fun consume(chars: Int) {
        charsUsedThisMonth.addAndGet(chars)
    }
}

class TranslationManager private constructor(context: Context) {
    private val database = TranslationDatabase.getInstance(context)
    private val cacheDao = database.cacheDao()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val mlKitTranslator by lazy {
        MLKitTranslator.getInstance(context)
    }
    private val appContext = context.applicationContext

    private var apiKeys = mutableListOf<TranslationApiKey>()
    private var currentApiKeyIndex = 0
    private var isEnabled = false
    private var batchSize = 30000
    private var targetLang = "EN"
    private var translateTitles = true
    private var translateDescriptions = true
    private var translateComments = true
    private var translateTags = true

    companion object {
        @Volatile
        private var INSTANCE: TranslationManager? = null

        fun getInstance(context: Context): TranslationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TranslationManager(context).also {
                    it.initialize()
                    INSTANCE = it
                }
            }
        }
    }

    fun initialize() {
        val apiKeyStrings = Preferences.translationApiKeys
        apiKeys.clear()
        apiKeyStrings.forEach { key ->
            if (key.isNotBlank()) {
                apiKeys.add(TranslationApiKey(key, Preferences.translationMonthlyLimit))
            }
        }
        
        if (Preferences.useMLKitTranslation) {
            // When ML Kit is enabled, ignore DeepL settings and force translate everything
            isEnabled = true
            translateTitles = Preferences.showTranslatedTitles
            translateDescriptions = true
            translateComments = true
            translateTags = Preferences.showTranslatedTags
        
            // Auto-download ML Kit model if needed
            if (Preferences.mlkitAutoDownload) {
                CoroutineScope(Dispatchers.IO).launch {
                    mlKitTranslator.initialize()
                }
            }
        } else {
            isEnabled = Preferences.isTranslationEnabled && apiKeys.isNotEmpty()
            targetLang = Preferences.translationTargetLang
            batchSize = Preferences.translationBatchSize
            translateTitles = Preferences.translateTitles
            translateDescriptions = Preferences.translateDescriptions
            translateComments = Preferences.translateComments
            translateTags = Preferences.translateTags
        }
    
        apiKeys.forEach { it.resetIfNeeded() }
    }

    private fun getNextApiKey(requestChars: Int): TranslationApiKey? {
        if (apiKeys.isEmpty()) return null

        for (i in 0 until apiKeys.size) {
            val index = (currentApiKeyIndex + i) % apiKeys.size
            val apiKey = apiKeys[index]
            if (apiKey.isActive && apiKey.hasQuota(requestChars)) {
                currentApiKeyIndex = index
                return apiKey
            }
        }

        for (apiKey in apiKeys) {
            if (apiKey.isActive) {
                return apiKey
            }
        }

        return null
    }
    
    private suspend fun translateWithMLKit(
        originalText: String,
        contentType: TranslationCache.ContentType,
        videoCode: String? = null,
        forceFresh: Boolean = false
    ): String {
        if (originalText.isBlank()) return originalText

        if (!forceFresh) {
            val cached = cacheDao.get(originalText, targetLang, contentType)
            if (cached != null && cached.translationEngine == TranslationCache.TranslationEngine.MLKIT) {
                return cached.translatedText
            }
        }

        return try {
            val translated = mlKitTranslator.translate(originalText)
        
            cacheDao.insert(
                TranslationCache(
                    originalText = originalText,
                    translatedText = translated,
                    targetLang = targetLang,
                    contentType = contentType,
                    videoCode = videoCode,
                    translationEngine = TranslationCache.TranslationEngine.MLKIT,
                    charsConsumed = originalText.length
                )
            )
        
            translated
        } catch (e: Exception) {
            Log.e("TranslationManager", "ML Kit translation failed: ${e.message}")
            originalText
        }
    }

    private suspend fun callDeepLApi(
        texts: List<String>,
        targetLang: String
    ): List<String> {
        val apiKey = getNextApiKey(texts.sumOf { it.length }) ?: throw TranslationException("No available API key")

        val formBody = FormBody.Builder()
            .add("target_lang", targetLang)
            .add("source_lang", "ZH")

        val joinedText = texts.joinToString(TEXT_SEPARATOR)
        formBody.add("text", joinedText)

        val request = Request.Builder()
            .url("https://api-free.deepl.com/v2/translate")
            .addHeader("Authorization", "DeepL-Auth-Key ${apiKey.key}")
            .post(formBody.build())
            .build()

        return withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    throw TranslationException("API call failed: ${response.code} - $errorBody")
                }

                val body = response.body?.string() ?: throw TranslationException("Empty response")
                val json = JSONObject(body)
                val translations = json.getJSONArray("translations")

                val results = mutableListOf<String>()
                for (i in 0 until translations.length()) {
                    results.add(translations.getJSONObject(i).getString("text"))
                }

                apiKey.consume(joinedText.length)

                results
            }
        }
    }

    private fun createBatches(texts: List<String>, maxBatchSize: Int = batchSize): List<List<String>> {
        val batches = mutableListOf<List<String>>()
        var currentBatch = mutableListOf<String>()
        var currentSize = 0

        for (text in texts) {
            val textSize = text.length

            if (textSize > maxBatchSize) {
                if (currentBatch.isNotEmpty()) {
                    batches.add(currentBatch.toList())
                    currentBatch.clear()
                    currentSize = 0
                }

                val chunks = text.chunked(maxBatchSize - TEXT_SEPARATOR.length)
                for (chunk in chunks) {
                    if (currentSize + chunk.length + TEXT_SEPARATOR.length > maxBatchSize) {
                        batches.add(currentBatch.toList())
                        currentBatch.clear()
                        currentSize = 0
                    }
                    currentBatch.add(chunk)
                    currentSize += chunk.length + TEXT_SEPARATOR.length
                }
            } else {
                if (currentSize + textSize + TEXT_SEPARATOR.length > maxBatchSize) {
                    batches.add(currentBatch.toList())
                    currentBatch.clear()
                    currentSize = 0
                }
                currentBatch.add(text)
                currentSize += textSize + TEXT_SEPARATOR.length
            }
        }

        if (currentBatch.isNotEmpty()) {
            batches.add(currentBatch)
        }

        return batches
    }

    suspend fun translate(
        originalText: String,
        contentType: TranslationCache.ContentType,
        videoCode: String? = null,
        forceFresh: Boolean = false
    ): String {
        if (Preferences.useMLKitTranslation) {
            return translateWithMLKit(originalText, contentType, videoCode, forceFresh)
        }
        
        if (!isEnabled || originalText.isBlank()) return originalText

        if (!forceFresh) {
            val cached = cacheDao.get(originalText, targetLang, contentType)
            if (cached != null) {
                return cached.translatedText
            }
        }

        when (contentType) {
            TranslationCache.ContentType.TITLE -> if (!translateTitles) return originalText
            TranslationCache.ContentType.DESCRIPTION -> if (!translateDescriptions) return originalText
            TranslationCache.ContentType.COMMENT -> if (!translateComments) return originalText
            TranslationCache.ContentType.TAG -> if (!translateTags) return originalText
            else -> {}
        }

        return try {
            val translated = callDeepLApi(listOf(originalText), targetLang).firstOrNull() ?: originalText

            cacheDao.insert(
                TranslationCache(
                    originalText = originalText,
                    translatedText = translated,
                    targetLang = targetLang,
                    contentType = contentType,
                    videoCode = videoCode,
                    apiKeyUsed = apiKeys.getOrNull(currentApiKeyIndex)?.key ?: "",
                    charsConsumed = originalText.length,
                    translationEngine = TranslationCache.TranslationEngine.DEEPL
                )
            )

            translated
        } catch (e: Exception) {
            Log.e("TranslationManager", "Translation failed: ${e.message}")
            originalText
        }
    }

    suspend fun translateBatch(
        texts: List<String>,
        contentType: TranslationCache.ContentType,
        videoCode: String? = null
    ): List<String> {
        // Handle ML Kit translation
        if (Preferences.useMLKitTranslation) {
            return mlKitTranslator.translateBatch(texts)
        }
        
        if (!isEnabled || texts.isEmpty()) return texts

        val results = MutableList(texts.size) { "" }
        val toTranslate = mutableListOf<Pair<Int, String>>()

        texts.forEachIndexed { index, text ->
            if (text.isBlank()) {
                results[index] = text
            } else {
                val cached = cacheDao.get(text, targetLang, contentType)
                if (cached != null) {
                    results[index] = cached.translatedText
                } else {
                    toTranslate.add(index to text)
                }
            }
        }

        if (toTranslate.isEmpty()) return results

        val textsToTranslate = toTranslate.map { it.second }
        val batches = createBatches(textsToTranslate)

        for (batch in batches) {
            try {
                val translatedBatch = callDeepLApi(batch, targetLang)

                for ((batchIndex, translated) in translatedBatch.withIndex()) {
                    val originalIndex = toTranslate[batchIndex].first
                    results[originalIndex] = translated

                    cacheDao.insert(
                        TranslationCache(
                            originalText = batch[batchIndex],
                            translatedText = translated,
                            targetLang = targetLang,
                            contentType = contentType,
                            videoCode = videoCode,
                            apiKeyUsed = apiKeys.getOrNull(currentApiKeyIndex)?.key ?: "",
                            charsConsumed = batch[batchIndex].length,
                            translationEngine = TranslationCache.TranslationEngine.DEEPL
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("TranslationManager", "Batch translation failed: ${e.message}")
                for ((index, text) in batch.withIndex()) {
                    results[toTranslate[index].first] = text
                }
            }
        }

        return results
    }

    suspend fun translateTags(tags: List<String>, videoCode: String? = null): List<String> {
        if (Preferences.useMLKitTranslation) {
            return mlKitTranslator.translateBatch(tags)
        }
        
        if (!isEnabled || !translateTags || tags.isEmpty()) return tags

        val joinedTags = tags.joinToString(TAG_SEPARATOR)

        val cached = cacheDao.get(joinedTags, targetLang, TranslationCache.ContentType.TAG)
        if (cached != null) {
            return cached.translatedText.split(TAG_SEPARATOR)
        }

        return try {
            val translated = callDeepLApi(listOf(joinedTags), targetLang).firstOrNull() ?: joinedTags

            cacheDao.insert(
                TranslationCache(
                    originalText = joinedTags,
                    translatedText = translated,
                    targetLang = targetLang,
                    contentType = TranslationCache.ContentType.TAG,
                    videoCode = videoCode,
                    apiKeyUsed = apiKeys.getOrNull(currentApiKeyIndex)?.key ?: "",
                    charsConsumed = joinedTags.length,
                    translationEngine = TranslationCache.TranslationEngine.DEEPL
                )
            )

            translated.split(TAG_SEPARATOR)
        } catch (e: Exception) {
            Log.e("TranslationManager", "Tag translation failed: ${e.message}")
            tags
        }
    }

    suspend fun getStats(): Map<String, Any> {
        val allCache = cacheDao.getAll().first()
        val totalChars = allCache.sumOf { it.charsConsumed }
        val totalItems = allCache.size

        val byType = allCache.groupBy { it.contentType }
            .mapValues { it.value.size }

        val byApiKey = allCache.groupBy { it.apiKeyUsed }
            .mapValues { it.value.sumOf { cache -> cache.charsConsumed } }

        val byEngine = allCache.groupBy { it.translationEngine }
            .mapValues { it.value.size }

        return mapOf(
            "totalChars" to totalChars,
            "totalItems" to totalItems,
            "byType" to byType,
            "byApiKey" to byApiKey,
            "byEngine" to byEngine,
            "apiKeys" to apiKeys.map { key ->
                mapOf(
                    "key" to key.key.take(8) + "..." + key.key.takeLast(4),
                    "monthlyLimit" to key.monthlyLimit,
                    "charsUsed" to key.charsUsedThisMonth.get(),
                    "remaining" to key.monthlyLimit - key.charsUsedThisMonth.get(),
                    "isActive" to key.isActive
                )
            },
            "mlKitStatus" to getMLKitStatus().name
        )
    }
    
    // ML Kit specific methods
    suspend fun getMLKitStatus(): MLKitTranslator.ModelStatus {
        return try {
            mlKitTranslator.checkModelStatus()
        } catch (e: Exception) {
            MLKitTranslator.ModelStatus.ERROR
        }
    }

    suspend fun downloadMLKitModel(): Boolean {
        return try {
            mlKitTranslator.initialize()
            // Wait for initialization
            delay(3000)
            // Check if ready after delay
            mlKitTranslator.isReady()
        } catch (e: Exception) {
            false
        }
    }

    fun getMLKitModelSize(): Long = mlKitTranslator.getModelSize()

    suspend fun deleteMLKitModel(): Boolean {
        return mlKitTranslator.deleteModel()
    }

    suspend fun getAllCacheItems(): List<TranslationCache> {
        return cacheDao.getAll().first()
    }

    suspend fun clearCache() {
        cacheDao.deleteAll()
    }

    suspend fun clearCacheByType(contentType: TranslationCache.ContentType) {
        cacheDao.deleteByType(contentType)
    }

    suspend fun deleteCacheItem(id: Int) {
        cacheDao.delete(id)
    }

    fun updateSettings() {
        initialize()
    }
}
