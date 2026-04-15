package com.nobodyz.han1meviewer.ui.fragment.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.preference.Preference
import androidx.preference.SeekBarPreference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textview.MaterialTextView
import com.nobodyz.han1meviewer.Preferences
import com.nobodyz.han1meviewer.R
import com.nobodyz.han1meviewer.logic.dao.DownloadDatabase
import com.nobodyz.han1meviewer.logic.dao.download.HanimeDownloadDao
import com.nobodyz.han1meviewer.logic.network.interceptor.SpeedLimitInterceptor
import com.nobodyz.han1meviewer.ui.fragment.PermissionRequester
import com.nobodyz.han1meviewer.ui.fragment.ToolbarHost
import com.nobodyz.han1meviewer.util.SafFileManager
import com.nobodyz.han1meviewer.util.SafFileManager.KEY_TREE_URI
import com.nobodyz.han1meviewer.util.SafFileManager.checkSafPermissions
import com.nobodyz.han1meviewer.util.SafFileManager.migratePrivateToSaf
import com.nobodyz.han1meviewer.util.setSummaryConverter
import com.nobodyz.han1meviewer.util.showAlertDialog
import com.nobodyz.han1meviewer.worker.HanimeDownloadManagerV2
import com.yenaly.yenaly_libs.base.preference.LongClickablePreference
import com.yenaly.yenaly_libs.base.settings.YenalySettingsFragment
import com.yenaly.yenaly_libs.utils.formatBytesPerSecond
import com.yenaly.yenaly_libs.utils.showLongToast

class DownloadSettingsFragment : YenalySettingsFragment(R.xml.settings_download){

    companion object {
        const val DOWNLOAD_PATH = "download_path"
        const val DOWNLOAD_COUNT_LIMIT = "download_count_limit"
        const val DOWNLOAD_SPEED_LIMIT = "download_speed_limit"
        const val USE_PRIVATE_STORAGE = "use_private_storage"
        const val IMPORT_DOWNLOADED_FILE = "import_downloaded_file"
    }

