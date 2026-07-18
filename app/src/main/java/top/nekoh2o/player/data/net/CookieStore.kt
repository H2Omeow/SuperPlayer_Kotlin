package top.nekoh2o.player.data.net

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.dataStore by preferencesDataStore("player_prefs")

/**
 * 网易云 cookie 管理 + SSO app_token：
 * - userCookie：QR/手动登录得到，优先使用（对应 web napcat_nc_cookie）
 * - guestCookie：首启拉 /register/anonimous 兜底（对应 nc_guest_cookie）
 * - level：音质档位
 * - appToken：SSO 登录后服务端下发的 Bearer token，App 用它鉴权 player 域名接口
 * 除 DataStore 落地外，额外用内存缓存，供拦截器/播放线程同步读取。
 */
object CookieStore {

    private val KEY_USER = stringPreferencesKey("nc_cookie")
    private val KEY_GUEST = stringPreferencesKey("nc_guest_cookie")
    private val KEY_LEVEL = stringPreferencesKey("quality_level")
    private val KEY_APP_TOKEN = stringPreferencesKey("app_token")

    private lateinit var appContext: Context

    @Volatile private var userCookie: String = ""
    @Volatile private var guestCookie: String = ""
    @Volatile private var appToken: String = ""
    @Volatile var level: String = "exhigh"
        private set

    suspend fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = appContext.dataStore.data.first()
        userCookie = prefs[KEY_USER] ?: ""
        guestCookie = prefs[KEY_GUEST] ?: ""
        appToken = prefs[KEY_APP_TOKEN] ?: ""
        level = prefs[KEY_LEVEL] ?: "exhigh"
    }

    // 播放线程同步读取：用户 cookie 优先，否则游客 cookie
    fun activeCookie(): String = userCookie.ifEmpty { guestCookie }

    // 仅用户 cookie（用于云端同步，不含游客）
    fun userCookieValue(): String = userCookie

    suspend fun setUserCookie(cookie: String) {
        userCookie = cookie
        appContext.dataStore.edit { it[KEY_USER] = cookie }
    }

    suspend fun setGuestCookie(cookie: String) {
        guestCookie = cookie
        appContext.dataStore.edit { it[KEY_GUEST] = cookie }
    }

    suspend fun setLevel(value: String) {
        level = value
        appContext.dataStore.edit { it[KEY_LEVEL] = value }
    }

    fun hasAnyCookie(): Boolean = activeCookie().isNotEmpty()

    // ==================== SSO app_token ====================
    fun appTokenValue(): String = appToken

    suspend fun setAppToken(token: String) {
        appToken = token
        appContext.dataStore.edit { it[KEY_APP_TOKEN] = token }
    }

    suspend fun clearAppToken() {
        appToken = ""
        appContext.dataStore.edit { it.remove(KEY_APP_TOKEN) }
    }
}
