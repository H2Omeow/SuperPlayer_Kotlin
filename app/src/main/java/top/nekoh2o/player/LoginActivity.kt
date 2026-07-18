package top.nekoh2o.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import top.nekoh2o.player.data.net.ApiFactory

/**
 * SSO 登录 WebView：
 * - 处理第三方登录的 app scheme 跳转（wtloginmqq:// 等），转 Intent 拉起 App。
 * - 登录成功后（回到 player 域名且拿到 sp.sid），把 cookie 桥接进 OkHttp CookieJar 并关闭。
 */
class LoginActivity : ComponentActivity() {

    private val accountCenter = "https://account.nekoh2o.top"
    private val playerHost = "player.nekoh2o.top"
    private val loginUrl =
        "$accountCenter/login?redirect=" +
            Uri.encode("https://player.nekoh2o.top/auth/sso/callback")

    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CookieManager.getInstance().setAcceptCookie(true)

        val web = WebView(this)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true

        web.webViewClient = object : WebViewClient() {

            // 拦截 URL 加载：非 http/https 的 scheme 交给系统拉起对应 App
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString() ?: return false
                return handleUrl(url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                tryFinishIfLoggedIn(url)
            }
        }

        setContentView(web)
        web.loadUrl(loginUrl)
    }

    // 处理非 http(s) 的 scheme（QQ/微信/微博等）
    private fun handleUrl(url: String): Boolean {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            // http(s) 交给 WebView 自己加载
            return false
        }
        // app scheme：尝试拉起对应 App
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            true
        } catch (e: Exception) {
            Toast.makeText(this, "未安装对应应用", Toast.LENGTH_SHORT).show()
            true
        }
    }

    // 检测是否登录完成：回到 player 域名且 cookie 里有 sp.sid
    private fun tryFinishIfLoggedIn(url: String?) {
        if (finished || url == null) return
        // 已经回到 player 域名（callback 后会 302 到根路径）
        if (!url.contains(playerHost)) return
        val cookies = CookieManager.getInstance().getCookie("https://$playerHost")
        if (!cookies.isNullOrEmpty() && cookies.contains("sp.sid")) {
            finished = true
            // 关键：把 WebView 的 cookie 刷到持久化再读取，避免异步丢失
            CookieManager.getInstance().flush()
            ApiFactory.cookieJar.saveRawCookies(playerHost, cookies)
            setResult(RESULT_OK)
            finish()
        }
    }
}
