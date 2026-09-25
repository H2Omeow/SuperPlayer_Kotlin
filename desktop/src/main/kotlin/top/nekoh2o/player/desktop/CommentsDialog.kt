package top.nekoh2o.player.desktop

import kotlinx.coroutines.*
import top.nekoh2o.player.data.model.Song
import top.nekoh2o.player.data.repo.*
import java.awt.*
import javax.swing.*

internal class CommentsDialog(owner: JFrame, private val original: Song) : AsyncDialog(owner, "歌曲评论 · " + original.nm) {
    private val repository = CommentsRepository()
    private val provider = JComboBox(arrayOf("网易云", "酷狗", "本站"))
    private val model = DefaultListModel<SongComment>()
    private val list = JList(model)
    private val editor = JTextArea(4, 40).apply { lineWrap = true; wrapStyleWord = true }
    private var song = original
    private var account = ""
    private var page = CommentPage(emptyList(), 0, false)
    private var parent: SongComment? = null
    private var offset = 0
    private var cursor = ""
    private var loading: Job? = null
    private val next = button("下一页") { if (page.more) { offset += 20; cursor = page.nextCursor; reload() } }
    private val like = button("点赞 / 取消") { list.selectedValue?.let { comment -> mutate { repository.like(source(), song, comment, page) } } }
    private val delete = button("删除本人评论") { list.selectedValue?.let { comment ->
        if (JOptionPane.showConfirmDialog(this, "删除这条评论？", "确认删除", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) mutate { repository.delete(source(), song, comment, page) }
    } }
    init {
        size = Dimension(780,650); setLocationRelativeTo(owner)
        val root = JPanel(BorderLayout(8,8)).apply { border = BorderFactory.createEmptyBorder(14,14,14,14) }; contentPane = root
        root.add(row(provider, button("刷新") { reload() }, button("返回主评论") { parent = null; offset = 0; cursor = ""; reload() }, next), BorderLayout.NORTH)
        list.cellRenderer = ListCellRenderer { _, value, _, selected, _ ->
            JTextArea(value.userName + "  ·  " + value.time + "  ·  ♥ " + value.likes + "  ·  回复 " + value.replies +
                "\n" + value.content + if (value.quotedContent.isNotBlank()) "\n↳ " + value.quotedUser + ": " + value.quotedContent else "").apply {
                isEditable = false; lineWrap = true; wrapStyleWord = true; border = BorderFactory.createEmptyBorder(10,10,10,10)
                background = if (selected) list.selectionBackground else list.background; foreground = list.foreground
                font = list.font; columns = 46
            }
        }
        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        root.add(JScrollPane(list))
        val bottom = JPanel(BorderLayout(6,6))
        bottom.add(row(button("查看回复") { list.selectedValue?.let { parent = it; offset = 0; cursor = ""; reload() } }, like, delete), BorderLayout.NORTH)
        bottom.add(JScrollPane(editor))
        bottom.add(JPanel(BorderLayout()).apply { add(row(button("发送评论") { send(false) }, button("回复选中评论") { send(true) }), BorderLayout.NORTH); add(status, BorderLayout.SOUTH) }, BorderLayout.SOUTH)
        root.add(bottom, BorderLayout.SOUTH)
        provider.selectedIndex = if (original.source == "kugou") 1 else 0
        provider.addActionListener { changeSource() }
        list.addListSelectionListener { delete.isEnabled = source() != "kugou" && list.selectedValue?.owned == true }
        reload()
    }
    private fun source() = arrayOf("netease", "kugou", "site")[provider.selectedIndex]
    private fun changeSource() {
        loading?.cancel(); parent = null; offset = 0; cursor = ""; model.clear()
        loading = task("正在匹配歌曲…") {
            val selectedSource = source()
            val matches = withContext(Dispatchers.IO) { repository.matches(original, selectedSource) }
            require(matches.isNotEmpty()) { "该平台没有匹配歌曲" }
            song = if (selectedSource == "site" || selectedSource == original.source) original else {
                val names = matches.mapIndexed { index, item -> (index + 1).toString() + ". " + item.nm + " — " + item.ar }.toTypedArray()
                val choice = JOptionPane.showInputDialog(this@CommentsDialog, "请选择该平台对应歌曲，避免展示错误评论", "匹配歌曲", JOptionPane.PLAIN_MESSAGE, null, names, names[0]) ?: return@task
                matches[names.indexOf(choice)]
            }
            loadPage()
        }
    }
    private fun reload() { loading?.cancel(); loading = task("加载评论…") { loadPage() } }
    private suspend fun loadPage() {
        provider.isEnabled = false; next.isEnabled = false; like.isEnabled = false; delete.isEnabled = false
        try {
            val selectedSource = source(); val selectedSong = song
            account = withContext(Dispatchers.IO) { repository.accountId(selectedSource) }
            page = withContext(Dispatchers.IO) { repository.list(selectedSource, selectedSong, account, offset, parent, page.specialId, cursor) }
            model.clear(); page.comments.forEach(model::addElement)
            status.text = (if (parent == null) "主评论" else "回复") + " · 共 " + page.total + " 条 · 第 " + (offset / 20 + 1) + " 页" +
                if (source() == "kugou") " · 酷狗暂不支持点赞/删除" else ""
            next.isEnabled = page.more; like.isEnabled = source() != "kugou"
        } finally { provider.isEnabled = true }
    }
    private fun send(reply: Boolean) {
        val content = editor.text
        val target = if (reply) list.selectedValue ?: return else parent
        mutate { repository.send(source(), song, content, target, page); editor.text = "" }
    }
    private fun mutate(block: suspend () -> Unit) {
        if (loading?.isActive == true) return
        loading = task("提交操作…") {
            provider.isEnabled = false
            try { block(); loadPage() } finally { provider.isEnabled = true }
        }
    }
}
