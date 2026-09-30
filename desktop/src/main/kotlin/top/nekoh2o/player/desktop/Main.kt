package top.nekoh2o.player.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.window.*
import androidx.compose.ui.unit.dp
import com.formdev.flatlaf.FlatDarkLaf
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import top.nekoh2o.player.data.model.*
import top.nekoh2o.player.data.net.*
import top.nekoh2o.player.data.repo.*
import java.awt.Component
import java.awt.Desktop
import java.awt.Frame
import java.nio.file.*
import javax.swing.*
import javax.swing.filechooser.FileNameExtensionFilter

internal fun button(label: String, action: () -> Unit) = JButton(label).apply { addActionListener { action() } }
internal fun row(vararg components: Component) = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 10, 6)).apply { components.forEach(::add) }
internal fun message(parent: Component, text: String) = JOptionPane.showMessageDialog(parent, text, "NekoPlayer", JOptionPane.INFORMATION_MESSAGE)
internal fun failure(parent: Component, error: Throwable) = message(parent, errorMessage(error))
internal fun errorMessage(error: Throwable): String = when (error) {
    is java.net.UnknownHostException -> "无法解析服务器地址，请检查网络"
    is java.net.SocketTimeoutException -> "请求超时，请稍后重试"
    is javax.net.ssl.SSLException -> "安全连接失败，请检查系统时间和网络"
    is retrofit2.HttpException -> when (error.code()) { 401 -> "请先登录本站账号"; 429 -> "音源额度已用尽或请求过于频繁"; else -> "服务暂时不可用（${error.code()}）" }
    else -> error.message?.takeIf { !it.contains("http") && !it.contains("token", true) }?.take(200) ?: "操作失败，请稍后重试"
}

fun main(args: Array<String>) {
    if (args.contains("--self-test")) { DesktopSelfTest.run(); return }
    FlatDarkLaf.setup()
    application {
        Window(onCloseRequest = ::exitApplication, title = "NekoPlayer · 1.1.0-pre",
            state = rememberWindowState(width = 1240.dp, height = 820.dp)) {
            window.minimumSize = java.awt.Dimension(850, 600)
            val controller = remember { DesktopController(window) }
            DisposableEffect(controller) { onDispose { controller.close() } }
            top.nekoh2o.player.ui.theme.NekoTheme { DesktopApp(controller) }
        }
    }
}

