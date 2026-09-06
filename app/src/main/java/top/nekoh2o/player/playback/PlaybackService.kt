package top.nekoh2o.player.playback

import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.runBlocking
import top.nekoh2o.player.audio.NativeAudioProcessor
import top.nekoh2o.player.audio.SystemAudioEffectsManager
import top.nekoh2o.player.data.cache.MusicCache
import top.nekoh2o.player.data.model.AudioEffectEngine
import top.nekoh2o.player.data.model.AudioEffectSettings
import top.nekoh2o.player.data.repo.DownloadIndex
import top.nekoh2o.player.data.repo.MusicRepository
import top.nekoh2o.player.data.store.SettingsStore
import android.os.Handler
import android.os.Looper
import java.io.File

@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private val repo = MusicRepository()
    private var audioEffectsManager: SystemAudioEffectsManager? = null
    private var nativeAudioProcessor: NativeAudioProcessor? = null

    override fun onCreate() {
        super.onCreate()

        MusicCache.init(this)

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("NekoPlayer/1.0")
            .setAllowCrossProtocolRedirects(true)

        // 上游使用 DefaultDataSource，同时支持网络、file:// 与 content:// URI。
        val localAndHttpFactory = DefaultDataSource.Factory(this, httpFactory)
        val cacheFactory = MusicCache.dataSourceFactory(localAndHttpFactory)
        val settingsStore = SettingsStore(this)

        val resolvingFactory = ResolvingDataSource.Factory(cacheFactory) { dataSpec ->
            val raw = dataSpec.uri.toString()

            if (!raw.startsWith("neko:")) return@Factory dataSpec

            val id = raw.removePrefix("neko:").toLongOrNull()
                ?: return@Factory dataSpec
            val key = MusicCache.cacheKeyForSong(id)

            // 已下载文件优先直读。兼容旧版本保存的裸绝对路径。
            DownloadIndex.get(id)?.let { downloaded ->
                normalizeReadableUri(downloaded.audioUri)?.let { localUri ->
                    return@Factory dataSpec.buildUpon()
                        .setUri(localUri)
                        .setKey("download:$id")
                        .build()
                }
            }

            // 完整缓存无需联网取址；保留任意可解析 URI，只让 CacheDataSource 按 key 命中。
            if (settingsStore.load().cacheEnabled && MusicCache.isFullyCached(key)) {
                return@Factory dataSpec.buildUpon().setKey(key).build()
            }

            val realUrl = runCatching { runBlocking { repo.resolvePlayUrl(id) } }.getOrNull()
            if (realUrl != null) {
                val builder = dataSpec.buildUpon()
                    .setUri(Uri.parse(realUrl))
                    .setKey(key)
                if (!settingsStore.load().cacheEnabled) {
                    builder.setFlags(dataSpec.flags or androidx.media3.datasource.DataSpec.FLAG_DONT_CACHE_IF_LENGTH_UNKNOWN)
                }
                builder.build()
            } else {
                // 取址失败时仍保留稳定 key，允许已有缓存尝试读取。
                dataSpec.buildUpon().setKey(key).build()
            }
        }

        val player = ExoPlayer.Builder(this)
            .setRenderersFactory(createRenderersFactory())
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

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && audioEffectsManager == null && nativeAudioProcessor == null) {
                    initAudioEffects(player.audioSessionId)
                }
            }
        })

        mediaSession = MediaSession.Builder(this, player).build()
    }

    private fun createRenderersFactory(): RenderersFactory {
        return RenderersFactory { eventHandler, videoRendererEventListener, audioRendererEventListener, textRendererOutput, metadataRendererOutput ->
            val settings = SettingsStore(this).load()

            // 根据音效引擎配置创建 AudioProcessor
            val audioProcessors = if (settings.audioEffects.engine == AudioEffectEngine.NATIVE_CPP) {
                val processor = NativeAudioProcessor()
                nativeAudioProcessor = processor
                arrayOf<AudioProcessor>(processor)
            } else {
                emptyArray()
            }

            // 创建带自定义 AudioProcessor 的 AudioSink
            val audioSink = DefaultAudioSink.Builder(this)
                .setAudioProcessors(audioProcessors)
                .build()

            arrayOf(
                MediaCodecVideoRenderer(
                    this,
                    MediaCodecSelector.DEFAULT,
                    50000L,
                    eventHandler,
                    videoRendererEventListener,
                    50
                ),
                MediaCodecAudioRenderer(
                    this,
                    MediaCodecSelector.DEFAULT,
                    eventHandler,
                    audioRendererEventListener,
                    audioSink
                )
            )
        }
    }

    private fun normalizeReadableUri(raw: String): Uri? {
        val uri = runCatching {
            val parsed = Uri.parse(raw)
            if (parsed.scheme.isNullOrBlank()) Uri.fromFile(File(raw)) else parsed
        }.getOrNull() ?: return null

        return runCatching {
            when (uri.scheme) {
                "content" -> {
                    contentResolver.openFileDescriptor(uri, "r")?.use { } ?: return null
                    uri
                }
                "file" -> {
                    val file = File(uri.path ?: return null)
                    uri.takeIf { file.isFile && file.canRead() }
                }
                else -> null
            }
        }.getOrNull()
    }

    private fun initAudioEffects(audioSessionId: Int) {
        val settings = SettingsStore(this).load()
        when (settings.audioEffects.engine) {
            AudioEffectEngine.SYSTEM -> {
                audioEffectsManager = SystemAudioEffectsManager(audioSessionId).apply {
                    if (initialize()) {
                        applySettings(settings.audioEffects)
                    }
                }
            }
            AudioEffectEngine.NATIVE_CPP -> {
                // Native 音效在 createRenderersFactory 中已初始化
                nativeAudioProcessor?.applySettings(settings.audioEffects)
            }
            AudioEffectEngine.NONE -> {
                // 不启用任何音效
            }
        }
    }

    private fun updateAudioEffects(settings: AudioEffectSettings) {
        when (settings.engine) {
            AudioEffectEngine.NONE -> {
                audioEffectsManager?.release()
                audioEffectsManager = null
                nativeAudioProcessor = null
                // 需要重新创建 player 以移除 AudioProcessor
                recreatePlayerWithNewSettings(settings)
            }
            AudioEffectEngine.SYSTEM -> {
                nativeAudioProcessor = null
                val sessionId = (mediaSession?.player as? ExoPlayer)?.audioSessionId ?: return
                if (audioEffectsManager == null) {
                    audioEffectsManager = SystemAudioEffectsManager(sessionId).apply {
                        initialize()
                    }
                }
                audioEffectsManager?.applySettings(settings)
            }
            AudioEffectEngine.NATIVE_CPP -> {
                audioEffectsManager?.release()
                audioEffectsManager = null

                // 如果已有 Native 处理器，直接应用设置
                if (nativeAudioProcessor != null) {
                    nativeAudioProcessor?.applySettings(settings)
                } else {
                    // 否则需要重新创建 player
                    recreatePlayerWithNewSettings(settings)
                }
            }
        }
    }

    private fun recreatePlayerWithNewSettings(settings: AudioEffectSettings) {
        val currentPlayer = mediaSession?.player as? ExoPlayer ?: return

        // 保存当前播放状态
        val currentMediaItems = mutableListOf<androidx.media3.common.MediaItem>()
        for (i in 0 until currentPlayer.mediaItemCount) {
            currentMediaItems.add(currentPlayer.getMediaItemAt(i))
        }
        val currentIndex = currentPlayer.currentMediaItemIndex
        val currentPosition = currentPlayer.currentPosition
        val playWhenReady = currentPlayer.playWhenReady

        // 释放旧 player
        currentPlayer.release()

        // 保存引擎设置到 Store（避免 createRenderersFactory 读取旧值）
        SettingsStore(this).save(SettingsStore(this).load().copy(audioEffects = settings))

        // 重新创建 player
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("NekoPlayer/1.0")
            .setAllowCrossProtocolRedirects(true)
        val localAndHttpFactory = DefaultDataSource.Factory(this, httpFactory)
        val cacheFactory = MusicCache.dataSourceFactory(localAndHttpFactory)
        val settingsStore = SettingsStore(this)
        val resolvingFactory = ResolvingDataSource.Factory(cacheFactory) { dataSpec ->
            val raw = dataSpec.uri.toString()
            if (!raw.startsWith("neko:")) return@Factory dataSpec
            val id = raw.removePrefix("neko:").toLongOrNull() ?: return@Factory dataSpec
            val key = MusicCache.cacheKeyForSong(id)
            DownloadIndex.get(id)?.let { downloaded ->
                normalizeReadableUri(downloaded.audioUri)?.let { localUri ->
                    return@Factory dataSpec.buildUpon().setUri(localUri).setKey("download:$id").build()
                }
            }
            if (settingsStore.load().cacheEnabled && MusicCache.isFullyCached(key)) {
                return@Factory dataSpec.buildUpon().setKey(key).build()
            }
            val realUrl = runCatching { runBlocking { repo.resolvePlayUrl(id) } }.getOrNull()
            if (realUrl != null) {
                val builder = dataSpec.buildUpon().setUri(Uri.parse(realUrl)).setKey(key)
                if (!settingsStore.load().cacheEnabled) {
                    builder.setFlags(dataSpec.flags or androidx.media3.datasource.DataSpec.FLAG_DONT_CACHE_IF_LENGTH_UNKNOWN)
                }
                builder.build()
            } else {
                dataSpec.buildUpon().setKey(key).build()
            }
        }

        val newPlayer = ExoPlayer.Builder(this)
            .setRenderersFactory(createRenderersFactory())
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        newPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && audioEffectsManager == null && nativeAudioProcessor == null) {
                    initAudioEffects(newPlayer.audioSessionId)
                }
            }
        })

        // 恢复播放状态
        newPlayer.setMediaItems(currentMediaItems, currentIndex, currentPosition)
        newPlayer.playWhenReady = playWhenReady
        newPlayer.prepare()

        // 更新 MediaSession
        mediaSession?.player = newPlayer
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "top.nekoh2o.player.ACTION_UPDATE_AUDIO_EFFECTS") {
            handleAudioEffectsUpdate(intent)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun handleAudioEffectsUpdate(intent: Intent) {
        val engineValue = intent.getIntExtra("engine", 0)
        val engine = AudioEffectEngine.entries.getOrNull(engineValue) ?: return

        val settings = AudioEffectSettings(
            engine = engine,
            eqBands = intent.getFloatArrayExtra("eq_bands")?.toList() ?: List(10) { 0f },
            bassBoost = intent.getIntExtra("bass_boost", 0),
            virtualizer = intent.getIntExtra("virtualizer", 0),
            reverbWet = intent.getIntExtra("reverb_wet", 0),
            reverbRoomSize = intent.getIntExtra("reverb_room_size", 50),
            reverbDamping = intent.getIntExtra("reverb_damping", 30),
            loudnessGain = intent.getIntExtra("loudness_gain", 0)
        )

        updateAudioEffects(settings)
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
        audioEffectsManager?.release()
        audioEffectsManager = null
        nativeAudioProcessor = null
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
