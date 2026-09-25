package top.nekoh2o.player.desktop

import com.formdev.flatlaf.FlatDarkLaf
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import top.nekoh2o.player.data.model.*
import top.nekoh2o.player.data.net.*
import top.nekoh2o.player.data.repo.*
import java.awt.*
import java.awt.event.*
import java.nio.file.*
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.filechooser.FileNameExtensionFilter

internal fun button(label: String, action: () -> Unit) = JButton(label).apply { addActionListener { action() } }
internal fun row(vararg components: Component) = JPanel(FlowLayout(FlowLayout.LEFT, 10, 6)).apply { components.forEach(::add) }
internal fun message(parent: Component, text: String) = JOptionPane.showMessageDialog(parent, text, "NekoPlayer", JOptionPane.INFORMATION_MESSAGE)
internal fun failure(parent: Component, error: Throwable) {
    val detail = when (error) {
        is java.net.UnknownHostException -> "无法解析服务器地址，请检查网络。"
        is java.net.SocketTimeoutException -> "请求超时，请稍后重试。"
        is javax.net.ssl.SSLException -> "安全连接失败，请检查系统时间和网络。"
        is retrofit2.HttpException -> "服务器返回 HTTP " + error.code()
        is UnsatisfiedLinkError -> "专业音效组件未加载，请使用与系统架构匹配的完整安装包。"
        else -> error.message?.takeIf { !it.contains("http") && !it.contains("token", true) }?.take(300) ?: "操作失败，请检查网络、账号权限或安装文件。"
    }
    JOptionPane.showMessageDialog(parent, detail, "操作未完成", JOptionPane.ERROR_MESSAGE)
}

fun main(args: Array<String>) {
    if (args.contains("--self-test")) { DesktopSelfTest.run(); return }
    FlatDarkLaf.setup()
    UIManager.put("Component.arc", 12); UIManager.put("Button.arc", 12)
    UIManager.put("Table.rowHeight", 42); UIManager.put("Component.focusColor", Color(0x62DCC0))
    val fonts = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
    val family = listOf("Microsoft YaHei UI", "Noto Sans CJK SC", "WenQuanYi Micro Hei", "Dialog").first { it in fonts }
    UIManager.put("defaultFont", Font(family, Font.PLAIN, 14))
    SwingUtilities.invokeLater {
        try { PlayerWindow().isVisible = true }
        catch (e: Throwable) { failure(JPanel(), e) }
    }
}

internal class SongTable : AbstractTableModel() {
    var songs: List<Song> = emptyList()
        set(value) { field = value; fireTableDataChanged() }
    override fun getRowCount() = songs.size
    override fun getColumnCount() = 4
    override fun getColumnName(column: Int) = arrayOf("#", "歌曲", "歌手", "来源")[column]
    override fun getValueAt(row: Int, column: Int): Any = songs[row].let {
        when (column) { 0 -> row + 1; 1 -> it.nm; 2 -> it.ar; else -> when (it.source) { "kugou" -> "酷狗"; "local" -> "本地"; else -> "网易云" } }
    }
}

