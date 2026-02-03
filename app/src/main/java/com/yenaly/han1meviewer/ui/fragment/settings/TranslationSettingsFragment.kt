package com.yenaly.han1meviewer.ui.fragment.settings

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.TranslationManager
import com.yenaly.yenaly_libs.utils.showToast

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
            TranslationManager.clearCache()
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
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.translation_key)
            .setMessage(R.string.translation_key_description)
            .setView(R.layout.dialog_translation_key_input)
            .setPositiveButton(R.string.save) { dialog, _ ->
                val dialogView = (dialog as androidx.appcompat.app.AlertDialog).findViewById<android.widget.EditText>(R.id.translation_key_input)
                val newKey = dialogView?.text?.toString()?.trim()
                if (newKey.isNullOrBlank()) {
                    showToast(getString(R.string.translation_key_empty_warning))
                } else {
                    Preferences.translationKey = newKey
                    translationKeyPref.summary = getTranslationKeySummary()
                    showToast(getString(R.string.translation_key_saved))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.reset_to_default) { _, _ ->
                Preferences.translationKey = "zh-en.en.67772d43-6981727d-8453ce13-74722d776562"
                translationKeyPref.summary = getTranslationKeySummary()
                showToast(getString(R.string.translation_key_reset))
            }
            .show()
            ?.findViewById<android.widget.EditText>(R.id.translation_key_input)
            ?.apply {
                setText(currentKey)
                setSelection(currentKey.length)
            }
    }

    private fun showTranslationDelayDialog() {
        val currentDelay = Preferences.translationDelayMs.toString()
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.translation_delay)
            .setMessage(R.string.translation_delay_description)
            .setView(R.layout.dialog_number_input)
            .setPositiveButton(R.string.save) { dialog, _ ->
                val dialogView = (dialog as androidx.appcompat.app.AlertDialog).findViewById<android.widget.EditText>(R.id.number_input)
                val newDelay = dialogView?.text?.toString()?.toLongOrNull()
                if (newDelay == null || newDelay < 0 || newDelay > 10000) {
                    showToast(getString(R.string.translation_delay_invalid))
                } else {
                    Preferences.preferenceSp.edit()
                        .putString(TRANSLATION_DELAY_MS, newDelay.toString())
                        .apply()
                    translationDelayPref.summary = getString(R.string.translation_delay_summary, newDelay)
                    showToast(getString(R.string.translation_delay_saved))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
            ?.findViewById<android.widget.EditText>(R.id.number_input)
            ?.apply {
                setText(currentDelay)
                hint = getString(R.string.translation_delay_hint)
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                setSelection(currentDelay.length)
            }
    }

    private fun showMaxRetriesDialog() {
        val currentRetries = Preferences.translationMaxRetries.toString()
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.translation_max_retries)
            .setMessage(R.string.translation_max_retries_description)
            .setView(R.layout.dialog_number_input)
            .setPositiveButton(R.string.save) { dialog, _ ->
                val dialogView = (dialog as androidx.appcompat.app.AlertDialog).findViewById<android.widget.EditText>(R.id.number_input)
                val newRetries = dialogView?.text?.toString()?.toIntOrNull()
                if (newRetries == null || newRetries < 0 || newRetries > 10) {
                    showToast(getString(R.string.translation_max_retries_invalid))
                } else {
                    Preferences.preferenceSp.edit()
                        .putInt(TRANSLATION_MAX_RETRIES, newRetries)
                        .apply()
                    translationMaxRetriesPref.summary = getString(R.string.translation_max_retries_summary, newRetries)
                    showToast(getString(R.string.translation_max_retries_saved))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
            ?.findViewById<android.widget.EditText>(R.id.number_input)
            ?.apply {
                setText(currentRetries)
                hint = getString(R.string.translation_max_retries_hint)
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                setSelection(currentRetries.length)
            }
    }

    private fun showRestartDialog() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.restart_app)
            .setMessage(R.string.translation_restart_message)
            .setPositiveButton(R.string.restart) { _, _ ->
                // Restart the app
                val intent = requireContext().packageManager
                    .getLaunchIntentForPackage(requireContext().packageName)
                intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP)
                intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                android.os.Process.killProcess(android.os.Process.myPid())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        // Update summary when returning to fragment
        translationKeyPref.summary = getTranslationKeySummary()
        translationDelayPref.summary = getString(R.string.translation_delay_summary, Preferences.translationDelayMs)
        translationMaxRetriesPref.summary = getString(R.string.translation_max_retries_summary, Preferences.translationMaxRetries)
    }
}