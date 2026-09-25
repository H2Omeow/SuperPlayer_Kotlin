package top.nekoh2o.player.desktop

import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import top.nekoh2o.player.data.model.*

@Serializable data class LibraryData(val favorites: List<Song> = emptyList(), val history: List<Song> = emptyList(), val playlists: List<Playlist> = emptyList())
class Library(private val preferences: FilePreferences = DesktopPaths.preferences) {
    private val json = Json { ignoreUnknownKeys = true }
    var data = json.decodeFromString<LibraryData>(preferences.getString("library", "{}")!!); private set
    fun reload() { data = json.decodeFromString(preferences.getString("library", "{}")!!) }
    fun favorite(song: Song) { data = data.copy(favorites = if (data.favorites.any { key(it) == key(song) }) data.favorites.filter { key(it) != key(song) } else data.favorites + song); save() }
    fun played(song: Song) { data = data.copy(history = (listOf(song) + data.history.filter { key(it) != key(song) }).take(200)); save() }
    fun merge(remote: UserData) { data = data.copy(favorites = (data.favorites + remote.favorites).distinctBy(::key), history = (data.history + remote.history).distinctBy(::key).take(200), playlists = (data.playlists + remote.playlists).distinctBy { it.id }); save() }
    fun addPlaylist(name: String) { require(name.isNotBlank()); data = data.copy(playlists = data.playlists + Playlist(java.util.UUID.randomUUID().toString(), name.trim())); save() }
    fun addToPlaylist(id: String, song: Song) { data = data.copy(playlists = data.playlists.map { if (it.id == id) it.copy(songs = (it.songs + song).distinctBy(::key).toMutableList()) else it }); save() }
    private fun save() = preferences.edit().putString("library", json.encodeToString(data)).apply()
    companion object { fun key(song: Song) = if (song.source == "kugou") "kg:" + song.hash else if (song.source == "local") "file:" + song.hash else "nc:" + song.id }
}
