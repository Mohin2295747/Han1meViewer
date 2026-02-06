package com.yenaly.han1meviewer

import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.yenaly.han1meviewer.HanimeConstants.HANIME_URL
import com.yenaly.han1meviewer.logic.network.HProxySelector
import com.yenaly.han1meviewer.logic.network.interceptor.SpeedLimitInterceptor
import com.yenaly.han1meviewer.ui.fragment.settings.DownloadSettingsFragment
import com.yenaly.han1meviewer.ui.fragment.settings.HKeyframeSettingsFragment
import com.yenaly.han1meviewer.ui.fragment.settings.HomeSettingsFragment
import com.yenaly.han1meviewer.ui.fragment.settings.MpvPlayerSettings
import com.yenaly.han1meviewer.ui.fragment.settings.NetworkSettingsFragment
import com.yenaly.han1meviewer.ui.fragment.settings.PlayerSettingsFragment
import com.yenaly.han1meviewer.ui.view.video.HJzvdStd
import com.yenaly.han1meviewer.ui.view.video.HMediaKernel
import com.yenaly.han1meviewer.util.CookieString
import com.yenaly.han1meviewer.util.SafFileManager
import com.yenaly.han1meviewer.worker.HanimeDownloadManagerV2
import com.yenaly.yenaly_libs.utils.applicationContext
import com.yenaly.yenaly_libs.utils.getSpValue
import com.yenaly.yenaly_libs.utils.putSpValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime

object Preferences {
    val preferenceSp: SharedPreferences
        get() = PreferenceManager.getDefaultSharedPreferences(applicationContext)

    var isAlreadyLogin: Boolean
        get() = getSpValue(ALREADY_LOGIN, false)
        set(value) {
            loginStateFlow.value = value
            putSpValue(ALREADY_LOGIN, value)
        }

    val loginStateFlow = MutableStateFlow(isAlreadyLogin)

    val savedUserId: String
        get() = preferenceSp.getString(SAVED_USER_ID,"") ?: ""

    var loginCookie
        get() = CookieString(getSpValue(LOGIN_COOKIE, EMPTY_STRING))
        set(value) {
            loginCookieStateFlow.value = value
            putSpValue(LOGIN_COOKIE, value.cookie)
        }

    val loginCookieStateFlow = MutableStateFlow(loginCookie)

    var cloudFlareCookie
        get() = CookieString(getSpValue(CLOUDFLARE_COOKIE, EMPTY_STRING))
        set(value) {
            cloudFlareCookieStateFlow.value = value
            putSpValue(CLOUDFLARE_COOKIE, value.cookie)
        }

    val cloudFlareCookieStateFlow = MutableStateFlow(cloudFlareCookie)

    private const val UPDATE_NODE_ID = "update_node_id"

    var updateNodeId: String
        get() = getSpValue(UPDATE_NODE_ID, EMPTY_STRING)
        set(value) = putSpValue(UPDATE_NODE_ID, value)

    var lastUpdatePopupTime
        get() = getSpValue(HomeSettingsFragment.LAST_UPDATE_POPUP_TIME, 0L)
        set(value) = putSpValue(HomeSettingsFragment.LAST_UPDATE_POPUP_TIME, value)

    val updatePopupIntervalDays
        get() = preferenceSp.getInt(HomeSettingsFragment.UPDATE_POPUP_INTERVAL_DAYS, 0)

    val useCIUpdateChannel
        get() = preferenceSp.getBoolean(HomeSettingsFragment.USE_CI_UPDATE_CHANNEL, false)

    @OptIn(ExperimentalTime::class)
    val isUpdateDialogVisible: Boolean
        get() {
            val now = kotlin.time.Clock.System.now()
            val lastCheckTime = kotlin.time.Instant.fromEpochSeconds(lastUpdatePopupTime)
            val interval = updatePopupIntervalDays
            return now > lastCheckTime + interval.days
        }