internal class PlayerWindow : JFrame("NekoPlayer · Desktop 1.0.9") {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)
    private val library = Library()
    private val player = DesktopPlayer()
    private val files = MediaFiles()
    private val music = MusicRepository()
    private val kugou = KugouRepository()
    private val quality = SongQualityRepository()
    private val model = SongTable()
    private val table = JTable(model)
    private val query = JTextField(26)
    private val source = JComboBox(arrayOf("网易云音乐", "酷狗音乐"))
    private val titleLabel = JLabel("现在开始，听见喜欢。")
    private val status = JLabel("就绪 · 支持本地音乐、网易云和酷狗")
    private val now = JLabel("NekoPlayer")
    private val lyric = JLabel("选择歌曲，或打开本地音频", SwingConstants.CENTER)
    private val progress = JSlider(0, 1000, 0)
    private val time = JLabel("00:00 / 00:00")
    private val pause = button("暂停") { player.pause(); updatePause() }
    private val repeat = JComboBox(arrayOf("顺序播放", "列表循环", "单曲循环", "随机播放"))
    private var queue: List<Song> = emptyList()
    private var current: Song? = null
    private var currentPath: Path? = null
    private var duration = 0.0
    private var lyrics: List<LyricLine> = emptyList()
    private var request: Job? = null
    private var searchJob: Job? = null
    private var searchPage = 0
    private var searchTerm = ""
    private var searchSource = 0
    private val more = button("加载更多") { search(true) }
    private var updatingSlider = false
    private val ticker = Timer(250) { tick() }

    init {
        player.effects = DesktopEffects.load()
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
        minimumSize = Dimension(960, 650); size = Dimension(1180, 780); setLocationRelativeTo(null)
        val root = JPanel(BorderLayout(20, 16)).apply { border = BorderFactory.createEmptyBorder(20, 20, 16, 20) }
        contentPane = root
        val sidebar = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS); preferredSize = Dimension(170, 400) }
        sidebar.add(JLabel("NEKO PLAYER").apply { font = font.deriveFont(Font.BOLD, 21f); foreground = Color(0x62DCC0) })
        sidebar.add(Box.createVerticalStrut(24))
        fun nav(label: String, action: () -> Unit) { sidebar.add(button(label, action).apply { maximumSize = Dimension(170, 42); alignmentX = 0f }); sidebar.add(Box.createVerticalStrut(8)) }
        nav("发现音乐") { searchJob?.cancel(); loadRecommendations() }
        nav("我的收藏") { showSongs("我的收藏", library.data.favorites) }
        nav("最近播放") { showSongs("最近播放", library.data.history) }
        nav("我的歌单") { playlists() }
        nav("播放队列") { showSongs("播放队列", queue) }
        nav("打开本地音乐") { openLocal() }
        sidebar.add(Box.createVerticalStrut(18))
        nav("账号与 Cookie") { AccountsDialog(this) { library.reload(); status.text = "本站云端数据已同步" }.isVisible = true }
        nav("音效与母带") { EffectsDialog(this, player).isVisible = true }
        nav("设置与音质") { settings() }
        nav("下载目录") { val dir = downloadDirectory(); Files.createDirectories(dir); Desktop.getDesktop().open(dir.toFile()) }
        sidebar.add(Box.createVerticalGlue())
        sidebar.add(JLabel("1.0.9 · 桌面版").apply { foreground = Color.GRAY })
        root.add(sidebar, BorderLayout.WEST)
        val center = JPanel(BorderLayout(8, 16))
        val header = JPanel(BorderLayout(0, 16))
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 24f)
        header.add(titleLabel, BorderLayout.NORTH)
        header.add(row(source, query, button("搜索") { search(false) }), BorderLayout.CENTER)
        query.addActionListener { search(false) }
        center.add(header, BorderLayout.NORTH)
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); table.rowHeight = 44; table.setShowGrid(false)
        table.columnModel.getColumn(0).maxWidth = 55; table.columnModel.getColumn(3).maxWidth = 90
        table.addMouseListener(object : MouseAdapter() { override fun mouseClicked(e: MouseEvent) { if (e.clickCount == 2) selected()?.let { start(it, model.songs) } } })
        table.inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "play")
        table.actionMap.put("play", object : AbstractAction() { override fun actionPerformed(e: ActionEvent?) { selected()?.let { start(it, model.songs) } } })
        center.add(JScrollPane(table), BorderLayout.CENTER)
        center.add(row(button("播放") { selected()?.let { start(it, model.songs) } }, button("收藏 / 取消") { selected()?.let { library.favorite(it); status.text = "收藏已更新" } },
            button("加入歌单") { selected()?.let(::addToPlaylist) }, button("下载") { selected()?.let(::download) },
            button("歌曲评论") { selected()?.takeIf { it.source != "local" }?.let { CommentsDialog(this, it).isVisible = true } }, more), BorderLayout.SOUTH)
        more.isVisible = false; root.add(center, BorderLayout.CENTER)
        val bottom = JPanel(BorderLayout(0, 8))
        val info = JPanel(BorderLayout()); now.font = now.font.deriveFont(Font.BOLD, 15f)
        info.add(now, BorderLayout.WEST); info.add(lyric, BorderLayout.CENTER); bottom.add(info, BorderLayout.NORTH)
        val controls = JPanel(BorderLayout())
        controls.add(row(button("上一首") { advance(-1) }, pause, button("下一首") { advance(1) }, repeat), BorderLayout.WEST)
        val seek = JPanel(BorderLayout()); seek.add(progress); seek.add(time, BorderLayout.SOUTH); controls.add(seek)
        val volume = JSlider(0, 100, 80).apply { preferredSize = Dimension(100, 28); toolTipText = "音量"; addChangeListener { player.volume = value / 100f } }
        controls.add(row(JLabel("音量"), volume), BorderLayout.EAST); bottom.add(controls); bottom.add(status, BorderLayout.SOUTH)
        root.add(bottom, BorderLayout.SOUTH)
        progress.addMouseListener(object : MouseAdapter() { override fun mouseReleased(e: MouseEvent) {
            if (!updatingSlider && duration > 0) currentPath?.let { player.play(it, duration * progress.value / 1000); updatePause() }
        } })
        player.onEnd = { scope.launch { advance(1, true) } }
        player.onError = { e -> scope.launch { status.text = "播放失败"; failure(this@PlayerWindow, e) } }
        addWindowListener(object : WindowAdapter() { override fun windowClosing(e: WindowEvent) {
            ticker.stop(); request?.cancel(); scope.cancel(); player.close(); dispose()
        } })
        ticker.start()
        status.text = "搜索在线音乐，或打开本地文件开始播放"
    }

    private fun selected() = table.selectedRow.takeIf { it >= 0 }?.let { model.songs.getOrNull(table.convertRowIndexToModel(it)) }
    private fun showSongs(title: String, songs: List<Song>) { titleLabel.text = title; model.songs = songs; more.isVisible = false; status.text = songs.size.toString() + " 首歌曲" }
    private fun work(label: String, block: suspend CoroutineScope.() -> Unit): Job = scope.launch {
        status.text = label
        try { block(); status.text = "就绪" } catch (e: CancellationException) { throw e } catch (e: Throwable) { status.text = "操作未完成"; failure(this@PlayerWindow, e) }
    }
    private fun search(next: Boolean) {
        if (!next) { searchTerm = query.text.trim(); searchSource = source.selectedIndex; searchPage = 0 }
        if (searchTerm.isBlank()) return
        searchJob?.cancel(); searchJob = work("正在搜索…") {
            more.isEnabled = false
            try {
                val songs = withContext(Dispatchers.IO) { if (searchSource == 1) kugou.search(searchTerm, searchPage + 1) else music.search(searchTerm, searchPage * 30) }
                val all = if (next) model.songs + songs else songs
                showSongs("搜索 · " + searchTerm, all.distinctBy(Library::key)); searchPage++
                more.isVisible = songs.isNotEmpty()
            } finally { more.isEnabled = true }
        }
    }
    private fun loadRecommendations() = work("正在获取推荐…") {
        val provider = source.selectedIndex
        val songs = withContext(Dispatchers.IO) { if (provider == 1) kugou.getRecommendSongs() else { if (!CookieStore.hasAnyCookie()) music.ensureGuestCookie(); music.recommendSongs() } }
        showSongs("发现音乐", songs)
    }
    private fun start(song: Song, songs: List<Song>) {
        request?.cancel(); player.stop(); queue = songs.toList(); current = song; currentPath = null; duration = 0.0; lyrics = emptyList()
        now.text = song.nm + " · " + song.ar; lyric.text = "正在准备播放…"; progress.value = 0
        request = work("正在检查可播放音质…") {
            val (path, label) = withContext(Dispatchers.IO) {
                if (song.source == "local") Paths.get(song.hash) to "本地文件"
                else {
                    val audio = quality.resolvePlayback(song, CookieStore.level) ?: error("当前账号没有该歌曲的完整播放权限")
                    val path = files.fetch(audio.url) { bytes, total -> scope.launch { status.text = "缓冲 " + audio.quality.label + " · " + if (total > 0) (bytes * 100 / total).toString() + "%" else (bytes / 1024).toString() + " KiB" } }
                    path to audio.quality.label
                }
            }
            currentPath = path; duration = withContext(Dispatchers.IO) { Decoder.duration(path) }
            now.text = song.nm + " · " + song.ar + "  [" + label + "]"
            library.played(song); player.play(path); updatePause(); lyric.text = "♪"
            launch {
                lyrics = withContext(Dispatchers.IO) {
                    if (song.source == "local") {
                        val lrc = Paths.get(song.hash.substringBeforeLast('.') + ".lrc")
                        if (Files.isRegularFile(lrc)) top.nekoh2o.player.lyric.LyricParser.parse(String(Files.readAllBytes(lrc), Charsets.UTF_8), null) else emptyList()
                    } else music.lyric(song)
                }
            }
        }
    }
    private fun advance(direction: Int, ended: Boolean = false) {
        if (queue.isEmpty()) return
        val index = queue.indexOfFirst { Library.key(it) == current?.let(Library::key) }
        val next = when {
            ended && repeat.selectedIndex == 2 -> index
            repeat.selectedIndex == 3 -> queue.indices.random()
            repeat.selectedIndex == 1 -> Math.floorMod(index + direction, queue.size)
            else -> index + direction
        }
        if (next in queue.indices) start(queue[next], queue) else { player.stop(); lyric.text = "播放结束" }
    }
    private fun tick() {
        val position = player.position
        if (!progress.valueIsAdjusting) { updatingSlider = true; progress.value = if (duration > 0) (position / duration * 1000).toInt().coerceIn(0,1000) else 0; updatingSlider = false }
        time.text = clock(position) + " / " + clock(duration)
        lyrics.lastOrNull { it.time <= position }?.let { lyric.text = it.text + (it.translation?.let { t -> "  ·  " + t } ?: "") }
    }
    private fun updatePause() { pause.text = if (player.paused) "继续" else "暂停" }
    private fun openLocal() {
        val chooser = JFileChooser().apply { isMultiSelectionEnabled = true; fileFilter = FileNameExtensionFilter("音频文件", "mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "ape", "wma") }
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            val songs = chooser.selectedFiles.map { Song(it.absolutePath.hashCode().toLong(), it.nameWithoutExtension, "本地音乐", source = "local", hash = it.absolutePath) }
            showSongs("本地音乐", songs); songs.firstOrNull()?.let { start(it, songs) }
        }
    }
    private fun downloadDirectory() = Paths.get(DesktopPaths.preferences.getString("downloads", Paths.get(System.getProperty("user.home"), "Music", "NekoPlayer").toString())!!)
    private fun download(song: Song) {
        if (song.source == "local") { message(this, "歌曲已在本地。"); return }
        work("正在检查可下载音质…") {
            val choices = withContext(Dispatchers.IO) { quality.downloadable(song) }
            require(choices.isNotEmpty()) { "当前账号没有可下载音质" }
            val labels = choices.map { it.label + if (it.size > 0) " · " + (it.size / 1024 / 1024) + " MiB" else "" }.toTypedArray()
            val selected = JOptionPane.showInputDialog(this@PlayerWindow, "选择已验证的可下载音质", "下载", JOptionPane.QUESTION_MESSAGE, null, labels, labels.first()) as? String ?: return@work
            val audio = withContext(Dispatchers.IO) { quality.resolveDownload(song, choices[labels.indexOf(selected)].id) } ?: error("所选音质的下载权限已变化，请重试")
            val dir = downloadDirectory(); Files.createDirectories(dir)
            val name = (song.ar + " - " + song.nm).map { if (it.code < 32 || it in "\\/:*?\"<>|") '_' else it }.joinToString("").take(140).trimEnd(' ', '.')
            val ext = java.net.URI(audio.url).path.substringAfterLast('.', "").lowercase().takeIf { it in setOf("mp3","flac","m4a","aac","wav","ogg","opus") } ?: "audio"
            val chooser = JFileChooser(dir.toFile()).apply { selectedFile = dir.resolve(name + "." + ext).toFile() }
            if (chooser.showSaveDialog(this@PlayerWindow) != JFileChooser.APPROVE_OPTION) return@work
            val destination = chooser.selectedFile.toPath()
            if (Files.exists(destination) && JOptionPane.showConfirmDialog(this@PlayerWindow, "覆盖已有文件？", "下载", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return@work
            status.text = "正在下载…"
            withContext(Dispatchers.IO) { val cached = files.fetch(audio.url); Files.copy(cached, destination, StandardCopyOption.REPLACE_EXISTING) }
            message(this@PlayerWindow, "下载完成：" + destination)
        }
    }
    private fun playlists() {
        val options = arrayOf("新建本地歌单", "打开本地歌单", "账号云歌单", "从本站合并收藏与歌单", "同步到本站")
        when (JOptionPane.showInputDialog(this, "歌单管理", "我的歌单", JOptionPane.PLAIN_MESSAGE, null, options, options[1])) {
            options[0] -> JOptionPane.showInputDialog(this, "歌单名称")?.takeIf { it.isNotBlank() }?.let { library.addPlaylist(it) }
            options[1] -> choosePlaylist()?.let { showSongs(it.name, it.songs) }
            options[2] -> work("正在读取云歌单…") {
                if (source.selectedIndex == 1) {
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
        if (items.isEmpty()) { message(this, "暂无内容"); return null }
        val names = items.mapIndexed { i, it -> (i + 1).toString() + ". " + label(it) }.toTypedArray()
        val answer = JOptionPane.showInputDialog(this, title, title, JOptionPane.PLAIN_MESSAGE, null, names, names[0]) ?: return null
        return items[names.indexOf(answer)]
    }
    private fun choosePlaylist() = choose(library.data.playlists, { it.name }, "本地歌单")
    private fun addToPlaylist(song: Song) { choosePlaylist()?.let { library.addToPlaylist(it.id, song); status.text = "已加入歌单" } }
    private fun settings() {
        val levels = QualityLevel.entries.toTypedArray()
        val selected = JComboBox(levels.map { it.label }.toTypedArray()).apply { selectedIndex = levels.indexOfFirst { it.value == CookieStore.level }.coerceAtLeast(0) }
        val directory = JTextField(downloadDirectory().toString(), 35)
        val panel = JPanel(GridLayout(0,1,8,8)).apply { add(JLabel("优先使用所选音质；不支持时选择最高可用音质")); add(selected); add(JLabel("下载目录")); add(directory); add(JLabel("桌面输出：48 kHz / 16-bit 双声道。下载保留原始音质。")) }
        if (JOptionPane.showConfirmDialog(this, panel, "设置", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) work("保存设置…") {
            withContext(Dispatchers.IO) { val path = Paths.get(directory.text.trim()); Files.createDirectories(path); DesktopPaths.preferences.edit().putString("downloads", path.toString()).apply(); CookieStore.setLevel(levels[selected.selectedIndex].value) }
        }
    }
    companion object { fun clock(seconds: Double) = "%02d:%02d".format(seconds.toInt().coerceAtLeast(0) / 60, seconds.toInt().coerceAtLeast(0) % 60) }
}
