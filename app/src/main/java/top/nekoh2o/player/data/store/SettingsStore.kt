package top.nekoh2o.player.data.store

import android.content.Context
import top.nekoh2o.player.data.model.AppSettings
import top.nekoh2o.player.data.model.BgSource

class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        globalBgEnabled = prefs.getBoolean(KEY_G_ENABLED, true),
        globalMaskAlpha = prefs.getFloat(KEY_G_MASK, 0.6f),
        globalBlurRadius = prefs.getInt(KEY_G_BLUR, 15),
        fpBgSource = runCatching {
            BgSource.valueOf(prefs.getString(KEY_FP_BG, BgSource.COVER.name)!!)
        }.getOrDefault(BgSource.COVER),
        fpMaskAlpha = prefs.getFloat(KEY_FP_MASK, 0.4f),
        fpBlurRadius = prefs.getInt(KEY_FP_BLUR, 20),
        cacheEnabled = prefs.getBoolean(KEY_CACHE_ENABLED, true),
        controlAlpha = prefs.getFloat(KEY_CTRL_ALPHA, 0.3f),
        playbackSpeed = prefs.getFloat(KEY_SPEED, 1.0f),
        floatingLyricEnabled = prefs.getBoolean(KEY_FLOAT_LYRIC, false),
        floatingLyricDoubleRow = prefs.getBoolean(KEY_FLOAT_DOUBLE_ROW, false),
        floatingLyricShowTranslation = prefs.getBoolean(KEY_FLOAT_TRANSLATION, true),
        downloadDirUri = prefs.getString(KEY_DOWNLOAD_DIR, "") ?: ""
    )

    fun save(s: AppSettings) {
        prefs.edit()
            .putBoolean(KEY_G_ENABLED, s.globalBgEnabled)
            .putFloat(KEY_G_MASK, s.globalMaskAlpha)
            .putInt(KEY_G_BLUR, s.globalBlurRadius)
            .putString(KEY_FP_BG, s.fpBgSource.name)
            .putFloat(KEY_FP_MASK, s.fpMaskAlpha)
            .putInt(KEY_FP_BLUR, s.fpBlurRadius)
            .putBoolean(KEY_CACHE_ENABLED, s.cacheEnabled)
            .putFloat(KEY_CTRL_ALPHA, s.controlAlpha)
            .putFloat(KEY_SPEED, s.playbackSpeed)
            .putBoolean(KEY_FLOAT_LYRIC, s.floatingLyricEnabled)
            .putBoolean(KEY_FLOAT_DOUBLE_ROW, s.floatingLyricDoubleRow)
            .putBoolean(KEY_FLOAT_TRANSLATION, s.floatingLyricShowTranslation)
            .putString(KEY_DOWNLOAD_DIR, s.downloadDirUri)
            .apply()
    }

    companion object {
        private const val KEY_G_ENABLED = "g_bg_enabled"
        private const val KEY_G_MASK = "g_mask"
        private const val KEY_G_BLUR = "g_blur"
        private const val KEY_FP_BG = "fp_bg"
        private const val KEY_FP_MASK = "fp_mask"
        private const val KEY_FP_BLUR = "fp_blur"
        private const val KEY_CACHE_ENABLED = "cache_enabled"
        private const val KEY_CTRL_ALPHA = "ctrl_alpha"
        private const val KEY_SPEED = "playback_speed"
        private const val KEY_FLOAT_LYRIC = "floating_lyric"
        private const val KEY_FLOAT_DOUBLE_ROW = "floating_double_row"
        private const val KEY_FLOAT_TRANSLATION = "floating_translation"
        private const val KEY_DOWNLOAD_DIR = "download_dir_uri"
    }
}
