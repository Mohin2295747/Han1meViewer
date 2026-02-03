package com.yenaly.han1meviewer.logic

import android.content.Context
import android.util.Log
import com.yenaly.han1meviewer.logic.model.PageVersion
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import com.yenaly.yenaly_libs.utils.applicationContext

object PageStorageManager {
    private const val TAG = "PageStorageManager"
    private const val VERSIONS_DIR = "page_versions"
    private const val ORIGINAL_DIR = "original_pages"
    private const val TRANSLATED_DIR = "translated_pages"
    
    private val json = Json { prettyPrint = true }
    private var initialized = false
    
    fun initialize() {
        if (!initialized) {
            ensureDirectories()
            initialized = true
            Log.d(TAG, "PageStorageManager initialized")
        }
    }
    
    private fun ensureDirectories() {
        getVersionsDir().mkdirs()
        getOriginalDir().mkdirs()
        getTranslatedDir().mkdirs()
    }
    
    private fun getVersionsDir(): File = File(applicationContext.filesDir, VERSIONS_DIR)
    private fun getOriginalDir(): File = File(applicationContext.filesDir, ORIGINAL_DIR)
    private fun getTranslatedDir(): File = File(applicationContext.filesDir, TRANSLATED_DIR)
    
    // Generate unique filename from URL
    private fun getFilename(url: String): String {
        return "version_${url.hashCode()}_${MD5(url).substring(0, 8)}.json"
    }
    
    private fun getOriginalFilename(url: String): String {
        return "original_${url.hashCode()}.html"
    }
    
    private fun getTranslatedFilename(url: String): String {
        return "translated_${url.hashCode()}.html"
    }
    
    // Save page version
    fun savePageVersion(pageVersion: PageVersion) {
        initialize()
        try {
            val file = File(getVersionsDir(), getFilename(pageVersion.url))
            file.writeText(json.encodeToString(pageVersion))
            Log.d(TAG, "Saved page version: ${pageVersion.url}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save page version", e)
        }
    }
    
    // Load page version
    fun loadPageVersion(url: String): PageVersion? {
        initialize()
        return try {
            val file = File(getVersionsDir(), getFilename(url))
            if (file.exists()) {
                json.decodeFromString<PageVersion>(file.readText())
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load page version", e)
            null
        }
    }
    
    // Save original HTML
    fun saveOriginalHtml(url: String, html: String) {
        initialize()
        try {
            val file = File(getOriginalDir(), getOriginalFilename(url))
            file.writeText(html)
            Log.d(TAG, "Saved original HTML: $url (${html.length} chars)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save original HTML", e)
        }
    }
    
    // Save translated HTML
    fun saveTranslatedHtml(url: String, html: String) {
        initialize()
        try {
            val file = File(getTranslatedDir(), getTranslatedFilename(url))
            file.writeText(html)
            Log.d(TAG, "Saved translated HTML: $url (${html.length} chars)")
            
            // Delete original to save space if translation successful
            val originalFile = File(getOriginalDir(), getOriginalFilename(url))
            if (originalFile.exists() && html.isNotEmpty()) {
                originalFile.delete()
                Log.d(TAG, "Deleted original HTML to save space: $url")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save translated HTML", e)
        }
    }
    
    // Load HTML (prefers translated, falls back to original)
    fun loadHtml(url: String): String? {
        initialize()
        return try {
            // Try translated first
            val translatedFile = File(getTranslatedDir(), getTranslatedFilename(url))
            if (translatedFile.exists()) {
                val content = translatedFile.readText()
                if (content.isNotEmpty()) {
                    return content
                }
            }
            
            // Fall back to original
            val originalFile = File(getOriginalDir(), getOriginalFilename(url))
            if (originalFile.exists()) {
                originalFile.readText()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load HTML", e)
            null
        }
    }
    
    // Calculate checksum for change detection
    fun calculateChecksum(html: String): String {
        return MD5(cleanHtmlForChecksum(html))
    }
    
    // Clean HTML for checksum (remove ads, dynamic content)
    private fun cleanHtmlForChecksum(html: String): String {
        // Remove script tags, ads, dynamic content
        return html.replace(Regex("""<script[^>]*>.*?</script>""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""<div[^>]*class="[^"]*ad[^"]*"[^>]*>.*?</div>""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""data-[^=]*="[^"]*""""), "") // Remove data attributes
            .replace(Regex("""on[^=]*="[^"]*""""), "") // Remove event handlers
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
    
    // MD5 helper
    private fun MD5(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            val hash = digest.digest(input.toByteArray())
            hash.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            input.hashCode().toString()
        }
    }
    
    // Get all stored URLs
    fun getAllUrls(): List<String> {
        initialize()
        return getVersionsDir().listFiles()?.mapNotNull { file ->
            try {
                val version = json.decodeFromString<PageVersion>(file.readText())
                version.url
            } catch (e: Exception) {
                null
            }
        } ?: emptyList()
    }
    
    // Get all page versions
    fun getAllPageVersions(): List<PageVersion> {
        initialize()
        return getVersionsDir().listFiles()?.mapNotNull { file ->
            try {
                json.decodeFromString<PageVersion>(file.readText())
            } catch (e: Exception) {
                null
            }
        } ?: emptyList()
    }
    
    // Delete page data
    fun deletePageData(url: String) {
        initialize()
        try {
            // Delete version file
            File(getVersionsDir(), getFilename(url)).delete()
            
            // Delete HTML files
            File(getOriginalDir(), getOriginalFilename(url)).delete()
            File(getTranslatedDir(), getTranslatedFilename(url)).delete()
            
            Log.d(TAG, "Deleted page data: $url")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete page data", e)
        }
    }
    
    // Clear all data
    fun clearAll() {
        initialize()
        getVersionsDir().deleteRecursively()
        getOriginalDir().deleteRecursively()
        getTranslatedDir().deleteRecursively()
        ensureDirectories()
        Log.d(TAG, "Cleared all page data")
    }
    
    // Get storage statistics
    fun getStorageStats(): StorageStats {
        initialize()
        val pages = getAllPageVersions()
        return StorageStats(
            totalPages = pages.size,
            translated = pages.count { it.translationStatus == PageVersion.TranslationStatus.TRANSLATED },
            failed = pages.count { it.translationStatus == PageVersion.TranslationStatus.FAILED },
            stale = pages.count { it.translationStatus == PageVersion.TranslationStatus.STALE },
            unchanged = pages.count { it.translationStatus == PageVersion.TranslationStatus.UNCHANGED },
            pending = pages.count { it.translationStatus == PageVersion.TranslationStatus.PENDING }
        )
    }
    
    data class StorageStats(
        val totalPages: Int,
        val translated: Int,
        val failed: Int,
        val stale: Int,
        val unchanged: Int,
        val pending: Int
    )
}