    val switchPlayerKernel: String
        get() = preferenceSp.getString(
            PlayerSettingsFragment.SWITCH_PLAYER_KERNEL,
            HMediaKernel.Type.ExoPlayer.name
        ) ?: HMediaKernel.Type.ExoPlayer.name

    val showBottomProgress: Boolean
        get() = preferenceSp.getBoolean(PlayerSettingsFragment.SHOW_BOTTOM_PROGRESS, true)

    val playerSpeed: Float
        get() = preferenceSp.getString(
            PlayerSettingsFragment.PLAYER_SPEED,
            HJzvdStd.DEF_SPEED.toString()
        )?.toFloat() ?: HJzvdStd.DEF_SPEED

    val slideSensitivity: Int
        get() = preferenceSp.getInt(
            PlayerSettingsFragment.SLIDE_SENSITIVITY,
            HJzvdStd.DEF_PROGRESS_SLIDE_SENSITIVITY
        )

    val longPressSpeedTime: Float
        get() = preferenceSp.getString(
            PlayerSettingsFragment.LONG_PRESS_SPEED_TIMES,
            HJzvdStd.DEF_LONG_PRESS_SPEED_TIMES.toString()
        )?.toFloat() ?: HJzvdStd.DEF_LONG_PRESS_SPEED_TIMES

    val videoLanguage: String
        get() = preferenceSp.getString(HomeSettingsFragment.VIDEO_LANGUAGE, "zhs") ?: "zht"

    val videoQuality: String
        get() = preferenceSp.getString(HomeSettingsFragment.DEFAULT_VIDEO_QUALITY, "1080P") ?: "1080P"

    val showPlayedIndicator: Boolean
        get() = preferenceSp.getBoolean(HomeSettingsFragment.SHOW_PLAYED_INDICATOR,true)

    val fakeLauncherIcon: String
        get() = preferenceSp.getString(
            HomeSettingsFragment.FAKE_LAUNCHER_ICON,
            "com.yenaly.han1meviewer.LauncherAliasDefault"
        ) ?: "com.yenaly.han1meviewer.LauncherAliasDefault"

    val baseUrl: String
        get() = preferenceSp.getString(NetworkSettingsFragment.DOMAIN_NAME, HANIME_URL[0]) ?: HANIME_URL[0]

    val useBuiltInHosts: Boolean
        get() = preferenceSp.getBoolean(NetworkSettingsFragment.USE_BUILT_IN_HOSTS, false)

    val whenCountdownRemind: Int
        get() = preferenceSp.getInt(
            HKeyframeSettingsFragment.WHEN_COUNTDOWN_REMIND,
            HJzvdStd.DEF_COUNTDOWN_SEC
        ) * 1_000

    val showCommentWhenCountdown: Boolean
        get() = preferenceSp.getBoolean(HKeyframeSettingsFragment.SHOW_COMMENT_WHEN_COUNTDOWN, false)

    val hKeyframesEnable: Boolean
        get() = preferenceSp.getBoolean(HKeyframeSettingsFragment.H_KEYFRAMES_ENABLE, true)

    val sharedHKeyframesEnable: Boolean
        get() = preferenceSp.getBoolean(HKeyframeSettingsFragment.SHARED_H_KEYFRAMES_ENABLE, true)

    val sharedHKeyframesUseFirst: Boolean
        get() = preferenceSp.getBoolean(HKeyframeSettingsFragment.SHARED_H_KEYFRAMES_USE_FIRST, false)

    val proxyType: Int
        get() = preferenceSp.getInt(NetworkSettingsFragment.PROXY_TYPE, HProxySelector.TYPE_SYSTEM)

    val proxyIp: String
        get() = preferenceSp.getString(NetworkSettingsFragment.PROXY_IP, EMPTY_STRING).orEmpty()

    val proxyPort: Int
        get() = preferenceSp.getInt(NetworkSettingsFragment.PROXY_PORT, -1)

