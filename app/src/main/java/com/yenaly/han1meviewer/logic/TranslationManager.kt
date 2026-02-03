package com.yenaly.han1meviewer.logic

import android.util.Log
import com.yenaly.han1meviewer.Preferences
import kotlinx.coroutines.delay
import org.jsoup.Jsoup
import java.net.URL

/**
 * @project Han1meViewer
 * @author YourName
 * @time 2024/01/01 001 12:00
 */
object TranslationManager {

    private const val TAG = "TranslationManager"
    private const val TRANSLATION_DELAY_MS = 2000L
    private const val TRANSLATION_TIMEOUT_MS = 10000L
    private const val MAX_RETRY_ATTEMPTS = 2

    // Translation configuration
    data class TranslationConfig(
        val translationKey: String = "zh-en.en.67772d43-6981727d-8453ce13-74722d776562",
        val isEnabled: Boolean = true,
        val baseUrl: String = "https://hanime.me/"
    ) {
        val translationBaseUrl: String
            get() = "https://translated.turbopages.org/proxy_u/$translationKey/https/hanime1.me/"
    }

    // Store translation cache to avoid repeated requests
    private val translationCache = mutableMapOf<String, TranslatedData>()

    data class TranslatedData(
        val titles: Map<String, String> = emptyMap(),
        val descriptions: Map<String, String> = emptyMap(),
        val tags: Map<String, String> = emptyMap(),
        val fetchedAt: Long = System.currentTimeMillis()
    )

    /**
     * Loads translation configuration from preferences
     */
    fun loadConfig(): TranslationConfig {
        val translationKey = Preferences.translationKey ?: "zh-en.en.67772d43-6981727d-8453ce13-74722d776562"
        val isEnabled = Preferences.isTranslationEnabled
        return TranslationConfig(
            translationKey = translationKey,
            isEnabled = isEnabled,
            baseUrl = Preferences.baseUrl
        )
    }

    /**
     * Saves translation configuration to preferences
     */
    fun saveConfig(config: TranslationConfig) {
        Preferences.translationKey = config.translationKey
        Preferences.isTranslationEnabled = config.isEnabled
        // Note: baseUrl is already handled separately
    }

    /**
     * Main function to get translated content
     * @param originalUrl The original URL to fetch content from
     * @param originalContent The original content to use as fallback
     * @return Translated content or original if translation fails
     */
    suspend fun getTranslatedContent(originalUrl: String, originalContent: String): String {
        val config = loadConfig()
        if (!config.isEnabled) {
            Log.d(TAG, "Translation is disabled")
            return originalContent
        }

        // Check cache first
        val cacheKey = originalUrl.hashCode().toString()
        val cached = translationCache[cacheKey]
        if (cached != null && System.currentTimeMillis() - cached.fetchedAt < 3600000) { // 1 hour cache
            Log.d(TAG, "Using cached translation for $originalUrl")
            return applyTranslation(originalContent, cached)
        }

        // Generate translated URL
        val translatedUrl = generateTranslatedUrl(originalUrl, config)
        if (translatedUrl.isBlank()) {
            Log.w(TAG, "Failed to generate translated URL")
            return originalContent
        }

        // Fetch translated content with retry logic
        var translatedData: TranslatedData? = null
        var lastException: Exception? = null
        
        for (attempt in 1..MAX_RETRY_ATTEMPTS) {
            try {
                Log.d(TAG, "Attempting translation (attempt $attempt) for: $originalUrl")
                
                // Wait before fetching (network-dependent delay)
                delay(TRANSLATION_DELAY_MS)
                
                // Fetch the translated page
                val translatedContent = fetchTranslatedPage(translatedUrl)
                
                // Parse translated content
                translatedData = parseTranslatedContent(translatedContent)
                
                // Cache the result
                translationCache[cacheKey] = translatedData
                Log.d(TAG, "Translation successful for $originalUrl")
                break
                
            } catch (e: Exception) {
                lastException = e
                Log.e(TAG, "Translation attempt $attempt failed for $originalUrl", e)
                
                // Wait longer between retries
                if (attempt < MAX_RETRY_ATTEMPTS) {
                    delay(TRANSLATION_DELAY_MS * attempt)
                }
            }
        }

        // Apply translation if successful, otherwise return original
        return if (translatedData != null) {
            applyTranslation(originalContent, translatedData)
        } else {
            Log.w(TAG, "All translation attempts failed, using original content", lastException)
            originalContent
        }
    }

