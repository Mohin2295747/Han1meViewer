package com.yenaly.han1meviewer.ui.fragment.settings

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreferenceCompat
import com.yenaly.han1meviewer.R
import com.yenaly.han1meviewer.Preferences
import com.yenaly.han1meviewer.logic.PageStorageManager
import com.yenaly.han1meviewer.logic.TranslationManager
import kotlinx.coroutines.runBlocking

class TranslationSettingsFragment : PreferenceFragmentCompat() {

    companion object {
        const val TRANSLATION_ENABLED = "translation_enabled"
        const val TRANSLATION_KEY = "translation_key"
        const val TRANSLATION_WAIT_ENABLED = "translation_wait_enabled"
        const val TRANSLATION_DELAY_MS = "translation_delay_ms"
        const val TRANSLATION_MAX_RETRIES = "translation_max_retries"
        const val CLEAR_TRANSLATION_CACHE = "clear_translation_cache"
        const val VIEW_STORED_PAGES = "view_stored_pages"
        const val CLEAR_ALL_PAGES = "clear_all_pages"
        const val RESTART_APP = "restart_app"
    }

    private lateinit var translationEnabledPref: SwitchPreferenceCompat
    private lateinit var translationKeyPref: Preference
    private lateinit var translationWaitEnabledPref: SwitchPreferenceCompat
    private lateinit var translationDelayPref: Preference
    private lateinit var translationMaxRetriesPref: Preference
    private lateinit var clearCachePref: Preference
    private lateinit var viewStoredPagesPref: Preference
    private lateinit var clearAllPagesPref: Preference
    private lateinit var restartAppPref: Preference

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_translation, rootKey)

        translationEnabledPref = findPreference(TRANSLATION_ENABLED)!!
        translationKeyPref = findPreference(TRANSLATION_KEY)!!
        translationWaitEnabledPref = findPreference(TRANSLATION_WAIT_ENABLED)!!
        translationDelayPref = findPreference(TRANSLATION_DELAY_MS)!!
        translationMaxRetriesPref = findPreference(TRANSLATION_MAX_RETRIES)!!
        clearCachePref = findPreference(CLEAR_TRANSLATION_CACHE)!!
        viewStoredPagesPref = findPreference(VIEW_STORED_PAGES)!!
        clearAllPagesPref = findPreference(CLEAR_ALL_PAGES)!!
        restartAppPref = findPreference(RESTART_APP)!!

        setupPreferences()
    }

    private fun setupPreferences() {
        translationKeyPref.summary = getTranslationKeySummary()
        updateStoredPagesSummary()

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
            runBlocking { TranslationManager.clearCache() }
            showToast(getString(R.string.translation_cache_cleared))
            true
        }

        viewStoredPagesPref.setOnPreferenceClickListener {
            navigateToPageStorage()
            true
        }

        clearAllPagesPref.setOnPreferenceClickListener {
            showClearAllPagesDialog()
            true
        }

        restartAppPref.setOnPreferenceClickListener {
            showRestartDialog()
            true
        }

        updatePreferenceStates(Preferences.isTranslationEnabled)
    }

    private fun updatePreferenceStates(isEnabled: Boolean) {
        translationKeyPref.isEnabled = isEnabled
        translationWaitEnabledPref.isEnabled = isEnabled
        translationDelayPref.isEnabled = isEnabled
        translationMaxRetriesPref.isEnabled = isEnabled
        clearCachePref.isEnabled = isEnabled
        viewStoredPagesPref.isEnabled = isEnabled
        clearAllPagesPref.isEnabled = isEnabled
    }

    private fun getTranslationKeySummary(): String {
        val key = Preferences.translationKey
        return if (key.isNullOrBlank()) {
            getString(R.string.translation_key_default)
        } else {
            if (key.length > 30) "${key.substring(0, 27)}..." else key
        }
    }

    private fun updateStoredPagesSummary() {
        runBlocking {
            PageStorageManager.initialize()
            val stats = PageStorageManager.getStorageStats()

            viewStoredPagesPref.summary = getString(
                R.string.stored_pages_summary,
                stats.totalPages,
                stats.translated,
                stats.failed,
                stats.stale
            )

            if (stats.totalPages > 0) {
                clearAllPagesPref.summary =
                    getString(R.string.clear_all_pages_summary, stats.totalPages)
                clearAllPagesPref.isEnabled = true
            } else {
                clearAllPagesPref.summary = getString(R.string.no_pages_stored)
                clearAllPagesPref.isEnabled = false
            }
        }
    }

    private fun showTranslationKeyDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_translation_key_input, null)
        val editText = dialogView.findViewById<EditText>(R.id.translation_key_input)
        editText.setText(Preferences.translationKey ?: "")

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.translation_key)
            .setMessage(R.string.translation_key_description)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { dialog, _ ->
                val newKey = editText.text.toString().trim()
                if (newKey.isNotBlank()) {
                    Preferences.translationKey = newKey
                    translationKeyPref.summary = getTranslationKeySummary()
                    showToast(getString(R.string.translation_key_saved))
                } else {
                    showToast(getString(R.string.translation_key_empty_warning))
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showTranslationDelayDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_number_input, null)
        val editText = dialogView.findViewById<EditText>(R.id.number_input)
        editText.setText(Preferences.translationDelayMs.toString())

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.translation_delay)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { dialog, _ ->
                editText.text.toString().toLongOrNull()?.let {
                    Preferences.preferenceSp.edit()
                        .putString(TRANSLATION_DELAY_MS, it.toString())
                        .apply()
                    translationDelayPref.summary =
                        getString(R.string.translation_delay_summary, it)
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showMaxRetriesDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_number_input, null)
        val editText = dialogView.findViewById<EditText>(R.id.number_input)
        editText.setText(Preferences.translationMaxRetries.toString())

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.translation_max_retries)
            .setView(dialogView)
            .setPositiveButton(R.string.save) { dialog, _ ->
                editText.text.toString().toIntOrNull()?.let {
                    Preferences.preferenceSp.edit()
                        .putInt(TRANSLATION_MAX_RETRIES, it)
                        .apply()
                    translationMaxRetriesPref.summary =
                        getString(R.string.translation_max_retries_summary, it)
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showClearAllPagesDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.clear_all_pages)
            .setMessage(R.string.clear_all_pages_confirmation)
            .setPositiveButton(R.string.clear) { dialog, _ ->
                runBlocking { PageStorageManager.clearAll() }
                updateStoredPagesSummary()
                showToast(getString(R.string.all_pages_cleared))
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun showRestartDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.restart_app)
            .setMessage(R.string.translation_restart_message)
            .setPositiveButton(R.string.restart_app) { dialog, _ ->
                val intent = requireContext().packageManager
                    .getLaunchIntentForPackage(requireContext().packageName)
                intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                android.os.Process.killProcess(android.os.Process.myPid())
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun navigateToPageStorage() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fcv_settings, PageStorageFragment())
            .addToBackStack(null)
            .commit()
    }

    override fun onResume() {
        super.onResume()
        translationKeyPref.summary = getTranslationKeySummary()
        translationDelayPref.summary =
            getString(R.string.translation_delay_summary, Preferences.translationDelayMs)
        translationMaxRetriesPref.summary =
            getString(R.string.translation_max_retries_summary, Preferences.translationMaxRetries)
        updateStoredPagesSummary()
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}