    val isAnalyticsEnabled: Boolean
        get() = preferenceSp.getBoolean(HomeSettingsFragment.USE_ANALYTICS, true)

    val downloadCountLimit: Int
        get() = preferenceSp.getInt(
            DownloadSettingsFragment.DOWNLOAD_COUNT_LIMIT,
            HanimeDownloadManagerV2.MAX_CONCURRENT_DOWNLOAD_DEF
        )

    val collapseDownloadedGroup: Boolean
        get() = preferenceSp.getBoolean(HomeSettingsFragment.COLLAPSE_DOWNLOADED_GROUP,false)

    val isUsePrivateStorage: Boolean
        get() = preferenceSp.getBoolean(DownloadSettingsFragment.USE_PRIVATE_STORAGE,true)

    val safDownloadPath: String?
        get() = preferenceSp.getString(SafFileManager.KEY_TREE_URI,null)

    val useDarkMode: String
        get() = preferenceSp.getString(HomeSettingsFragment.USE_DARK_MODE,"always_off") ?: "always_off"

    val useDynamicColor: Boolean
        get() = preferenceSp.getBoolean(HomeSettingsFragment.USE_DYNAMIC_COLOR,false)

    val allowResumePlayback: Boolean
        get() = preferenceSp.getBoolean(HomeSettingsFragment.ALLOW_RESUME_PLAYBACK,true)

    val searchArtistIgnoreVideoType: Boolean
        get() = preferenceSp.getBoolean(HomeSettingsFragment.SEARCH_ARTIST_IGNORE_VIDEO_TYPE,false)

    val disableMobileDataWarning: Boolean
        get() = preferenceSp.getBoolean(HomeSettingsFragment.DISABLE_MOBILE_DATA_WARNING,false)

    val mpvProfile: String
        get() = preferenceSp.getString(MpvPlayerSettings.MPV_PROFILE, "fast") ?: "fast"

    val enableGPUNextRenderer: Boolean
        get() = preferenceSp.getBoolean(MpvPlayerSettings.ENABLE_GPU_NEXT_RENDERER, false)

    val mpvInterpolation: Boolean
        get() = preferenceSp.getBoolean(MpvPlayerSettings.MPV_INTERPOLATION, false)

    val mpvDeband: Boolean
        get() = preferenceSp.getBoolean(MpvPlayerSettings.MPV_DEBAND, true)

    val mpvFramedrop: Boolean
        get() = preferenceSp.getBoolean(MpvPlayerSettings.MPV_FRAMEDROP, true)

    val mpvHwdec: String
        get() = preferenceSp.getString(MpvPlayerSettings.MPV_HWDEC, "Auto")?: "Auto"

    val mpvCacheSecs: Int
        get() = preferenceSp.getInt(MpvPlayerSettings.MPV_CACHE_SECS, 60)

    val mpvTlsVerify: Boolean
        get() = preferenceSp.getBoolean(MpvPlayerSettings.MPV_TLS_VERIFY, true)

    val mpvNetworkTimeout: Int
        get() = preferenceSp.getInt(MpvPlayerSettings.MPV_NETWORK_TIMEOUT, 10)

    val customMpvParams: String
        get() = preferenceSp.getString(MpvPlayerSettings.CUSTOM_PARAMS,"")?: ""

    val downloadSpeedLimit: Long
        get() {
            val index = preferenceSp.getInt(
                DownloadSettingsFragment.DOWNLOAD_SPEED_LIMIT,
                SpeedLimitInterceptor.NO_LIMIT_INDEX
            )
            return SpeedLimitInterceptor.SPEED_BYTES[index]
        }

    var isTranslationEnabled: Boolean
        get() = getSpValue(IS_TRANSLATION_ENABLED, false)
        set(value) = putSpValue(IS_TRANSLATION_ENABLED, value)

    var translationApiKeys: Set<String>
        get() = getSpValue(TRANSLATION_API_KEYS, setOf<String>())
        set(value) = putSpValue(TRANSLATION_API_KEYS, value)

