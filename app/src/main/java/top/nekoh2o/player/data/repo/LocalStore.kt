package top.nekoh2o.player.data.repo

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import top.nekoh2o.player.data.model.Playlist
import top.nekoh2o.player.data.model.Song

/**
 * 对应 web 端 localStorage 的三份数据：播放历史、收藏、自定义歌单。
 * 用 SharedPreferences + JSON 持久化。内存持有可变副本，读写同步。
 */
class LocalStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("user_data", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    val history = mutableListOf<Song>()
    val favorites = mutableListOf<Song>()
    val playlists = mutableListOf<Playlist>()

    init {
        history.addAll(load(KEY_HISTORY))
        favorites.addAll(load(KEY_FAV))
        playlists.addAll(loadPlaylists())
    }

    private fun load(key: String): List<Song> =
        runCatching {
            prefs.getString(key, null)?.let { json.decodeFromString<List<Song>>(it) }
        }.getOrNull() ?: emptyList()

    private fun loadPlaylists(): List<Playlist> =
        runCatching {
            prefs.getString(KEY_PL, null)?.let { json.decodeFromString<List<Playlist>>(it) }
        }.getOrNull() ?: emptyList()

    // ---------- 历史 ----------
    fun addHistory(song: Song) {
        history.removeAll { it.id == song.id }
        history.add(0, song)
        while (history.size > 100) history.removeAt(history.size - 1)
        saveHistory()
    }
    fun saveHistory() = prefs.edit().putString(KEY_HISTORY, json.encodeToString(history.toList())).apply()

    // ---------- 收藏 ----------
    fun isFav(id: Long) = favorites.any { it.id == id }
    fun toggleFav(song: Song): Boolean {
        val idx = favorites.indexOfFirst { it.id == song.id }
        val nowFav: Boolean
        if (idx >= 0) { favorites.removeAt(idx); nowFav = false }
        else { favorites.add(song); nowFav = true }
        saveFav()
        return nowFav
    }
    fun saveFav() = prefs.edit().putString(KEY_FAV, json.encodeToString(favorites.toList())).apply()

    // ---------- 自定义歌单 ----------
    fun createPlaylist(name: String): Playlist {
        val pl = Playlist(id = System.currentTimeMillis().toString(), name = name)
        playlists.add(pl)
        savePlaylists()
        return pl
    }
    fun deletePlaylist(index: Int) {
        if (index in playlists.indices) { playlists.removeAt(index); savePlaylists() }
    }
    fun addToPlaylist(index: Int, song: Song): Boolean {
        val pl = playlists.getOrNull(index) ?: return false
        if (pl.songs.any { it.id == song.id }) return false
        pl.songs.add(song); savePlaylists(); return true
    }
    fun removeFromPlaylist(plIndex: Int, songIndex: Int) {
        playlists.getOrNull(plIndex)?.songs?.let {
            if (songIndex in it.indices) { it.removeAt(songIndex); savePlaylists() }
        }
    }
    fun savePlaylists() = prefs.edit().putString(KEY_PL, json.encodeToString(playlists.toList())).apply()

    // ---------- 云端合并（对应 auth.js mergeById / mergePlaylists）----------
    fun mergeFromRemote(rHistory: List<Song>, rFav: List<Song>, rPls: List<Playlist>) {
        replaceMerged(history, mergeById(history, rHistory).take(100))
        replaceMerged(favorites, mergeById(favorites, rFav))
        val merged = mergePlaylists(playlists, rPls)
        playlists.clear(); playlists.addAll(merged)
        saveHistory(); saveFav(); savePlaylists()
    }
    private fun replaceMerged(target: MutableList<Song>, merged: List<Song>) {
        target.clear(); target.addAll(merged)
    }
    private fun mergeById(local: List<Song>, remote: List<Song>): List<Song> {
        val seen = HashSet<Long>()
        val out = mutableListOf<Song>()
        remote.forEach { if (seen.add(it.id)) out.add(it) }
        local.forEach { if (seen.add(it.id)) out.add(it) }
        return out
    }
    private fun mergePlaylists(local: List<Playlist>, remote: List<Playlist>): List<Playlist> {
        val map = LinkedHashMap<String, Playlist>()
        remote.forEach { map[it.id] = it }
        local.forEach { lp ->
            val ex = map[lp.id]
            if (ex == null) map[lp.id] = lp
            else {
                val merged = mergeById(lp.songs, ex.songs).toMutableList()
                map[lp.id] = ex.copy(songs = merged)
            }
        }
        return map.values.toList()
    }

    companion object {
        private const val KEY_HISTORY = "my_history"
        private const val KEY_FAV = "my_favorites"
        private const val KEY_PL = "my_playlists"
    }
}