    private val downloadPath
            by safePreference<LongClickablePreference>(DOWNLOAD_PATH)
    private val importDownloadedFile
            by safePreference<Preference>(IMPORT_DOWNLOADED_FILE)
    private val downloadCountLimit
            by safePreference<SeekBarPreference>(DOWNLOAD_COUNT_LIMIT)
    private val downloadSpeedLimit
            by safePreference<SeekBarPreference>(DOWNLOAD_SPEED_LIMIT)
    private val storagePermissionRequester: PermissionRequester?
        get() = activity as? PermissionRequester
    private val openDirectoryPicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            SafFileManager.persistUriPermission(requireContext(), result.data)
            Preferences.preferenceSp.edit {
                putBoolean(USE_PRIVATE_STORAGE, false)
            }
            showLongToast(getString(R.string.directory_saved, result.data))
        } else {
            showLongToast(getString(R.string.no_directory_selected))
        }
    }

    private val dao: HanimeDownloadDao
        get() = DownloadDatabase.instance.hanimeDownloadDao

    override fun onStart() {
        super.onStart()
 //       (activity as SettingsActivity).setupToolbar()
        (activity as? ToolbarHost)?.setupToolbar(
            getString(R.string.download_settings),
            canNavigateBack = true
        )
        storagePermissionRequester?.requestStoragePermission(
            onGranted = {
                // 用户已授权，可以继续
                //Toast.makeText(requireContext(), "可以下载了喵\uD83D\uDC7F", Toast.LENGTH_SHORT).show()
                //TODO 大概需要做点什么
            },
            onDenied = {
                // 拒绝授权，返回上一层
                Toast.makeText(requireContext(), "拒绝？拒绝就不好办了喵👿", Toast.LENGTH_LONG).show()
                parentFragmentManager.popBackStack()
            },
            onPermanentlyDenied = {
                // 用户选择“不再询问”，引导去设置页
                showGoToSettingsDialog()
            }
        )
    }

    private fun showGoToSettingsDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("权限被永久拒绝")
            .setMessage("请前往设置开启存储权限，以便保存下载内容。")
            .setPositiveButton("去设置") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = "package:${requireContext().packageName}".toUri()
                }
                startActivity(intent)
            }
            .setNegativeButton("取消") { _, _ ->
                parentFragmentManager.popBackStack()
            }
            .show()
    }

    override fun onPreferencesCreated(savedInstanceState: Bundle?) {
        downloadPath.apply {
            setOnPreferenceClickListener{
                requireContext().showAlertDialog {
                    setTitle(getString(R.string.select_download_folder))
                    setMessage(getString(R.string.select_folder_message))
                    setPositiveButton(R.string.ok){_,_->
                        openDirectoryPicker.launch(SafFileManager.buildOpenDirectoryIntent())
                    }
                    setNegativeButton(getString(R.string.cancel)){ _, _ -> }
                }
                updateDownloadPathSummary()
                return@setOnPreferenceClickListener true
            }

            val uri = SafFileManager.getSavedUri()
            summary = if (uri != null){
                DocumentFile.fromTreeUri(requireContext(),uri)?.name ?:uri.toString()
            } else {
                "null"
            }
            setOnPreferenceLongClickListener {
//                val path = HFileManager.getAppDownloadFolder(context).path
//                path.copyToClipboard()
//                showShortToast(R.string.copy_to_clipboard)
                if (!Preferences.isUsePrivateStorage) {
                    requireContext().showAlertDialog {
                        setTitle(getString(R.string.restore_default_path))
                        setMessage(getString(R.string.restore_default_message))
                        setPositiveButton(R.string.ok){_,_->
                            Preferences.preferenceSp.edit {
                                putBoolean(USE_PRIVATE_STORAGE, true)
                                remove(KEY_TREE_URI)
                            }
                            updateDownloadPathSummary()
                            showLongToast(getString(R.string.default_path_restored))
                        }
                        setNegativeButton(getString(R.string.cancel)){  _, _ -> }
                    }
                }else{
                    requireContext().showAlertDialog {
                        setTitle(getString(R.string.already_default_path))
                        setMessage(getString(R.string.already_default_message))
                        setPositiveButton(getString(R.string.understood)){ _,_-> }
                    }
                }
                return@setOnPreferenceLongClickListener true
            }
        }
        importDownloadedFile.setOnPreferenceClickListener {
            if (!Preferences.isUsePrivateStorage &&
                !Preferences.safDownloadPath.isNullOrBlank() &&
                checkSafPermissions(requireContext())
                ){
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(getString(R.string.confirm_import))
                    .setMessage(getString(R.string.import_warning))
                    .setPositiveButton(R.string.ok) { _, _ ->
                        val dialogView =
                            layoutInflater.inflate(R.layout.layout_dialog_progress, null, false)
                        val titleTv = dialogView.findViewById<MaterialTextView>(R.id.progress_title)
                        val percentTv =
                            dialogView.findViewById<MaterialTextView>(R.id.progress_value)
                        val progressBar =
                            dialogView.findViewById<LinearProgressIndicator>(R.id.progress_bar)

                        val progressDialog = MaterialAlertDialogBuilder(requireContext())
                            .setTitle(getString(R.string.import_progress))
                            .setView(dialogView)
                            .setCancelable(false)
                            .create()
                        progressDialog.show()
                        // 启动迁移
                        migratePrivateToSaf(requireContext(), dao) { migrated, total ->
                            Log.i("migrate","$migrated,$total")
                            when (total) {
                                0 -> {
                                    progressDialog.dismiss()
                                    showLongToast(getString(R.string.no_exportable_files))
                                    return@migratePrivateToSaf
                                }
                                -1 -> {
                                    progressDialog.dismiss()
                                    showLongToast(getString(R.string.permission_error))
                                    return@migratePrivateToSaf
                                }
                            }
                            val percent = migrated * 100 / total
                            titleTv.text = getString(R.string.importing)
                            progressBar.max = 100
                            progressBar.progress = percent
                            percentTv.text = getString(R.string.import_progress_format).format(migrated, total, percent)

                            if (migrated == total) {
                                progressDialog.dismiss()
                                showLongToast(getString(R.string.import_complete, total))
                            }
                        }
                    }
                    .setNegativeButton(getString(R.string.cancel), null)
                    .show()
            }else{
                requireContext().showAlertDialog {
                    setTitle(getString(R.string.specify_path_first))
                    setMessage(getString(R.string.path_permission_message))
                    setPositiveButton(R.string.understood){ _,_-> }
                }
            }
            true
        }

        downloadCountLimit.apply {
            setSummaryConverter(
                defValue = HanimeDownloadManagerV2.MAX_CONCURRENT_DOWNLOAD_DEF,
                converter = ::toDownloadCountLimitPrettyString
            ) {
                // HanimeDownloadManager.maxConcurrentDownloadCount = it
                HanimeDownloadManagerV2.maxConcurrentDownloadCount = it
            }
        }
        downloadSpeedLimit.apply {
            min = 0
            max = SpeedLimitInterceptor.SPEED_BYTES.lastIndex
            setSummaryConverter(defValue = SpeedLimitInterceptor.NO_LIMIT_INDEX, converter = { i ->
                SpeedLimitInterceptor.SPEED_BYTES[i].toDownloadSpeedPrettyString()
            })
        }
    }

    override fun onResume() {
        super.onResume()
        updateDownloadPathSummary()
    }
    private fun updateDownloadPathSummary() {
        val usePrivate = Preferences.isUsePrivateStorage
        val path = if (usePrivate) {
            requireContext().getExternalFilesDir(null)?.absolutePath.orEmpty()
        } else {
            SafFileManager.getSavedUri()?.path
//            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
//                .resolve("Han1meViewer").absolutePath
        }
        downloadPath.summary = path
    }



    private fun Long.toDownloadSpeedPrettyString(): String {
        return if (this == 0L) {
            getString(R.string.no_limit)
        } else {
            this.formatBytesPerSecond()
        }
    }

    private fun toDownloadCountLimitPrettyString(value: Int): String {
        return if (value == 0) {
            getString(R.string.no_limit)
        } else {
            value.toString()
        }
    }


}