    var translationMonthlyLimit: Int
        get() = getSpValue(TRANSLATION_MONTHLY_LIMIT, 500000)
        set(value) = putSpValue(TRANSLATION_MONTHLY_LIMIT, value)

    var translationTargetLang: String
        get() = getSpValue(TRANSLATION_TARGET_LANG, "EN")
        set(value) = putSpValue(TRANSLATION_TARGET_LANG, value)

    var translationBatchSize: Int
        get() = getSpValue(TRANSLATION_BATCH_SIZE, 30000)
        set(value) = putSpValue(TRANSLATION_BATCH_SIZE, value)

    var translateTitles: Boolean
        get() = getSpValue(TRANSLATE_TITLES, true)
        set(value) = putSpValue(TRANSLATE_TITLES, value)

    var translateDescriptions: Boolean
        get() = getSpValue(TRANSLATE_DESCRIPTIONS, true)
        set(value) = putSpValue(TRANSLATE_DESCRIPTIONS, value)

    var translateComments: Boolean
        get() = getSpValue(TRANSLATE_COMMENTS, true)
        set(value) = putSpValue(TRANSLATE_COMMENTS, value)

    var translateTags: Boolean
        get() = getSpValue(TRANSLATE_TAGS, true)
        set(value) = putSpValue(TRANSLATE_TAGS, value)

    var useMLKitTranslation: Boolean
        get() = getSpValue(USE_MLKIT_TRANSLATION, false)
        set(value) = putSpValue(USE_MLKIT_TRANSLATION, value)

    var mlkitAutoDownload: Boolean
        get() = getSpValue(MLKIT_AUTO_DOWNLOAD, true)
        set(value) = putSpValue(MLKIT_AUTO_DOWNLOAD, value)

    var showTranslatedTags: Boolean
        get() = getSpValue(SHOW_TRANSLATED_TAGS, true)
        set(value) = putSpValue(SHOW_TRANSLATED_TAGS, value)

    var showTranslatedTitles: Boolean
        get() = getSpValue(SHOW_TRANSLATED_TITLES, true)
        set(value) = putSpValue(SHOW_TRANSLATED_TITLES, value)

    var mlkitLastDownloadTime: Long
        get() = getSpValue(MLKIT_LAST_DOWNLOAD_TIME, 0L)
        set(value) = putSpValue(MLKIT_LAST_DOWNLOAD_TIME, value)

    companion object {
        private const val ALREADY_LOGIN = "already_login"
        private const val SAVED_USER_ID = "saved_user_id"
        private const val LOGIN_COOKIE = "login_cookie"
        private const val CLOUDFLARE_COOKIE = "cloudflare_cookie"
        private const val UPDATE_NODE_ID = "update_node_id"
        private const val EMPTY_STRING = ""
        private const val IS_TRANSLATION_ENABLED = "is_translation_enabled"
        private const val TRANSLATION_API_KEYS = "translation_api_keys"
        private const val TRANSLATION_MONTHLY_LIMIT = "translation_monthly_limit"
        private const val TRANSLATION_TARGET_LANG = "translation_target_lang"
        private const val TRANSLATION_BATCH_SIZE = "translation_batch_size"
        private const val TRANSLATE_TITLES = "translate_titles"
        private const val TRANSLATE_DESCRIPTIONS = "translate_descriptions"
        private const val TRANSLATE_COMMENTS = "translate_comments"
        private const val TRANSLATE_TAGS = "translate_tags"
        private const val USE_MLKIT_TRANSLATION = "use_mlkit_translation"
        private const val MLKIT_AUTO_DOWNLOAD = "mlkit_auto_download"
        private const val SHOW_TRANSLATED_TAGS = "show_translated_tags"
        private const val SHOW_TRANSLATED_TITLES = "show_translated_titles"
        private const val MLKIT_LAST_DOWNLOAD_TIME = "mlkit_last_download_time"
    }
}