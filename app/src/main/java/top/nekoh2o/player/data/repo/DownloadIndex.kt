package top.nekoh2o.player.data.repo

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import top.nekoh2o.player.data.model.DownloadedSong
import top.nekoh2o.player.data.model.Song

/**
 * 已下载歌曲的持久化索引（songId → DownloadedSong）。
 * 在 PlayerApp.onCreate() 中调用 [init]，之后可全局使用。
 */
object DownloadIndex {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private lateinit var prefs: SharedPreferences
    private val songs = mutableMapOf<Long, DownloadedSong>()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("download_index", Context.MODE_PRIVATE)
        load()
    }

    fun isDownloaded(songId: Long): Boolean = songs.containsKey(songId)

    fun get(songId: Long): DownloadedSong? = songs[songId]

    fun add(entry: DownloadedSong) {
        songs[entry.songId] = entry
        persist()
    }

    fun remove(songId: Long) {
        songs.remove(songId)
        persist()
    }

    fun all(): List<DownloadedSong> = songs.values.sortedByDescending { it.downloadedAt }

    /** 快速查歌曲（供缓存管理等界面使用）。 */
    fun findSong(songId: Long): Song? = songs[songId]?.song

    private fun load() {
        val raw = prefs.getString("entries", null) ?: return
        runCatching {
            json.decodeFromString<List<DownloadedSongSurrogate>>(raw).forEach { s ->
                val entry = DownloadedSong(
                    songId = s.songId,
                    song = Song(s.songId, s.nm, s.ar, s.pc),
                    audioUri = s.audioUri,
                    lrcPath = s.lrcPath,
                    quality = s.quality,
                    downloadedAt = s.downloadedAt
                )
                songs[entry.songId] = entry
            }
        }
    }

    private fun persist() {
        val list = songs.values.map { e ->
            DownloadedSongSurrogate(
                songId = e.songId,
                nm = e.song.nm,
                ar = e.song.ar,
                pc = e.song.pc,
                audioUri = e.audioUri,
                lrcPath = e.lrcPath,
                quality = e.quality,
                downloadedAt = e.downloadedAt
            )
        }
        prefs.edit().putString("entries", json.encodeToString(list)).apply()
    }

    /** 避免 Song 的嵌套序列化依赖，用扁平化的替代类型。 */
    @kotlinx.serialization.Serializable
    private data class DownloadedSongSurrogate(
        val songId: Long,
        val nm: String,
        val ar: String,
        val pc: String? = null,
        val audioUri: String,
        val lrcPath: String? = null,
        val quality: String = "exhigh",
        val downloadedAt: Long = 0L
    )
}
