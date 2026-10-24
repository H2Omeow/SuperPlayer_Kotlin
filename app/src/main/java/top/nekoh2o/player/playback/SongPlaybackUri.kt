package top.nekoh2o.player.playback

import android.net.Uri
import top.nekoh2o.player.data.model.Song

/** Stable local media addresses retain provider identifiers until the stream is resolved. */
object SongPlaybackUri {
    fun encode(song: Song) = if (song.source != "netease") {
        Uri.Builder().scheme("neko").authority("kugou").appendPath(song.id.toString())
            .appendQueryParameter("hash", song.hash).appendQueryParameter("album_id", song.albumId)
            .appendQueryParameter("album_audio_id", song.albumAudioId.toString())
            .appendQueryParameter("source", song.source)
            .appendQueryParameter("provider_source", song.providerSource)
            .appendQueryParameter("provider_media_id", song.providerMediaId)
            .appendQueryParameter("plugin_metadata", song.pluginMetadata)
            .appendQueryParameter("title", song.nm)
            .appendQueryParameter("artist", song.ar)
            .build()
    } else Uri.parse("neko:" + song.id)

    fun decode(uri: Uri): Song? {
        if (uri.scheme != "neko") return null
        if (uri.isOpaque) return uri.schemeSpecificPart.toLongOrNull()?.let { Song(it, "", "") }
        if (uri.host != "kugou") return null
        val id = uri.lastPathSegment?.toLongOrNull() ?: return null
        val source = uri.getQueryParameter("source") ?: "kugou"
        return Song(id, uri.getQueryParameter("title").orEmpty(), uri.getQueryParameter("artist").orEmpty(), source = source, hash = uri.getQueryParameter("hash").orEmpty(),
            albumId = uri.getQueryParameter("album_id").orEmpty(),
            albumAudioId = uri.getQueryParameter("album_audio_id")?.toLongOrNull() ?: 0,
            providerSource = uri.getQueryParameter("provider_source").orEmpty(),
            providerMediaId = uri.getQueryParameter("provider_media_id").orEmpty(),
            pluginMetadata = uri.getQueryParameter("plugin_metadata").orEmpty())
    }
}
