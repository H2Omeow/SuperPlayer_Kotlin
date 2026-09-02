package top.nekoh2o.player.data.store

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import top.nekoh2o.player.data.model.AppSettings
import top.nekoh2o.player.data.model.BgSource
import top.nekoh2o.player.data.model.Song

class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val gson = Gson()

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
        downloadDirUri = prefs.getString(KEY_DOWNLOAD_DIR, "") ?: "",
        audioQuality = prefs.getString(KEY_AUDIO_QUALITY, "exhigh") ?: "exhigh",
        landscapeMode = prefs.getBoolean(KEY_LANDSCAPE_MODE, false)
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
            .putString(KEY_AUDIO_QUALITY, s.audioQuality)
            .putBoolean(KEY_LANDSCAPE_MODE, s.landscapeMode)
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
        private const val KEY_AUDIO_QUALITY = "audio_quality"
        private const val KEY_LANDSCAPE_MODE = "landscape_mode"

        // 播放状态保存
        private const val KEY_PLAY_QUEUE = "play_queue"
        private const val KEY_PLAY_INDEX = "play_index"
        private const val KEY_PLAY_POSITION = "play_position"
    }

    // ==================== 播放状态保存/恢复 ====================

    data class PlaybackState(
        val queue: List<Song>,
        val currentIndex: Int,
        val position: Long
    )

    fun savePlaybackState(queue: List<Song>, currentIndex: Int, position: Long) {
        val json = gson.toJson(queue)
        prefs.edit()
            .putString(KEY_PLAY_QUEUE, json)
            .putInt(KEY_PLAY_INDEX, currentIndex)
            .putLong(KEY_PLAY_POSITION, position)
            .apply()
    }

    fun loadPlaybackState(): PlaybackState? {
        val json = prefs.getString(KEY_PLAY_QUEUE, null) ?: return null
        val index = prefs.getInt(KEY_PLAY_INDEX, 0)
        val position = prefs.getLong(KEY_PLAY_POSITION, 0)

        return try {
            val type = object : TypeToken<List<Song>>() {}.type
            val queue: List<Song> = gson.fromJson(json, type)
            if (queue.isEmpty()) null else PlaybackState(queue, index, position)
        } catch (e: Exception) {
            null
        }
    }

    fun clearPlaybackState() {
        prefs.edit()
            .remove(KEY_PLAY_QUEUE)
            .remove(KEY_PLAY_INDEX)
            .remove(KEY_PLAY_POSITION)
            .apply()
    }
}
