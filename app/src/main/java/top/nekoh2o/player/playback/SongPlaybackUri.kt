package top.nekoh2o.player.playback

import android.net.Uri
import top.nekoh2o.player.data.model.Song

/** Stable local media addresses retain provider identifiers until the stream is resolved. */
object SongPlaybackUri {
    fun encode(song: Song): Uri = if (song.source == "kugou") {
        Uri.Builder().scheme("neko").authority("kugou").appendPath(song.id.toString())
            .appendQueryParameter("hash", song.hash).appendQueryParameter("album_id", song.albumId)
            .appendQueryParameter("album_audio_id", song.albumAudioId.toString()).build()
    } else Uri.parse("neko:" + song.id)

    fun decode(uri: Uri): Song? {
        if (uri.scheme != "neko") return null
        if (uri.isOpaque) return uri.schemeSpecificPart.toLongOrNull()?.let { Song(it, "", "") }
        if (uri.host != "kugou") return null
        val id = uri.lastPathSegment?.toLongOrNull() ?: return null
        return Song(id, "", "", source = "kugou", hash = uri.getQueryParameter("hash").orEmpty(),
            albumId = uri.getQueryParameter("album_id").orEmpty(),
            albumAudioId = uri.getQueryParameter("album_audio_id")?.toLongOrNull() ?: 0)
    }
}
