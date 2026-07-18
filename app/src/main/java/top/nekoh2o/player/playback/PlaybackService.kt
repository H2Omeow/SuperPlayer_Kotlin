package top.nekoh2o.player.playback

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.runBlocking
import top.nekoh2o.player.data.repo.MusicRepository

class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val repo = MusicRepository()

    override fun onCreate() {
        super.onCreate()

        // 上游 HTTP 数据源：带 UA，避免部分 CDN 拒绝
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("NekoPlayer/1.0")
            .setAllowCrossProtocolRedirects(true)

        // 解析型数据源：MediaItem 的 uri 是 "neko:歌曲id"，播放到时才现拿真实 url。
        // resolver 运行在播放加载线程，允许阻塞，用 runBlocking 调 suspend 取址。
        val resolvingFactory = ResolvingDataSource.Factory(httpFactory) { dataSpec ->
            val raw = dataSpec.uri.toString()
            if (raw.startsWith("neko:")) {
                val id = raw.removePrefix("neko:").toLongOrNull()
                val real = if (id != null) runBlocking { repo.resolvePlayUrl(id) } else null
                if (real != null) dataSpec.withUri(android.net.Uri.parse(real))
                else dataSpec
            } else dataSpec
        }

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
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