    /**
     * Generates the translated URL from the original URL
     */
    private fun generateTranslatedUrl(originalUrl: String, config: TranslationConfig): String {
        return try {
            // Remove protocol and base URL to get the path
            val baseUrlRegex = Regex("https?://[^/]+")
            val path = originalUrl.replace(baseUrlRegex, "")
            
            // Construct translated URL
            "${config.translationBaseUrl}$path"
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate translated URL", e)
            ""
        }
    }

    /**
     * Fetches the translated page content
     */
    private fun fetchTranslatedPage(url: String): String {
        return Jsoup.connect(url)
            .timeout(TRANSLATION_TIMEOUT_MS.toInt())
            .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.5")
            .header("Accept-Encoding", "gzip, deflate")
            .get()
            .outerHtml()
    }

    /**
     * Parses translated content from the HTML
     */
    private fun parseTranslatedContent(translatedHtml: String): TranslatedData {
        val doc = Jsoup.parse(translatedHtml)
        
        // Parse titles (adjust selectors based on actual HTML structure)
        val titleMap = mutableMapOf<String, String>()
        doc.select("h1, h2, h3, h4, .title, .video-title").forEach { element ->
            val original = element.attr("data-original") ?: element.text()
            val translated = element.text()
            if (original.isNotBlank() && translated.isNotBlank()) {
                titleMap[original] = translated
            }
        }

        // Parse descriptions
        val descriptionMap = mutableMapOf<String, String>()
        doc.select(".description, .introduction, .video-caption-text, p").forEach { element ->
            val original = element.attr("data-original") ?: element.text()
            val translated = element.text()
            if (original.isNotBlank() && translated.isNotBlank() && original != translated) {
                descriptionMap[original] = translated
            }
        }

        // Parse tags
        val tagMap = mutableMapOf<String, String>()
        doc.select(".tag, .single-video-tag a, [class*='tag']").forEach { element ->
            val original = element.attr("data-original") ?: element.text()
            val translated = element.text()
            if (original.isNotBlank() && translated.isNotBlank()) {
                tagMap[original] = translated
            }
        }

        return TranslatedData(
            titles = titleMap,
            descriptions = descriptionMap,
            tags = tagMap
        )
    }

    /**
     * Applies translation to the original content
     */
    private fun applyTranslation(originalContent: String, translatedData: TranslatedData): String {
        var result = originalContent
        
        // Apply title translations
        translatedData.titles.forEach { (original, translated) ->
            if (original.isNotBlank() && translated.isNotBlank()) {
                result = result.replace(original, translated)
            }
        }

        // Apply description translations
        translatedData.descriptions.forEach { (original, translated) ->
            if (original.isNotBlank() && translated.isNotBlank()) {
                result = result.replace(original, translated)
            }
        }

        // Apply tag translations
        translatedData.tags.forEach { (original, translated) ->
            if (original.isNotBlank() && translated.isNotBlank()) {
                result = result.replace(original, translated)
            }
        }

        return result
    }

    /**
     * Clears the translation cache
     */
    fun clearCache() {
        translationCache.clear()
        Log.d(TAG, "Translation cache cleared")
    }

    /**
     * Pre-translates a URL (for proactive translation)
     */
    suspend fun preTranslate(url: String): Boolean {
        return try {
            val config = loadConfig()
            if (!config.isEnabled) return false
            
            val translatedUrl = generateTranslatedUrl(url, config)
            if (translatedUrl.isBlank()) return false
            
            delay(TRANSLATION_DELAY_MS)
            fetchTranslatedPage(translatedUrl)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Pre-translation failed for $url", e)
            false
        }
    }

    /**
     * Extracts Chinese text from content for debugging
     */
    fun extractChineseText(content: String): List<String> {
        val chineseRegex = Regex("[\u4e00-\u9fff]+")
        return chineseRegex.findAll(content).map { it.value }.toList()
    }
}