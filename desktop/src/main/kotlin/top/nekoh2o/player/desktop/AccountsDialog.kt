package top.nekoh2o.player.desktop

import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import top.nekoh2o.player.data.net.*
import top.nekoh2o.player.data.repo.*
import top.nekoh2o.player.data.model.KugouAccountSelection
import java.awt.*
import java.awt.event.*
import java.net.*
import java.util.Base64
import javax.swing.*
import com.sun.net.httpserver.HttpServer

internal open class AsyncDialog(owner: JFrame, title: String) : JDialog(owner, title, false) {
    protected val scope = CoroutineScope(SupervisorJob() + Dispatchers.Swing)
    protected val status = JLabel(" ")
    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE
        addWindowListener(object : WindowAdapter() { override fun windowClosed(e: WindowEvent) { scope.cancel() } })
    }
    protected fun task(label: String, block: suspend CoroutineScope.() -> Unit): Job = scope.launch {
        status.text = label
        try { block() } catch (e: CancellationException) { throw e }
        catch (e: Throwable) { status.text = "操作未完成"; failure(this@AsyncDialog, e) }
    }
}

internal class AccountsDialog(owner: JFrame) : AsyncDialog(owner, "账号与 Cookie") {
    private val platform = JComboBox(arrayOf("酷狗原版", "酷狗概念版"))
    private val phone = JTextField(16)
    private val code = JTextField(8)
    private val image = JLabel("点击扫码登录生成二维码", SwingConstants.CENTER)
    private val state = JLabel()
    private val kg = KugouRepository()
    private val nc = MusicRepository()
    private var qrJob: Job? = null
    private var smsJob: Job? = null
    private var cooldown: Job? = null
    private val sendSms = button("获取验证码") { sendCode() }
    private var loginServer: HttpServer? = null
    init {
        size = Dimension(780, 580); setLocationRelativeTo(owner)
        val root = JPanel(BorderLayout(12, 12)).apply { border = BorderFactory.createEmptyBorder(16,16,16,16) }
        contentPane = root
        val tabs = JTabbedPane()
        val netease = JPanel(GridLayout(0,1,6,6)).apply {
            add(JLabel("网易云：扫码登录或导入 MUSIC_U Cookie"))
            add(row(button("网易云扫码登录") { qr(false) }, button("管理网易云 Cookie") { cookies(null) }))
        }
        val kugou = JPanel(GridLayout(0,1,6,6)).apply {
            add(row(platform, button("设为当前平台") { val p = platform.selectedIndex; task("切换平台…") { withContext(Dispatchers.IO) { CookieStore.setKgPlatform(p) }; refresh() } }))
            add(row(JLabel("手机号"), phone, sendSms))
            add(row(JLabel("验证码"), code, button("短信登录") { smsLogin() }))
            add(row(button("酷狗扫码登录") { qr(true) }, button("管理该平台 Cookie") { cookies(platform.selectedIndex) }))
            add(JLabel("原版与概念版会话独立保存；登录后可设为当前平台。"))
        }
        val site = JPanel(GridLayout(0,1,6,6)).apply {
            add(JLabel("本站账号：使用浏览器登录，返回后验证账号身份。"))
            add(row(button("浏览器登录本站") { siteLogin() }, button("导入登录令牌 / 回调链接") { importSiteToken() }))
            add(row(button("检查本站账号") { task("检查账号…") { val user = withContext(Dispatchers.IO) { ApiFactory.user.me() }; require(user.code == 0 && user.user != null) { "本站会话无效，请重新登录" }; status.text = "本站：" + (user.user!!.nickname ?: user.user!!.username) } },
                button("退出本站") { task("退出登录…") { withContext(Dispatchers.IO) { runCatching { ApiFactory.user.logout() }; CookieStore.clearAppToken() }; refresh() } }))
        }
        tabs.addTab("网易云", netease); tabs.addTab("酷狗", kugou); tabs.addTab("本站", site)
        tabs.addChangeListener { qrJob?.cancel(); image.icon = null; image.text = "点击扫码登录生成二维码" }
        platform.selectedIndex = CookieStore.kgPlatformValue()
        platform.addActionListener { qrJob?.cancel(); image.icon = null; image.text = "请使用所选平台的手机应用扫码"; refresh() }
        root.add(state, BorderLayout.NORTH); root.add(tabs, BorderLayout.CENTER)
        image.preferredSize = Dimension(310, 320); root.add(image, BorderLayout.EAST); root.add(status, BorderLayout.SOUTH)
        addWindowListener(object : WindowAdapter() { override fun windowClosed(e: WindowEvent) { loginServer?.stop(0) } })
        refresh()
    }
    private fun refresh() {
        state.text = "网易云 Cookie：" + ifText(CookieStore.hasNcUserCookie()) + "   酷狗原版：" + ifText(CookieStore.kgTokenValue(0).isNotBlank()) +
            "   概念版：" + ifText(CookieStore.kgTokenValue(1).isNotBlank()) + "   本站令牌：" + ifText(CookieStore.appTokenValue().isNotBlank())
        status.text = "当前酷狗平台：" + if (CookieStore.kgPlatformValue() == 0) "原版" else "概念版"
    }
    private fun ifText(present: Boolean) = if (present) "已保存" else "未保存"
    private fun sendCode() {
        if (smsJob?.isActive == true || cooldown?.isActive == true) return
        val p = platform.selectedIndex; val number = phone.text.trim(); sendSms.isEnabled = false
        smsJob = task("正在发送验证码…") {
            try {
                withContext(Dispatchers.IO) { kg.sendCode(number, p) }; status.text = "验证码已发送"
                cooldown = scope.launch {
                    for (seconds in 60 downTo 1) { sendSms.text = seconds.toString() + " 秒后重试"; delay(1000) }
                    sendSms.text = "获取验证码"; sendSms.isEnabled = true
                }
            } finally { if (cooldown?.isActive != true) sendSms.isEnabled = true }
        }
    }
    private fun smsLogin() {
        if (smsJob?.isActive == true) return
        val p = platform.selectedIndex; val number = phone.text.trim(); val verification = code.text.trim()
        smsJob = task("正在登录…") {
            try { withContext(Dispatchers.IO) { kg.login(number, verification, p) } }
            catch (e: KugouAccountSelection) {
                val names = e.accounts.map { it.nickname + " (" + it.userid + ")" }.toTypedArray()
                val selected = JOptionPane.showInputDialog(this@AccountsDialog, "选择手机号绑定的账号", "酷狗", JOptionPane.PLAIN_MESSAGE, null, names, names.first()) ?: return@task
                withContext(Dispatchers.IO) { kg.login(number, verification, p, e.accounts[names.indexOf(selected)].userid.toString()) }
            }
            withContext(Dispatchers.IO) { CookieStore.setKgPlatform(p) }; code.text = ""; refresh(); status.text = "酷狗登录成功"
        }
    }
    private fun qr(isKugou: Boolean) {
        qrJob?.cancel(); val p = platform.selectedIndex
        qrJob = task("生成二维码…") {
            val kgSession = if (isKugou) withContext(Dispatchers.IO) { kg.createLoginQR(p) } else null
            val ncKey = if (!isKugou) withContext(Dispatchers.IO) { nc.qrKey() ?: error("无法生成网易云二维码") } else ""
            val encoded = kgSession?.image ?: withContext(Dispatchers.IO) { nc.qrCreate(ncKey) ?: error("二维码图片为空") }
            image.icon = ImageIcon(Base64.getDecoder().decode(encoded.substringAfter(','))).let { ImageIcon(it.image.getScaledInstance(300,300,Image.SCALE_SMOOTH)) }; image.text = ""
            try {
                withTimeout(180000) {
                    while (true) {
                        delay(2000)
                        val result = withContext(Dispatchers.IO) { if (kgSession != null) kg.checkLoginQR(kgSession).status else nc.qrCheck(ncKey) }
                        if ((isKugou && result == 4) || (!isKugou && result == 803)) {
                            if (isKugou) withContext(Dispatchers.IO) { CookieStore.setKgPlatform(p) }
                            refresh(); status.text = "扫码登录成功"; image.icon = null; image.text = "登录成功"; break
                        }
                        if ((!isKugou && result == 800) || (isKugou && result == 0)) { status.text = "二维码已过期，请重新生成"; break }
                        status.text = if (result == 2 || result == 802) "已扫码，请在手机上确认" else "等待扫码…"
                    }
                }
            } catch (_: TimeoutCancellationException) { status.text = "二维码已过期，请重新生成" }
        }
    }
    private fun cookies(p: Int?) {
        val value = if (p == null) CookieStore.userCookieValue() else CookieStore.kgCookieValue(p)
        val text = JTextArea(value, 8, 48).apply { lineWrap = true; wrapStyleWord = true }
        val panel = JPanel(BorderLayout(8,8)).apply { add(JLabel("凭据仅保存在本机。可以复制、替换导入或清空。"), BorderLayout.NORTH); add(JScrollPane(text)) }
        val options = arrayOf("保存 / 导入", "退出账号", "清空所有 Cookie", "取消")
        val choice = JOptionPane.showOptionDialog(this, panel, if (p == null) "网易云 Cookie" else if (p == 0) "酷狗原版 Cookie" else "酷狗概念版 Cookie", JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0])
        if (choice !in 0..2) return
        task("保存凭据…") {
            withContext(Dispatchers.IO) {
                if (p == null) {
                    val raw = if (choice == 0) text.text.trim().removePrefix("Cookie:").trim() else ""
                    require(raw.length <= 16384 && raw.none { it.code < 32 || it.code > 126 }) { "Cookie 必须是单行文本" }
                    require(raw.isEmpty() || raw.split(';').any { it.trim().startsWith("MUSIC_U=") && it.trim().length > 8 }) { "请提供含 MUSIC_U 的登录 Cookie" }
                    CookieStore.setUserCookie(raw)
                } else when(choice) { 0 -> CookieStore.kgSessions.importCookie(p, text.text); 1 -> CookieStore.kgSessions.clearLogin(p); 2 -> CookieStore.kgSessions.clearCookie(p) }
            }
            refresh()
        }
    }
    private suspend fun acceptSiteToken(token: String) {
        require(token.length in 16..16384 && token.none { it.isWhitespace() }) { "登录令牌格式无效" }
        withContext(Dispatchers.IO) {
            val previous = CookieStore.appTokenValue()
            try {
                CookieStore.setAppToken(token)
                val result = ApiFactory.user.me()
                require(result.code == 0 && result.user != null) { "登录令牌无效或已过期" }
            } catch (e: Throwable) { CookieStore.setAppToken(previous); throw e }
        }
        refresh(); status.text = "本站登录成功"
    }
    private fun importSiteToken() {
        val raw = JOptionPane.showInputDialog(this, "粘贴本站登录令牌或 nekoplayer://auth 回调链接")?.trim() ?: return
        task("验证本站账号…") { acceptSiteToken(if (raw.startsWith("nekoplayer://auth?")) parseQuery(URI(raw).rawQuery)["token"].orEmpty() else raw) }
    }
    private fun siteLogin() {
        loginServer?.stop(0)
        task("等待浏览器登录…") {
            val server = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0)
            loginServer = server
            val path = "/auth/" + java.util.UUID.randomUUID().toString()
            val token = CompletableDeferred<String>()
            server.createContext(path) { exchange ->
                val result = if (exchange.requestMethod == "GET" && exchange.requestURI.path == path) parseQuery(exchange.requestURI.rawQuery)["token"] else null
                val ok = !result.isNullOrBlank() && result.length <= 16384
                val bytes = (if (ok) "You may return to NekoPlayer. Your session is being verified." else "Invalid login callback.").toByteArray()
                exchange.responseHeaders.set("Content-Type", "text/plain; charset=utf-8")
                exchange.responseHeaders.set("Cache-Control", "no-store")
                exchange.sendResponseHeaders(if (ok) 200 else 400, bytes.size.toLong()); exchange.responseBody.use { it.write(bytes) }; exchange.close()
                if (ok) token.complete(result!!)
            }
            server.start()
            try {
                val redirect = "http://127.0.0.1:" + server.address.port + path
                Desktop.getDesktop().browse(URI("https://account.nekoh2o.top/login?redirect=" + URLEncoder.encode(redirect, "UTF-8")))
                try { acceptSiteToken(withTimeout(180000) { token.await() }) }
                catch (_: TimeoutCancellationException) { status.text = "登录等待超时，可重新登录或导入回调链接。" }
            } finally { server.stop(0); if (loginServer === server) loginServer = null }
        }
    }
    companion object {
        private fun parseQuery(query: String?) = query.orEmpty().split('&').mapNotNull { item ->
            val pair = item.split('=', limit = 2); if (pair.size == 2) pair[0] to runCatching { URLDecoder.decode(pair[1], "UTF-8") }.getOrDefault("") else null
        }.toMap()
    }
}
