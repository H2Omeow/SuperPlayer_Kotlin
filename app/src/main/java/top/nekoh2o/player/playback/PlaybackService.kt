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
import top.nekoh2o.player.data.repo.DownloadIndex
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

                // 优先使用本地已下载文件，跳过网络与缓存
                val downloaded = if (id != null) DownloadIndex.get(id) else null
                if (downloaded != null) {
                    return@Factory dataSpec.withUri(Uri.parse(downloaded.audioUri))
                }

                val realUrl = if (id != null) {
                    runBlocking { repo.resolvePlayUrl(id) }
                } else null

                // 用歌曲 id 作为缓存 key，避免因每次解析出的直链不同导致
                // 缓存按 URL 存储、缓存管理无法反查歌曲信息（显示"未知歌曲"）
                if (realUrl != null) {
                    dataSpec.withUri(Uri.parse(realUrl))
                        .buildUpon()
                        .setKey(id.toString())
                        .build()
                } else dataSpec
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
            // 网络播放时持有 WifiLock + WakeLock，避免 CPU/WiFi 休眠导致后台断流
            .setWakeMode(C.WAKE_MODE_NETWORK)
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
