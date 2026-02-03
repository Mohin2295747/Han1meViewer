package com.yenaly.han1meviewer.ui.fragment.settings

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.TranslationManager
import kotlinx.coroutines.runBlocking

/**
 * Translation settings fragment for configuring translation options
 */
class TranslationSettingsFragment : PreferenceFragmentCompat() {

    companion object {
        const val TRANSLATION_ENABLED = "translation_enabled"
        const val TRANSLATION_KEY = "translation_key"
        const val TRANSLATION_WAIT_ENABLED = "translation_wait_enabled"
        const val TRANSLATION_DELAY_MS = "translation_delay_ms"
        const val TRANSLATION_MAX_RETRIES = "translation_max_retries"
        const val CLEAR_TRANSLATION_CACHE = "clear_translation_cache"
        const val RESTART_APP = "restart_app"
    }

    private lateinit var translationEnabledPref: SwitchPreferenceCompat
    private lateinit var translationKeyPref: Preference
    private lateinit var translationWaitEnabledPref: SwitchPreferenceCompat
    private lateinit var translationDelayPref: Preference
    private lateinit var translationMaxRetriesPref: Preference
    private lateinit var clearCachePref: Preference
    private lateinit var restartAppPref: Preference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_translation, rootKey)

        // Initialize preferences
        translationEnabledPref = findPreference(TRANSLATION_ENABLED)!!
        translationKeyPref = findPreference(TRANSLATION_KEY)!!
        translationWaitEnabledPref = findPreference(TRANSLATION_WAIT_ENABLED)!!
        translationDelayPref = findPreference(TRANSLATION_DELAY_MS)!!
        translationMaxRetriesPref = findPreference(TRANSLATION_MAX_RETRIES)!!
        clearCachePref = findPreference(CLEAR_TRANSLATION_CACHE)!!
        restartAppPref = findPreference(RESTART_APP)!!

        setupPreferences()
    }

    private fun setupPreferences() {
        // Set initial values
        translationKeyPref.summary = getTranslationKeySummary()
        
        // Set up preference change listeners
        translationEnabledPref.setOnPreferenceChangeListener { _, newValue ->
            val enabled = newValue as Boolean
            Preferences.isTranslationEnabled = enabled
            updatePreferenceStates(enabled)
            true
        }

        translationKeyPref.setOnPreferenceClickListener {
            showTranslationKeyDialog()
            true
        }

        translationWaitEnabledPref.setOnPreferenceChangeListener { _, newValue ->
            Preferences.preferenceSp.edit()
                .putBoolean(TRANSLATION_WAIT_ENABLED, newValue as Boolean)
                .apply()
            true
        }

        translationDelayPref.setOnPreferenceClickListener {
            showTranslationDelayDialog()
            true
        }

        translationMaxRetriesPref.setOnPreferenceClickListener {
            showMaxRetriesDialog()
            true
        }

        clearCachePref.setOnPreferenceClickListener {
            runBlocking {
                TranslationManager.clearCache()
            }
            showToast(getString(R.string.translation_cache_cleared))
            true
        }

        restartAppPref.setOnPreferenceClickListener {
            showRestartDialog()
            true
        }

        // Initial state update
        updatePreferenceStates(Preferences.isTranslationEnabled)
    }

    private fun updatePreferenceStates(isEnabled: Boolean) {
        translationKeyPref.isEnabled = isEnabled
        translationWaitEnabledPref.isEnabled = isEnabled
        translationDelayPref.isEnabled = isEnabled
        translationMaxRetriesPref.isEnabled = isEnabled
        clearCachePref.isEnabled = isEnabled
    }

    private fun getTranslationKeySummary(): String {
        val key = Preferences.translationKey
        return if (key.isNullOrBlank()) {
            getString(R.string.translation_key_default)
        } else {
            if (key.length > 30) {
                "${key.substring(0, 27)}..."
            } else {
                key
            }
        }
    }

    private fun showTranslationKeyDialog() {
        val currentKey = Preferences.translationKey ?: "zh-en.en.67772d43-6981727d-8453ce13-74722d776562"
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_translation_key_input, null)
        val editText = dialogView.findViewById<EditText>(R.id.translation_key_input)
        editText.setText(currentKey)
        
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.translation_key)
            .setMessage(R.string.translation_key_description)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { dialog, _ ->
                val newKey = editText.text?.toString()?.trim()
                if (newKey.isNullOrBlank()) {
                    showToast(getString(R.string.translation_key_empty_warning))
                } else {
                    Preferences.translationKey = newKey
                    translationKeyPref.summary = getTranslationKeySummary()
                    showToast(getString(R.string.translation_key_saved))
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .setNeutralButton(R.string.reset_to_default) { dialog, _ ->
                Preferences.translationKey = "zh-en.en.67772d43-6981727d-8453ce13-74722d776562"
                translationKeyPref.summary = getTranslationKeySummary()
                showToast(getString(R.string.translation_key_reset))
                dialog.dismiss()
            }
            .show()
    }

    private fun showTranslationDelayDialog() {
        val currentDelay = Preferences.translationDelayMs.toString()
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_number_input, null)
        val editText = dialogView.findViewById<EditText>(R.id.number_input)
        editText.setText(currentDelay)
        editText.hint = getString(R.string.translation_delay_hint)
        
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.translation_delay)
            .setMessage(R.string.translation_delay_description)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { dialog, _ ->
                val newDelay = editText.text?.toString()?.toLongOrNull()
                if (newDelay == null || newDelay < 0 || newDelay > 10000) {
                    showToast(getString(R.string.translation_delay_invalid))
                } else {
                    Preferences.preferenceSp.edit()
                        .putString(TRANSLATION_DELAY_MS, newDelay.toString())
                        .apply()
                    translationDelayPref.summary = getString(R.string.translation_delay_summary, newDelay)
                    showToast(getString(R.string.translation_delay_saved))
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showMaxRetriesDialog() {
        val currentRetries = Preferences.translationMaxRetries.toString()
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_number_input, null)
        val editText = dialogView.findViewById<EditText>(R.id.number_input)
        editText.setText(currentRetries)
        editText.hint = getString(R.string.translation_max_retries_hint)
        
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.translation_max_retries)
            .setMessage(R.string.translation_max_retries_description)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { dialog, _ ->
                val newRetries = editText.text?.toString()?.toIntOrNull()
                if (newRetries == null || newRetries < 0 || newRetries > 10) {
                    showToast(getString(R.string.translation_max_retries_invalid))
                } else {
                    Preferences.preferenceSp.edit()
                        .putInt(TRANSLATION_MAX_RETRIES, newRetries)
                        .apply()
                    translationMaxRetriesPref.summary = getString(R.string.translation_max_retries_summary, newRetries)
                    showToast(getString(R.string.translation_max_retries_saved))
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun showRestartDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.restart_app)
            .setMessage(R.string.translation_restart_message)
            .setPositiveButton(R.string.restart_app) { dialog, _ ->
                // Restart the app
                val intent = requireContext().packageManager
                    .getLaunchIntentForPackage(requireContext().packageName)
                intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent != null) {
                    startActivity(intent)
                }
                android.os.Process.killProcess(android.os.Process.myPid())
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        // Update summary when returning to fragment
        translationKeyPref.summary = getTranslationKeySummary()
        translationDelayPref.summary = getString(R.string.translation_delay_summary, Preferences.translationDelayMs)
        translationMaxRetriesPref.summary = getString(R.string.translation_max_retries_summary, Preferences.translationMaxRetries)
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