internal class DesktopController(private val owner: Frame) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)
    val library = Library()
    val player = DesktopPlayer()
    private val files = MediaFiles()
    private val music = MusicRepository()
    private val kugou = KugouRepository()
    private val quality = SongQualityRepository()
    var songs by mutableStateOf<List<Song>>(emptyList())
    var query by mutableStateOf("")
    var source by mutableStateOf("netease")
    var title by mutableStateOf("发现音乐")
    var status by mutableStateOf("搜索音乐，或打开本地文件开始播放")
    var error by mutableStateOf<String?>(null)
    var loading by mutableStateOf(false)
    var hasMore by mutableStateOf(false)
    var queue by mutableStateOf<List<Song>>(emptyList())
    var current by mutableStateOf<Song?>(null)
    var currentPath: Path? = null
    var duration by mutableStateOf(0.0)
    var position by mutableStateOf(0.0)
    var lyrics by mutableStateOf<List<LyricLine>>(emptyList())
    var paused by mutableStateOf(true)
    var repeat by mutableStateOf(1)
    var currentVolume by mutableStateOf(0.8f)
    var fullPlayer by mutableStateOf(false)
    var revision by mutableStateOf(0)
    var usage by mutableStateOf<SourceUsage?>(null)
    var videos by mutableStateOf<List<MusicVideo>>(emptyList())
    var videoMode by mutableStateOf(false)
    private var request: Job? = null
    private var searchJob: Job? = null
    private var searchPage = 0
    private var searchTerm = ""
    private var searchSource = "netease"
    private val ticker = Timer(250) { position = player.position; paused = player.paused || currentPath == null }
    init {
        player.effects = DesktopEffects.load()
        player.onEnd = { scope.launch { advance(1, true) } }
        player.onError = { problem -> scope.launch { error = errorMessage(problem); paused = true; status = "播放失败" } }
        ticker.start()
    }
    fun close() { ticker.stop(); scope.cancel(); player.close() }
    fun accounts() { AccountsDialog(owner) { library.reload(); revision++; status = "本站云端数据已同步" }.isVisible = true }
    fun effects() { EffectsDialog(owner, player).isVisible = true }
    fun comments(song: Song) { if (song.source != "local") CommentsDialog(owner, song).isVisible = true }
    fun favorite(song: Song) { library.favorite(song); revision++; if (title == "我的收藏") songs = library.data.favorites }
    fun pause() { if (currentPath == null) songs.firstOrNull()?.let { start(it, songs) } else { player.pause(); paused = player.paused } }
    fun seek(seconds: Double) { currentPath?.let { player.play(it, seconds.coerceIn(0.0, duration)); position = seconds; paused = false } }
    fun setVolume(value: Float) { currentVolume = value; player.volume = value }
    fun switchSource(value: String) {
        if (value.startsWith("animemusic-") && CookieStore.appTokenValue().isBlank()) {
            error = "请先登录本站账号后使用惜缘惜梦音源"
            return
        }
        searchJob?.cancel(); source = value; songs = emptyList(); videos = emptyList(); hasMore = false
    }
    fun showLibrary(label: String) { searchJob?.cancel(); showSongs(label, when (label) { "我的收藏" -> library.data.favorites; "最近播放" -> library.data.history; "播放队列" -> queue; else -> emptyList() }) }
    fun sourceUsage() = work("正在读取共享额度…") { usage = withContext(Dispatchers.IO) { ApiFactory.animemusic.usage() } }
    fun playVideo(video: MusicVideo) = work("正在解析 MV…") {
        val url = withContext(Dispatchers.IO) { MvRepository().url(video) } ?: error("暂无可播放 MV")
        if (!player.paused && currentPath != null) { player.pause(); paused = true }
        Desktop.getDesktop().browse(java.net.URI(url))
    }
    fun sync() = work("正在同步本站…") {
        withContext(Dispatchers.IO) { DesktopCloudSync.sync(library) }; revision++; status = "云端同步完成"
    }
    private fun showSongs(label: String, list: List<Song>) { title = label; songs = list; videoMode = false; hasMore = false }
    fun work(label: String, block: suspend CoroutineScope.() -> Unit): Job = scope.launch {
        status = label; loading = true
        try { block(); if (status == label) status = "就绪" }
        catch (problem: CancellationException) { throw problem }
        catch (problem: Throwable) { error = errorMessage(problem); status = "操作未完成" }
        finally { loading = false }
    }
    fun search(next: Boolean = false) {
        if (!next) { searchTerm = query.trim(); searchSource = source; searchPage = 0 }
        if (searchTerm.isBlank()) return
        searchJob?.cancel(); searchJob = work("正在搜索…") {
            val page = searchPage + 1
            if (videoMode) {
                val found = withContext(Dispatchers.IO) { MvRepository().search(searchSource, searchTerm, page) }
                videos = (if (next) videos + found else found).distinctBy { it.source + ":" + it.id }
                title = "MV · $searchTerm"; hasMore = found.size >= 30
            } else {
                val found = withContext(Dispatchers.IO) { when {
                    searchSource.startsWith("animemusic-") -> AnimemusicRepository().search(searchSource.removePrefix("animemusic-"), searchTerm, page).songs
                    searchSource == "kugou" -> kugou.search(searchTerm, page)
                    else -> music.search(searchTerm, searchPage * 30)
                } }
                val all = if (next) songs + found else found
                showSongs("搜索 · $searchTerm", all.distinctBy(Library::key)); hasMore = found.size >= 30
            }
            searchPage++; status = "搜索完成"
        }
    }
    fun loadRecommendations() = work("正在获取推荐…") {
        val provider = source
        if (provider.startsWith("animemusic-")) { status = "请输入关键词搜索本站音源"; return@work }
        val list = withContext(Dispatchers.IO) { if (provider == "kugou") kugou.getRecommendSongs() else { if (!CookieStore.hasAnyCookie()) music.ensureGuestCookie(); music.recommendSongs() } }
        showSongs("发现音乐", list)
    }
    fun start(song: Song, list: List<Song>) {
        request?.cancel(); player.stop(); queue = list.toList(); current = song; currentPath = null; duration = 0.0; lyrics = emptyList(); position = 0.0; paused = false
        request = work("正在检查可播放音质…") {
            val path = withContext(Dispatchers.IO) {
                if (song.source == "local") Paths.get(song.hash)
                else {
                    val audio = quality.resolvePlayback(song, CookieStore.level) ?: error("没有可用的完整播放链接")
                    files.fetch(audio.url) { bytes, total -> scope.launch { status = "缓冲 ${audio.quality.label} · " + if (total > 0) "${bytes * 100 / total}%" else "${bytes / 1024} KiB" } }
                }
            }
            currentPath = path; duration = withContext(Dispatchers.IO) { Decoder.duration(path) }
            library.played(song); revision++; player.play(path); paused = false; status = "正在播放"
            launch {
                lyrics = withContext(Dispatchers.IO) {
                    if (song.source == "local") {
                        val file = Paths.get(song.hash.substringBeforeLast('.') + ".lrc")
                        if (Files.isRegularFile(file)) top.nekoh2o.player.lyric.LyricParser.parse(String(Files.readAllBytes(file), Charsets.UTF_8), null) else emptyList()
                    } else runCatching { music.lyric(song) }.getOrDefault(emptyList())
                }
            }
        }
    }
    fun advance(direction: Int, ended: Boolean = false) {
        if (queue.isEmpty()) return
        val index = queue.indexOfFirst { Library.key(it) == current?.let(Library::key) }
        val next = when { ended && repeat == 2 -> index; repeat == 3 -> queue.indices.random(); repeat == 1 -> Math.floorMod(index + direction, queue.size); else -> index + direction }
        if (next in queue.indices) start(queue[next], queue) else { player.stop(); currentPath = null; paused = true; status = "播放结束" }
    }
    fun openLocal() {
        val chooser = JFileChooser().apply { isMultiSelectionEnabled = true; fileFilter = FileNameExtensionFilter("音频文件", "mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "ape", "wma") }
        if (chooser.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION) {
            val songs = chooser.selectedFiles.map { Song(it.absolutePath.hashCode().toLong(), it.nameWithoutExtension, "本地音乐", source = "local", hash = it.absolutePath) }
            showSongs("本地音乐", songs); songs.firstOrNull()?.let { start(it, songs) }
        }
    }
    private fun downloadDirectory() = Paths.get(DesktopPaths.preferences.getString("downloads", Paths.get(System.getProperty("user.home"), "Music", "NekoPlayer").toString())!!)
    fun download(song: Song) {
        if (song.source == "local") { message(owner, "歌曲已在本地。"); return }
        work("正在检查可下载音质…") {
            val choices = withContext(Dispatchers.IO) { quality.downloadable(song) }
            require(choices.isNotEmpty()) { "当前账号没有可下载音质" }
            val labels = choices.map { it.label + if (it.size > 0) " · " + (it.size / 1024 / 1024) + " MiB" else "" }.toTypedArray()
            val selected = JOptionPane.showInputDialog(owner, "选择已验证的可下载音质", "下载", JOptionPane.QUESTION_MESSAGE, null, labels, labels.first()) as? String ?: return@work
            val audio = withContext(Dispatchers.IO) { quality.resolveDownload(song, choices[labels.indexOf(selected)].id) } ?: error("所选音质的下载权限已变化，请重试")
            val dir = downloadDirectory(); Files.createDirectories(dir)
            val name = (song.ar + " - " + song.nm).map { if (it.code < 32 || it in "\\/:*?\"<>|") '_' else it }.joinToString("").take(140).trimEnd(' ', '.')
            val ext = java.net.URI(audio.url).path.substringAfterLast('.', "").lowercase().takeIf { it in setOf("mp3","flac","m4a","aac","wav","ogg","opus") } ?: "audio"
            val chooser = JFileChooser(dir.toFile()).apply { selectedFile = dir.resolve(name + "." + ext).toFile() }
            if (chooser.showSaveDialog(owner) != JFileChooser.APPROVE_OPTION) return@work
            val destination = chooser.selectedFile.toPath()
            if (Files.exists(destination) && JOptionPane.showConfirmDialog(owner, "覆盖已有文件？", "下载", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return@work
            status = "正在下载…"
            withContext(Dispatchers.IO) { val cached = files.fetch(audio.url); Files.copy(cached, destination, StandardCopyOption.REPLACE_EXISTING) }
            message(owner, "下载完成：" + destination)
        }
    }
    fun playlists() {
        val options = arrayOf("新建本地歌单", "打开本地歌单", "账号云歌单", "从本站合并收藏与歌单", "同步到本站")
        when (JOptionPane.showInputDialog(owner, "歌单管理", "我的歌单", JOptionPane.PLAIN_MESSAGE, null, options, options[1])) {
            options[0] -> JOptionPane.showInputDialog(owner, "歌单名称")?.takeIf { it.isNotBlank() }?.let { library.addPlaylist(it) }
            options[1] -> choosePlaylist()?.let { showSongs(it.name, it.songs) }
            options[2] -> work("正在读取云歌单…") {
                if (source == "kugou") {
                    val lists = withContext(Dispatchers.IO) { kugou.getUserPlaylists() }
                    val chosen = choose(lists, { it.specialName }, "酷狗歌单") ?: return@work
                    showSongs(chosen.specialName, withContext(Dispatchers.IO) { kugou.getPlaylistDetail(chosen.collectionId.ifBlank { chosen.specialId.toString() }) })
                } else {
                    val lists = withContext(Dispatchers.IO) { val uid = ApiFactory.netease.userAccount(CookieStore.activeCookie()).account?.id ?: error("请先登录网易云"); ApiFactory.netease.userPlaylist(uid, CookieStore.activeCookie()).playlist }
                    val chosen = choose(lists, { it.name }, "网易云歌单") ?: return@work
                    val tracks = withContext(Dispatchers.IO) {
                        val detail = ApiFactory.netease.playlistDetail(chosen.id).playlist ?: error("无法读取歌单")
                        detail.trackIds.map { it.id }.chunked(100).flatMap { ids -> ApiFactory.netease.songDetail(ids.joinToString(",")).songs.map { Song(it.id, it.name, it.ar.joinToString(" / ") { a -> a.name }, it.al?.picUrl) } }
                    }
                    showSongs(chosen.name, tracks)
                }
            }
            options[3] -> work("正在同步…") { withContext(Dispatchers.IO) { DesktopCloudSync.sync(library) }; showSongs("我的收藏", library.data.favorites) }
            options[4] -> work("正在同步…") { withContext(Dispatchers.IO) {
                DesktopCloudSync.sync(library, CredentialSyncMode.LOCAL_AUTHORITATIVE)
            } }
        }
    }
    private fun <T> choose(items: List<T>, label: (T) -> String, title: String): T? {
        if (items.isEmpty()) { message(owner, "暂无内容"); return null }
        val names = items.mapIndexed { i, it -> (i + 1).toString() + ". " + label(it) }.toTypedArray()
        val answer = JOptionPane.showInputDialog(owner, title, title, JOptionPane.PLAIN_MESSAGE, null, names, names[0]) ?: return null
        return items[names.indexOf(answer)]
    }
    private fun choosePlaylist() = choose(library.data.playlists, { it.name }, "本地歌单")
    fun addToPlaylist(song: Song) { choosePlaylist()?.let { library.addToPlaylist(it.id, song); status = "已加入歌单" } }
    fun settings() {
        val levels = QualityLevel.entries.toTypedArray()
        val selected = JComboBox(levels.map { it.label }.toTypedArray()).apply { selectedIndex = levels.indexOfFirst { it.value == CookieStore.level }.coerceAtLeast(0) }
        val directory = JTextField(downloadDirectory().toString(), 35)
        val panel = JPanel(java.awt.GridLayout(0,1,8,8)).apply { add(JLabel("优先使用所选音质；不支持时选择最高可用音质")); add(selected); add(JLabel("下载目录")); add(directory); add(JLabel("桌面输出：48 kHz / 16-bit 双声道。下载保留原始音质。")) }
        if (JOptionPane.showConfirmDialog(owner, panel, "设置", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) work("保存设置…") {
            withContext(Dispatchers.IO) { val path = Paths.get(directory.text.trim()); Files.createDirectories(path); DesktopPaths.preferences.edit().putString("downloads", path.toString()).apply(); CookieStore.setLevel(levels[selected.selectedIndex].value) }
        }
    }
    companion object { fun clock(seconds: Double) = "%02d:%02d".format(seconds.toInt().coerceAtLeast(0) / 60, seconds.toInt().coerceAtLeast(0) % 60) }
}
