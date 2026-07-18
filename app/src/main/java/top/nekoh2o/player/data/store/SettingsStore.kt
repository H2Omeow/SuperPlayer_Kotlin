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
        fpBlurRadius = prefs.getInt(KEY_FP_BLUR, 20)
    )

    fun save(s: AppSettings) {
        prefs.edit()
            .putBoolean(KEY_G_ENABLED, s.globalBgEnabled)
            .putFloat(KEY_G_MASK, s.globalMaskAlpha)
            .putInt(KEY_G_BLUR, s.globalBlurRadius)
            .putString(KEY_FP_BG, s.fpBgSource.name)
            .putFloat(KEY_FP_MASK, s.fpMaskAlpha)
            .putInt(KEY_FP_BLUR, s.fpBlurRadius)
            .apply()
    }

    companion object {
        private const val KEY_G_ENABLED = "g_bg_enabled"
        private const val KEY_G_MASK = "g_mask"
        private const val KEY_G_BLUR = "g_blur"
        private const val KEY_FP_BG = "fp_bg"
        private const val KEY_FP_MASK = "fp_mask"
        private const val KEY_FP_BLUR = "fp_blur"
    }
}
