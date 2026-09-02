package top.nekoh2o.player.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import top.nekoh2o.player.data.model.Song
import top.nekoh2o.player.data.net.CookieStore
import top.nekoh2o.player.data.repo.KugouRepository

/**
 * 酷狗账号管理 ViewModel
 * 负责：酷狗登录、账号管理、音乐源切换、QQ 登录集成
 */
class KugouAccountViewModel(app: Application) : AndroidViewModel(app) {

    private val kgRepo = KugouRepository()

    init {
        // 确保 dfid 初始化
        viewModelScope.launch {
            kgRepo.ensureInitialized()
        }
    }

    // ==================== 酷狗登录 ====================

    /**
     * 发送验证码
     */
    suspend fun sendCode(phone: String): Boolean {
        return kgRepo.sendCode(phone)
    }

    /**
     * 验证码登录
     */
    suspend fun login(phone: String, code: String, platform: Int): LoginResult {
        CookieStore.setKgPlatform(platform)
        val data = kgRepo.login(phone, code)
        return if (data != null) {
            CookieStore.setKgUserid(data.userid.toString())
            CookieStore.setKgDfid(data.dfid)
            LoginResult(success = true)
        } else {
            LoginResult(success = false, error = "登录失败")
        }
    }

    /**
     * Token 登录
     */
    suspend fun loginWithToken(mid: String, token: String, platform: Int): LoginResult {
        CookieStore.setKgPlatform(platform)
        CookieStore.setKgToken(token)
        CookieStore.setKgUserid(mid)

        val dfid = kgRepo.getDfid()
        if (dfid != null) {
            CookieStore.setKgDfid(dfid)
        }

        return LoginResult(success = true)
    }

    /**
     * QQ 授权登录
     */
    suspend fun loginWithQQ(openid: String, accessToken: String): LoginResult {
        val data = kgRepo.loginWithQQ(openid, accessToken)
        return if (data != null) {
            LoginResult(success = true)
        } else {
            LoginResult(success = false, error = "QQ登录失败")
        }
    }

    // ==================== QQ 扫码登录 ====================

    /**
     * 创建 QQ 扫码登录二维码
     */
    suspend fun createQQLoginQR(): top.nekoh2o.player.data.model.KgQQQRCreateData? {
        return kgRepo.createQQLoginQR()
    }

    /**
     * 检查 QQ 扫码登录状态
     */
    suspend fun checkQQLoginQR(qrData: top.nekoh2o.player.data.model.KgQQQRCreateData): top.nekoh2o.player.data.model.KgQQQRCheckResp? {
        return kgRepo.checkQQLoginQR(qrData)
    }

    // ==================== 账号信息 ====================

    /**
     * 获取酷狗用户信息
     */
    suspend fun getUserInfo(): UserInfo? {
        val userInfo = kgRepo.getUserInfo() ?: return null
        val vipInfo = kgRepo.getVipInfo()

        return UserInfo(
            userId = userInfo.userid,
            nickname = userInfo.nickname,
            avatar = userInfo.avatar,
            vipType = vipInfo?.vipType ?: 0,
            vipEndTime = vipInfo?.endTime ?: 0,
            platform = CookieStore.kgPlatformValue()
        )
    }

    /**
     * 领取酷狗 VIP（概念版）
     */
    suspend fun receiveVip(vipType: Int, days: Int): String? {
        return kgRepo.receiveVip(vipType, days)
    }

    // ==================== 平台切换 ====================

    /**
     * 切换酷狗平台版本
     * @param platform 0=原版 1=概念版
     */
    fun switchPlatform(platform: Int, onSchedulePush: () -> Unit) {
        viewModelScope.launch {
            CookieStore.setKgPlatform(platform)
            onSchedulePush()
        }
    }

    /**
     * 获取当前平台
     */
    fun getCurrentPlatform(): Int {
        return CookieStore.kgPlatformValue()
    }

    // ==================== Token 管理 ====================

    /**
     * 保存酷狗 Token
     */
    fun saveToken(token: String, platform: Int, onSchedulePush: () -> Unit) {
        viewModelScope.launch {
            CookieStore.setKgToken(token.trim())
            CookieStore.setKgPlatform(platform)
            onSchedulePush()
        }
    }

    /**
     * 获取当前 Token
     */
    fun getToken(): String {
        return CookieStore.kgTokenValue()
    }

    // ==================== 酷狗数据获取 ====================

    /**
     * 获取酷狗推荐歌曲
     */
    suspend fun getRecommendSongs(): List<Song> {
        return runCatching { kgRepo.getRecommendSongs() }.getOrDefault(emptyList())
    }

    /**
     * 搜索酷狗歌曲
     */
    suspend fun search(keyword: String, page: Int): List<Song> {
        return runCatching { kgRepo.search(keyword, page) }.getOrDefault(emptyList())
    }

    /**
     * 获取搜索建议
     */
    suspend fun searchSuggest(keyword: String): List<String> {
        return runCatching { kgRepo.searchSuggest(keyword) }.getOrDefault(emptyList())
    }
}

/**
 * 登录结果
 */
data class LoginResult(
    val success: Boolean,
    val error: String? = null
)

/**
 * 用户信息
 */
data class UserInfo(
    val userId: Long,
    val nickname: String,
    val avatar: String?,
    val vipType: Int,
    val vipEndTime: Long,
    val platform: Int
)
