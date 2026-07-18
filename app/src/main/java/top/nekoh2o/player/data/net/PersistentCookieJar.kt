package top.nekoh2o.player.data.net

import android.content.Context
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.ConcurrentHashMap

class PersistentCookieJar(context: Context) : CookieJar {

    private val prefs = context.applicationContext
        .getSharedPreferences("cookies", Context.MODE_PRIVATE)

    private val cache = ConcurrentHashMap<String, MutableList<Cookie>>()

    init {
        prefs.all.forEach { (host, raw) ->
            val serialized = raw as? String ?: return@forEach
            val url = "https://$host/".toHttpUrlOrNull()
                ?: return@forEach

            val cookies = serialized
                .split('\u0000')
                .filter(String::isNotBlank)
                .mapNotNull { Cookie.parse(url, it) }

            if (cookies.isNotEmpty()) {
                cache[host] = cookies.toMutableList()
            }
        }
    }

    override fun saveFromResponse(
        url: HttpUrl,
        cookies: List<Cookie>
    ) {
        val host = url.host
        val list = cache.getOrPut(host) { mutableListOf() }

        cookies.forEach { cookie ->
            list.removeAll { existing ->
                existing.name == cookie.name &&
                    existing.domain == cookie.domain &&
                    existing.path == cookie.path
            }
            list.add(cookie)
        }

        persist(host, list)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val host = url.host
        val list = cache[host] ?: return emptyList()

        val valid = list.filter { it.expiresAt > now }

        if (valid.size != list.size) {
            list.clear()
            list.addAll(valid)
            persist(host, list)
        }

        return valid.filter { it.matches(url) }
    }

    private fun persist(host: String, cookies: List<Cookie>) {
        prefs.edit()
            .putString(
                host,
                cookies.joinToString("\u0000") { it.toString() }
            )
            .apply()
    }
    // 从 WebView CookieManager 的 "a=b; c=d" 头注入，用于 SSO 登录后桥接会话
    fun saveRawCookies(host: String, cookieHeader: String) {
        val url = "https://$host/".toHttpUrlOrNull() ?: return
        val list = cache.getOrPut(host) { mutableListOf() }
        cookieHeader.split(";").forEach { part ->
            val c = Cookie.parse(url, part.trim()) ?: return@forEach
            list.removeAll { it.name == c.name }
            list.add(c)
        }
        persist(host, list)
    }

    fun clear() {
        cache.clear()
        prefs.edit().clear().apply()
    }
}
