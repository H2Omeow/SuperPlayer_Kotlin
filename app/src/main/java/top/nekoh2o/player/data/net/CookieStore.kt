package top.nekoh2o.player.data.net

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.dataStore by preferencesDataStore("player_prefs")

/**
 * 网易云 cookie 管理 + 酷狗 token + SSO app_token：
 * - userCookie：网易云QR/手动登录得到，优先使用（对应 web napcat_nc_cookie）
 * - guestCookie：网易云首启拉 /register/anonimous 兜底（对应 nc_guest_cookie）
 * - kgToken：酷狗音乐登录token
 * - kgPlatform：酷狗音乐平台类型（0=原版，1=概念版）
 * - level：音质档位
 * - appToken：SSO 登录后服务端下发的 Bearer token，App 用它鉴权 player 域名接口
 * 除 DataStore 落地外，额外用内存缓存，供拦截器/播放线程同步读取。
 */
object CookieStore {

    private val KEY_USER = stringPreferencesKey("nc_cookie")
    private val KEY_GUEST = stringPreferencesKey("nc_guest_cookie")
    private val KEY_LEVEL = stringPreferencesKey("quality_level")
    private val KEY_APP_TOKEN = stringPreferencesKey("app_token")
    private val KEY_KG_TOKEN = stringPreferencesKey("kg_token")
    private val KEY_KG_PLATFORM = stringPreferencesKey("kg_platform")
    private val KEY_KG_USERID = stringPreferencesKey("kg_userid")
    private val KEY_KG_DFID = stringPreferencesKey("kg_dfid")

    private lateinit var appContext: Context
    @Volatile private var userCookie: String = ""
    @Volatile private var guestCookie: String = ""
    @Volatile private var appToken: String = ""
    @Volatile private var kgToken: String = ""
    @Volatile private var kgPlatform: String = "0"  // 0=原版，1=概念版
    @Volatile private var kgUserid: String = ""
    @Volatile private var kgDfid: String = ""
    @Volatile var level: String = "exhigh"
        private set
    private val ready = CompletableDeferred<Unit>()
    private val initMutex = Mutex()

    suspend fun init(context: Context) {
        initMutex.withLock {
            if (ready.isCompleted) return

            appContext = context.applicationContext
            val prefs = appContext.dataStore.data.first()
            userCookie = prefs[KEY_USER] ?: ""
            guestCookie = prefs[KEY_GUEST] ?: ""
            appToken = prefs[KEY_APP_TOKEN] ?: ""
            kgToken = prefs[KEY_KG_TOKEN] ?: ""
            kgPlatform = prefs[KEY_KG_PLATFORM] ?: "0"
            kgUserid = prefs[KEY_KG_USERID] ?: ""
            kgDfid = prefs[KEY_KG_DFID] ?: ""
            level = prefs[KEY_LEVEL] ?: "exhigh"

            android.util.Log.d("CookieStore", "init() - appToken loaded: ${appToken.take(20)}... (len=${appToken.length})")
            android.util.Log.d("CookieStore", "init() - kgToken loaded: ${kgToken.take(20)}... (len=${kgToken.length})")

            ready.complete(Unit)
        }
    }

    suspend fun awaitReady() {
        ready.await()
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

    fun hasNcUserCookie(): Boolean = userCookie.isNotEmpty()

    // ==================== SSO app_token ====================
    fun appTokenValue(): String {
        android.util.Log.d("CookieStore", "appTokenValue() called - returning: ${appToken.take(20)}... (len=${appToken.length})")
        return appToken
    }

    suspend fun setAppToken(token: String) {
        android.util.Log.d("CookieStore", "setAppToken() - saving: ${token.take(20)}... (len=${token.length})")
        appToken = token
        appContext.dataStore.edit { it[KEY_APP_TOKEN] = token }
    }

    suspend fun clearAppToken() {
        appToken = ""
        appContext.dataStore.edit { it.remove(KEY_APP_TOKEN) }
    }

    // ==================== 酷狗 token ====================
    fun kgTokenValue(): String = kgToken

    suspend fun setKgToken(token: String) {
        android.util.Log.d("CookieStore", "setKgToken() - saving: ${token.take(20)}... (len=${token.length})")
        kgToken = token
        appContext.dataStore.edit { it[KEY_KG_TOKEN] = token }
    }

    suspend fun clearKgToken() {
        kgToken = ""
        appContext.dataStore.edit { it.remove(KEY_KG_TOKEN) }
    }

    fun hasKgToken(): Boolean = kgToken.isNotEmpty()

    // 酷狗平台类型：0=原版，1=概念版
    fun kgPlatformValue(): Int = kgPlatform.toIntOrNull() ?: 0

    suspend fun setKgPlatform(platform: Int) {
        kgPlatform = platform.toString()
        appContext.dataStore.edit { it[KEY_KG_PLATFORM] = kgPlatform }
    }

    // 酷狗 userid
    fun kgUseridValue(): String = kgUserid

    suspend fun setKgUserid(userid: String) {
        kgUserid = userid
        appContext.dataStore.edit { it[KEY_KG_USERID] = userid }
    }

    // 酷狗 dfid
    fun kgDfidValue(): String = kgDfid

    suspend fun setKgDfid(dfid: String) {
        kgDfid = dfid
        appContext.dataStore.edit { it[KEY_KG_DFID] = dfid }
    }

    // 构建酷狗 cookie 字符串: token=xxx;userid=xxx;dfid=xxx
    fun kgCookieValue(): String {
        val parts = mutableListOf<String>()
        if (kgToken.isNotEmpty()) parts.add("token=$kgToken")
        if (kgUserid.isNotEmpty()) parts.add("userid=$kgUserid")
        if (kgDfid.isNotEmpty()) parts.add("dfid=$kgDfid")
        return parts.joinToString(";")
    }
}
