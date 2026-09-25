package top.nekoh2o.player.data.repo

import top.nekoh2o.player.data.model.Song
import top.nekoh2o.player.data.net.AnimemusicApi
import top.nekoh2o.player.data.net.ApiFactory
import top.nekoh2o.player.lyric.LyricParser

data class AnimemusicSearchPage(val songs: List<Song>, val hasMore: Boolean)

/** Adapter for the public animemusic v1 protocol. */
class AnimemusicRepository(private val api: AnimemusicApi = ApiFactory.animemusic) {
    suspend fun search(platform: String, keyword: String, page: Int = 1, limit: Int = 30): AnimemusicSearchPage {
        val response = api.search(platform, keyword, page, limit)
        if (response.code != 200) return AnimemusicSearchPage(emptyList(), false)
        val songs = response.data.mapNotNull { item ->
            val id = item.id.ifBlank { item.songId }
            if (id.isBlank() || item.title.isBlank()) return@mapNotNull null
            Song(
                id = stableId(platform, id),
                nm = item.title,
                ar = item.artist.ifBlank { "未知艺术家" },
                pc = item.artwork?.toAbsoluteArtworkUrl(),
                source = "animemusic-$platform",
                hash = id,
                albumId = item.album,
                albumAudioId = item.album_audio_id.toLongOrNull() ?: 0,
                providerSource = platform,
                providerMediaId = id
            )
        }
        return AnimemusicSearchPage(songs, !response.isEnd && songs.isNotEmpty())
    }

    suspend fun resolveUrl(song: Song, quality: String): String? {
        val platform = song.providerSource.takeIf { it.isNotBlank() } ?: platformFrom(song.source) ?: return null
        val id = song.providerMediaId.ifBlank { song.hash.ifBlank { song.id.toString() } }
        return api.url(platform, id, quality).takeIf { it.code == 200 }?.url
    }

    suspend fun lyric(song: Song): List<top.nekoh2o.player.data.model.LyricLine> {
        val platform = song.providerSource.takeIf { it.isNotBlank() } ?: platformFrom(song.source) ?: return emptyList()
        val id = song.providerMediaId.ifBlank { song.hash.ifBlank { song.id.toString() } }
        val response = runCatching { api.lyric(platform, id) }.getOrNull() ?: return emptyList()
        val main = response.lyric?.takeIf { it.isNotBlank() } ?: return emptyList()
        return LyricParser.parse(main, response.tlyric)
    }

    companion object {
        val platforms = listOf(
            "kg" to "酷狗", "kw" to "酷我", "wy" to "网易", "tx" to "QQ", "mg" to "咪咕", "bilibili" to "B站"
        )
        fun label(platform: String): String = platforms.firstOrNull { it.first == platform }?.second ?: platform
        fun platformFrom(source: String): String? = source.removePrefix("animemusic-").takeIf { it != source }
        private fun stableId(platform: String, id: String): Long {
            val value = (platform + ":" + id).hashCode().toLong()
            return if (value == 0L) 1L else value
        }
        private fun String.toAbsoluteArtworkUrl(): String = if (startsWith("//")) "https:$this" else if (startsWith("/")) "https://animemusic.bzxhkj.com$this" else this
    }
}
