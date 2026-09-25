package top.nekoh2o.player.data.net

import top.nekoh2o.player.desktop.DesktopPaths
import top.nekoh2o.player.desktop.FilePreferences

object CookieStore {
    private val prefs by lazy { FilePreferences(DesktopPaths.home.resolve("accounts.properties")) }
    val kgSessions by lazy { KugouSessionStore(FilePreferences(DesktopPaths.home.resolve("kugou.properties"))) }
    suspend fun awaitReady() = Unit
    private fun value(key: String) = prefs.getString(key, "").orEmpty()
    private fun put(key: String, value: String) = prefs.edit().putString(key, value).apply()
    fun activeCookie() = userCookieValue().ifBlank { value("nc_guest") }
    fun userCookieValue() = value("nc_user")
    suspend fun setUserCookie(cookie: String) = put("nc_user", cookie)
    suspend fun setGuestCookie(cookie: String) = put("nc_guest", cookie)
    val level get() = value("quality").ifBlank { "exhigh" }
    suspend fun setLevel(value: String) = put("quality", value)
    fun hasAnyCookie() = activeCookie().isNotBlank()
    fun hasNcUserCookie() = userCookieValue().isNotBlank()
    fun appTokenValue() = value("site_token")
    suspend fun setAppToken(token: String) = put("site_token", token)
    suspend fun clearAppToken() = put("site_token", "")
    fun kgPlatformValue() = value("kg_platform").toIntOrNull()?.takeIf { it in 0..1 } ?: 0
    suspend fun setKgPlatform(platform: Int) { require(platform in 0..1); put("kg_platform", platform.toString()) }
    fun kgTokenValue(platform: Int = kgPlatformValue()) = kgSessions.value(platform, "token")
    fun kgUseridValue(platform: Int = kgPlatformValue()) = kgSessions.value(platform, "userid")
    fun kgDfidValue(platform: Int = kgPlatformValue()) = kgSessions.value(platform, "dfid")
    fun kgCookieValue(platform: Int = kgPlatformValue()) = kgSessions.cookie(platform)
    fun hasKgToken() = kgTokenValue().isNotBlank()
    suspend fun setKgToken(value: String) = kgSessions.merge(kgPlatformValue(), mapOf("token" to value))
    suspend fun setKgUserid(value: String) = kgSessions.merge(kgPlatformValue(), mapOf("userid" to value))
    suspend fun setKgDfid(value: String) = kgSessions.merge(kgPlatformValue(), mapOf("dfid" to value))
    suspend fun clearKgToken() = kgSessions.clearLogin(kgPlatformValue())
}
