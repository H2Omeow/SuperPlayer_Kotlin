package top.nekoh2o.player.playback

import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.runBlocking
import top.nekoh2o.player.data.cache.MusicCache
import top.nekoh2o.player.data.repo.MusicRepository

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val repo = MusicRepository()

    override fun onCreate() {
        super.onCreate()

        MusicCache.init(this)

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("NekoPlayer/1.0")
            .setAllowCrossProtocolRedirects(true)

        val cacheFactory = MusicCache.dataSourceFactory(httpFactory)

        val resolvingFactory = ResolvingDataSource.Factory(cacheFactory) { dataSpec ->
            val raw = dataSpec.uri.toString()

            if (raw.startsWith("neko:")) {
                val id = raw.removePrefix("neko:").toLongOrNull()
                val realUrl = if (id != null) {
                    runBlocking {
                        repo.resolvePlayUrl(id)
                    }
                } else {
                    null
                }

                if (realUrl != null) {
                    dataSpec.withUri(Uri.parse(realUrl))
                } else {
                    dataSpec
                }
            } else {
                dataSpec
            }
        }

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(resolvingFactory)
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(
        controllerInfo: MediaSession.ControllerInfo
    ): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (
            player == null ||
            !player.playWhenReady ||
            player.mediaItemCount == 0
        ) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
