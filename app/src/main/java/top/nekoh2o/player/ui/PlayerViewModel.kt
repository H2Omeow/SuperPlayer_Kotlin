package top.nekoh2o.player.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import top.nekoh2o.player.PlayerApp
import top.nekoh2o.player.data.cache.MusicCache
import top.nekoh2o.player.data.model.AlbumItem
import top.nekoh2o.player.data.model.AppSettings
import top.nekoh2o.player.data.model.ArtistItem
import top.nekoh2o.player.data.model.BgSource
import top.nekoh2o.player.data.model.CachedItem
import top.nekoh2o.player.data.model.DownloadTask
import top.nekoh2o.player.data.model.DownloadedSong
import top.nekoh2o.player.data.model.LyricLine
import top.nekoh2o.player.data.model.PersonalizedItem
import top.nekoh2o.player.data.model.Playlist
import top.nekoh2o.player.data.model.SearchType
import top.nekoh2o.player.data.model.Song
import top.nekoh2o.player.data.model.User
import top.nekoh2o.player.data.net.ApiFactory
import top.nekoh2o.player.data.net.CookieStore
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Settings
import top.nekoh2o.player.data.repo.DownloadIndex
import top.nekoh2o.player.data.repo.Downloader
import top.nekoh2o.player.data.repo.MusicRepository
import top.nekoh2o.player.data.repo.UserRepository
import top.nekoh2o.player.data.store.SettingsStore
import top.nekoh2o.player.lyric.LyricParser
import top.nekoh2o.player.playback.FloatingLyricService
import top.nekoh2o.player.playback.FloatingLyricState
import top.nekoh2o.player.playback.PlaybackService
import top.nekoh2o.player.ui.manager.LyricManager
import java.io.File

enum class PlayMode { LOOP, SINGLE, RANDOM }

data class UiState(
    val query: String = "",
    val searchType: SearchType = SearchType.SONG,
    val suggestions: List<String> = emptyList(),
    val results: List<Song> = emptyList(),
    val artistResults: List<ArtistItem> = emptyList(),
    val albumResults: List<AlbumItem> = emptyList(),
    val searching: Boolean = false,
    val hasMore: Boolean = true,
    val recPlaylists: List<PersonalizedItem> = emptyList(),
    val recSongs: List<Song> = emptyList(),
    val recLoading: Boolean = false,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val playMode: PlayMode = PlayMode.LOOP,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val lyrics: List<LyricLine> = emptyList(),
    val lyricIndex: Int = -1,
    val history: List<Song> = emptyList(),
    val favorites: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val user: User? = null,
    val loggedIn: Boolean = false,
    // 个性化
    val settings: AppSettings = AppSettings(),
    val wallpaperUrl: String? = null,
    val quality: String = "exhigh",
    val sleepMinutes: Int = 0,
    val toast: String? = null,
    // 缓存管理
    val cachedItems: List<CachedItem> = emptyList(),
    val selectedCacheKeys: Set<String> = emptySet(),
    // 下载管理
    val downloadTasks: List<DownloadTask> = emptyList(),
    val downloadedSongs: List<DownloadedSong> = emptyList(),
    // 网易云 Cookie（用于设置页展示/编辑）
    val ncCookie: String = "",
    // 网易云账号信息
    val ncAccount: NcAccountState = NcAccountState(),
    // 音乐源切换：netease | kugou
    val musicSource: String = "netease",
    // 酷狗账号信息
    val kgAccount: KgAccountState = KgAccountState()
) {
    val current: Song? get() = queue.getOrNull(currentIndex)
}

// 网易云账号信息 UI 状态
data class NcAccountState(
    val userId: Long = 0,
    val nickname: String = "",
    val avatarUrl: String? = null,
    val vipType: Int = 0,  // 0=非会员 11=VIP
    val vipExpireTime: Long = 0,  // 会员到期时间戳（毫秒）
    val isValid: Boolean = false  // Cookie 是否有效
)

