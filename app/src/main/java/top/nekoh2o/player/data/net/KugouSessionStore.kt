package top.nekoh2o.player.data.net

import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.HttpUrl

/** Separate standard/lite cookies; neither session shares the application's SSO cookie jar. */
class KugouSessionStore(private val prefs: SharedPreferences) {
    private val sessions = Array(2) { platform -> parse(prefs.getString("cookies_" + platform, "").orEmpty()) }

    @Synchronized fun value(platform: Int, name: String): String = sessions[checked(platform)][name].orEmpty()
    @Synchronized fun cookie(platform: Int): String = sessions[checked(platform)].entries.joinToString("; ") { it.key + "=" + it.value }

    @Synchronized fun merge(platform: Int, values: Map<String, String>) {
        val session = sessions[checked(platform)]
        values.filterKeys(::allowed).forEach { (key, value) ->
            if (value.isBlank() || value in listOf("undefined", "null") || value.any { it == ';' || it.code == 13 || it.code == 10 }) {
                session.remove(key)
            } else session[key] = value
        }
        prefs.edit().putString("cookies_" + platform, cookie(platform)).apply()
    }

    @Synchronized fun mergeResponse(platform: Int, url: HttpUrl, headers: List<String>, credentials: Boolean = false) {
        val values = headers.mapNotNull { Cookie.parse(url, it) }.filter {
            allowed(it.name) && (credentials || it.name !in AUTH_NAMES)
        }.associate { it.name to if (it.expiresAt <= System.currentTimeMillis()) "" else it.value }
        merge(platform, values)
    }

    @Synchronized fun commitLogin(platform: Int, url: HttpUrl, headers: List<String>, token: String, userid: Long, dfid: String) {
        val values = AUTH_NAMES.associateWith { "" }.toMutableMap()
        headers.mapNotNull { Cookie.parse(url, it) }.filter { allowed(it.name) }.forEach {
            values[it.name] = if (it.expiresAt <= System.currentTimeMillis()) "" else it.value
        }
        values["token"] = token
        values["userid"] = userid.toString()
        if (dfid.isNotBlank()) values["dfid"] = dfid
        merge(platform, values)
    }

    @Synchronized fun clearLogin(platform: Int) = merge(platform, AUTH_NAMES.associateWith { "" })

    @Synchronized fun migrate(platform: Int, token: String, userid: String, dfid: String) {
        if (prefs.getBoolean("legacy_migrated", false)) return
        if (cookie(platform).isEmpty()) merge(platform, mapOf("token" to token, "userid" to userid, "dfid" to dfid))
        prefs.edit().putBoolean("legacy_migrated", true).apply()
    }

    private fun checked(platform: Int): Int { require(platform in 0..1); return platform }

    companion object {
        private val AUTH_NAMES = setOf("token", "userid", "vip_type", "vip_token", "t1")
        private fun allowed(name: String) = name in AUTH_NAMES || name == "dfid" || name.startsWith("KUGOU_API_")
        private fun parse(raw: String): MutableMap<String, String> = raw.split(';').mapNotNull { part ->
            val i = part.indexOf('=')
            if (i <= 0) return@mapNotNull null
            val key = part.substring(0, i).trim()
            val value = part.substring(i + 1).trim()
            if (!allowed(key) || value.isEmpty() || value in listOf("undefined", "null")) null else key to value
        }.toMap().toMutableMap()
    }
}
