package com.yenaly.han1meviewer.ui.fragment.settings

import android.os.Bundle
import androidx.preference.*
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.logic.MLKitTranslator
import com.yenaly.han1meviewer.logic.TranslationManager
import com.yenaly.han1meviewer.ui.fragment.YenalySettingsFragment
import kotlinx.coroutines.*

class MLKitTranslationSettingsFragment : YenalySettingsFragment() {

    private lateinit var mlkitSwitch: SwitchPreferenceCompat
    private lateinit var autoDownloadPref: SwitchPreferenceCompat
    private lateinit var showTagsPref: SwitchPreferenceCompat
    private lateinit var showTitlesPref: SwitchPreferenceCompat
    private lateinit var downloadButton: Preference
    private lateinit var deleteButton: Preference
    private lateinit var statusPref: Preference
    private lateinit var sizePref: Preference
    
    private val translationManager by lazy {
        TranslationManager.getInstance(requireContext())
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_mlkit_translation, rootKey)

        mlkitSwitch = findPreference("use_mlkit_translation")!!
        autoDownloadPref = findPreference("mlkit_auto_download")!!
        showTagsPref = findPreference("show_translated_tags")!!
        showTitlesPref = findPreference("show_translated_titles")!!
        downloadButton = findPreference("download_mlkit_model")!!
        deleteButton = findPreference("delete_mlkit_model")!!
        statusPref = findPreference("mlkit_model_status")!!
        sizePref = findPreference("mlkit_model_size")!!

        setupListeners()
        updateStatus()
    }

    private fun setupListeners() {
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
    }

    private fun updateStatus() {
        viewLifecycleOwner.lifecycleScope.launch {
            val status = withContext(Dispatchers.IO) {
                translationManager.getMLKitStatus()
            }

            val statusText = when (status) {
                MLKitTranslator.ModelStatus.NOT_INITIALIZED -> "Not initialized"
                MLKitTranslator.ModelStatus.NOT_DOWNLOADED -> "Not downloaded"
                MLKitTranslator.ModelStatus.DOWNLOADING -> "Downloading..."
                MLKitTranslator.ModelStatus.DOWNLOADED -> "Downloaded"
                MLKitTranslator.ModelStatus.ERROR -> "Error"
            }

            statusPref.summary = statusText

            // Update button visibility
            downloadButton.isVisible = status != MLKitTranslator.ModelStatus.DOWNLOADED
            deleteButton.isVisible = status == MLKitTranslator.ModelStatus.DOWNLOADED

            // Update model size
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
            viewLifecycleOwner.lifecycleScope.launch {
                val status = withContext(Dispatchers.IO) {
                    translationManager.getMLKitStatus()
                }

                if (status == MLKitTranslator.ModelStatus.NOT_DOWNLOADED) {
                    downloadModel()
                }
            }
        }
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    
    // Setup toolbar with back button
    setupToolbar()
}

private fun setupToolbar() {
    (activity as? AppCompatActivity)?.supportActionBar?.apply {
        setDisplayHomeAsUpEnabled(true)
        title = "ML Kit Translation"
    }
}

// Handle back button press
override fun onOptionsItemSelected(item: MenuItem): Boolean {
    return when (item.itemId) {
        android.R.id.home -> {
            findNavController().navigateUp()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
}

    private fun downloadModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            statusPref.summary = "Downloading..."
            downloadButton.isEnabled = false

            val success = withContext(Dispatchers.IO) {
                translationManager.downloadMLKitModel()
            }

            if (success) {
                showToast("Model downloaded successfully")
            } else {
                showToast("Failed to download model")
            }

            updateStatus()
            downloadButton.isEnabled = true
        }
    }

    private fun deleteModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                translationManager.deleteMLKitModel()
            }

            if (success) {
                showToast("Model deleted")
            } else {
                showToast("Failed to delete model")
            }

            updateStatus()
        }
    }
}