// 酷狗账号信息 UI 状态
data class KgAccountState(
    val userId: Long = 0,
    val nickname: String = "",
    val avatar: String? = null,
    val vipType: Int = 0,  // 0=普通 1=VIP 2=豪华VIP
    val vipEndTime: Long = 0,
    val isValid: Boolean = false,  // Token 是否有效
    val platform: Int = 0  // 0=原版 1=概念版
)

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MusicRepository()
    private val userRepo = UserRepository()
    private val ncRepo = top.nekoh2o.player.data.repo.NeteaseRepository()
    private val kgRepo = top.nekoh2o.player.data.repo.KugouRepository()
    private val local = (app as PlayerApp).localStore
    private val settingsStore = SettingsStore(app)

    // Delegated ViewModels
    val cacheVm = top.nekoh2o.player.ui.viewmodel.CacheViewModel(app)
    val downloadVm = top.nekoh2o.player.ui.viewmodel.DownloadViewModel(app)
    val settingsVm = top.nekoh2o.player.ui.viewmodel.SettingsViewModel(app)
    val searchVm = top.nekoh2o.player.ui.viewmodel.SearchViewModel(app)
    val libraryVm = top.nekoh2o.player.ui.viewmodel.LibraryViewModel(app)
    val neteaseVm = top.nekoh2o.player.ui.viewmodel.NeteaseAccountViewModel(app)
    val kugouVm = top.nekoh2o.player.ui.viewmodel.KugouAccountViewModel(app)

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    private var controller: MediaController? = null
    private val queue = mutableListOf<Song>()

    private var searchOffset = 0
    private var searchKeyword = ""
    private var searchLoading = false

    private var progressJob: Job? = null
    private var suggestJob: Job? = null
    private var pushJob: Job? = null
    private var sleepJob: Job? = null

    init {
        pushMineToState()
        // 读取个性化设置与音质
        _ui.value = _ui.value.copy(
            settings = settingsStore.load(),
            quality = CookieStore.level
        )

        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            controller = future.get()
            controller?.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _ui.value = _ui.value.copy(isPlaying = isPlaying)
                    if (isPlaying) startProgressLoop() else stopProgressLoop()
                }
                override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                    onSongChanged()
                }
                override fun onPlaybackStateChanged(state: Int) {
                    syncFromController()
                }
            })
            syncFromController()
            // 恢复已保存的播放倍速
            val savedSpeed = _ui.value.settings.playbackSpeed
            if (savedSpeed != 1.0f) controller?.setPlaybackSpeed(savedSpeed)
            // 恢复上次播放状态
            restorePlaybackState()
        }, MoreExecutors.directExecutor())

        viewModelScope.launch {
            // 等待 CookieStore 和 ApiFactory 初始化完成
            CookieStore.awaitReady()
            ApiFactory.awaitReady()
            _ui.value = _ui.value.copy(quality = CookieStore.level)
            // 初始化酷狗（确保 dfid 存在）
            kgRepo.ensureInitialized()
            refreshLoginInternal()
            // 检测网易云 Cookie 有效性
            checkNcCookieValidity()
        }
        refreshWallpaper(_ui.value.settings.landscapeMode)

        // 订阅下载任务实时进度 + 载入已下载列表
        viewModelScope.launch {
            Downloader.tasks.collect { tasks ->
                _ui.value = _ui.value.copy(downloadTasks = tasks)
            }
        }
        _ui.value = _ui.value.copy(downloadedSongs = DownloadIndex.all())
    }

    // ==================== 状态同步 ====================
    private fun syncFromController() {
        val c = controller ?: return
        _ui.value = _ui.value.copy(
            queue = queue.toList(),
            currentIndex = if (queue.isNotEmpty()) c.currentMediaItemIndex else -1,
            isPlaying = c.isPlaying,
            durationMs = c.duration.coerceAtLeast(0)
        )
    }

    private fun onSongChanged() {
        val c = controller ?: return
        val idx = c.currentMediaItemIndex
        val song = queue.getOrNull(idx)
        _ui.value = _ui.value.copy(
            currentIndex = if (queue.isNotEmpty()) idx else -1,
            lyrics = emptyList(),
            lyricIndex = -1
        )
        if (song != null) {
            local.addHistory(song)
            pushMineToState()
            loadLyric(song.id)
        }
        // 自动保存播放状态
        saveCurrentPlaybackState()
    }

    private fun startProgressLoop() {
        stopProgressLoop()
        progressJob = viewModelScope.launch {
            while (true) {
                val c = controller ?: break
                val pos = c.currentPosition.coerceAtLeast(0)
                val dur = c.duration.coerceAtLeast(0)
                val li = currentLyricIndex(pos)
                _ui.value = _ui.value.copy(positionMs = pos, durationMs = dur, lyricIndex = li)
                // 同步悬浮窗歌词（主行 + 翻译行 + 逐字进度）
                if (_ui.value.settings.floatingLyricEnabled && FloatingLyricState.enabled.value) {
                    val cur = if (li >= 0) _ui.value.lyrics.getOrNull(li) else null
                    FloatingLyricState.publish(cur?.text ?: "")
                    val trans = if (_ui.value.settings.floatingLyricShowTranslation) cur?.translation ?: "" else ""
                    FloatingLyricState.publishTranslation(trans)
                    // 双行模式无翻译时用下一句作兜底
                    val nextText = if (li >= 0) _ui.value.lyrics.getOrNull(li + 1)?.text ?: "" else ""
                    FloatingLyricState.publishNextLine(nextText)
                    FloatingLyricState.publishProgress(lineFillFraction(li, pos))
                }
                delay(100)
            }
        }
    }
    private fun stopProgressLoop() { progressJob?.cancel(); progressJob = null }

    private fun currentLyricIndex(posMs: Long): Int {
        val lys = _ui.value.lyrics
        if (lys.isEmpty()) return -1
        val sec = posMs / 1000.0
        var idx = -1
        for (i in lys.indices.reversed()) {
            if (lys[i].time <= sec) { idx = i; break }
        }
        return idx
    }

    /**
     * 当前行的填充比例 0f~1f，供悬浮窗卡拉OK渐变使用。
     * 有逐字时间时按逐字进度累加字符占比；无逐字时整行直接高亮（返回 1f），
     * 不做逐字扫光，避免没有逐字歌词的歌曲被误加渐变。
     */
    private fun lineFillFraction(lineIndex: Int, posMs: Long): Float {
        val lys = _ui.value.lyrics
        val line = lys.getOrNull(lineIndex) ?: return 0f
        val sec = posMs / 1000.0
        val words = line.words
        if (words.isNullOrEmpty()) return 1f // 无逐字时间：整行高亮，不逐字渐变
        val totalChars = words.sumOf { it.text.length }.coerceAtLeast(1)
        var filled = 0.0
        for (w in words) {
            val p = ((sec - w.start) / w.dur).coerceIn(0.0, 1.0)
            filled += w.text.length * p
            if (p < 1.0) break
        }
        return (filled / totalChars).toFloat().coerceIn(0f, 1f)
    }

    private fun Song.toMediaItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri("neko:$id")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(nm)
                    .setArtist(ar)
                    .apply { pc?.let { setArtworkUri(Uri.parse("$it?param=500y500")) } }
                    .build()
            )
            .build()

    // ==================== 音乐源切换 ====================

    /**
     * 切换音乐源（网易云/酷狗）
     */
    fun switchMusicSource(source: String) {
        _ui.value = _ui.value.copy(
            musicSource = source,
            results = emptyList(),
            suggestions = emptyList(),
            query = "",
            recSongs = emptyList(),
            recPlaylists = emptyList()
        )
        toast("已切换到${if (source == "netease") "网易云音乐" else "酷狗音乐"}")
        loadRecommend()
    }

    // ==================== 酷狗音乐相关 ====================

    /**
     * 酷狗发送验证码
     */
    fun kgSendCode(phone: String) {
        viewModelScope.launch {
            val success = kgRepo.sendCode(phone)
            if (success) {
                toast("验证码已发送")
            } else {
                toast("发送验证码失败")
            }
        }
    }

    /**
     * 酷狗登录
     */
    fun kgLogin(phone: String, code: String, platform: Int) {
        viewModelScope.launch {
            CookieStore.setKgPlatform(platform)
            val data = kgRepo.login(phone, code)
            if (data != null) {
                // 保存登录返回的 userid 和 dfid
                CookieStore.setKgUserid(data.userid.toString())
                CookieStore.setKgDfid(data.dfid)
                toast("登录成功")
                refreshKgAccount()
            } else {
                toast("登录失败")
            }
        }
    }

    /**
     * 酷狗MID+Token登录
     */
    fun kgLoginWithToken(mid: String, token: String, platform: Int) {
        viewModelScope.launch {
            CookieStore.setKgPlatform(platform)
            CookieStore.setKgToken(token)
            // MID就是userid
            CookieStore.setKgUserid(mid)
            // 获取dfid
            val dfid = kgRepo.getDfid()
            if (dfid != null) {
                CookieStore.setKgDfid(dfid)
            }
            toast("登录成功")
            refreshKgAccount()
        }
    }

    /**
     * 刷新酷狗账号信息
     */
    fun refreshKgAccount() {
        viewModelScope.launch {
            val userInfo = kgRepo.getUserInfo()
            val vipInfo = kgRepo.getVipInfo()
            if (userInfo != null) {
                _ui.value = _ui.value.copy(
                    kgAccount = KgAccountState(
                        userId = userInfo.userid,
                        nickname = userInfo.nickname,
                        avatar = userInfo.avatar,
                        vipType = vipInfo?.vipType ?: 0,
                        vipEndTime = vipInfo?.endTime ?: 0,
                        isValid = true,
                        platform = CookieStore.kgPlatformValue()
                    )
                )
            }
        }
    }

    /**
     * 领取酷狗VIP（概念版）
     */
    fun kgReceiveVip(vipType: Int, days: Int) {
        viewModelScope.launch {
            val result = kgRepo.receiveVip(vipType, days)
            toast(result ?: "领取失败")
            if (result != null && !result.contains("失败")) {
                refreshKgAccount()
            }
        }
    }

    /**
     * 切换酷狗平台版本
     */
    fun kgSwitchPlatform(platform: Int) {
        viewModelScope.launch {
            CookieStore.setKgPlatform(platform)
            _ui.value = _ui.value.copy(
                kgAccount = _ui.value.kgAccount.copy(platform = platform)
            )
            toast("已切换到${if (platform == 0) "原版" else "概念版"}")
        }
    }

    // ==================== 搜索 ====================
    fun onQueryChange(q: String) {
        _ui.value = _ui.value.copy(query = q)
        searchVm.getSuggestions(q, _ui.value.musicSource) { suggestions ->
            _ui.value = _ui.value.copy(suggestions = suggestions)
        }
    }

    fun setSearchType(type: SearchType) {
        val old = _ui.value.searchType
        _ui.value = _ui.value.copy(searchType = type)
        // 若已有关键词，切换类型时自动刷新
        if (old != type && searchKeyword.isNotEmpty()) {
            doSearch(searchKeyword)
        }
    }

    fun doSearch(keyword: String = _ui.value.query) {
        val kw = keyword.trim()
        if (kw.isEmpty()) return
        searchKeyword = kw
        searchOffset = 0
        _ui.value = _ui.value.copy(
            query = kw, searching = true,
            results = emptyList(), artistResults = emptyList(), albumResults = emptyList(),
            suggestions = emptyList(), hasMore = true
        )
        viewModelScope.launch {
            val result = searchVm.search(kw, _ui.value.searchType, _ui.value.musicSource)
            searchOffset = result.songs.size + result.artists.size + result.albums.size
            _ui.value = _ui.value.copy(
                results = result.songs,
                artistResults = result.artists,
                albumResults = result.albums,
                searching = false,
                hasMore = result.hasMore
            )
        }
    }

    fun loadMoreSearch() {
        if (searchLoading || !_ui.value.hasMore || searchKeyword.isEmpty()) return
        searchLoading = true
        viewModelScope.launch {
            val result = searchVm.loadMore(_ui.value.searchType, _ui.value.musicSource, _ui.value.hasMore)
            searchOffset += result.songs.size + result.artists.size + result.albums.size
            _ui.value = _ui.value.copy(
                results = _ui.value.results + result.songs,
                artistResults = _ui.value.artistResults + result.artists,
                albumResults = _ui.value.albumResults + result.albums,
                hasMore = result.hasMore
            )
            searchLoading = false
        }
    }

    // 加载歌手热门歌曲并返回（供 UI 导航到详情页用）
    fun loadArtistSongs(artistId: Long, onResult: (List<Song>) -> Unit) {
        viewModelScope.launch {
            val songs = searchVm.getArtistTopSongs(artistId)
            onResult(songs)
        }
    }

    // 加载专辑歌曲并返回
    fun loadAlbumSongs(albumId: Long, onResult: (List<Song>) -> Unit) {
        viewModelScope.launch {
            val songs = searchVm.getAlbumDetail(albumId)
            onResult(songs)
        }
    }

    // ==================== 推荐 ====================
    fun loadRecommend() {
        if (_ui.value.recLoading) return
        _ui.value = _ui.value.copy(recLoading = true)
        viewModelScope.launch {
            val result = searchVm.loadRecommendations(_ui.value.musicSource)
            _ui.value = _ui.value.copy(
                recPlaylists = result.playlists,
                recSongs = result.songs,
                recLoading = false
            )
        }
    }

    fun openPlaylist(id: Long) {
        viewModelScope.launch {
            val songs = searchVm.getPlaylistDetail(id)
            if (songs.isNotEmpty()) {
                _ui.value = _ui.value.copy(results = songs, hasMore = false)
                searchKeyword = ""
            }
        }
    }

    // ==================== 播放入口 ====================
    fun playNow(song: Song) {
        val c = controller ?: return
        val existing = queue.indexOfFirst { it.id == song.id }
        if (existing != -1 && existing == c.currentMediaItemIndex) { c.play(); return }
        if (existing != -1) { queue.removeAt(existing); c.removeMediaItem(existing) }
        val insertAt = if (queue.isEmpty()) 0 else c.currentMediaItemIndex + 1
        queue.add(insertAt, song)
        c.addMediaItem(insertAt, song.toMediaItem())
        c.seekTo(insertAt, 0)
        c.prepare()
        c.play()
        syncFromController()
        saveCurrentPlaybackState()
    }

    fun addToQueue(song: Song) {
        val c = controller ?: return
        if (queue.isEmpty()) { playNow(song); return }
        if (queue.any { it.id == song.id }) { toast("已在播放列表"); return }
        queue.add(song)
        c.addMediaItem(song.toMediaItem())
        syncFromController()
        toast("已添加到播放列表")
        saveCurrentPlaybackState()
    }

    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (index !in queue.indices) return
        queue.removeAt(index)
        c.removeMediaItem(index)
        syncFromController()
        saveCurrentPlaybackState()
    }

    fun moveInQueue(from: Int, to: Int) {
        val c = controller ?: return
        if (from !in queue.indices || to !in queue.indices) return
        val s = queue.removeAt(from)
        queue.add(to, s)
        c.moveMediaItem(from, to)
        syncFromController()
        saveCurrentPlaybackState()
    }

    fun playAt(index: Int) {
        val c = controller ?: return
        if (index !in queue.indices) return
        c.seekTo(index, 0); c.prepare(); c.play()
        syncFromController()
        saveCurrentPlaybackState()
    }

    // ==================== 播放控制 ====================
    fun togglePlay() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
        saveCurrentPlaybackState()
    }
    fun next() {
        controller?.seekToNext()
        saveCurrentPlaybackState()
    }
    fun prev() {
        controller?.seekToPrevious()
        saveCurrentPlaybackState()
    }
    fun seekTo(ms: Long) {
        controller?.seekTo(ms)
        // 延迟保存，避免拖动进度条时频繁写入
        viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            saveCurrentPlaybackState()
        }
    }

    fun cyclePlayMode() {
        val c = controller ?: return
        val next = when (_ui.value.playMode) {
            PlayMode.LOOP -> PlayMode.SINGLE
            PlayMode.SINGLE -> PlayMode.RANDOM
            PlayMode.RANDOM -> PlayMode.LOOP
        }
        when (next) {
            PlayMode.LOOP -> { c.repeatMode = Player.REPEAT_MODE_ALL; c.shuffleModeEnabled = false }
            PlayMode.SINGLE -> { c.repeatMode = Player.REPEAT_MODE_ONE; c.shuffleModeEnabled = false }
            PlayMode.RANDOM -> { c.repeatMode = Player.REPEAT_MODE_ALL; c.shuffleModeEnabled = true }
        }
        _ui.value = _ui.value.copy(playMode = next)
    }

    // ==================== 歌词 ====================
    private fun loadLyric(id: Long) {
        viewModelScope.launch {
            // 已下载且带本地 .lrc → 优先读本地，不走网络
            val localLrc = DownloadIndex.get(id)?.lrcPath?.let {
                LyricManager.readLrcFile(getApplication(), it)
            }
            val lys = if (!localLrc.isNullOrBlank()) {
                LyricParser.parse(localLrc)
            } else {
                runCatching { repo.lyric(id) }.getOrDefault(emptyList())
            }
            _ui.value = _ui.value.copy(lyrics = lys, lyricIndex = -1)
        }
    }

    // ==================== 收藏 / 歌单 ====================
    fun toggleFav(song: Song) {
        val nowFav = libraryVm.toggleFavorite(song)
        pushMineToState()
        toast(if (nowFav) "已收藏" else "已取消收藏")
    }
    fun isFav(id: Long) = libraryVm.isFavorite(id)

    fun createPlaylist(name: String) {
        libraryVm.createPlaylist(name)
        pushMineToState()
    }
    fun deletePlaylist(index: Int) {
        libraryVm.deletePlaylist(index)
        pushMineToState()
    }
    fun addToPlaylist(index: Int, song: Song) {
        val ok = libraryVm.addToPlaylist(index, song)
        pushMineToState()
        toast(if (ok) "已添加到歌单" else "歌曲已在歌单中")
    }
    fun removeFromPlaylist(plIndex: Int, songIndex: Int) {
        libraryVm.removeFromPlaylist(plIndex, songIndex)
        pushMineToState()
    }

    /** 播放全部：将一批歌曲替换当前队列并从第 0 首开始播放 */
    fun playAll(songs: List<Song>) {
        val c = controller ?: return
        if (songs.isEmpty()) { toast("列表为空"); return }
        c.clearMediaItems()
        queue.clear()
        queue.addAll(songs)
        c.addMediaItems(songs.map { it.toMediaItem() })
        c.seekTo(0, 0)
        c.prepare()
        c.play()
        syncFromController()
        saveCurrentPlaybackState()
    }

    private fun pushMineToState() {
        _ui.value = _ui.value.copy(
            history = local.history.toList(),
            favorites = local.favorites.toList(),
            playlists = local.playlists.toList()
        )
        schedulePush()
    }

    // ==================== 个性化设置 ====================
    // 全局背景
    fun setGlobalBgEnabled(v: Boolean) {
        val updated = settingsVm.setGlobalBgEnabled(v, _ui.value.settings)
        _ui.value = _ui.value.copy(settings = updated)
    }
    fun setGlobalMaskAlpha(v: Float) {
        val updated = settingsVm.setGlobalMaskAlpha(v, _ui.value.settings)
        _ui.value = _ui.value.copy(settings = updated)
    }
    fun setGlobalBlurRadius(v: Int) {
        val updated = settingsVm.setGlobalBlurRadius(v, _ui.value.settings)
        _ui.value = _ui.value.copy(settings = updated)
    }
    // 全屏播放器背景
    fun setFpBgSource(src: BgSource) {
        val updated = settingsVm.setFpBgSource(src, _ui.value.settings)
        _ui.value = _ui.value.copy(settings = updated)
    }
    fun setFpMaskAlpha(v: Float) {
        val updated = settingsVm.setFpMaskAlpha(v, _ui.value.settings)
        _ui.value = _ui.value.copy(settings = updated)
    }
    fun setFpBlurRadius(v: Int) {
        val updated = settingsVm.setFpBlurRadius(v, _ui.value.settings)
        _ui.value = _ui.value.copy(settings = updated)
    }
    fun setCacheEnabled(v: Boolean) {
        val updated = settingsVm.setCacheEnabled(v, _ui.value.settings)
        _ui.value = _ui.value.copy(settings = updated)
    }
    fun setControlAlpha(v: Float) {
        val updated = settingsVm.setControlAlpha(v, _ui.value.settings)
        _ui.value = _ui.value.copy(settings = updated)
    }
    fun setLandscapeMode(v: Boolean) {
        val updated = settingsVm.setLandscapeMode(v, _ui.value.settings)
        _ui.value = _ui.value.copy(settings = updated)
        refreshWallpaper(v)  // 切换横竖屏时立即刷新壁纸以匹配新模式
    }
    fun setUiScale(scale: Float) {
        val updated = settingsVm.setUiScale(scale, _ui.value.settings)
        _ui.value = _ui.value.copy(settings = updated)
    }
    fun setQuality(level: String) {
        viewModelScope.launch { CookieStore.setLevel(level) }
        _ui.value = _ui.value.copy(quality = level)
        toast("音质已切换")
    }

    /**
     * 根据网易云会员等级返回可用的音质选项
     */
    fun getAvailableQualities(vipType: Int): List<Pair<String, String>> {
        return when {
            vipType >= 11 -> listOf(  // SVIP
                "standard" to "标准",
                "higher" to "较高",
                "exhigh" to "极高",
                "lossless" to "无损 SQ",
                "hires" to "Hi-Res",
                "jyeffect" to "高清臻音",
                "sky" to "沉浸环绕声",
                "jymaster" to "超清母带",
                "dolby" to "臻音全景声"
            )
            vipType >= 1 -> listOf(  // VIP
                "standard" to "标准",
                "higher" to "较高",
                "exhigh" to "极高",
                "lossless" to "无损 SQ",
                "hires" to "Hi-Res",
                "jyeffect" to "高清臻音"
            )
            else -> listOf(  // 普通用户
                "standard" to "标准",
                "higher" to "较高",
                "exhigh" to "极高"
            )
        }
    }
    fun refreshWallpaper(isLandscape: Boolean = false) {
        viewModelScope.launch {
            val url = userRepo.randomWallpaper(isLandscape)
            _ui.value = _ui.value.copy(wallpaperUrl = url)
        }
    }

    // ==================== 缓存管理 ====================
    fun refreshCacheList() {
        viewModelScope.launch {
            val keys = cacheVm.getCachedKeys()
            // 汇总所有已知歌曲来源，构建 id → Song 查表，修复缓存全显示"未知歌曲"
            val lookup = HashMap<Long, Song>()
            val sources = listOf(
                queue, local.history, local.favorites,
                _ui.value.results, _ui.value.recSongs
            )
            sources.forEach { list -> list.forEach { lookup.putIfAbsent(it.id, it) } }
            local.playlists.forEach { pl -> pl.songs.forEach { lookup.putIfAbsent(it.id, it) } }
            // 已下载索引兜底
            DownloadIndex.all().forEach { lookup.putIfAbsent(it.songId, it.song) }

            val size = MusicCache.cacheSpace() / keys.size.coerceAtLeast(1)
            val items = keys.map { key ->
                val song = key.toLongOrNull()?.let { lookup[it] }
                top.nekoh2o.player.data.model.CachedItem(key, song, size)
            }
            _ui.value = _ui.value.copy(cachedItems = items, selectedCacheKeys = emptySet())
        }
    }

    fun toggleCacheSelect(key: String) {
        val sel = _ui.value.selectedCacheKeys.toMutableSet()
        if (sel.contains(key)) sel.remove(key) else sel.add(key)
        _ui.value = _ui.value.copy(selectedCacheKeys = sel)
    }

    fun selectAllCache() {
        val all = _ui.value.cachedItems.map { it.key }.toSet()
        _ui.value = _ui.value.copy(selectedCacheKeys = all)
    }

    fun clearSelectedCache() {
        val sel = _ui.value.selectedCacheKeys
        val count = cacheVm.removeCache(sel)
        toast("已删除 $count 项缓存")
        refreshCacheList()
    }

    fun clearAllCache() {
        cacheVm.clearAllCache()
        toast("已清空所有缓存")
        refreshCacheList()
    }

    // ==================== 网易云 Cookie 管理 ====================
    fun loadNcCookie() {
        _ui.value = _ui.value.copy(ncCookie = neteaseVm.getNcCookie())
    }

    fun saveNcCookie(cookie: String) {
        neteaseVm.saveNcCookie(cookie) {
            _ui.value = _ui.value.copy(ncCookie = cookie.trim())
            schedulePush()
            toast("Cookie 已保存")
        }
    }

    fun saveKgToken(token: String, platform: Int) {
        kugouVm.saveToken(token, platform) {
            schedulePush()
            toast("酷狗 Token 已保存")
            if (token.isNotEmpty()) {
                refreshKgAccount()
            }
        }
    }

    fun clearNcCookie() {
        neteaseVm.clearNcCookie()
        _ui.value = _ui.value.copy(ncCookie = "")
        toast("Cookie 已清除")
    }
    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        _ui.value = _ui.value.copy(sleepMinutes = minutes)
        if (minutes <= 0) { toast("已取消定时关闭"); return }
        toast("将在 $minutes 分钟后暂停")
        sleepJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            controller?.pause()
            _ui.value = _ui.value.copy(sleepMinutes = 0)
            toast("定时关闭已触发")
        }
    }

    // ==================== 倍速 ====================
    fun setPlaybackSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
        val updated = settingsVm.setPlaybackSpeed(speed, _ui.value.settings)
        _ui.value = _ui.value.copy(settings = updated)
    }

    // ==================== 下载 ====================
    /**
     * 下载歌曲到本地（音频 + .lrc 歌词）。
     * @param quality 音质标识：standard / higher / exhigh / lossless
     */
    fun downloadSong(song: Song, quality: String = _ui.value.quality) {
        viewModelScope.launch {
            downloadVm.downloadSong(
                song = song,
                quality = quality,
                downloadDirUri = _ui.value.settings.downloadDirUri,
                onSuccess = {
                    _ui.value = _ui.value.copy(downloadedSongs = DownloadIndex.all())
                    toast("已下载：${song.nm}")
                },
                onError = { error ->
                    toast(error)
                }
            )
        }
    }

    /** 删除一首已下载歌曲（同时删除索引和文件）。 */
    fun removeDownloaded(songId: Long) {
        downloadVm.removeDownloaded(songId)
        _ui.value = _ui.value.copy(downloadedSongs = DownloadIndex.all())
        toast("已删除")
    }

    /** 刷新已下载列表 */
    fun refreshDownloaded() {
        _ui.value = _ui.value.copy(downloadedSongs = downloadVm.getDownloadedSongs())
    }

    fun setDownloadDir(uri: String) {
        val updated = settingsVm.setDownloadDir(uri, _ui.value.settings)
        _ui.value = _ui.value.copy(settings = updated)
    }

    /** 重试失败的下载任务 */
    fun retryDownload(song: Song, quality: String) {
        downloadSong(song, quality)
    }

    // ==================== 悬浮窗歌词设置 ====================
    fun setFloatingLyricDoubleRow(v: Boolean) {
        val updated = settingsVm.setFloatingLyricDoubleRow(v, _ui.value.settings)
        _ui.value = _ui.value.copy(settings = updated)
    }
    fun setFloatingLyricShowTranslation(v: Boolean) {
        val updated = settingsVm.setFloatingLyricShowTranslation(v, _ui.value.settings)
        _ui.value = _ui.value.copy(settings = updated)
    }

    // ==================== 悬浮窗歌词 ====================
    /**
     * 切换悬浮窗歌词开关。
     * 若未授权悬浮窗权限，跳转系统授权页（调用方在此之前应已向用户说明原因）。
     */
    fun toggleFloatingLyric(context: Context) {
        val wantEnable = !_ui.value.settings.floatingLyricEnabled
        if (wantEnable && !Settings.canDrawOverlays(context)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                android.net.Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        }
        val updated = settingsVm.setFloatingLyricEnabled(wantEnable, _ui.value.settings)
        _ui.value = _ui.value.copy(settings = updated)
        val serviceIntent = Intent(context, FloatingLyricService::class.java)
        if (wantEnable) context.startService(serviceIntent) else context.stopService(serviceIntent)
    }

    // ==================== 电池优化豁免 ====================
    /**
     * 请求忽略电池优化，已豁免时提示用户。
     * 调用方在此之前应已向用户说明为何需要该权限。
     */
    fun requestIgnoreBatteryOptimizations(context: Context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                android.net.Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            toast("已豁免电池优化")
        }
    }

    // ==================== 登录与同步 ====================
    fun refreshLogin() {
        viewModelScope.launch {
            CookieStore.awaitReady()
            refreshLoginInternal()
        }
    }

    private suspend fun refreshLoginInternal() {
        android.util.Log.d("PlayerViewModel", "refreshLoginInternal() - calling fetchMe()")

        var attempts = 0
        var lastError: Exception? = null

        while (attempts < 3) {
            attempts++
            android.util.Log.d("PlayerViewModel", "refreshLoginInternal() - attempt $attempts/3")

            val u = runCatching { userRepo.fetchMe() }.getOrElse { e ->
                lastError = e as? Exception
                android.util.Log.e("PlayerViewModel", "refreshLoginInternal() - attempt $attempts failed: ${e.message}")
                null
            }

            if (u != null) {
                android.util.Log.d("PlayerViewModel", "refreshLoginInternal() - success: $u")
                _ui.value = _ui.value.copy(user = u, loggedIn = true)
                pullFromCloud()
                // 登录成功后检测网易云 Cookie 有效性
                checkNcCookieValidity()
                return
            }

            // 失败后等待一小段时间再重试
            if (attempts < 3) {
                kotlinx.coroutines.delay(500L * attempts)
            }
        }

        // 三次失败后提示
        android.util.Log.e("PlayerViewModel", "refreshLoginInternal() - all attempts failed")
        _ui.value = _ui.value.copy(user = null, loggedIn = false)
        toast("网络异常或登录已失效，请重新登录")
    }

    fun onSsoLoggedIn() = refreshLogin()

    // 浏览器登录完成后，账户中心通过深链回跳并携带 JWT：存下 token 再拉取用户信息
    fun onSsoTokenReceived(token: String) {
        viewModelScope.launch {
            CookieStore.awaitReady()
            CookieStore.setAppToken(token)
            refreshLoginInternal()
        }
    }

    private fun pullFromCloud() {
        viewModelScope.launch {
            val remote = userRepo.pull() ?: return@launch
            local.mergeFromRemote(remote.history, remote.favorites, remote.playlists)
            if (remote.ncCookie.isNotEmpty()) {
                CookieStore.setUserCookie(remote.ncCookie)
            }
            if (remote.kgToken.isNotEmpty()) {
                CookieStore.setKgToken(remote.kgToken)
                CookieStore.setKgPlatform(remote.kgPlatform)
            }
            pushMineToState()
            schedulePush()
            refreshKgAccount()
        }
    }

    private fun schedulePush() {
        if (!_ui.value.loggedIn) return
        pushJob?.cancel()
        pushJob = viewModelScope.launch {
            delay(1500)
            userRepo.push(
                history = local.history.toList(),
                favorites = local.favorites.toList(),
                playlists = local.playlists.toList(),
                ncCookie = CookieStore.userCookieValue(),
                kgToken = CookieStore.kgTokenValue(),
                kgPlatform = CookieStore.kgPlatformValue()
            )
        }
    }

    fun logout() {
        viewModelScope.launch {
            userRepo.logout()
            ApiFactory.cookieJar.clear()
            CookieStore.clearAppToken()
            _ui.value = _ui.value.copy(user = null, loggedIn = false)
            toast("已退出登录")
        }
    }

    // ==================== 网易云相关 ====================

    /**
     * 冷启动检测网易云 Cookie 有效性
     */
    fun checkNcCookieValidity() {
        if (!CookieStore.hasNcUserCookie()) return
        viewModelScope.launch {
            val valid = userRepo.checkNcLoginStatus()
            if (!valid) {
                toast("网易云 Cookie 已失效，请重新登录")
                // 清除失效的网易云账号信息
                _ui.value = _ui.value.copy(
                    ncAccount = NcAccountState(isValid = false)
                )
            } else {
                // Cookie 有效时加载账号信息
                refreshNcAccount()
            }
        }
    }

    /**
     * 刷新网易云账号信息（含会员状态）
     */
    fun refreshNcAccount() {
        viewModelScope.launch {
            val resp = userRepo.fetchNcAccount()
            if (resp != null && resp.code == 200 && resp.account != null && resp.profile != null) {
                _ui.value = _ui.value.copy(
                    ncAccount = NcAccountState(
                        userId = resp.account.id,
                        nickname = resp.profile.nickname,
                        avatarUrl = resp.profile.avatarUrl,
                        vipType = resp.account.vipType,
                        vipExpireTime = resp.account.viptypeVersion,
                        isValid = true
                    )
                )
            }
        }
    }
    // ==================== QR 扫码登录（网易云） ====================
    suspend fun qrKeyOnce(): String? = neteaseVm.getQrKey()
    suspend fun qrCreateOnce(key: String): String? = neteaseVm.createQr(key)
    suspend fun qrCheckOnce(key: String): Int {
        val code = neteaseVm.checkQrStatus(key)
        if (code == 803) {
            _ui.value = _ui.value.copy(toast = "网易云登录成功")
            schedulePush()
        }
        return code
    }

    // ==================== 网易云相关功能 ====================

    /**
     * 获取网易云账号信息（含会员状态）
     */
    suspend fun fetchNcAccount() = userRepo.fetchNcAccount()

    /**
     * 获取网易云用户歌单
     */
    suspend fun fetchNcPlaylists(uid: Long): List<top.nekoh2o.player.data.model.NcPlaylistItem> {
        return userRepo.fetchNcPlaylists(uid)
    }

    /**
     * 获取红心歌曲列表
     */
    suspend fun fetchNcLikeSongs(uid: Long): List<Song> {
        val ids = userRepo.fetchNcLikeList(uid)
        return repo.songsByIds(ids)
    }

    /**
     * 获取播放记录
     */
    suspend fun fetchNcPlayRecord(uid: Long, type: Int = 1): List<top.nekoh2o.player.data.model.NcRecordItem> {
        return userRepo.fetchNcRecord(uid, type)
    }

    /**
     * 同步网易云歌单到本地
     */
    fun syncNcPlaylistToLocal(playlist: top.nekoh2o.player.data.model.NcPlaylistItem) {
        viewModelScope.launch {
            val songs = neteaseVm.getPlaylistDetail(playlist.id)
            if (songs.isEmpty()) {
                toast("歌单为空或获取失败")
                return@launch
            }
            // 创建新播放列表并添加歌曲
            val localPl = local.createPlaylist(playlist.name)
            songs.forEach { localPl.songs.add(it) }
            local.savePlaylists()
            pushMineToState()
            schedulePush()
            toast("已同步歌单：${playlist.name}（${songs.size}首）")
        }
    }

    /**
     * 同步红心歌曲到本地收藏
     */
    fun syncNcLikeSongsToLocal(songs: List<Song>) {
        libraryVm.syncNeteaseHeartToLocal(songs)
        pushMineToState()
        schedulePush()
        toast("已同步 ${songs.size} 首红心歌曲到收藏")
    }

    /**
     * 同步播放记录到本地历史
     */
    fun syncNcRecordToLocal(songs: List<Song>) {
        libraryVm.syncNeteaseRecordToLocal(songs)
        pushMineToState()
        schedulePush()
        toast("已同步 ${songs.size} 首播放记录到历史")
    }

    // ==================== 提示 ====================
    fun toast(msg: String) { _ui.value = _ui.value.copy(toast = msg) }
    fun clearToast() { _ui.value = _ui.value.copy(toast = null) }

    // ==================== 酷狗 QQ 登录 ====================

    /**
     * QQ授权登录
     */
    fun kgLoginWithQQ(openid: String, accessToken: String) {
        viewModelScope.launch {
            val data = kgRepo.loginWithQQ(openid, accessToken)
            if (data != null) {
                toast("QQ登录成功")
                refreshKgAccount()
            } else {
                toast("QQ登录失败")
            }
        }
    }

    /**
     * 拉起手机 QQ 登录
     */
    fun launchQQLogin(appId: String = "102058589") {
        val context = getApplication<Application>()
        val helper = top.nekoh2o.player.utils.QQLoginHelper
        if (!helper.isQQInstalled(context)) {
            toast("手机未安装QQ，请先安装QQ或使用其他登录方式")
            return
        }
        val success = helper.launchQQLogin(context, appId)
        if (!success) {
            toast("拉起QQ失败，请使用其他登录方式")
        }
    }

    /**
     * 创建QQ扫码登录二维码
     */
    suspend fun kgCreateQQLoginQR(): top.nekoh2o.player.data.model.KgQQQRCreateData? =
        kugouVm.createQQLoginQR()

    /**
     * 检查QQ扫码登录状态
     */
    suspend fun kgCheckQQLoginQR(qrData: top.nekoh2o.player.data.model.KgQQQRCreateData): top.nekoh2o.player.data.model.KgQQQRCheckResp? =
        kugouVm.checkQQLoginQR(qrData)

    // ==================== 播放状态保存/恢复 ====================

    /**
     * 恢复上次播放状态
     */
    private fun restorePlaybackState() {
        android.util.Log.d("PlayerViewModel", "开始尝试恢复播放状态")
        val state = settingsStore.loadPlaybackState()
        if (state == null) {
            android.util.Log.d("PlayerViewModel", "没有保存的播放状态")
            return
        }
        if (state.queue.isEmpty()) {
            android.util.Log.w("PlayerViewModel", "保存的队列为空")
            return
        }
        val c = controller
        if (c == null) {
            android.util.Log.e("PlayerViewModel", "恢复播放状态失败：controller 为 null")
            return
        }

        viewModelScope.launch {
            android.util.Log.d("PlayerViewModel", "恢复播放状态：队列 ${state.queue.size} 首，索引 ${state.currentIndex}，进度 ${state.position} ms")
            // 恢复队列
            queue.clear()
            queue.addAll(state.queue)
            c.clearMediaItems()
            c.addMediaItems(state.queue.map { it.toMediaItem() })

            // 恢复播放位置
            val safeIndex = state.currentIndex.coerceIn(0, state.queue.size - 1)
            c.seekTo(safeIndex, state.position)
            c.prepare()
            // 不自动播放，让用户手动开始

            syncFromController()
            android.util.Log.d("PlayerViewModel", "播放状态恢复成功")
        }
    }

    /**
     * 保存当前播放状态
     */
    fun saveCurrentPlaybackState() {
        val c = controller
        if (c == null) {
            android.util.Log.w("PlayerViewModel", "保存播放状态失败：controller 为 null")
            return
        }
        if (queue.isEmpty()) {
            android.util.Log.w("PlayerViewModel", "保存播放状态跳过：队列为空")
            return
        }

        val currentIndex = c.currentMediaItemIndex.coerceAtLeast(0)
        val position = c.currentPosition.coerceAtLeast(0)

        android.util.Log.d("PlayerViewModel", "正在保存播放状态：队列 ${queue.size} 首，索引 $currentIndex，进度 $position ms")
        settingsStore.savePlaybackState(queue.toList(), currentIndex, position)
        android.util.Log.d("PlayerViewModel", "播放状态已保存")
    }

    override fun onCleared() {
        // 保存播放状态
        saveCurrentPlaybackState()
        stopProgressLoop()
        controller?.release()
        super.onCleared()
    }
}
