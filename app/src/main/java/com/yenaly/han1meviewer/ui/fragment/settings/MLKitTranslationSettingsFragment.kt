package com.yenaly.han1meviewer.ui.fragment.settings

import android.os.Bundle
import android.widget.Toast
import androidx.preference.*
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.MLKitTranslator
import com.yenaly.han1meviewer.logic.TranslationManager
import com.yenaly.han1meviewer.ui.activity.SettingsRouter
import com.yenaly.han1meviewer.ui.fragment.ToolbarHost
import com.yenaly.yenaly_libs.base.settings.YenalySettingsFragment
import kotlinx.coroutines.*

class MLKitTranslationSettingsFragment : YenalySettingsFragment(R.xml.settings_mlkit_translation) {
    
    private val translationManager by lazy {
        TranslationManager.getInstance(requireContext())
    }

    override fun onStart() {
        super.onStart()
        (activity as? ToolbarHost)?.setupToolbar(
            getString(R.string.mlkit_translation_title),
            canNavigateBack = true
        )
    }

    override fun onPreferencesCreated(savedInstanceState: Bundle?) {
        val mlkitSwitch by safePreference<SwitchPreferenceCompat>("use_mlkit_translation")
        val autoDownloadPref by safePreference<SwitchPreferenceCompat>("mlkit_auto_download")
        val showTagsPref by safePreference<SwitchPreferenceCompat>("show_translated_tags")
        val showTitlesPref by safePreference<SwitchPreferenceCompat>("show_translated_titles")
        val downloadButton by safePreference<Preference>("download_mlkit_model")
        val deleteButton by safePreference<Preference>("delete_mlkit_model")
        val statusPref by safePreference<Preference>("mlkit_model_status")
        val sizePref by safePreference<Preference>("mlkit_model_size")

        mlkitSwitch.setOnPreferenceChangeListener { _, newValue ->
            val enabled = newValue as Boolean
            if (enabled) {
                checkAndDownloadModel()
            }
            true
        }

        downloadButton.setOnPreferenceClickListener {
            downloadModel()
            true
        }

        deleteButton.setOnPreferenceClickListener {
            deleteModel()
            true
        }

        updateStatus()
    }

    private fun updateStatus() {
        CoroutineScope(Dispatchers.Main).launch {
            val status = withContext(Dispatchers.IO) {
                translationManager.getMLKitStatus()
            }

            val mlkitSwitch by safePreference<SwitchPreferenceCompat>("use_mlkit_translation")
            val downloadButton by safePreference<Preference>("download_mlkit_model")
            val deleteButton by safePreference<Preference>("delete_mlkit_model")
            val statusPref by safePreference<Preference>("mlkit_model_status")
            val sizePref by safePreference<Preference>("mlkit_model_size")

            val statusText = when (status) {
                MLKitTranslator.ModelStatus.NOT_INITIALIZED -> "Not initialized"
                MLKitTranslator.ModelStatus.NOT_DOWNLOADED -> "Not downloaded"
                MLKitTranslator.ModelStatus.DOWNLOADING -> "Downloading..."
                MLKitTranslator.ModelStatus.DOWNLOADED -> "Downloaded"
                MLKitTranslator.ModelStatus.ERROR -> "Error"
            }

            statusPref.summary = statusText

            downloadButton.isVisible = status != MLKitTranslator.ModelStatus.DOWNLOADED
            deleteButton.isVisible = status == MLKitTranslator.ModelStatus.DOWNLOADED

            if (status == MLKitTranslator.ModelStatus.DOWNLOADED) {
                val sizeMB = translationManager.getMLKitModelSize() / (1024 * 1024)
                sizePref.summary = "${sizeMB} MB"
            } else {
                sizePref.summary = "Approx. 40 MB"
            }
        }
    }

    private fun checkAndDownloadModel() {
        if (Preferences.mlkitAutoDownload) {
            CoroutineScope(Dispatchers.Main).launch {
                val status = withContext(Dispatchers.IO) {
                    translationManager.getMLKitStatus()
                }

                if (status == MLKitTranslator.ModelStatus.NOT_DOWNLOADED) {
                    downloadModel()
                }
            }
        }
    }

    private fun downloadModel() {
        CoroutineScope(Dispatchers.Main).launch {
            val statusPref by safePreference<Preference>("mlkit_model_status")
            val downloadButton by safePreference<Preference>("download_mlkit_model")
            
            statusPref.summary = "Downloading..."
            downloadButton.isEnabled = false

            val success = withContext(Dispatchers.IO) {
                translationManager.downloadMLKitModel()
            }

            if (success) {
                Toast.makeText(requireContext(), "Model downloaded successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Failed to download model", Toast.LENGTH_SHORT).show()
            }

            updateStatus()
            downloadButton.isEnabled = true
        }
    }

    private fun deleteModel() {
        CoroutineScope(Dispatchers.Main).launch {
            val success = withContext(Dispatchers.IO) {
                translationManager.deleteMLKitModel()
            }

            if (success) {
                Toast.makeText(requireContext(), "Model deleted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Failed to delete model", Toast.LENGTH_SHORT).show()
            }

            updateStatus()
        }
    }
}