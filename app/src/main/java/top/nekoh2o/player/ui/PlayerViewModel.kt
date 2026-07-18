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
import top.nekoh2o.player.data.model.AppSettings
import top.nekoh2o.player.data.model.BgSource
import top.nekoh2o.player.data.model.LyricLine
import top.nekoh2o.player.data.model.PersonalizedItem
import top.nekoh2o.player.data.model.Playlist
import top.nekoh2o.player.data.model.Song
import top.nekoh2o.player.data.model.User
import top.nekoh2o.player.data.net.ApiFactory
import top.nekoh2o.player.data.net.CookieStore
import top.nekoh2o.player.data.repo.MusicRepository
import top.nekoh2o.player.data.repo.UserRepository
import top.nekoh2o.player.data.store.SettingsStore
import top.nekoh2o.player.playback.PlaybackService

enum class PlayMode { LOOP, SINGLE, RANDOM }

data class UiState(
    val query: String = "",
    val suggestions: List<String> = emptyList(),
    val results: List<Song> = emptyList(),
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
    val toast: String? = null
) {
    val current: Song? get() = queue.getOrNull(currentIndex)
}

class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MusicRepository()
    private val userRepo = UserRepository()
    private val local = (app as PlayerApp).localStore
    private val settingsStore = SettingsStore(app)

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
        }, MoreExecutors.directExecutor())

        refreshLogin()
        refreshWallpaper()
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
                delay(200)
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

    // ==================== 搜索 ====================
    fun onQueryChange(q: String) {
        _ui.value = _ui.value.copy(query = q)
        suggestJob?.cancel()
        if (q.trim().length < 2) {
            _ui.value = _ui.value.copy(suggestions = emptyList())
            return
        }
        suggestJob = viewModelScope.launch {
            delay(300)
            val s = runCatching { repo.suggest(q.trim()) }.getOrDefault(emptyList())
            _ui.value = _ui.value.copy(suggestions = s)
        }
    }

    fun doSearch(keyword: String = _ui.value.query) {
        val kw = keyword.trim()
        if (kw.isEmpty()) return
        searchKeyword = kw
        searchOffset = 0
        _ui.value = _ui.value.copy(
            query = kw, searching = true, results = emptyList(),
            suggestions = emptyList(), hasMore = true
        )
        viewModelScope.launch {
            val list = runCatching { repo.search(kw, 0) }.getOrDefault(emptyList())
            searchOffset = list.size
            _ui.value = _ui.value.copy(
                results = list, searching = false, hasMore = list.size >= 30
            )
        }
    }

    fun loadMoreSearch() {
        if (searchLoading || !_ui.value.hasMore || searchKeyword.isEmpty()) return
        searchLoading = true
        viewModelScope.launch {
            val more = runCatching { repo.search(searchKeyword, searchOffset) }
                .getOrDefault(emptyList())
            searchOffset += more.size
            _ui.value = _ui.value.copy(
                results = _ui.value.results + more, hasMore = more.size >= 30
            )
            searchLoading = false
        }
    }

    // ==================== 推荐 ====================
    fun loadRecommend() {
        if (_ui.value.recLoading) return
        _ui.value = _ui.value.copy(recLoading = true)
        viewModelScope.launch {
            val pls = runCatching { repo.personalizedPlaylists(8) }.getOrDefault(emptyList())
            val songs = runCatching { repo.recommendSongs() }.getOrDefault(emptyList())
            _ui.value = _ui.value.copy(recPlaylists = pls, recSongs = songs, recLoading = false)
        }
    }

    fun openPlaylist(id: Long) {
        viewModelScope.launch {
            val songs = runCatching { repo.playlistTracks(id) }.getOrDefault(emptyList())
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
    }

    fun addToQueue(song: Song) {
        val c = controller ?: return
        if (queue.isEmpty()) { playNow(song); return }
        if (queue.any { it.id == song.id }) { toast("已在播放列表"); return }
        queue.add(song)
        c.addMediaItem(song.toMediaItem())
        syncFromController()
        toast("已添加到播放列表")
    }

    fun removeFromQueue(index: Int) {
        val c = controller ?: return
        if (index !in queue.indices) return
        queue.removeAt(index)
        c.removeMediaItem(index)
        syncFromController()
    }

    fun moveInQueue(from: Int, to: Int) {
        val c = controller ?: return
        if (from !in queue.indices || to !in queue.indices) return
        val s = queue.removeAt(from)
        queue.add(to, s)
        c.moveMediaItem(from, to)
        syncFromController()
    }

    fun playAt(index: Int) {
        val c = controller ?: return
        if (index !in queue.indices) return
        c.seekTo(index, 0); c.prepare(); c.play()
        syncFromController()
    }

    // ==================== 播放控制 ====================
    fun togglePlay() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { controller?.seekToNext() }
    fun prev() { controller?.seekToPrevious() }
    fun seekTo(ms: Long) { controller?.seekTo(ms) }

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
            val lys = runCatching { repo.lyric(id) }.getOrDefault(emptyList())
            _ui.value = _ui.value.copy(lyrics = lys, lyricIndex = -1)
        }
    }

    // ==================== 收藏 / 歌单 ====================
    fun toggleFav(song: Song) {
        val nowFav = local.toggleFav(song)
        pushMineToState()
        toast(if (nowFav) "已收藏" else "已取消收藏")
    }
    fun isFav(id: Long) = local.isFav(id)

    fun createPlaylist(name: String) { local.createPlaylist(name); pushMineToState() }
    fun deletePlaylist(index: Int) { local.deletePlaylist(index); pushMineToState() }
    fun addToPlaylist(index: Int, song: Song) {
        val ok = local.addToPlaylist(index, song)
        pushMineToState()
        toast(if (ok) "已添加到歌单" else "歌曲已在歌单中")
    }
    fun removeFromPlaylist(plIndex: Int, songIndex: Int) {
        local.removeFromPlaylist(plIndex, songIndex); pushMineToState()
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
        val s = _ui.value.settings.copy(globalBgEnabled = v)
        settingsStore.save(s); _ui.value = _ui.value.copy(settings = s)
    }
    fun setGlobalMaskAlpha(v: Float) {
        val s = _ui.value.settings.copy(globalMaskAlpha = v)
        settingsStore.save(s); _ui.value = _ui.value.copy(settings = s)
    }
    fun setGlobalBlurRadius(v: Int) {
        val s = _ui.value.settings.copy(globalBlurRadius = v)
        settingsStore.save(s); _ui.value = _ui.value.copy(settings = s)
    }
    // 全屏播放器背景
    fun setFpBgSource(src: BgSource) {
        val s = _ui.value.settings.copy(fpBgSource = src)
        settingsStore.save(s); _ui.value = _ui.value.copy(settings = s)
    }
    fun setFpMaskAlpha(v: Float) {
        val s = _ui.value.settings.copy(fpMaskAlpha = v)
        settingsStore.save(s); _ui.value = _ui.value.copy(settings = s)
    }
    fun setFpBlurRadius(v: Int) {
        val s = _ui.value.settings.copy(fpBlurRadius = v)
        settingsStore.save(s); _ui.value = _ui.value.copy(settings = s)
    }
    fun setQuality(level: String) {
        viewModelScope.launch { CookieStore.setLevel(level) }
        _ui.value = _ui.value.copy(quality = level)
        toast("音质已切换")
    }
    fun refreshWallpaper() {
        viewModelScope.launch {
            val url = userRepo.randomWallpaper()
            _ui.value = _ui.value.copy(wallpaperUrl = url)
        }
    }

    // ==================== 定时关闭 ====================
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

    // ==================== 登录与同步 ====================
    fun refreshLogin() {
        viewModelScope.launch {
            val u = userRepo.fetchMe()
            _ui.value = _ui.value.copy(user = u, loggedIn = u != null)
            if (u != null) pullFromCloud()
        }
    }

    fun onSsoLoggedIn() = refreshLogin()

    private fun pullFromCloud() {
        viewModelScope.launch {
            val remote = userRepo.pull() ?: return@launch
            local.mergeFromRemote(remote.history, remote.favorites, remote.playlists)
            if (remote.ncCookie.isNotEmpty()) {
                CookieStore.setUserCookie(remote.ncCookie)
            }
            pushMineToState()
            schedulePush()
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
                ncCookie = CookieStore.userCookieValue()
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

    // ==================== QR 扫码登录（网易云） ====================
    suspend fun qrKeyOnce(): String? = repo.qrKey()
    suspend fun qrCreateOnce(key: String): String? = repo.qrCreate(key)
    suspend fun qrCheckOnce(key: String): Int {
        val code = repo.qrCheck(key)
        if (code == 803) {
            _ui.value = _ui.value.copy(toast = "网易云登录成功")
            schedulePush()
        }
        return code
    }

    // ==================== 提示 ====================
    fun toast(msg: String) { _ui.value = _ui.value.copy(toast = msg) }
    fun clearToast() { _ui.value = _ui.value.copy(toast = null) }

    override fun onCleared() {
        stopProgressLoop()
        controller?.release()
        super.onCleared()
    }
}
