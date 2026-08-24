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
    val ncCookie: String = ""
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
            // 恢复已保存的播放倍速
            val savedSpeed = _ui.value.settings.playbackSpeed
            if (savedSpeed != 1.0f) controller?.setPlaybackSpeed(savedSpeed)
        }, MoreExecutors.directExecutor())

        viewModelScope.launch {
            // 主动初始化 CookieStore，确保凭据加载完成
            CookieStore.init(app)
            _ui.value = _ui.value.copy(quality = CookieStore.level)
            refreshLoginInternal()
        }
        refreshWallpaper()

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
            when (_ui.value.searchType) {
                SearchType.SONG -> {
                    val list = runCatching { repo.search(kw, 0) }.getOrDefault(emptyList())
                    searchOffset = list.size
                    _ui.value = _ui.value.copy(results = list, searching = false, hasMore = list.size >= 30)
                }
                SearchType.ARTIST -> {
                    val list = runCatching { repo.searchArtist(kw, 0) }.getOrDefault(emptyList())
                    searchOffset = list.size
                    _ui.value = _ui.value.copy(artistResults = list, searching = false, hasMore = list.size >= 30)
                }
                SearchType.ALBUM -> {
                    val list = runCatching { repo.searchAlbum(kw, 0) }.getOrDefault(emptyList())
                    searchOffset = list.size
                    _ui.value = _ui.value.copy(albumResults = list, searching = false, hasMore = list.size >= 30)
                }
            }
        }
    }

    fun loadMoreSearch() {
        if (searchLoading || !_ui.value.hasMore || searchKeyword.isEmpty()) return
        searchLoading = true
        viewModelScope.launch {
            when (_ui.value.searchType) {
                SearchType.SONG -> {
                    val more = runCatching { repo.search(searchKeyword, searchOffset) }.getOrDefault(emptyList())
                    searchOffset += more.size
                    _ui.value = _ui.value.copy(results = _ui.value.results + more, hasMore = more.size >= 30)
                }
                SearchType.ARTIST -> {
                    val more = runCatching { repo.searchArtist(searchKeyword, searchOffset) }.getOrDefault(emptyList())
                    searchOffset += more.size
                    _ui.value = _ui.value.copy(artistResults = _ui.value.artistResults + more, hasMore = more.size >= 30)
                }
                SearchType.ALBUM -> {
                    val more = runCatching { repo.searchAlbum(searchKeyword, searchOffset) }.getOrDefault(emptyList())
                    searchOffset += more.size
                    _ui.value = _ui.value.copy(albumResults = _ui.value.albumResults + more, hasMore = more.size >= 30)
                }
            }
            searchLoading = false
        }
    }

    // 加载歌手热门歌曲并返回（供 UI 导航到详情页用）
    fun loadArtistSongs(artistId: Long, onResult: (List<Song>) -> Unit) {
        viewModelScope.launch {
            val songs = runCatching { repo.artistTopSongs(artistId) }.getOrDefault(emptyList())
            onResult(songs)
        }
    }

    // 加载专辑歌曲并返回
    fun loadAlbumSongs(albumId: Long, onResult: (List<Song>) -> Unit) {
        viewModelScope.launch {
            val songs = runCatching { repo.albumSongs(albumId) }.getOrDefault(emptyList())
            onResult(songs)
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
            // 已下载且带本地 .lrc → 优先读本地，不走网络
            val localLrc = DownloadIndex.get(id)?.lrcPath?.let { readLocalLrc(it) }
            val lys = if (!localLrc.isNullOrBlank()) {
                LyricParser.parse(localLrc)
            } else {
                runCatching { repo.lyric(id) }.getOrDefault(emptyList())
            }
            _ui.value = _ui.value.copy(lyrics = lys, lyricIndex = -1)
        }
    }

    /** 读取本地 .lrc 文件内容，支持 SAF content:// 与普通文件路径。 */
    private fun readLocalLrc(path: String): String? = runCatching {
        if (path.startsWith("content:")) {
            getApplication<Application>().contentResolver
                .openInputStream(Uri.parse(path))?.bufferedReader()?.use { it.readText() }
        } else {
            val f = File(path)
            if (f.exists()) f.readText() else null
        }
    }.getOrNull()

    /** 将已解析的歌词行重建为标准 .lrc 文本，供下载时保存。 */
    private fun buildLrcText(lines: List<LyricLine>): String = buildString {
        for (l in lines) {
            val totalCs = (l.time * 100).toLong()
            val mm = totalCs / 6000
            val ss = (totalCs % 6000) / 100
            val cs = totalCs % 100
            append(String.format("[%02d:%02d.%02d]", mm, ss, cs)).append(l.text).append('\n')
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
    fun setCacheEnabled(v: Boolean) {
        val s = _ui.value.settings.copy(cacheEnabled = v)
        settingsStore.save(s); _ui.value = _ui.value.copy(settings = s)
    }
    fun setControlAlpha(v: Float) {
        val s = _ui.value.settings.copy(controlAlpha = v)
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

    // ==================== 缓存管理 ====================
    fun refreshCacheList() {
        viewModelScope.launch {
            val keys = MusicCache.cachedKeys()
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
        sel.forEach { MusicCache.remove(it) }
        toast("已删除 ${sel.size} 项缓存")
        refreshCacheList()
    }

    fun clearAllCache() {
        MusicCache.clear()
        toast("已清空所有缓存")
        refreshCacheList()
    }

    // ==================== 网易云 Cookie 管理 ====================
    fun loadNcCookie() {
        _ui.value = _ui.value.copy(ncCookie = CookieStore.userCookieValue())
    }

    fun saveNcCookie(cookie: String) {
        viewModelScope.launch {
            CookieStore.setUserCookie(cookie.trim())
            _ui.value = _ui.value.copy(ncCookie = cookie.trim())
            // 同步到云端
            schedulePush()
            toast("Cookie 已保存")
        }
    }

    fun clearNcCookie() {
        viewModelScope.launch {
            CookieStore.setUserCookie("")
            _ui.value = _ui.value.copy(ncCookie = "")
            toast("Cookie 已清除")
        }
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
        val s = _ui.value.settings.copy(playbackSpeed = speed)
        settingsStore.save(s)
        _ui.value = _ui.value.copy(settings = s)
    }

    // ==================== 下载 ====================
    /**
     * 下载歌曲到本地（音频 + .lrc 歌词）。
     * @param quality 音质标识：standard / higher / exhigh / lossless
     */
    fun downloadSong(song: Song, quality: String = _ui.value.quality) {
        if (DownloadIndex.isDownloaded(song.id)) { toast("已下载过该歌曲"); return }
        viewModelScope.launch {
            val url = runCatching { repo.resolvePlayUrl(song.id, quality) }.getOrNull()
            if (url == null) { toast("获取下载地址失败"); return@launch }
            // 拉取歌词一并保存为 .lrc（失败则跳过，播放时回退网络）
            val lrcText = runCatching { repo.lyric(song.id) }.getOrNull()
                ?.takeIf { it.isNotEmpty() }?.let { buildLrcText(it) }
            val dirUri = _ui.value.settings.downloadDirUri.ifBlank { null }
            toast("开始下载：${song.nm}")
            val result = runCatching {
                Downloader.download(getApplication(), song, url, quality, lrcText, dirUri)
            }.getOrElse {
                toast("下载失败：${it.message}"); return@launch
            }
            DownloadIndex.add(result)
            _ui.value = _ui.value.copy(downloadedSongs = DownloadIndex.all())
            toast("已下载：${song.nm}")
        }
    }

    /** 删除一首已下载歌曲的索引记录（不物理删除文件，避免误删用户文件）。 */
    fun removeDownloaded(songId: Long) {
        DownloadIndex.remove(songId)
        _ui.value = _ui.value.copy(downloadedSongs = DownloadIndex.all())
        toast("已从下载列表移除")
    }

    /** 设置自定义下载目录（SAF tree URI）。 */
    fun setDownloadDir(uri: String) {
        val s = _ui.value.settings.copy(downloadDirUri = uri)
        settingsStore.save(s); _ui.value = _ui.value.copy(settings = s)
        toast("下载目录已更新")
    }

    // ==================== 悬浮窗歌词设置 ====================
    fun setFloatingLyricDoubleRow(v: Boolean) {
        val s = _ui.value.settings.copy(floatingLyricDoubleRow = v)
        settingsStore.save(s); _ui.value = _ui.value.copy(settings = s)
    }
    fun setFloatingLyricShowTranslation(v: Boolean) {
        val s = _ui.value.settings.copy(floatingLyricShowTranslation = v)
        settingsStore.save(s); _ui.value = _ui.value.copy(settings = s)
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
        val s = _ui.value.settings.copy(floatingLyricEnabled = wantEnable)
        settingsStore.save(s)
        _ui.value = _ui.value.copy(settings = s)
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
        val u = userRepo.fetchMe()
        android.util.Log.d("PlayerViewModel", "refreshLoginInternal() - fetchMe result: $u")
        _ui.value = _ui.value.copy(user = u, loggedIn = u != null)
        if (u != null) pullFromCloud()
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
